package com.smartgrievance.otp;

import com.smartgrievance.common.web.Flash;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class VerifiedCitizenInterceptor implements HandlerInterceptor {
    private final Flash flash;

    public VerifiedCitizenInterceptor(Flash flash) {
        this.flash = flash;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ((request.getContextPath() + "/citizen/complaints").equals(request.getRequestURI())
                && !"POST".equals(request.getMethod())) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user
                && !user.mobileVerified()) {
            String target = request.getContextPath() + "/verify-mobile";
            flash.info(request, response, target, "flash.otp.verifyFirst");
            response.sendRedirect(target);
            return false;
        }
        return true;
    }
}
