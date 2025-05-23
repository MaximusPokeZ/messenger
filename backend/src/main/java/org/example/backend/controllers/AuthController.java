package org.example.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.backend.dto.requests.UserRequest;
import org.example.backend.dto.responses.TokenResponse;
import org.example.backend.services.RegisterLoginService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final RegisterLoginService registerLoginService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse register(@RequestBody UserRequest userRequest) {
        return registerLoginService.register(userRequest.getUsername(), userRequest.getPassword());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse login(@RequestBody UserRequest userRequest) {
        return registerLoginService.login(userRequest.getUsername(), userRequest.getPassword());
    }


}
