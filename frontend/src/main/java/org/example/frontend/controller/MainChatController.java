package org.example.frontend.controller;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.dialog.ChatSettingsDialog;
import org.example.frontend.httpToSpring.ChatApiClient;
import org.example.frontend.manager.DBManager;
import org.example.frontend.manager.DaoManager;
import org.example.frontend.manager.GrpcClient;
import org.example.frontend.manager.SceneManager;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.ChatSetting;
import org.example.frontend.model.main.Message;
import org.example.frontend.model.main.User;
import org.example.frontend.utils.MessageUtils;
import org.example.frontend.utils.RoomTokenEncoder;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class MainChatController {

  private final GrpcClient grpcClient = GrpcClient.getInstance();

  @FXML
  private TitledPane chatDetailsPane;
  @FXML
  private Label cipherLabel;
  @FXML
  private Label modeLabel;
  @FXML
  private Label paddingLabel;
  @FXML
  private Label ivLabel;
  @FXML
  private Label userLabel;
  @FXML
  private Button logoutButton;
  @FXML
  public TextField searchField;
  @FXML
  private Button searchButton;
  @FXML
  private ListView<ChatRoom> chatListView;
  @FXML
  private Label chatTitleLabel;
  @FXML
  private Label chatStatusLabel;
  @FXML
  private ScrollPane messagesScrollPane;
  @FXML
  private VBox messagesContainer;
  @FXML
  private TextField messageInputField;
  @FXML
  private Button sendButton;
  @FXML
  public VBox searchResultsPanel;
  @FXML
  public ListView<String> searchResultsListView;
  @FXML
  private Button closeSearchButton;

  private ChatRoom currentChat;

  private List<ChatRoom> chatRooms = new ArrayList<>();

  private final String currentUserName = JwtStorage.getUsername();

  public void initialize() {
    DBManager.initInstance(currentUserName);
    DaoManager.init();

    chatDetailsPane.setDisable(true);

    chatRooms = DaoManager.getChatRoomDao().findAll();
    updateChatListUI();

    chatListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 1) {
        ChatRoom room = chatListView.getSelectionModel().getSelectedItem();
        if (room != null) {
          openChat(room);
        }
      }
    });

    chatListView.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(ChatRoom room, boolean empty) {
        super.updateItem(room, empty);
        if (empty || room == null) {
          setText(null);
        } else {
          setText(room.getOtherUser() + (room.getLastMessage() != null ? " — " + room.getLastMessage() + " - " + MessageUtils.formatTime(room.getLastMessageTime()) : ""));
        }
      }
    });

    grpcClient.connect(
            currentUserName,
            msg -> Platform.runLater(() -> {
                      switch(msg.getType()) {
                        case ChatProto.MessageType.TEXT -> handleTextMessage(msg);
                        case ChatProto.MessageType.INIT_ROOM -> handleInitRoomMessage(msg);
                        default -> throw new UnsupportedOperationException("Unsupported chat type: " + msg.getType());
                      }
            }
                    ),
            () -> log.info("Disconnected from server"),
            Throwable::printStackTrace
    );


    searchResultsPanel.setVisible(false);
  }

  private void handleUnknownMessage(ChatProto.ChatMessage msg) {
  }

  private void handleInitRoomMessage(ChatProto.ChatMessage msg) {
    // TODO проверка на наличие комнаты такой
    //grpcClient.sendInitRoomRequest(msg.) //TODO тут не менять отправителя и получателя местами!
  }

  private void handleTextMessage(ChatProto.ChatMessage msg) {
    // TODO нужно извлечь из токена roomId и записать в нужное место
  }

  @FXML
  private void onLogoutClick() {
    JwtStorage.setUsername(null);
    JwtStorage.setToken(null);
    try {
      grpcClient.shutdown();
      SceneManager.switchToLoginScene();
    } catch (IOException e) {
      throw new RuntimeException("Failed to log out: " + e);
    }
  }

  @FXML
  public void onSearchClick() {
    log.info("Search clicked");
    String searchText = searchField.getText().trim().toLowerCase();
    List<String> allUsers = ChatApiClient.getOnlineUsers().stream()
            .map(User::getUsername)
            .filter(u -> !u.equals(currentUserName))
            .collect(Collectors.toList());

    List<String> filteredUsers = searchText.isEmpty()
            ? allUsers
            : allUsers.stream()
            .filter(u -> u.toLowerCase().contains(searchText))
            .collect(Collectors.toList());

    searchResultsListView.setItems(FXCollections.observableArrayList(filteredUsers));
    searchResultsPanel.setVisible(true);

    searchResultsListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 2) {
        String selectedUser = searchResultsListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null && !selectedUser.equals(currentUserName)) {
          createNewChatRoom(selectedUser);
          onCloseSearchClick();
        }
      }
    });
  }

  private void createNewChatRoom(String username) {
    log.info("Creating new chat with: {}", username);
    ChatSettingsDialog dialog = new ChatSettingsDialog();
    Optional<ChatSetting> result = dialog.showAndWait();

    String guid = UUID.randomUUID().toString();
    result.ifPresent(settings -> {
      log.info("Cipher: {}", settings.getCipher());
      log.info("Cipher mode: {}", settings.getCipherMode());
      log.info("Padding mode: {}", settings.getPaddingMode());
      log.info("IV: {}", settings.getIv());

      String token = RoomTokenEncoder.encode(
              guid,
              settings.getCipher(),
              settings.getCipherMode(),
              settings.getPaddingMode(),
              settings.getIv()
      );

      ChatRoom chatRoom = ChatRoom.builder()
              .roomId(guid)
              .owner(currentUserName)
              .otherUser(username)
              .cipher(settings.getCipher())
              .cipherMode(settings.getCipherMode())
              .paddingMode(settings.getPaddingMode())
              .iv(settings.getIv())
              .build();

      chatRooms.add(chatRoom);
      DaoManager.getChatRoomDao().insert(chatRoom);

      updateChatListUI();
      sendInitRoomRequest(username, token, guid);

      openChat(chatRoom);
    });
  }

  private void updateChatListUI() {
    chatListView.setItems(FXCollections.observableArrayList(chatRooms));
  }

  private void openChat(ChatRoom room) {
    this.currentChat = room;

    chatDetailsPane.setDisable(false);

    cipherLabel.setText("Cipher: " + room.getCipher());
    modeLabel.setText("Mode: " + room.getCipherMode());
    paddingLabel.setText("Padding: " + room.getPaddingMode());
    ivLabel.setText("IV: " + room.getIv());

    chatTitleLabel.setText(room.getOtherUser());
    chatStatusLabel.setText("Online"); // пока захардкожено, можно расширить

    messagesContainer.getChildren().clear();

    List<Message> messages = DaoManager.getMessageDao().findByRoomId(room.getRoomId());
    for (Message msg : messages) {
      messagesContainer.getChildren().add(createMessageBubble(msg));
    }

    messagesScrollPane.layout();
    messagesScrollPane.setVvalue(1.0);
  }

  private Node createMessageBubble(Message message) {
    Label label = new Label(String.format("[%s] %s: %s",
            Instant.ofEpochMilli(message.getTimestamp()),
            message.getSender(),
            message.getContent()
    ));
    label.setWrapText(true);
    label.setMaxWidth(300);
    label.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 10; -fx-background-radius: 10;");
    return label;
  }

  private void sendInitRoomRequest(String toUser, String token, String roomId) {

    //TODO : послать запрос на создание комнаты у другого юзера + там же диффи хелман

    String g = "5";
    String p = "23";

    String publicComponent = ""; //  = DiffieHellman.generatePublicComponent(g, p);

    boolean accepted = grpcClient.sendInitRoomRequest(
            currentUserName, toUser, token, publicComponent
    );

    if (accepted) {
      log.info("User {} accepted room creation", toUser);
    } else {
      log.warn("User {} rejected room creation", toUser);
    }

  }

  @FXML
  private void onSendMessage() {

    // TODO токен надо всегда генерировать в зависимости если у нас чтото помннялось в комнате
    String text = messageInputField.getText().trim();
    if (text.isEmpty()) return;
    messageInputField.clear();

    String recipient = currentUserName.equals("max") ? "sasha" : "max";

    boolean delivered = grpcClient.sendMessage(currentUserName, recipient, text);
    if (delivered) {
      Platform.runLater(() -> log.info(
              "[{}] from: {}: {}",
              Instant.now(),
              currentUserName,
              text
      ));
    }
  }

  @FXML
  private void onCloseSearchClick() {
    searchResultsPanel.setVisible(false);
    searchResultsListView.getItems().clear();
    searchField.clear();
  }

  @FXML
  private void onEditSettingsClick() {
    if (currentChat == null) return;

    ChatSettingsDialog dialog = new ChatSettingsDialog();
    dialog.setTitle("Edit Encryption Settings");

    dialog.setInitialSettings(currentChat);

    Optional<ChatSetting> result = dialog.showAndWait();
    result.ifPresent(settings -> {
      currentChat.setCipher(settings.getCipher());
      currentChat.setCipherMode(settings.getCipherMode());
      currentChat.setPaddingMode(settings.getPaddingMode());
      currentChat.setIv(settings.getIv());

      DaoManager.getChatRoomDao().update(currentChat);

      //TODO : послать запрос на изменение комнаты у другого юзера или просто послать сообщение новое
      // в котором будет наш токен в котором уже будет обновленная информация

      openChat(currentChat);
    });
  }
}
