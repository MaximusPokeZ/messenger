package org.example.frontend.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher.context.Context;
import org.example.frontend.dialog.ChatSettingsDialog;
import org.example.frontend.factory.ContextFactory;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MainChatController {

  private final GrpcClient grpcClient = GrpcClient.getInstance();

  @FXML
  private Button inviteUserButton;
  @FXML
  private Button leaveChatButton;
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

  private static class FileTransferState {
    OutputStream outputStream;
    byte[] previous;

    FileTransferState(OutputStream os) {
      this.outputStream = os;
      this.previous = null;
    }
  }

  private final ConcurrentMap<String, FileTransferState> fileTransfers = new ConcurrentHashMap<>();

  private final String currentUserName = JwtStorage.getUsername();




  public void initialize() {
    DBManager.initInstance(currentUserName);
    DaoManager.init();

    userLabel.setText(currentUserName);
    chatDetailsPane.setDisable(true);
    leaveChatButton.setDisable(true);
    inviteUserButton.setDisable(true);
    inviteUserButton.setVisible(false);

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
          setText(Objects.equals(currentUserName, room.getOtherUser()) ? room.getOwner() : room.getOtherUser() + (room.getLastMessage() != null ? " — " + room.getLastMessage() + " - " + MessageUtils.formatTime(room.getLastMessageTime()) : ""));
        }
      }
    });

    grpcClient.connect(
            currentUserName,
            msg -> Platform.runLater(() -> {
              switch (msg.getType()) {
                case TEXT -> handleTextMessage(msg);
                case INIT_ROOM -> handleInitRoomMessage(msg);
                case FILE -> handleFileMessage(msg);
                case DELETE_CHAT, REMOVE_USER -> handleDeleteChat(msg);
                case OWNER_LEFT -> handleOwnerLeft(msg);
                case SELF_LEFT -> handleSelfLeft(msg);
                default -> throw new UnsupportedOperationException("Unsupported chat type: " + msg.getType());
              }
            }),
            () -> log.info("Disconnected from server"),
            Throwable::printStackTrace
    );


    searchResultsPanel.setVisible(false);
  }

  private void handleSelfLeft(ChatProto.ChatMessage msg) {
    String roomId = RoomTokenEncoder.decode(msg.getToken()).guid();

    DaoManager.getMessageDao().deleteByRoomId(roomId);
    DaoManager.getChatRoomDao().delete(roomId);

    leaveChatButton.setDisable(true);

    reloadChatListUI(true);

    log.info("You have left the chat {}", roomId);
  }

  private void handleOwnerLeft(ChatProto.ChatMessage msg) {
    String roomId = RoomTokenEncoder.decode(msg.getToken()).guid();

    ChatRoom room = DaoManager.getChatRoomDao().findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Chat room not found"));

    room.setOwner(currentUserName);
    room.setOtherUser(null);
    DaoManager.getChatRoomDao().update(room);

    reloadChatListUI(false);

    log.info("Owner left chat {}. New owner is {}", roomId, currentUserName);

    openChat(room);
  }

  private void handleDeleteChat(ChatProto.ChatMessage msg) {
    String roomId = RoomTokenEncoder.decode(msg.getToken()).guid();

    DaoManager.getMessageDao().deleteByRoomId(roomId);
    DaoManager.getChatRoomDao().delete(roomId);

    leaveChatButton.setDisable(true);
    reloadChatListUI(true);

    log.info("Chat {} was deleted", roomId);
  }

  private void handleInitRoomMessage(ChatProto.ChatMessage msg) {
    String fromUser = msg.getFromUserName();
    String token = msg.getToken();
    String roomId = RoomTokenEncoder.decode(token).guid();

    log.info("Create room by fromUser: {}", fromUser);

    Optional<ChatRoom> existing = DaoManager.getChatRoomDao().findByRoomId(roomId);

    if (existing.isEmpty()) {
      ChatRoom room = ChatRoom.builder()
              .roomId(roomId)
              .owner(fromUser)
              .otherUser(currentUserName)
              .cipher(RoomTokenEncoder.decode(token).cipher())
              .cipherMode(RoomTokenEncoder.decode(token).cipherMode())
              .paddingMode(RoomTokenEncoder.decode(token).paddingMode())
              .iv(RoomTokenEncoder.decode(token).IV())
              .keyBitLength(RoomTokenEncoder.decode(token).keyBitLength())
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
      log.info("Cipher: {}", decodedToken.cipher());
      log.info("Cipher mode: {}", decodedToken.cipherMode());
      log.info("Padding mode: {}", decodedToken.paddingMode());
      log.info("IV: {}", decodedToken.IV());

      log.info("Cipher in room: {}", room.getCipher());
      log.info("Cipher mode in room: {}", room.getCipherMode());
      log.info("Padding mode in room: {}", room.getPaddingMode());
      log.info("IV in room: {}", room.getIv());


      room.setCipher(decodedToken.cipher());
      room.setCipherMode(decodedToken.cipherMode());
      room.setPaddingMode(decodedToken.paddingMode());
      room.setIv(decodedToken.IV());

      log.info("Cipher in room after: {}", room.getCipher());
      log.info("Cipher mode in room after: {}", room.getCipherMode());
      log.info("Padding mode in room after: {}", room.getPaddingMode());
      log.info("IV in room after: {}", room.getIv());


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
        log.info("Changed room settings for {}", roomId);
      }
    }

    Context context = ContextFactory.getContext(room);
    try {
      String fileName = msg.getFileName();
      Path dir = Paths.get("downloads");
      Files.createDirectories(dir);
      Path out = dir.resolve(fileName);

      FileTransferState state = fileTransfers.computeIfAbsent(fileName, fn -> {
        try {
          OutputStream os = Files.newOutputStream(out,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND);
          return new FileTransferState(os);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });

      byte[] encryptedChunk = msg.getChunk().toByteArray();
      Pair<byte[], byte[]> decrypted = context.encryptDecryptInner(
              encryptedChunk,
              state.previous,
              false
      );

      if (msg.getIsLast()) {
        byte[] unpadded = context.removePadding(decrypted.getKey());
        state.outputStream.write(unpadded);
        state.outputStream.close();
        fileTransfers.remove(fileName);
        log.info("[Файл готов] {}", fileName); //TODO тут можно уже отображать в чат
      } else {
        state.outputStream.write(decrypted.getKey());
        state.previous = decrypted.getValue();
      }
    } catch (Exception e) {
      log.error("Ошибка обработки файла {}", msg.getFileName(), e);
      FileTransferState failedState = fileTransfers.remove(msg.getFileName());
      if (failedState != null) {
        try { failedState.outputStream.close(); } catch (IOException ignored) {}
      }
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
      log.info("Cipher: {}", decodedToken.cipher());
      log.info("Cipher mode: {}", decodedToken.cipherMode());
      log.info("Padding mode: {}", decodedToken.paddingMode());
      log.info("IV: {}", decodedToken.IV());

      log.info("Cipher in room: {}", room.getCipher());
      log.info("Cipher mode in room: {}", room.getCipherMode());
      log.info("Padding mode in room: {}", room.getPaddingMode());
      log.info("IV in room: {}", room.getIv());


      room.setCipher(decodedToken.cipher());
      room.setCipherMode(decodedToken.cipherMode());
      room.setPaddingMode(decodedToken.paddingMode());
      room.setIv(decodedToken.IV());

      log.info("Cipher in room after: {}", room.getCipher());
      log.info("Cipher mode in room after: {}", room.getCipherMode());
      log.info("Padding mode in room after: {}", room.getPaddingMode());
      log.info("IV in room after: {}", room.getIv());


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
        log.info("Changed room settings for {}", roomId);
      }
    }

    Context contextTextMessage = ContextFactory.getContext(room);

    byte[] encodedData = Base64.getDecoder().decode(msg.getText());
    Pair<byte[], byte[]> decrypted = contextTextMessage.encryptDecryptInner(encodedData, null, false);


    Message message = Message.builder()
            .roomId(roomId)
            .sender(msg.getFromUserName())
            .timestamp(msg.getDateTime())
            .content(new String(contextTextMessage.removePadding(decrypted.getKey())))
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
      GrpcClient.resetInstance();
      DBManager.resetInstance();
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
      log.info("Cipher after send: {}", settings.getCipher());
      log.info("Cipher mode after send: {}", settings.getCipherMode());
      log.info("Padding mode after send: {}", settings.getPaddingMode());
      log.info("IV after send: {}", settings.getIv());
      log.info("key bit length: {}", settings.getKeyBitLength());

      String token = RoomTokenEncoder.encode(
              guid,
              settings.getCipher(),
              settings.getCipherMode(),
              settings.getPaddingMode(),
              settings.getIv(),
              settings.getKeyBitLength()
      );

      ChatRoom chatRoom = ChatRoom.builder()
              .roomId(guid)
              .owner(currentUserName)
              .otherUser(username)
              .cipher(settings.getCipher())
              .cipherMode(settings.getCipherMode())
              .paddingMode(settings.getPaddingMode())
              .iv(settings.getIv())
              .keyBitLength(settings.getKeyBitLength())
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
    Optional<ChatRoom> updatedRoomOpt = DaoManager.getChatRoomDao().findByRoomId(room.getRoomId());
    if (updatedRoomOpt.isEmpty()) {
      log.warn("Tried to open nonexistent room: {}", room.getRoomId());
      return;
    }

    ChatRoom updatedRoom = updatedRoomOpt.get();
    this.currentChat = updatedRoom;

    sendButton.setDisable(false);
    sendFileButton.setDisable(false);
    messageInputField.setDisable(false);
    chatDetailsPane.setDisable(false);
    leaveChatButton.setDisable(false);

    cipherLabel.setText("Cipher: " + updatedRoom.getCipher());
    modeLabel.setText("Mode: " + updatedRoom.getCipherMode());
    paddingLabel.setText("Padding: " + updatedRoom.getPaddingMode());
    ivLabel.setText("IV: " + updatedRoom.getIv());

    chatTitleLabel.setText(Objects.equals(currentUserName, updatedRoom.getOtherUser()) ? updatedRoom.getOwner() : updatedRoom.getOtherUser());

    messagesContainer.getChildren().clear();

    List<Message> messages = DaoManager.getMessageDao().findByRoomId(updatedRoom.getRoomId());
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

    Context contextSendTextMessage = ContextFactory.getContext(room);

    byte[] afterPadding = contextSendTextMessage.addPadding(text.getBytes());

    Pair<byte[], byte[]> encrypted = contextSendTextMessage.encryptDecryptInner(afterPadding, null, true);

    String cipherText = Base64.getEncoder().encodeToString(encrypted.getKey());



    String token = RoomTokenEncoder.encode(
            room.getRoomId(),
            room.getCipher(),
            room.getCipherMode(),
            room.getPaddingMode(),
            room.getIv(),
            room.getKeyBitLength()
    );

    log.info("Cipher before send: {}", room.getCipher());
    log.info("Cipher mode before send: {}", room.getCipherMode());
    log.info("Padding mode before send: {}", room.getPaddingMode());
    log.info("IV before send: {}", room.getIv());

    long timestamp = System.currentTimeMillis();

    log.info("Current User: {}", currentUserName);
    log.info("Other User: {}", room.getOtherUser());
    log.info("Current room User: {}", room.getOwner());
    boolean delivered = grpcClient.sendMessage(
            currentUserName,
            Objects.equals(room.getOtherUser(), currentUserName) ? room.getOwner() : room.getOtherUser(),
            cipherText,
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

      log.info("Your chose: {}", selectedFile.getName());

      ChatRoom room = currentChat;
      if (room == null) return;

      String token = RoomTokenEncoder.encode(
              room.getRoomId(),
              room.getCipher(),
              room.getCipherMode(),
              room.getPaddingMode(),
              room.getIv(),
              room.getKeyBitLength()
      );

      boolean delivered = false;

      try {
        delivered = grpcClient.sendFile(selectedFile, currentUserName, room.getOtherUser(), room, token)
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
      currentChat.setKeyBitLength(settings.getKeyBitLength());

      DaoManager.getChatRoomDao().update(currentChat);

      //TODO : послать запрос на изменение комнаты у другого юзера или просто послать сообщение новое
      // в котором будет наш токен в котором уже будет обновленная информация

      openChat(currentChat);
    });
  }

  @FXML
  private void onLeaveChatClick() {
    if (currentChat == null) return;

    String owner = currentChat.getOwner();
    String otherUser = currentChat.getOtherUser();
    String roomId = currentChat.getRoomId();

    String token = RoomTokenEncoder.encode(
            roomId,
            currentChat.getCipher(),
            currentChat.getCipherMode(),
            currentChat.getPaddingMode(),
            currentChat.getIv(),
            currentChat.getKeyBitLength()
    );

    log.info("Current Username: {}", currentUserName);
    log.info("Owner: {}", currentChat.getOwner());
    log.info("Other user: {}", currentChat.getOtherUser());

    if (currentUserName.equals(owner) && currentChat.getOtherUser() != null) {
      List<String> choices = List.of("Delete chat", "Remove other", "Leave chat");
      ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
      dialog.setTitle("Exit chat");
      dialog.setHeaderText("You are the chat owner. Select an action:");

      Optional<String> result = dialog.showAndWait();
      result.ifPresent(choice -> {
        switch (choice) {
          case "Delete chat" -> {
            DaoManager.getMessageDao().deleteByRoomId(roomId);
            DaoManager.getChatRoomDao().delete(roomId);

            grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.DELETE_CHAT);
            reloadChatListUI(true);
          }
          case "Remove other" -> {
            ChatRoom updatedRoom = DaoManager.getChatRoomDao().findByRoomId(roomId)
                    .orElseThrow(() -> new RuntimeException("Chat room not found"));

            updatedRoom.setOtherUser(null);
            DaoManager.getChatRoomDao().update(updatedRoom);
            currentChat = updatedRoom;

            grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.REMOVE_USER);
            reloadChatListUI(false);
            openChat(currentChat);
          }
          case "Leave chat" -> {
            DaoManager.getMessageDao().deleteByRoomId(roomId);
            DaoManager.getChatRoomDao().delete(roomId);

            grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.OWNER_LEFT);
            reloadChatListUI(true);
          }
        }
      });
    } else {
      Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
      confirm.setTitle("Exit chat");
      confirm.setHeaderText("Are you sure you want to leave the chat?");
      confirm.setContentText("All messages will be deleted");

      Optional<ButtonType> buttonType = confirm.showAndWait();
      if (buttonType.isPresent() && buttonType.get() == ButtonType.OK) {
        DaoManager.getMessageDao().deleteByRoomId(roomId);
        DaoManager.getChatRoomDao().delete(roomId);

        if (otherUser != null) grpcClient.sendControlMessage(currentUserName, Objects.equals(otherUser, currentUserName) ? owner : otherUser, token, ChatProto.MessageType.SELF_LEFT);

        messageInputField.setDisable(true);
        sendButton.setDisable(true);
        sendFileButton.setDisable(true);
        leaveChatButton.setDisable(true);

        log.info("CurrentRoomChat {}", currentChat.getRoomId());
        reloadChatListUI(true);
      }
    }
  }


  private void reloadChatListUI(boolean deleteMessages) {
    chatRooms = DaoManager.getChatRoomDao().findAll();
    updateChatListUI();
    if (deleteMessages) {
      DaoManager.getChatRoomDao().delete(currentChat.getRoomId());
      messagesContainer.getChildren().clear();
      chatRooms.remove(currentChat);
      currentChat = null;
      leaveChatButton.setDisable(true);
      messageInputField.setDisable(true);
      sendButton.setDisable(true);
      sendFileButton.setDisable(true);
    }
    chatTitleLabel.setText("Select chat");
    cipherLabel.setText("Cipher:");
    modeLabel.setText("Mode:");
    paddingLabel.setText("Padding:");
    ivLabel.setText("IV");
    chatDetailsPane.setDisable(true);
  }

  @FXML
  public void onInviteUserClick(ActionEvent actionEvent) {
  }
}
