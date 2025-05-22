package org.example.backend.services;

import lombok.RequiredArgsConstructor;
import org.example.backend.models.UserDb;
import org.example.backend.repository.UserDbRepository;
import org.example.backend.security.JwtService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserDbRepository userDbRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String register(String username, String password) {
        if (userDbRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already in use");
        }

        UserDb user = new UserDb();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userDbRepository.save(user);

        return jwtService.generateToken(user);
    }
}
