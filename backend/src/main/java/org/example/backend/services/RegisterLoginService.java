package org.example.backend.services;

import org.example.backend.dto.responses.TokenResponse;

public interface RegisterLoginService {
    TokenResponse register(String username, String password);
    TokenResponse login(String username, String password);
}
