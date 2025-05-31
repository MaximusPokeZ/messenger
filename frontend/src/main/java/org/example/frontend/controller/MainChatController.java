package org.example.frontend.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.dialog.ChatSettingsDialog;
import org.example.frontend.httpToSpring.ChatApiClient;
import org.example.frontend.manager.*;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.ChatSetting;
import org.example.frontend.model.main.Message;
import org.example.frontend.model.main.User;
import org.example.frontend.utils.DiffieHellman;
import org.example.frontend.utils.MessageUtils;
import org.example.frontend.utils.RoomTokenEncoder;
import org.example.shared.ChatProto;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private Button sendFileButton;
  @FXML
  public VBox searchResultsPanel;
  @FXML
  public ListView<String> searchResultsListView;
  @FXML
  private Button closeSearchButton;

  private ChatRoom currentChat;

  private List<ChatRoom> chatRooms = new ArrayList<>();

  private final Map<String, OutputStream> fileStreams = new HashMap<>();

  private final String currentUserName = JwtStorage.getUsername();

  public void initialize() {
    DBManager.initInstance(currentUserName);
    DaoManager.init();

    userLabel.setText(currentUserName);
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
                        case ChatProto.MessageType.FILE -> handleFileMessage(msg);
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
    String fromUser = msg.getFromUserName();
    String token = msg.getToken();
    String roomId = RoomTokenEncoder.decode(token).guid();

    Optional<ChatRoom> existing = DaoManager.getChatRoomDao().findByRoomId(roomId);

    if (existing.isEmpty()) {
      ChatRoom room = ChatRoom.builder()
              .roomId(roomId)
              .owner(currentUserName)
              .otherUser(fromUser)
              .cipher(RoomTokenEncoder.decode(token).cipher())
              .cipherMode(RoomTokenEncoder.decode(token).cipherMode())
              .paddingMode(RoomTokenEncoder.decode(token).paddingMode())
              .iv(RoomTokenEncoder.decode(token).IV())
              .build();

      DaoManager.getChatRoomDao().insert(room);
      chatRooms.add(room);
      updateChatListUI();

      String g = "5";
      String p = "23";
      DiffieHellman dh = new DiffieHellman(g, p);
      dh.getKey(new BigInteger(msg.getPublicExponent()));
      DiffieHellmanManager.put(roomId, dh);

      grpcClient.sendInitRoomRequest(
              currentUserName, fromUser, token, dh.getPublicComponent().toString()
      );

      log.info("Room '{}' initialized with user '{}'", roomId, fromUser);

    } else {
      DiffieHellman dh = DiffieHellmanManager.get(roomId);
      if (dh == null) {
        log.warn("No DH context found for room: {}", roomId);
        return;
      }

      dh.getKey(new BigInteger(msg.getPublicExponent()));
      log.info("Shared key get for room {} ", roomId);
    }
  }

  private void handleFileMessage(ChatProto.ChatMessage msg) {
    try {
      String name = msg.getFileName();
      Path dir = Paths.get("downloads");
      Files.createDirectories(dir);
      Path out = dir.resolve(name);
      OutputStream os = fileStreams.computeIfAbsent(name,
              fn -> {
                try {
                  return Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
      );
      os.write(msg.getChunk().toByteArray());
      if (msg.getIsLast()) {
        os.close();
        fileStreams.remove(name);
        log.info("[Файл готов] " + name);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private void handleTextMessage(ChatProto.ChatMessage msg) {
    RoomTokenEncoder.DecodedRoomToken decodedToken;
    try {
      decodedToken = RoomTokenEncoder.decode(msg.getToken());
    } catch (Exception e) {
      log.error("Failed to decode token: {}", msg.getToken(), e);
      return;
    }

    String roomId = decodedToken.guid();
    Optional<ChatRoom> optionalRoom = DaoManager.getChatRoomDao().findByRoomId(roomId);

    if (optionalRoom.isEmpty()) {
      log.warn("No chat room found for message: {}", msg);
      return;
    }

    ChatRoom room = optionalRoom.get();

    boolean changed = !room.getCipher().equals(decodedToken.cipher()) ||
            !room.getCipherMode().equals(decodedToken.cipherMode()) ||
            !room.getPaddingMode().equals(decodedToken.paddingMode()) ||
            !room.getIv().equals(decodedToken.IV());

    if (changed) {
      log.info("Room settings changed for room '{}'. Updating...", roomId);
      room.setCipher(decodedToken.cipher());
      room.setCipherMode(decodedToken.cipherMode());
      room.setPaddingMode(decodedToken.paddingMode());
      room.setIv(decodedToken.IV());

      DaoManager.getChatRoomDao().update(room);

      for (int i = 0; i < chatRooms.size(); i++) {
        if (chatRooms.get(i).getRoomId().equals(roomId)) {
          chatRooms.set(i, room);
          break;
        }
      }

      if (currentChat != null && currentChat.getRoomId().equals(roomId)) {
        currentChat = room;
        cipherLabel.setText("Cipher: " + room.getCipher());
        modeLabel.setText("Mode: " + room.getCipherMode());
        paddingLabel.setText("Padding: " + room.getPaddingMode());
        ivLabel.setText("IV: " + room.getIv());
      }
    }

    Message message = Message.builder()
            .roomId(roomId)
            .sender(msg.getFromUserName())
            .timestamp(msg.getDateTime())
            .content(msg.getText())
            .build();

    DaoManager.getMessageDao().insert(message);

    if (currentChat != null && currentChat.getRoomId().equals(roomId)) {
      messagesContainer.getChildren().add(createMessageBubble(message));
      messagesScrollPane.layout();
      messagesScrollPane.setVvalue(1.0);
    }
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

    sendButton.setDisable(false);
    sendFileButton.setDisable(false);
    messageInputField.setDisable(false);
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
    Label label = new Label(String.format("[%s] %s",
            Instant.ofEpochMilli(message.getTimestamp()),
            message.getContent()
    ));
    label.setWrapText(true);
    label.setMaxWidth(300);
    label.setStyle("-fx-padding: 10; -fx-background-radius: 10;");

    HBox bubble = new HBox(label);
    bubble.setPadding(new Insets(5));

    if (message.getSender().equals(currentUserName)) {
      bubble.setAlignment(Pos.CENTER_LEFT);
      label.setStyle(label.getStyle() + "-fx-background-color: #d0ffd0;");
    } else {
      bubble.setAlignment(Pos.CENTER_RIGHT);
      label.setStyle(label.getStyle() + "-fx-background-color: #d0d0ff;");
    }

    return bubble;
  }


  private void sendInitRoomRequest(String toUser, String token, String roomId) {
    String g = "5";
    String p = "23";

    DiffieHellman dh = new DiffieHellman(g, p);
    String publicComponent = dh.getPublicComponent().toString();

    DiffieHellmanManager.put(roomId, dh);

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
    String text = messageInputField.getText().trim();
    if (text.isEmpty()) return;
    messageInputField.clear();

    ChatRoom room = currentChat;
    if (room == null) return;

    String token = RoomTokenEncoder.encode(
            room.getRoomId(),
            room.getCipher(),
            room.getCipherMode(),
            room.getPaddingMode(),
            room.getIv()
    );

    long timestamp = System.currentTimeMillis();

    boolean delivered = grpcClient.sendMessage(
            currentUserName,
            room.getOtherUser(),
            text,
            token
    );

    if (delivered) {
      Message message = Message.builder()
              .roomId(room.getRoomId())
              .sender(currentUserName)
              .timestamp(timestamp)
              .content(text)
              .build();

      DaoManager.getMessageDao().insert(message);
      messagesContainer.getChildren().add(createMessageBubble(message));
      messagesScrollPane.layout();
      messagesScrollPane.setVvalue(1.0);
    }
  }



  @FXML
  private void chooseFile() {
    Window window = messageInputField.getScene().getWindow();

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Выберите файл для отправки");

    File selectedFile = fileChooser.showOpenDialog(window);
    if (selectedFile != null) {

      log.info("Вы выбрали: {}", selectedFile.getName());

      ChatRoom room = currentChat;

      boolean delivered = false;

      try {
        delivered = grpcClient.sendFile(selectedFile, currentUserName, room.getOtherUser())
                .orTimeout(5, TimeUnit.SECONDS)
                .get();
      } catch (Exception e) {
        log.info(e.getMessage());
      }


      log.info("is delivered???? : {}", delivered);
      if(delivered) {
        //TODO отображать, записывать в бд, что мы файл отправили
      }
    } else {
      log.info("Выбор файла отменён.");
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
