package org.example.frontend.test;

import org.example.frontend.manager.DBManager;
import org.example.frontend.manager.DaoManager;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.Message;


public class Test {
  public static void main(String[] args) {

    String username = "alice";

    DBManager.initInstance(username);

    DaoManager.init();

    DaoManager.getChatRoomDao().deleteAll();
    DaoManager.getMessageDao().deleteAll();


    DBManager.getInstance().close();
  }
}
