package org.example.backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.dto.responses.UsernameResponse;
import org.example.backend.models.UserDb;
import org.example.backend.repository.UserDbRepository;
import org.example.backend.services.UserService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDbService implements UserService {
    private final UserDbRepository userDbRepository;


    @Override
    public UserDb createUser(UserDb user) {
        return userDbRepository.save(user);
    }

    @Override
    public UserDb loadUserByUsername(String username) {
        return findUserByUsername(username).orElseThrow(
                () -> new UsernameNotFoundException("Username " + username + " not found")
        );
    }

    @Override
    public Optional<UserDb> findUserByUsername(String username) {
        return userDbRepository.findByUsername(username);
    }

}
