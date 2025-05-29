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
import org.example.frontend.manager.SceneManager;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.ChatSetting;
import org.example.frontend.model.main.User;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class MainChatController {

  private ManagedChannel channel;
  private ChatServiceGrpc.ChatServiceStub asyncStub;
  private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;


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

  private List<ChatRoom> chatRooms;

  public String currentUserName = JwtStorage.getUsername(); // TODO: брать из JWT;

  public void initialize() {

    channel = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext()
            .build();
    asyncStub    = ChatServiceGrpc.newStub(channel);
    blockingStub = ChatServiceGrpc.newBlockingStub(channel);

    ChatProto.ConnectRequest connectRequest = ChatProto.ConnectRequest.newBuilder().setUserName(currentUserName).build();
    asyncStub.connect(
            connectRequest,
            new io.grpc.stub.StreamObserver<ChatProto.ChatMessage>() {
              @Override
              public void onNext(ChatProto.ChatMessage msg) {
                Platform.runLater(() ->
                        log.info(
                                "[" + msg.getDateTime() + "] " +
                                        "from: " + msg.getFromUserName() + ": " + msg.getText()
                        )
                );
              }

              @Override
              public void onError(Throwable t) {
                t.printStackTrace();
              }

              @Override
              public void onCompleted() {
                System.out.println("disconnected");
              }
            }
    );

    messageInputField.setDisable(false);
    sendButton.setDisable(false);

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
    List<String> allUsers = ChatApiClient.getOnlineUsersTest().stream() // TODO: убрать тест
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

    result.ifPresent(settings -> {
      log.info("Cipher: {}", settings.getCipher());
      log.info("Cipher mode: {}", settings.getCipherMode());
      log.info("Padding mode: {}", settings.getPaddingMode());
      log.info("IV: {}", settings.getIv());
    });
  }

  @FXML
  private void onSendMessage() {
    String text = messageInputField.getText().trim();
    if (text.isEmpty()) return;
    messageInputField.clear();

    ChatProto.SendMessageRequest request = ChatProto.SendMessageRequest.newBuilder()
            .setFromUserName(currentUserName)
            .setToUserName(currentUserName.equals("max") ? "sasha" : "max")
            .setDateTime(Instant.now().toString())
            .setText(text)
            .build();


    ChatProto.SendMessageResponse response = blockingStub.sendMessage(request);
    if(response.getDelivered()) {
      Platform.runLater(() ->
              log.info(
                      "[" + request.getDateTime() + "] " +
                              "from: " + request.getFromUserName() + ": " + request.getText()
              )
      );
    }
  }

  @FXML
  private void onCloseSearchClick() {
    searchResultsPanel.setVisible(false);
    searchResultsListView.getItems().clear();
    searchField.clear();
  }


}
