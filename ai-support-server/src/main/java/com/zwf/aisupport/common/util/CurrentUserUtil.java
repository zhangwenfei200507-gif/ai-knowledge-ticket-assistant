package com.zwf.aisupport.common.util;

import com.zwf.aisupport.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    public static CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }

        return currentUser;
    }

    public static Long getCurrentUserId() {
        CurrentUser currentUser = getCurrentUser();
        return currentUser == null ? null : currentUser.getUserId();
    }
    public static String getCurrentUserRole() {
        CurrentUser currentUser = getCurrentUser();
        return currentUser == null ? null : currentUser.getRole();
    }
}