package org.example.frontend.model.main;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatItem {
  private int id;
  private String chatName;
  private String lastMessage;
  private String lastMessageTime;
  private boolean read;
}
