package org.example.frontend.model.DAO;

import org.example.frontend.model.main.Message;

import java.util.List;

public interface MessageDao {
  void createTable();
  void insert(Message message);
  List<Message> findByRoomId(String roomId);

  void deleteAll();
}
