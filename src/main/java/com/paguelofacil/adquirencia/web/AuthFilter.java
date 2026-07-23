package com.paguelofacil.adquirencia.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro de autenticación por sesión: todo requiere haber iniciado sesión,
 * salvo el login, el logout y los recursos estáticos públicos (css/js/img).
 * Las páginas se redirigen a /login; las llamadas /api responden 401.
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    /** Atributo de sesión que marca al usuario autenticado. */
    public static final String SESSION_USER = "authUser";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (isPublic(path) || isAuthenticated(req)) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            res.sendRedirect(req.getContextPath() + "/login");
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/login")
                || path.equals("/login.html")
                || path.equals("/logout")
                || path.equals("/error")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/favicon");
    }

    private boolean isAuthenticated(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && s.getAttribute(SESSION_USER) != null;
    }
}
