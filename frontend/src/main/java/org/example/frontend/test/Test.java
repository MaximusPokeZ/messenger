package org.example.frontend.test;

import org.example.frontend.manager.DBManager;
import org.example.frontend.model.DAO.ChatRoomDao;
import org.example.frontend.model.DAO.MessageDao;
import org.example.frontend.model.DAO.impl.ChatRoomDaoImpl;
import org.example.frontend.model.DAO.impl.MessageDaoImpl;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.Message;

import java.sql.Connection;

public class Test {
  public static void main(String[] args) {

    String username = "alice";

    DBManager dbManager = new DBManager(username);
    dbManager.init();

    Connection conn = dbManager.getConnection();
    ChatRoomDao chatRoomDao = new ChatRoomDaoImpl(conn);
    MessageDao messageDao = new MessageDaoImpl(conn);

    chatRoomDao.deleteAll();
    messageDao.deleteAll();

    ChatRoom room = new ChatRoom("room-alice-bob", "bob", "Привет!", System.currentTimeMillis());
    chatRoomDao.insert(room);

    Message message = new Message(0, "room-alice-bob", "alice", "Привет, Боб!", null, System.currentTimeMillis());
    messageDao.insert(message);

    System.out.println("=== Chat Rooms ===");
    chatRoomDao.findAll().forEach(r -> System.out.println(r.getRoomId() + ": " + r.getOtherUser()));

    System.out.println("=== Messages ===");
    messageDao.findByRoomId("room-alice-bob").forEach(m -> System.out.println(m.getSender() + ": " + m.getContent()));



    dbManager.close();
  }
}
