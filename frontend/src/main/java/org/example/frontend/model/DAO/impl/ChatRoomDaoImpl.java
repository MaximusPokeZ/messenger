package org.example.frontend.model.DAO.impl;


import lombok.extern.slf4j.Slf4j;
import org.example.frontend.manager.DBManager;
import org.example.frontend.model.DAO.ChatRoomDao;
import org.example.frontend.model.main.ChatRoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ChatRoomDaoImpl implements ChatRoomDao {
  private final Connection connection;

  public ChatRoomDaoImpl() {
    this.connection = DBManager.getInstance().getConnection();
    createTable();
  }

  @Override
  public void createTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS chat_rooms (
                  room_id TEXT PRIMARY KEY,
                  owner TEXT NOT NULL,
                  other_user TEXT NOT NULL,
                  last_message TEXT,
                  last_message_time INTEGER,
                  cipher TEXT,
                  cipher_mode TEXT,
                  padding_mode TEXT,
                  iv TEXT
            );
            """;
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create chat_rooms table", e);
    }
  }

  @Override
  public void insert(ChatRoom room) {
    String sql = """
                INSERT INTO chat_rooms 
                (room_id, owner, other_user, last_message, last_message_time, cipher, cipher_mode, padding_mode, iv) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, room.getRoomId());
      stmt.setString(2, room.getOwner());
      stmt.setString(3, room.getOtherUser());
      stmt.setString(4, room.getLastMessage());
      stmt.setLong(5, room.getLastMessageTime());
      stmt.setString(6, room.getCipher());
      stmt.setString(7, room.getCipherMode());
      stmt.setString(8, room.getPaddingMode());
      stmt.setString(9, room.getIv());
      stmt.executeUpdate();

      log.info("Inserted chat_room {}", room.getRoomId());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert chat room", e);
    }
  }

  @Override
  public void updateLastMessage(String roomId, String message, long timestamp) {
    String sql = "UPDATE chat_rooms SET last_message = ?, last_message_time = ? WHERE room_id = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, message);
      stmt.setLong(2, timestamp);
      stmt.setString(3, roomId);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update last message", e);
    }
  }

  @Override
  public List<ChatRoom> findAll() {
    String sql = "SELECT * FROM chat_rooms ORDER BY last_message_time DESC";

    List<ChatRoom> rooms = new ArrayList<>();
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

      while (rs.next()) {
        rooms.add(ChatRoom.builder()
                .roomId(rs.getString("room_id"))
                .owner(rs.getString("owner"))
                .otherUser(rs.getString("other_user"))
                .lastMessage(rs.getString("last_message"))
                .lastMessageTime(rs.getLong("last_message_time"))
                .cipher(rs.getString("cipher"))
                .cipherMode(rs.getString("cipher_mode"))
                .paddingMode(rs.getString("padding_mode"))
                .iv(rs.getString("iv"))
                .build());
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve chat rooms", e);
    }
    return rooms;
  }

  @Override
  public Optional<ChatRoom> findByRoomId(String roomId) {
    String sql = "SELECT * FROM chat_rooms WHERE room_id = ?";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, roomId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(ChatRoom.builder()
                  .roomId(rs.getString("room_id"))
                  .owner(rs.getString("owner"))
                  .otherUser(rs.getString("other_user"))
                  .lastMessage(rs.getString("last_message"))
                  .lastMessageTime(rs.getLong("last_message_time"))
                  .cipher(rs.getString("cipher"))
                  .cipherMode(rs.getString("cipher_mode"))
                  .paddingMode(rs.getString("padding_mode"))
                  .iv(rs.getString("iv"))
                  .build());
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve chat room by ID", e);
    }
    return Optional.empty();
  }

  @Override
  public void delete(String roomId) {
    String sql = "DELETE FROM chat_rooms WHERE room_id = ?";
    try ( PreparedStatement pstmt = connection.prepareStatement(sql)) {
     pstmt.setString(1, roomId);
     pstmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete chat room by ID", e);
    }
  }

  @Override
  public void deleteAll() {
    String sql = "DELETE FROM chat_rooms";
    try (Statement stmt = connection.createStatement()) {
      int affectedRows = stmt.executeUpdate(sql);
      log.info("Chats removed: {}", affectedRows);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete all chat room", e);
    }
  }
}
