package org.example.frontend.controller;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
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
import org.example.frontend.model.main.User;
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

    grpcClient.connect(
            currentUserName,
            msg -> Platform.runLater(() -> log.info(
                    "[{}] from: {}: {}",
                    msg.getDateTime(),
                    msg.getFromUserName(),
                    msg.getText()
            )),
            () -> log.info("Disconnected from server"),
            Throwable::printStackTrace
    );

    searchResultsPanel.setVisible(false);
  }

  @FXML
  private void onLogoutClick() {
    JwtStorage.setUsername(null);
    JwtStorage.setToken(null);
    try {
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
      sendInitRoomRequest(username, token, guid);
    });
  }

  private void sendInitRoomRequest(String toUser, String token, String roomId) {


    //TODO : послать запрос на создание комнаты у другого юзера + там же диффи хелман


  }

  @FXML
  private void onSendMessage() {
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

}
