package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.entity.OAuthIdentity;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.OAuthIdentityMapper;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.OAuthAuthenticationService;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.UserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OAuthAuthenticationServiceImpl implements OAuthAuthenticationService {

    private static final Set<String> PROVIDERS = Set.of("google", "github");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final OAuthIdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final OAuthProperties oauthProperties;
    private final Duration tokenTtl;

    public OAuthAuthenticationServiceImpl(UserMapper userMapper,
                                          OAuthIdentityMapper identityMapper,
                                          PasswordEncoder passwordEncoder,
                                          JwtUtil jwtUtil,
                                          StringRedisTemplate redis,
                                          OAuthProperties oauthProperties,
                                          @Value("${app.jwt.ttl}") long tokenTtlMillis) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redis = redis;
        this.oauthProperties = oauthProperties;
        this.tokenTtl = Duration.ofMillis(tokenTtlMillis);
    }

    @Override
    @Transactional
    public User authenticateProvider(String provider, Map<String, Object> attributes) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalizedProvider)) {
            throw new BusinessException("Unsupported sign-in provider");
        }

        String subject = stringValue(attributes.get(normalizedProvider.equals("google") ? "sub" : "id"));
        String email = stringValue(attributes.get("email"));
        boolean verified = Boolean.TRUE.equals(attributes.get("email_verified"));
        if (subject == null || subject.isBlank() || email == null || email.isBlank() || !verified) {
            throw new BusinessException("A verified provider identity is required");
        }

        OAuthIdentity identity = identityMapper.findByProviderSubject(normalizedProvider, subject);
        User user;
        if (identity != null) {
            user = userMapper.findById(identity.getUserId());
            if (user == null) {
                throw new BusinessException("The linked account is no longer available");
            }
            identityMapper.touchLastLogin(identity.getId());
        } else {
            user = userMapper.findByEmail(email.trim().toLowerCase(Locale.ROOT));
            if (user == null) {
                user = createUser(normalizedProvider, subject, email, attributes);
            }
            identity = new OAuthIdentity();
            identity.setUserId(user.getId());
            identity.setProvider(normalizedProvider);
            identity.setProviderSubject(subject);
            identityMapper.insert(identity);
        }

        if (oauthProperties.isBootstrapAdminEmail(email)
                && user.getEmail() != null
                && user.getEmail().trim().equalsIgnoreCase(email.trim())
                && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            userMapper.promoteBootstrapAdmin(user.getId());
            user = userMapper.findById(user.getId());
            if (user == null) {
                throw new BusinessException("The account could not be loaded after administrator provisioning");
            }
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("This account is disabled");
        }
        if (user.getRole() == null
                || !Set.of("ADMIN", "USER").contains(user.getRole().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("This account has no valid access role");
        }
        return user;
    }

    @Override
    public String createExchangeCode(Long userId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(
                RedisKeyConstant.OAUTH_EXCHANGE_PREFIX + code,
                String.valueOf(userId),
                Duration.ofSeconds(Math.min(180, Math.max(10, oauthProperties.getExchangeCodeTtlSeconds())))
        );
        return code;
    }

    @Override
    public LoginVO exchange(String code) {
        if (code == null || code.length() < 32 || code.length() > 128) {
            throw new BusinessException("Sign-in session expired. Please try again.");
        }
        String userIdValue = redis.opsForValue().getAndDelete(RedisKeyConstant.OAUTH_EXCHANGE_PREFIX + code);
        if (userIdValue == null) {
            throw new BusinessException("Sign-in session expired. Please try again.");
        }

        User user;
        try {
            user = userMapper.findById(Long.parseLong(userIdValue));
        } catch (NumberFormatException exception) {
            throw new BusinessException("Sign-in session is invalid. Please try again.");
        }
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("This account is unavailable");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
        redis.opsForValue().set(RedisKeyConstant.LOGIN_TOKEN_PREFIX + token,
                String.valueOf(user.getId()), tokenTtl);

        LoginVO result = new LoginVO();
        result.setToken(token);
        result.setUser(toUserVO(user));
        return result;
    }

    private User createUser(String provider, String subject, String email, Map<String, Object> attributes) {
        User user = new User();
        user.setUsername("oauth_" + shortHash(provider + ":" + subject));
        user.setPasswordHash(passwordEncoder.encode(randomSecret()));
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setDisplayName(displayName(attributes, email));
        user.setRole(oauthProperties.isBootstrapAdminEmail(email) ? "ADMIN" : "USER");
        user.setStatus(1);
        user.setTokenVersion(0);
        userMapper.insertOAuthUser(user);
        return user;
    }

    private String displayName(Map<String, Object> attributes, String email) {
        String value = stringValue(attributes.get("name"));
        if (value == null || value.isBlank()) {
            value = stringValue(attributes.get("login"));
        }
        if (value == null || value.isBlank()) {
            value = email.substring(0, email.indexOf('@'));
        }
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private String randomSecret() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UserVO toUserVO(User user) {
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setEmail(user.getEmail());
        result.setRole(user.getRole());
        result.setStatus(user.getStatus());
        result.setCreatedTime(user.getCreatedTime());
        result.setUpdatedTime(user.getUpdatedTime());
        return result;
    }
}
