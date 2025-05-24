package org.example.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.backend.dto.requests.UserRequest;
import org.example.backend.dto.responses.TokenResponse;
import org.example.backend.dto.responses.UsernameResponse;
import org.example.backend.services.RegisterLoginService;
import org.example.backend.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final RegisterLoginService registerLoginService;

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse register(@RequestBody UserRequest userRequest) {
        return registerLoginService.register(userRequest.getUsername(), userRequest.getPassword());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse login(@RequestBody UserRequest userRequest) {
        TokenResponse response = registerLoginService.login(userRequest.getUsername(), userRequest.getPassword());
        if (response.getToken() != null) {
            userService.markUserAsOnline(userRequest.getUsername());
        }
        return response;
    }

    @GetMapping("/allOnline")
    @ResponseStatus(HttpStatus.OK)
    public List<UsernameResponse> allOnline() {
        return userService.getAllOnlineUsers();
    }


}
