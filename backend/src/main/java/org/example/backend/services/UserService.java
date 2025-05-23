package org.example.backend.services;

import org.example.backend.models.UserDb;

import java.util.Optional;

public interface UserService {
    UserDb createUser(UserDb user);

    UserDb loadUserByUsername(String username);

    Optional<UserDb> findUserByUsername(String username);
}
