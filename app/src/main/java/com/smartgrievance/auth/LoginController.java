package com.smartgrievance.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    @GetMapping("/")
    String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }
}
