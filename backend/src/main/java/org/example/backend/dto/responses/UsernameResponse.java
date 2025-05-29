package org.example.backend.dto.responses;

import lombok.*;

@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class UsernameResponse {
    private String username;
}
