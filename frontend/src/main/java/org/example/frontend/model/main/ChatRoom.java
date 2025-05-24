package org.example.frontend.model.main;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {
  private String roomId;
  private String otherUser;
  private String lastMessage;
  private long lastMessageTime;
}
