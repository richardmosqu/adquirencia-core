package com.paguelofacil.adquirencia.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login compartido de un solo usuario. Las credenciales vienen de las
 * variables de entorno APP_USERNAME / APP_PASSWORD (con defaults locales).
 */
@Controller
public class LoginController {

    private final String appUsername;
    private final String appPassword;

    public LoginController(@Value("${app.username}") String appUsername,
                           @Value("${app.password}") String appPassword) {
        this.appUsername = appUsername;
        this.appPassword = appPassword;
    }

    /** Página de login (recurso estático login.html). */
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request) {
        if (appUsername.equals(username) && appPassword.equals(password)) {
            request.getSession(true).setAttribute(AuthFilter.SESSION_USER, username);
            return "redirect:/";
        }
        return "redirect:/login?error";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }
}
