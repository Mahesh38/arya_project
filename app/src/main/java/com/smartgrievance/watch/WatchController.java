package com.smartgrievance.watch;

import com.smartgrievance.common.web.Flash;
import com.smartgrievance.user.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class WatchController {
    private final WatchService watchers;
    private final Flash flash;

    WatchController(WatchService watchers, Flash flash) {
        this.watchers = watchers;
        this.flash = flash;
    }

    @PostMapping("/citizen/complaints/{no}/watch")
    String watch(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        watchers.watch(no, user);
        flash.success(redirect, "flash.watch.started");
        return "redirect:/complaints/" + no;
    }

    @PostMapping("/citizen/complaints/{no}/unwatch")
    String unwatch(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        boolean stillVisible = watchers.unwatch(no, user);
        flash.success(redirect, "flash.watch.stopped");
        return stillVisible ? "redirect:/complaints/" + no : "redirect:/citizen/watching";
    }
}
