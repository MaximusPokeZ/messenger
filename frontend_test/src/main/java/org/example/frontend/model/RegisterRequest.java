package org.example.frontend.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequest {
  private String username;
  private String password;
}
