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
  private String roomId; // генерируем рандомно уникальный
  private String owner;
  private String otherUser;
  private String lastMessage; // это последнее сообщение как в тг для отрисовки
  private long lastMessageTime; // это последнее время сообщение как в тг для отрисовки

  private String cipher;
  private String cipherMode;
  private String paddingMode;
  private String iv;
  private String keyBitLength;

  public String getInterlocutor(String currentUser) {
    if (currentUser.equals(owner)) {
      return otherUser;
    } else if (currentUser.equals(otherUser)) {
      return owner;
    }
    return "Unknown";
  }
}
