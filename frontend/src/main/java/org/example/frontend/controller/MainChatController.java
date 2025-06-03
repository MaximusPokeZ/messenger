package org.example.frontend.controller;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
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

import java.awt.*;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javafx.scene.media.*;
import javafx.scene.layout.*;
import javafx.scene.Cursor;

@Slf4j
public class MainChatController {

  private final GrpcClient grpcClient = GrpcClient.getInstance();
  @FXML
  private Label keyLengthLabel;
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
  public TextField searchField;
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

    chatRooms = DaoManager.getChatRoomDao().findAll();

    userLabel.setText(currentUserName);
    chatDetailsPane.setDisable(true);
    leaveChatButton.setDisable(true);
    inviteUserButton.setVisible(false);

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
          String message = room.getLastMessage();
          if (message != null && message.length() > 10) {
            message = message.substring(0, 10) + "...";
          }

          String time = room.getLastMessage() != null
                  ? " - " + MessageUtils.formatTime(room.getLastMessageTime())
                  : "";

          setText(room.getInterlocutor(currentUserName) +
                  (message != null ? " — " + message + time : ""));
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
                case OWNER_LEFT, SELF_LEFT -> handleOwnerLeft(msg);
                default -> throw new UnsupportedOperationException("Unsupported chat type: " + msg.getType());
              }
            }),
            () -> log.info("Disconnected from server"),
            Throwable::printStackTrace
    );

    for (ChatRoom room : chatRooms) {
      String roomId = room.getRoomId();
      String token = RoomTokenEncoder.encode(
              room.getRoomId(),
              room.getCipher(),
              room.getCipherMode(),
              room.getPaddingMode(),
              room.getIv(),
              room.getKeyBitLength()
      );

      DiffieHellman dh = new DiffieHellman();
      String publicComponent = dh.getPublicComponent().toString();

      log.info("Start DH for room id {}", roomId);

      searchResultsPanel.setVisible(false);

      updateChatListUI();

      if (room.getInterlocutor(currentUserName) == null) return;

      boolean isDelivered = grpcClient.sendInitRoomRequest(
              currentUserName,
              room.getInterlocutor(currentUserName),
              token,
              publicComponent
      );

      if (isDelivered) {
        log.info("Delivered public component {}", publicComponent);
        DiffieHellmanManager.put(roomId, dh);
      }
    }
  }

  private void handleOwnerLeft(ChatProto.ChatMessage msg) {
    String roomId = RoomTokenEncoder.decode(msg.getToken()).guid();

    ChatRoom room = DaoManager.getChatRoomDao().findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Chat room not found"));

    room.setOwner(currentUserName);
    room.setOtherUser(null);
    DaoManager.getChatRoomDao().update(room);

    for (int i = 0; i < chatRooms.size(); i++) {
      if (chatRooms.get(i).getRoomId().equals(roomId)) {
        chatRooms.set(i, room);
        break;
      }
    }

    reloadChatListUI(false);

    log.info("Owner left chat {}. New owner is {}", roomId, currentUserName);

    inviteUserButton.setVisible(true);

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


      DiffieHellman dh = new DiffieHellman();
      dh.getKey(new BigInteger(msg.getPublicExponent()));
      dh.setPublicComponentOther(msg.getPublicExponent());
      DiffieHellmanManager.put(roomId, dh);

      grpcClient.sendInitRoomRequest(
              currentUserName, fromUser, token, dh.getPublicComponent().toString()
      );

      log.info("Room '{}' initialized with user '{}'", roomId, fromUser);

    } else {
      DiffieHellman dhc = DiffieHellmanManager.get(roomId);
      if (dhc == null) {
        DiffieHellman dh = new DiffieHellman();
        dh.getKey(new BigInteger(msg.getPublicExponent()));
        dh.setPublicComponentOther(msg.getPublicExponent());
        DiffieHellmanManager.put(roomId, dh);
        grpcClient.sendInitRoomRequest(
                currentUserName, existing.get().getInterlocutor(currentUserName), token, dh.getPublicComponent().toString()
        );
      } else {
        if (dhc.getSharedSecret() == null || !dhc.getPublicComponentOther().equals(msg.getPublicExponent())) {
          grpcClient.sendInitRoomRequest(
                  currentUserName, existing.get().getInterlocutor(currentUserName), token, dhc.getPublicComponent().toString());
        }
        dhc.getKey(new BigInteger(msg.getPublicExponent()));
        dhc.setPublicComponentOther(msg.getPublicExponent());

        log.info("Shared key get for room {} ", roomId);
      }
    }
  }

  private void handleFileMessage(ChatProto.ChatMessage msg) {
    Optional<ChatRoom> optionalRoom = prepareRoomAndDH(msg);
    if (optionalRoom.isEmpty()) return;

    ChatRoom room = optionalRoom.get();

    Context context;
    try {
      context = ContextFactory.getContext(room);
    } catch (Exception e) {
      log.info("Error with context");
      return;
    }


    try {
      String fileName = msg.getFileName();

      Path downloadsPath = Paths.get(System.getProperty("user.home"), "Downloads", "SecureChat_" + currentUserName);
      Files.createDirectories(downloadsPath);

      Path out = downloadsPath.resolve(fileName);

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
        log.info("[The file is ready] {}", fileName);

        Message message = Message.builder()
                .roomId(room.getRoomId())
                .sender(msg.getFromUserName())
                .timestamp(msg.getDateTime())
                .content("[File] " + fileName)
                .filePath(out.toString())
                .build();

        updateLastMessage(room, message);

        if (currentChat != null && currentChat.getRoomId().equals(room.getRoomId())) {
          messagesContainer.getChildren().add(createMessageBubble(message));
          messagesScrollPane.layout();
          messagesScrollPane.setVvalue(1.0);
        }

      } else {
        state.outputStream.write(decrypted.getKey());
        state.previous = decrypted.getValue();
      }
    } catch (Exception e) {
      log.error("File processing error {}", msg.getFileName(), e);
      showAlert(Alert.AlertType.ERROR, "File processing error");
      FileTransferState failedState = fileTransfers.remove(msg.getFileName());
      if (failedState != null) {
        try { failedState.outputStream.close(); } catch (IOException ignored) {
          log.error("Failed to close stream");
        }
      }
    }
  }

  private void handleTextMessage(ChatProto.ChatMessage msg) {
    Optional<ChatRoom> optionalRoom = prepareRoomAndDH(msg);
    if (optionalRoom.isEmpty()) return;

    ChatRoom room = optionalRoom.get();

    Context contextTextMessage;
    try {
      contextTextMessage = ContextFactory.getContext(room);
    } catch (Exception e) {
      log.info("Error with context");
      return;
    }

    byte[] encodedData = Base64.getDecoder().decode(msg.getText());
    Pair<byte[], byte[]> decrypted = contextTextMessage.encryptDecryptInner(encodedData, null, false);

    Message message = Message.builder()
            .roomId(room.getRoomId())
            .sender(msg.getFromUserName())
            .timestamp(msg.getDateTime())
            .content(new String(contextTextMessage.removePadding(decrypted.getKey())))
            .build();

    updateDbLastMessage(room, message);

    if (currentChat != null && currentChat.getRoomId().equals(room.getRoomId())) {
      messagesContainer.getChildren().add(createMessageBubble(message));
      messagesScrollPane.layout();
      messagesScrollPane.setVvalue(1.0);
    }
  }

  private Optional<ChatRoom> prepareRoomAndDH(ChatProto.ChatMessage msg) {
    RoomTokenEncoder.DecodedRoomToken decodedToken;
    try {
      decodedToken = RoomTokenEncoder.decode(msg.getToken());
    } catch (Exception e) {
      log.error("Failed to decode token: {}", msg.getToken(), e);
      return Optional.empty();
    }

    String roomId = decodedToken.guid();
    Optional<ChatRoom> optionalRoom = DaoManager.getChatRoomDao().findByRoomId(roomId);
    if (optionalRoom.isEmpty()) {
      log.warn("No chat room found for message: {}", msg);
      return Optional.empty();
    }

    ChatRoom room = optionalRoom.get();

    boolean changed = !room.getCipher().equals(decodedToken.cipher()) ||
            !room.getCipherMode().equals(decodedToken.cipherMode()) ||
            !room.getPaddingMode().equals(decodedToken.paddingMode()) ||
            !room.getIv().equals(decodedToken.IV()) ||
            !room.getKeyBitLength().equals(decodedToken.keyBitLength());

    if (changed) {
      log.info("Room settings changed for room '{}'. Updating...", roomId);

      room.setCipher(decodedToken.cipher());
      room.setCipherMode(decodedToken.cipherMode());
      room.setPaddingMode(decodedToken.paddingMode());
      room.setIv(decodedToken.IV());
      room.setKeyBitLength(decodedToken.keyBitLength());

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
        keyLengthLabel.setText("Key length bits: " + room.getKeyBitLength());
        log.info("Changed room settings for {}", roomId);
      }
    }

    DiffieHellman DH = DiffieHellmanManager.get(roomId);
    if (DH == null) {
      DiffieHellman dh = new DiffieHellman();
      dh.getKey(new BigInteger(msg.getPublicExponent()));
      dh.setPublicComponentOther(msg.getPublicExponent());
      DiffieHellmanManager.put(room.getRoomId(), dh);
    } else {
      if (DH.getSharedSecret() == null) {
        DH.getKey(new BigInteger(msg.getPublicExponent()));
        DH.setPublicComponentOther(msg.getPublicExponent());
      }
    }

    return Optional.of(room);
  }


  @FXML
  private void onLogoutClick() {
    try {
      JwtStorage.setUsername(null);
      JwtStorage.setToken(null);

        GrpcClient.resetInstance();
        DBManager.resetInstance();
        SceneManager.switchToLoginScene();

    } catch (IOException e) {
      throw new RuntimeException("Failed to log out: " + e);
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        log.error("Server unavailable: {}", e.getMessage());
      }
    } catch (Exception e) {
      log.info("io.grpc disabled");
    }

  }

  @FXML
  public void onSearchClick() {
    log.info("Search clicked");
    String searchText = searchField.getText().trim().toLowerCase();
    List<String> allUsers = ChatApiClient.getOnlineUsers().stream()
            .map(User::getUsername)
            .filter(u -> !u.equals(currentUserName))
            .toList();

    List<String> filteredUsers = searchText.isEmpty()
            ? allUsers
            : allUsers.stream()
            .filter(u -> u.toLowerCase().contains(searchText))
            .toList();

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

      this.currentChat = chatRoom;
      updateChatListUI();
      if (!sendInitRoomRequest(username, guid)) {
        DaoManager.getMessageDao().deleteByRoomId(currentChat.getRoomId());
        DaoManager.getChatRoomDao().delete(currentChat.getRoomId());
        chatRooms.remove(currentChat);

        showAlert(Alert.AlertType.ERROR, "User " + username + " rejected room creation. The room is being deleted.");
        reloadChatListUI(true);
      }

      openChat(chatRoom);
    });
  }

  private void updateChatListUI() {
    chatListView.setItems(FXCollections.observableArrayList(chatRooms));
  }

  private void openChat(ChatRoom room) {
    if (room == null) return;
    Optional<ChatRoom> updatedRoomOpt = DaoManager.getChatRoomDao().findByRoomId(room.getRoomId());
    if (updatedRoomOpt.isEmpty()) {
      log.warn("Tried to open nonexistent room: {}", room.getRoomId());
      return;
    }

    ChatRoom updatedRoom = updatedRoomOpt.get();
    this.currentChat = updatedRoom;

    DiffieHellman dh = DiffieHellmanManager.get(currentChat.getRoomId());
    if ((dh == null || dh.getSharedSecret() == null) && currentChat.getInterlocutor(currentUserName) != null) {
      sendInitRoomRequest(currentChat.getInterlocutor(currentUserName), currentChat.getRoomId());
    }

    if (currentChat.getInterlocutor(currentUserName) == null) {
      inviteUserButton.setVisible(true);
    }

    sendButton.setDisable(false);
    sendFileButton.setDisable(false);
    messageInputField.setDisable(false);
    chatDetailsPane.setDisable(false);
    leaveChatButton.setDisable(false);

    log.info("Updating chat room: {}", updatedRoom.getRoomId());
    log.info("Updating cipher mode: {}", updatedRoom.getCipherMode());
    log.info("Updating padding mode: {}", updatedRoom.getPaddingMode());
    log.info("Updating iv: {}", updatedRoom.getIv());
    log.info("Updating key bit length: {}", updatedRoom.getKeyBitLength());

    cipherLabel.setText("Cipher: " + updatedRoom.getCipher());
    modeLabel.setText("Mode: " + updatedRoom.getCipherMode());
    paddingLabel.setText("Padding: " + updatedRoom.getPaddingMode());
    ivLabel.setText("IV: " + updatedRoom.getIv());
    keyLengthLabel.setText("Key length bits: " + updatedRoom.getKeyBitLength());

    chatTitleLabel.setText(updatedRoom.getInterlocutor(currentUserName));

    messagesContainer.getChildren().clear();

    List<Message> messages = DaoManager.getMessageDao().findByRoomId(updatedRoom.getRoomId());
    for (Message msg : messages) {
      messagesContainer.getChildren().add(createMessageBubble(msg));
    }

    messagesScrollPane.layout();
    messagesScrollPane.setVvalue(1.0);
  }

  private Node createMessageBubble(Message message) {
    Node contentNode;

    if (message.getFilePath() != null) {
      Path filePath = Paths.get(message.getFilePath());
      contentNode = createMediaContent(message, filePath);
    } else {
      Label label = new Label(String.format("[%s] %s",
              MessageUtils.formatTime(message.getTimestamp()),
              message.getContent()
      ));
      label.setWrapText(true);
      label.setMaxWidth(300);
      label.setStyle("-fx-padding: 10; -fx-background-radius: 10;");
      contentNode = label;
    }

    HBox bubble = new HBox(contentNode);
    bubble.setPadding(new Insets(5));

    if (message.getSender().equals(currentUserName)) {
      bubble.setAlignment(Pos.CENTER_LEFT);
      contentNode.setStyle(contentNode.getStyle() +
              "; -fx-background-color: #d0ffd0; -fx-padding: 10; -fx-background-radius: 10;");
    } else {
      bubble.setAlignment(Pos.CENTER_RIGHT);
      contentNode.setStyle(contentNode.getStyle() +
              "; -fx-background-color: #d0d0ff; -fx-padding: 10; -fx-background-radius: 10;");
    }

    return bubble;
  }

  private Node createMediaContent(Message message, Path filePath) {
    String fileName = filePath.getFileName().toString().toLowerCase();
    String fileExtension = getFileExtension(fileName);

    try {
      return switch (fileExtension) {
        case "jpg", "jpeg", "png", "bmp" -> createImageContent(filePath, message);
        case "gif" -> createGifContent(filePath, message);
        case "mp4", "avi", "mov", "wmv" -> createVideoContent(filePath, message);
        case "mp3", "wav", "flac", "aac" -> createAudioContent(filePath, message);
        default -> createFileLink(message, filePath);
      };
    } catch (Exception e) {
      e.printStackTrace();
      return createErrorLabel("File upload error:" + fileName);
    }
  }

  private Node createImageContent(Path filePath, Message message) {
    try {
      File imageFile = filePath.toFile();
      if (!imageFile.exists()) {
        return createErrorLabel("Image not found");
      }

      ImageView imageView = getImageView(imageFile, true);

      VBox container = new VBox(5);
      container.getChildren().add(imageView);
      container.getChildren().add(createTimestampLabel(message));

      return container;

    } catch (Exception e) {
      return createErrorLabel("Error loading image");
    }
  }

  private Node createGifContent(Path filePath, Message message) {
    try {
      File gifFile = filePath.toFile();
      if (!gifFile.exists()) {
        return createErrorLabel("GIF not found");
      }

      ImageView gifView = getImageView(gifFile, false);

      VBox container = new VBox(5);
      container.getChildren().add(gifView);
      container.getChildren().add(createTimestampLabel(message));

      return container;

    } catch (Exception e) {
      return createErrorLabel("Error loading GIF");
    }
  }

  private ImageView getImageView(File gifFile, boolean b) {
    Image gifImage = new Image(gifFile.toURI().toString());
    ImageView gifView = new ImageView(gifImage);

    double maxWidth = 200;
    double maxHeight = 200;

    if (gifImage.getWidth() > maxWidth || gifImage.getHeight() > maxHeight) {
      gifView.setFitWidth(maxWidth);
      gifView.setFitHeight(maxHeight);
      gifView.setPreserveRatio(true);
    }

    gifView.setSmooth(true);
    gifView.setCache(b);

    gifView.setOnMouseClicked(e -> openImageInFullSize(gifImage));
    gifView.setCursor(Cursor.HAND);
    return gifView;
  }

  private Node createVideoContent(Path filePath, Message message) {
    try {
      File videoFile = filePath.toFile();
      if (!videoFile.exists()) {
        return createErrorLabel("Video not found");
      }

      Media media = new Media(videoFile.toURI().toString());
      MediaPlayer mediaPlayer = new MediaPlayer(media);
      MediaView mediaView = new MediaView(mediaPlayer);

      mediaView.setFitWidth(250);
      mediaView.setFitHeight(200);
      mediaView.setPreserveRatio(true);

      mediaPlayer.setOnError(() -> {
        log.error("Media Player Error: {}", String.valueOf(mediaPlayer.getError()));
      });

      media.setOnError(() -> {
        log.error("Media Error: {}", String.valueOf(media.getError()));
      });

      mediaPlayer.setOnReady(() -> {
        log.error("Media ready. Duration: {}", mediaPlayer.getTotalDuration());
      });

      HBox controls = createVideoControls(mediaPlayer);

      StackPane videoContainer = new StackPane();
      videoContainer.setPrefSize(250, 200);
      videoContainer.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc;");

      Label videoPlaceholder = new Label("📹 " + filePath.getFileName().toString());
      videoPlaceholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

      videoContainer.getChildren().addAll(videoPlaceholder, mediaView);

      VBox container = new VBox(5);
      container.getChildren().addAll(videoContainer, controls, createTimestampLabel(message));

      return container;

    } catch (Exception e) {
      e.printStackTrace();
      return createErrorLabel("Error loading video: " + e.getMessage());
    }
  }

  private Node createAudioContent(Path filePath, Message message) {
    try {
      File audioFile = filePath.toFile();
      if (!audioFile.exists()) {
        return createErrorLabel("Audio not found");
      }

      Media media = new Media(audioFile.toURI().toString());
      MediaPlayer mediaPlayer = new MediaPlayer(media);

      HBox audioInterface = createAudioInterface(mediaPlayer, filePath.getFileName().toString());

      VBox container = new VBox(5);
      container.getChildren().add(audioInterface);
      container.getChildren().add(createTimestampLabel(message));

      return container;

    } catch (Exception e) {
      return createErrorLabel("Error loading audio");
    }
  }

  private HBox createVideoControls(MediaPlayer mediaPlayer) {
    Button playButton = new Button("▶");
    Button pauseButton = new Button("⏸");
    Button stopButton = new Button("⏹");

    pauseButton.setVisible(false);

    Slider progressSlider = new Slider();
    progressSlider.setMin(0.0);
    progressSlider.setValue(0.0);
    progressSlider.setMaxWidth(150);

    Label timeLabel = new Label("00:00 / 00:00");
    timeLabel.setMinWidth(80);
    timeLabel.setStyle("-fx-font-size: 10px;");

    playButton.setOnAction(e -> mediaPlayer.play());
    pauseButton.setOnAction(e -> mediaPlayer.pause());
    stopButton.setOnAction(e -> {
      mediaPlayer.stop();
      progressSlider.setValue(0.0);
    });

    pauseButton.setOnAction(e -> {
      try {
        mediaPlayer.pause();
        playButton.setVisible(true);
        pauseButton.setVisible(false);
      } catch (Exception ex) {
        log.error("Pause Error: {}", ex.getMessage());
      }
    });

    stopButton.setOnAction(e -> {
      try {
        mediaPlayer.stop();
        progressSlider.setValue(0.0);
        playButton.setVisible(true);
        pauseButton.setVisible(false);
        timeLabel.setText("00:00 / 00:00");
      } catch (Exception ex) {
        log.error("Stop error: {}", ex.getMessage());
      }
    });

    mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
      log.error("Player status changed: {}", newStatus);
      switch (newStatus) {
        case PLAYING:
          playButton.setVisible(false);
          pauseButton.setVisible(true);
          break;
        case PAUSED:
        case STOPPED:
        case READY:
          playButton.setVisible(true);
          pauseButton.setVisible(false);
          break;
        default:
          break;
      }
    });

    mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
      if (!progressSlider.isValueChanging() && newTime != null) {
        try {
          Duration totalDuration = mediaPlayer.getTotalDuration();
          if (totalDuration != null && totalDuration.greaterThan(Duration.ZERO)) {
            progressSlider.setMax(totalDuration.toSeconds());
            progressSlider.setValue(newTime.toSeconds());

            String current = formatDuration(newTime);
            String total = formatDuration(totalDuration);
            timeLabel.setText(current + " / " + total);
          }
        } catch (Exception ex) {
          log.error("Error updating progress: {}", ex.getMessage());
        }
      }
    });

    progressSlider.setOnMouseReleased(e -> {
      try {
        if (mediaPlayer.getTotalDuration() != null) {
          mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
        }
      } catch (Exception ex) {
        log.error("Rewind error: {}", ex.getMessage());
      }
    });

    mediaPlayer.setOnEndOfMedia(() -> {
      playButton.setVisible(false);
      pauseButton.setVisible(false);
      progressSlider.setValue(0.0);
    });

    HBox controls = new HBox(5);
    controls.getChildren().addAll(playButton, pauseButton, stopButton, progressSlider, timeLabel);
    controls.setAlignment(Pos.CENTER_LEFT);

    return controls;
  }

  private HBox createAudioInterface(MediaPlayer mediaPlayer, String fileName) {
    Button playButton = new Button("▶");
    Button pauseButton = new Button("⏸");

    Label fileLabel = new Label("🎵 " + fileName);
    fileLabel.setMaxWidth(150);
    fileLabel.setStyle("-fx-text-fill: #333; -fx-font-size: 12px;");

    Slider progressSlider = new Slider();
    progressSlider.setMin(0.0);
    progressSlider.setValue(0.0);
    progressSlider.setMaxWidth(100);

    Label timeLabel = new Label("00:00");

    playButton.setOnAction(e -> {
      mediaPlayer.play();
      playButton.setVisible(false);
      pauseButton.setVisible(true);
    });

    pauseButton.setOnAction(e -> {
      mediaPlayer.pause();
      playButton.setVisible(true);
      pauseButton.setVisible(false);
    });

    pauseButton.setVisible(false);

    mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
      if (!progressSlider.isValueChanging()) {
        Duration totalDuration = mediaPlayer.getTotalDuration();
        if (totalDuration != null && totalDuration.greaterThan(Duration.ZERO)) {
          progressSlider.setMax(totalDuration.toSeconds());
          progressSlider.setValue(newTime.toSeconds());
          timeLabel.setText(formatDuration(newTime));
        }
      }
    });

    mediaPlayer.setOnEndOfMedia(() -> {
      progressSlider.setValue(0.0);
      mediaPlayer.seek(Duration.ZERO);
      mediaPlayer.pause();
      playButton.setVisible(true);
      pauseButton.setVisible(false);
    });

    progressSlider.setOnMouseReleased(e -> {
      mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
    });

    VBox playerBox = new VBox(3);
    playerBox.getChildren().add(fileLabel);

    HBox controlsBox = new HBox(5);
    controlsBox.getChildren().addAll(playButton, pauseButton, progressSlider, timeLabel);
    controlsBox.setAlignment(Pos.CENTER_LEFT);

    playerBox.getChildren().add(controlsBox);

    HBox container = new HBox();
    container.getChildren().add(playerBox);
    container.setAlignment(Pos.CENTER_LEFT);

    return container;
  }

  private Label createTimestampLabel(Message message) {
    Label timestampLabel = new Label(
            Instant.ofEpochMilli(message.getTimestamp()).toString()
    );
    timestampLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
    return timestampLabel;
  }

  private Label createErrorLabel(String errorMessage) {
    Label errorLabel = new Label(errorMessage);
    errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
    return errorLabel;
  }

  private Node createFileLink(Message message, Path filePath) {
    return getHyperlink(message, filePath);
  }

  private String getFileExtension(String fileName) {
    int lastIndexOf = fileName.lastIndexOf(".");
    if (lastIndexOf == -1) {
      return "";
    }
    return fileName.substring(lastIndexOf + 1);
  }

  private String formatDuration(Duration duration) {
    int minutes = (int) duration.toMinutes();
    int seconds = (int) duration.toSeconds() % 60;
    return String.format("%02d:%02d", minutes, seconds);
  }

  private void openImageInFullSize(Image image) {
    Stage imageStage = new Stage();
    imageStage.setTitle("View image");

    ImageView fullImageView = new ImageView(image);
    fullImageView.setPreserveRatio(true);

    ScrollPane scrollPane = new ScrollPane(fullImageView);
    scrollPane.setFitToWidth(true);
    scrollPane.setFitToHeight(true);

    Scene scene = new Scene(scrollPane, 600, 400);
    imageStage.setScene(scene);
    imageStage.show();
  }

  private static Hyperlink getHyperlink(Message message, Path filePath) {
    Path parentDir = filePath.getParent();

    String displayText = String.format("[%s] [File] %s",
            Instant.ofEpochMilli(message.getTimestamp()),
            filePath.getFileName()
    );

    Hyperlink folderLink = new Hyperlink(displayText);
    folderLink.setWrapText(true);
    folderLink.setMaxWidth(300);

    folderLink.setOnAction(e -> {
      try {
        Desktop.getDesktop().open(parentDir.toFile());
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    });
    return folderLink;
  }

  private boolean sendInitRoomRequest(String toUser, String roomId) {
    String token = RoomTokenEncoder.encode(
            roomId,
            currentChat.getCipher(),
            currentChat.getCipherMode(),
            currentChat.getPaddingMode(),
            currentChat.getIv(),
            currentChat.getKeyBitLength()
    );


    DiffieHellman dh = new DiffieHellman();
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

    return accepted;
  }

  @FXML
  private void onSendMessage() {
    String text = messageInputField.getText().trim();
    if (text.isEmpty()) return;
    messageInputField.clear();

    ChatRoom room = currentChat;

    Context context;
    try{
      context = ContextFactory.getContext(room);
    } catch (Exception e) {
      log.info("there no key for cipher");
      return;
    }

    byte[] afterPadding = context.addPadding(text.getBytes());

    Pair<byte[], byte[]> encrypted = context.encryptDecryptInner(afterPadding, null, true);

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
    log.info("Key bit before send: {}", room.getKeyBitLength());

    long timestamp = System.currentTimeMillis();

    log.info("Current User: {}", currentUserName);
    log.info("Other User: {}", room.getOtherUser());
    log.info("Current room User: {}", room.getOwner());

    if (DiffieHellmanManager.get(room.getRoomId()) == null) {
      DiffieHellman dh = new DiffieHellman();
      DiffieHellmanManager.put(room.getRoomId(), dh);
    }

    boolean delivered = grpcClient.sendMessage(
            currentUserName,
            room.getInterlocutor(currentUserName),
            cipherText,
            token,
            DiffieHellmanManager.get(room.getRoomId()).getPublicComponent().toString()
    );

    if (delivered) {
      Message message = Message.builder()
              .roomId(room.getRoomId())
              .sender(currentUserName)
              .timestamp(timestamp)
              .content(text)
              .build();

      updateLastMessage(room, message);
    }
  }

  @FXML
  private void chooseFile() {
    Window window = messageInputField.getScene().getWindow();

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select file to send");

    File selectedFile = fileChooser.showOpenDialog(window);
    if (selectedFile == null) {
      log.info("File selection canceled");
      return;
    }

    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
    confirmAlert.setTitle("Confirmation of file sending");
    confirmAlert.setHeaderText("Are you sure you want to send this file?");
    confirmAlert.setContentText(selectedFile.getName());

    Optional<ButtonType> result = confirmAlert.showAndWait();
    if (result.isEmpty() || result.get() != ButtonType.OK) {
      log.info("The user cancelled the file submission.");
      return;
    }

    log.info("File selected: {}", selectedFile.getName());

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
      delivered = grpcClient.sendFile(selectedFile, currentUserName, room.getInterlocutor(currentUserName), room, token)
              .orTimeout(5, TimeUnit.SECONDS)
              .get();
    } catch (Exception e) {
      log.info("Error sending file: {}", e.getMessage());
    }

    if (delivered) {
      Message fileMessage = Message.builder()
              .roomId(room.getRoomId())
              .sender(currentUserName)
              .timestamp(System.currentTimeMillis())
              .content("[File] " + selectedFile.getName())
              .filePath(selectedFile.getAbsolutePath())
              .build();

      updateLastMessage(room, fileMessage);
    } else {
      log.info("File not delivered");
    }

  }

  private void updateLastMessage(ChatRoom room, Message fileMessage) {
    updateDbLastMessage(room, fileMessage);

    messagesContainer.getChildren().add(createMessageBubble(fileMessage));
    messagesScrollPane.layout();
    messagesScrollPane.setVvalue(1.0);
  }

  private void updateDbLastMessage(ChatRoom room, Message fileMessage) {
    DaoManager.getMessageDao().insert(fileMessage);

    room.setLastMessage(fileMessage.getContent());
    room.setLastMessageTime(fileMessage.getTimestamp());

    DaoManager.getChatRoomDao().update(room);

    for (int i = 0; i < chatRooms.size(); i++) {
      if (chatRooms.get(i).getRoomId().equals(room.getRoomId())) {
        chatRooms.set(i, room);
        break;
      }
    }

    updateChatListUI();
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


      String stylesPath = Objects.requireNonNull(SceneManager.class.getResource("/css/styles.css")).toExternalForm();

      dialog.getDialogPane().getStylesheets().add(stylesPath);


      Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
      okButton.getStyleClass().add("primary-button");

      Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
      cancelButton.getStyleClass().add("secondary-button");


      Node comboNode = dialog.getDialogPane().lookup(".combo-box");
      if (comboNode instanceof ComboBox<?>) {
        @SuppressWarnings("unchecked")
        ComboBox<String> comboBox = (ComboBox<String>) comboNode;
        comboBox.getStyleClass().add("input-field");
      }

      Optional<String> result = dialog.showAndWait();
      result.ifPresent(choice -> {
        switch (choice) {
          case "Delete chat" -> {
            boolean isDelivered = grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.DELETE_CHAT);
            if (!isDelivered) {
              showAlert(Alert.AlertType.ERROR, "Other user left messenger!");
              break;
            }
            DaoManager.getMessageDao().deleteByRoomId(roomId);
            DaoManager.getChatRoomDao().delete(roomId);
            reloadChatListUI(true);
          }
          case "Remove other" -> {
            boolean isDelivered = grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.REMOVE_USER);
            if (!isDelivered) {
              showAlert(Alert.AlertType.ERROR, "Other user left messenger!");
              break;
            }
            ChatRoom updatedRoom = DaoManager.getChatRoomDao().findByRoomId(roomId)
                    .orElseThrow(() -> new RuntimeException("Chat room not found"));

            updatedRoom.setOtherUser(null);
            DaoManager.getChatRoomDao().update(updatedRoom);
            currentChat = updatedRoom;

            inviteUserButton.setVisible(true);
            reloadChatListUI(false);
            openChat(currentChat);
          }
          case "Leave chat" -> {
            boolean isDelivered = grpcClient.sendControlMessage(currentUserName, otherUser, token, ChatProto.MessageType.OWNER_LEFT);
            if (!isDelivered) {
              showAlert(Alert.AlertType.ERROR, "Other user left messenger!");
              break;
            }
            DaoManager.getMessageDao().deleteByRoomId(roomId);
            DaoManager.getChatRoomDao().delete(roomId);

            reloadChatListUI(true);
          }
        }
      });
    } else {

      Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
      confirm.setTitle("Exit chat");
      confirm.setHeaderText("Are you sure you want to leave the chat?");
      confirm.setContentText("All messages will be deleted");


      String stylesPath = Objects.requireNonNull(SceneManager.class.getResource("/css/styles.css")).toExternalForm();

      confirm.getDialogPane().getStylesheets().add(stylesPath);


      Button okButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
      okButton.setText("Yes");
      okButton.getStyleClass().add("primary-button");

      Button cancelButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL);
      cancelButton.getStyleClass().add("secondary-button");

      Optional<ButtonType> buttonType = confirm.showAndWait();
      if (buttonType.isPresent() && buttonType.get() == ButtonType.OK) {
        DaoManager.getMessageDao().deleteByRoomId(roomId);
        DaoManager.getChatRoomDao().delete(roomId);

        if (otherUser != null) {
          boolean isDelivered = grpcClient.sendControlMessage(currentUserName, currentChat.getInterlocutor(currentUserName), token, ChatProto.MessageType.SELF_LEFT);
          if (!isDelivered) {
            showAlert(Alert.AlertType.ERROR, "Other user left messenger!");
            return;
          }
        }

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
      inviteUserButton.setVisible(false);
      messageInputField.setDisable(true);
      sendButton.setDisable(true);
      sendFileButton.setDisable(true);
    }
    chatTitleLabel.setText("Select chat");
    cipherLabel.setText("Cipher:");
    modeLabel.setText("Mode:");
    paddingLabel.setText("Padding:");
    ivLabel.setText("IV");
    keyLengthLabel.setText("Key Length:");
    leaveChatButton.setDisable(true);
    chatDetailsPane.setDisable(true);
  }

  @FXML
  private void onInviteUserClick() {
    if (currentChat == null) return;

    List<String> allUsers = ChatApiClient.getOnlineUsers().stream()
            .map(User::getUsername)
            .filter(u -> !u.equals(currentUserName))
            .toList();

    if (allUsers.isEmpty()) {
      showAlert(Alert.AlertType.WARNING, "There are no users available to invite");
      return;
    }

    showUserSelectionDialog(allUsers);
  }

  private void showUserSelectionDialog(List<String> allUsers) {
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("Invite User");
    dialog.setHeaderText("Select a user to invite to chat:");

    VBox vbox = new VBox(10);
    vbox.setPadding(new Insets(20));

    TextField searchField = new TextField();
    searchField.setPromptText("Type to search users...");

    ListView<String> userListView = new ListView<>();
    userListView.setPrefHeight(200);
    userListView.setPrefWidth(300);

    ObservableList<String> filteredUsers = FXCollections.observableArrayList(allUsers);
    userListView.setItems(filteredUsers);

    searchField.textProperty().addListener((observable, oldValue, newValue) -> {
      filteredUsers.clear();
      if (newValue == null || newValue.trim().isEmpty()) {
        filteredUsers.addAll(allUsers);
      } else {
        String searchText = newValue.toLowerCase().trim();
        filteredUsers.addAll(
                allUsers.stream()
                        .filter(user -> user.toLowerCase().contains(searchText))
                        .toList()
        );
      }

      if (!filteredUsers.isEmpty()) {
        userListView.getSelectionModel().selectFirst();
      }
    });

    if (!filteredUsers.isEmpty()) {
      userListView.getSelectionModel().selectFirst();
    }

    userListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 2) {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
          dialog.setResult(selectedUser);
          dialog.close();
        }
      }
    });

    searchField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER) {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
          dialog.setResult(selectedUser);
          dialog.close();
        }
      } else if (event.getCode() == KeyCode.DOWN && !filteredUsers.isEmpty()) {
        userListView.requestFocus();
        userListView.getSelectionModel().selectFirst();
      }
    });

    userListView.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER) {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
          dialog.setResult(selectedUser);
          dialog.close();
        }
      } else if (event.getCode() == KeyCode.UP &&
              userListView.getSelectionModel().getSelectedIndex() == 0) {
        searchField.requestFocus();
      }
    });

    vbox.getChildren().addAll(
            new Label("Search:"),
            searchField,
            new Label("Users:"),
            userListView
    );

    dialog.getDialogPane().setContent(vbox);

    ButtonType inviteButtonType = new ButtonType("Invite", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane().getButtonTypes().addAll(inviteButtonType, cancelButtonType);

    Platform.runLater(searchField::requestFocus);

    dialog.setResultConverter(dialogButton -> {
      if (dialogButton == inviteButtonType) {
        return userListView.getSelectionModel().getSelectedItem();
      }
      return null;
    });

    Button inviteButton = (Button) dialog.getDialogPane().lookupButton(inviteButtonType);
    inviteButton.disableProperty().bind(
            userListView.getSelectionModel().selectedItemProperty().isNull()
    );

    Optional<String> result = dialog.showAndWait();
    result.ifPresent(selectedUser -> {
      if (sendInitRoomRequest(selectedUser, currentChat.getRoomId())) {
        currentChat.setOtherUser(selectedUser);
        DaoManager.getChatRoomDao().update(currentChat);

        showAlert(Alert.AlertType.INFORMATION, "The user has been invited to the chat");

        for (int i = 0; i < chatRooms.size(); i++) {
          if (chatRooms.get(i).getRoomId().equals(currentChat.getRoomId())) {
            chatRooms.set(i, currentChat);
            break;
          }
        }
        inviteUserButton.setVisible(false);
      } else {
        currentChat.setOtherUser(null);
        showAlert(Alert.AlertType.ERROR, "User " + selectedUser + " rejected room creation.");
      }
      openChat(currentChat);
      updateChatListUI();
    });
  }

  private void showAlert(Alert.AlertType type, String message) {
    Alert alert = new Alert(type);
    alert.setTitle("Information");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
