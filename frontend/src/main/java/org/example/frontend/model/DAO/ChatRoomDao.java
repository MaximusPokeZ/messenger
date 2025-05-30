package org.example.frontend.model.DAO;

import org.example.frontend.model.main.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatRoomDao {
  void createTable();
  void insert(ChatRoom room);
  void updateLastMessage(String roomId, String message, long timestamp);

  void update(ChatRoom room);

  List<ChatRoom> findAll();
  Optional<ChatRoom> findByRoomId(String roomId);
  void delete(String roomId);

  void deleteAll();
}