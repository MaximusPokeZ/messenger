package org.example.backend.security;

import java.util.List;

public class SecurityConstants {
    public static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/auth/**",
            "/h2-console/**"

    );

    private SecurityConstants() {
    }
}
