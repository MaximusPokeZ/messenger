package org.example.frontend.manager;

import lombok.Getter;
import org.example.frontend.model.DAO.impl.ChatRoomDaoImpl;
import org.example.frontend.model.DAO.impl.MessageDaoImpl;

public class DaoManager {
  @Getter
  private static ChatRoomDaoImpl chatRoomDao;
  @Getter
  private static MessageDaoImpl messageDao;

  public static void init() {
    chatRoomDao = new ChatRoomDaoImpl();
    messageDao = new MessageDaoImpl();
  }


  private DaoManager() {}
}
