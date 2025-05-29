package org.example.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.dto.responses.UsernameResponse;
import org.example.backend.services.impl.ChatServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
public class ForClientsController {
    private final ChatServiceImpl chatService;

    @GetMapping("/allOnline")
    @ResponseStatus(HttpStatus.OK)
    public List<UsernameResponse> allOnline() {
        return chatService.getOnlineUsernames();
    }
}
