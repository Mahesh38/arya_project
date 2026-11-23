package com.smartgrievance.notification;

import com.smartgrievance.common.web.AdminFilters;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.user.CurrentUser;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notifications")
class NotificationController {
    private final NotificationService notifications;
    private final Flash flash;

    NotificationController(NotificationService notifications, Flash flash) {
        this.notifications = notifications;
        this.flash = flash;
    }

    @GetMapping
    String list(@RequestParam(required = false) String page, @RequestParam(required = false) String size,
                @AuthenticationPrincipal CurrentUser user, Model model) {
        var result = notifications.list(user, AdminFilters.page(page, size));
        model.addAttribute("page", result);
        model.addAttribute("pageLinks", AdminPageLinks.of("/notifications", result, Map.of()));
        return "notification/list";
    }

    @PostMapping("/{id}/open")
    String open(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
        return notifications.open(id, user).map(number -> "redirect:/complaints/" + number)
                .orElse("redirect:/notifications");
    }

    @PostMapping("/read-all")
    String readAll(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        notifications.markAllRead(user);
        flash.success(redirect, "flash.notifications.allRead");
        return "redirect:/notifications";
    }

    @GetMapping("/unread-count")
    ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("unread", notifications.unreadCount(user)));
    }
}
