package org.example.backend.services;

import org.example.backend.dto.responses.UsernameResponse;
import org.example.backend.models.UserDb;

import java.util.List;
import java.util.Optional;

public interface UserService {
    UserDb createUser(UserDb user);

    UserDb loadUserByUsername(String username);

    Optional<UserDb> findUserByUsername(String username);

//    void markUserAsOnline(String username);

//    void markUserAsOffline(String username);
//
//    List<UsernameResponse> getAllOnlineUsers();
}
