package org.example.backend.repository;

import org.example.backend.dto.responses.UsernameResponse;
import org.example.backend.models.UserDb;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDbRepository extends JpaRepository<UserDb, String> {
    Optional<UserDb> findByUsername(String username);
    boolean existsByUsername(String username);
}
