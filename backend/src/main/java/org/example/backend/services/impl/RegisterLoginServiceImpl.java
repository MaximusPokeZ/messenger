package org.example.backend.services.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.dto.responses.TokenResponse;
import org.example.backend.models.UserDb;
import org.example.backend.services.RegisterLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterLoginServiceImpl implements RegisterLoginService {

    private final UserDbService userService;

    private final PasswordEncoder passwordEncoder;


    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.expiration-ms}")
    private Duration tokenLifeTimeInMinutes;

    @Override
    public TokenResponse register(String username, String password) {
        Date issuredDate = new Date();
        Date expiredDate = new Date(issuredDate.getTime() + tokenLifeTimeInMinutes.toMillis());

        if (userService.findUserByUsername(username).isPresent()) {
            log.info("there is such user in the table!");
            return new TokenResponse();
        }

        UserDb tmpUser = new UserDb();
        tmpUser.setUsername(username);
        tmpUser.setPassword(password);
        UserDb userFromDB = userService.createUser(tmpUser);


        Algorithm algorithm = Algorithm.HMAC256(secret);

        String jwtEncodedString = JWT.create()
                .withIssuer(issuer)
                .withSubject(userFromDB.getUsername())
                .withIssuedAt(issuredDate)
                .withExpiresAt(expiredDate)
                .sign(algorithm);

        return new TokenResponse(jwtEncodedString);
    }

    @Override
    public TokenResponse login(String username, String password) {

        log.info("login: {}", username);
        log.info("password: {}", password);

        Date issuredDate = new Date();
        Date expiredDate = new Date(issuredDate.getTime() + tokenLifeTimeInMinutes.toMillis());

        if (userService.findUserByUsername(username).isEmpty()) {
            log.info("there is no such user in the table!");
            return new TokenResponse();
        }


        UserDb userFromDB = userService.findUserByUsername(username).get();

        if (!passwordEncoder.matches(password, userFromDB.getPassword())) {
            log.info("password does not match!");
            return new TokenResponse();
        }


        Algorithm algorithm = Algorithm.HMAC256(secret);

        String jwtEncodedString = JWT.create()
                .withIssuer(issuer)
                .withSubject(userFromDB.getUsername())
                .withIssuedAt(issuredDate)
                .withExpiresAt(expiredDate)
                .sign(algorithm);

        log.info("token generated: {}", jwtEncodedString);

        return new TokenResponse(jwtEncodedString);
    }
}
