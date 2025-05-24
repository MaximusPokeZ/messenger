package org.example.frontend.model.main;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Message {
  private long id;
  private String roomId;
  private String sender;
  private String content;
  private String filePath;
  private long timestamp;
}
