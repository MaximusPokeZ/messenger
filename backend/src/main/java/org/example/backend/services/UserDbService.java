package org.example.backend.services;

import lombok.RequiredArgsConstructor;
import org.example.backend.repository.UserDbRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDbService {
    private final UserDbRepository userDbRepository;

    public void setOnlineStatus(String username, boolean online) {
        userDbRepository.findByUsername(username).ifPresent(user -> {
            user.setOnline(online);
            userDbRepository.save(user);
        });
    }
}
