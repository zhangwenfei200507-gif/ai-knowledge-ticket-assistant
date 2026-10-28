package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.security.CurrentUser;
import com.zwf.aisupport.vo.AdminUserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private UserMapper userMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preventsRemovingTheLastActiveAdministrator() {
        authenticateAs(1L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper, new OAuthProperties());
        AdminUserVO target = user(2L, "ADMIN", 1, "admin-two@example.com");
        when(userMapper.findAdminUserById(2L)).thenReturn(target);
        when(userMapper.countActiveAdminUsers()).thenReturn(1L);

        assertThatThrownBy(() -> service.updateAccess(2L, "USER", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("At least one active administrator must remain");

        verify(userMapper, never()).updateAccess(2L, "USER", 1);
    }

    @Test
    void rejectsSelfModification() {
        authenticateAs(7L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper, new OAuthProperties());
        when(userMapper.findAdminUserById(7L)).thenReturn(user(7L, "ADMIN", 1, "admin@example.com"));

        assertThatThrownBy(() -> service.updateAccess(7L, "USER", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You cannot change your own account access");

        verify(userMapper, never()).updateAccess(7L, "USER", 0);
    }

    @Test
    void updatesOtherAccountsAndReturnsThePersistedAccessState() {
        authenticateAs(1L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper, new OAuthProperties());
        AdminUserVO before = user(8L, "USER", 1, "operator@example.com");
        AdminUserVO after = user(8L, "ADMIN", 1, "operator@example.com");
        when(userMapper.findAdminUserById(8L)).thenReturn(before, after);

        AdminUserVO result = service.updateAccess(8L, "admin", 1);

        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(userMapper).updateAccess(8L, "ADMIN", 1);
    }

    @Test
    void clampsPageSizeAndMarksConfiguredBootstrapAdministrator() {
        OAuthProperties properties = new OAuthProperties();
        properties.setBootstrapAdminEmails("zhangwenfei200507@gmail.com");
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper, properties);
        AdminUserVO admin = user(3L, "ADMIN", 1, "zhangwenfei200507@gmail.com");
        when(userMapper.countAdminUsers("", "", null)).thenReturn(1L);
        when(userMapper.findAdminUsers("", "", null, 100, 0L)).thenReturn(List.of(admin));

        var result = service.findUsers(null, null, null, 0, 500);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getItems()).singleElement().extracting(AdminUserVO::isBootstrapAdmin).isEqualTo(true);
    }

    private void authenticateAs(Long id) {
        CurrentUser currentUser = new CurrentUser(id, "admin", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, List.of()));
    }

    private AdminUserVO user(Long id, String role, int status, String email) {
        AdminUserVO result = new AdminUserVO();
        result.setId(id);
        result.setRole(role);
        result.setStatus(status);
        result.setEmail(email);
        return result;
    }
}
