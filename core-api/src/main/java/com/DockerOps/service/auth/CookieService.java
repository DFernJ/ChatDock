package com.DockerOps.service.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CookieService {

    @Autowired
    private JwtService jwtService;

    @Value("${app.security.cookie.secure}")
    private boolean secure;

    private static final String AUTH_COOKIE = "auth_cookie";


    public void setAuthCookie(HttpServletResponse response , String jwtToken) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE, jwtToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(jwtService.getExpirationMs()))
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("auth_cookie", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
