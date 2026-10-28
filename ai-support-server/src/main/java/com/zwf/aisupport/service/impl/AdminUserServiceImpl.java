package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.AdminUserService;
import com.zwf.aisupport.vo.AdminUserVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 120;

    private final UserMapper userMapper;
    private final OAuthProperties oauthProperties;

    public AdminUserServiceImpl(UserMapper userMapper, OAuthProperties oauthProperties) {
        this.userMapper = userMapper;
        this.oauthProperties = oauthProperties;
    }

    @Override
    public PageVO<AdminUserVO> findUsers(String keyword, String role, Integer status, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            normalizedKeyword = normalizedKeyword.substring(0, MAX_KEYWORD_LENGTH);
        }
        String normalizedRole = role == null || role.isBlank() ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!normalizedRole.isEmpty() && !List.of("ADMIN", "USER").contains(normalizedRole)) {
            throw new BusinessException("Role filter must be ADMIN or USER");
        }
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("Status filter must be active or disabled");
        }

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(1, page);
        long total = userMapper.countAdminUsers(normalizedKeyword, normalizedRole, status);
        long pageCount = Math.max(1, (total + safeSize - 1) / safeSize);
        safePage = (int) Math.min(safePage, pageCount);
        long offset = (long) (safePage - 1) * safeSize;
        List<AdminUserVO> users = userMapper.findAdminUsers(
                normalizedKeyword, normalizedRole, status, safeSize, offset);
        users.forEach(this::markBootstrapAdmin);
        return PageVO.of(users, safePage, safeSize, total);
    }

    @Override
    public AdminUserVO getUser(Long id) {
        AdminUserVO user = requireUser(id);
        markBootstrapAdmin(user);
        return user;
    }

    @Override
    @Transactional
    public AdminUserVO updateAccess(Long id, String role, Integer status) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "USER").contains(normalizedRole)) {
            throw new BusinessException("Role must be ADMIN or USER");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("Status must be active or disabled");
        }

        userMapper.lockActiveAdminUsers();
        AdminUserVO current = requireUser(id);
        if (id.equals(CurrentUserUtil.requireCurrentUserId())) {
            throw new BusinessException("You cannot change your own account access");
        }
        boolean bootstrapAdmin = oauthProperties.isBootstrapAdminEmail(current.getEmail());
        if (bootstrapAdmin && (!"ADMIN".equals(normalizedRole) || status != 1)) {
            throw new BusinessException("The configured bootstrap administrator must remain active");
        }

        boolean currentlyActiveAdmin = "ADMIN".equalsIgnoreCase(current.getRole())
                && Integer.valueOf(1).equals(current.getStatus());
        boolean remainsActiveAdmin = "ADMIN".equals(normalizedRole) && status == 1;
        if (currentlyActiveAdmin && !remainsActiveAdmin && userMapper.countActiveAdminUsers() <= 1) {
            throw new BusinessException("At least one active administrator must remain");
        }

        userMapper.updateAccess(id, normalizedRole, status);
        return getUser(id);
    }

    private AdminUserVO requireUser(Long id) {
        AdminUserVO user = userMapper.findAdminUserById(id);
        if (user == null) {
            throw new BusinessException("User account not found");
        }
        return user;
    }

    private void markBootstrapAdmin(AdminUserVO user) {
        user.setBootstrapAdmin(oauthProperties.isBootstrapAdminEmail(user.getEmail()));
    }
}
