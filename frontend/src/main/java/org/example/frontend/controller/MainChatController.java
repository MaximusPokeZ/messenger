package org.example.frontend.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.ChatItem;
import org.example.frontend.model.main.Message;
import org.example.frontend.model.main.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MainChatController {
  @FXML
  private Label userLabel;
  @FXML
  private Button logoutButton;
  @FXML
  private TextField searchField;
  @FXML
  private Button searchButton;
  @FXML
  private ListView<ChatItem> chatListView;
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
  private VBox searchResultsPanel;
  @FXML
  private ListView searchResultsListView;
  @FXML
  private Button closeSearchButton;

  private ChatItem currentChat;

  private List<ChatItem> chatItems;

  private String currentUserName = "Max";

  public void initialize() {
    setupUI();
    loadUserChats();
    setupEventHandlers();
  }

  private void setupUI() {
    currentUserName = JwtStorage.getUsername();
    userLabel.setText("User: " + currentUserName);

    chatListView.setCellFactory(cell -> new ListCell<ChatItem>() {
      @Override
      protected void updateItem(ChatItem item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          VBox vBox = new VBox(2);

          Label nameLabel = new Label(item.getChatName());
          nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

          Label lastMessageLabel = new Label(item.getLastMessage());
          lastMessageLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

          Label timeLabel = new Label(item.getLastMessageTime());
          timeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 10px;");

          vBox.getChildren().addAll(nameLabel, lastMessageLabel, timeLabel);
          setGraphic(vBox);
        }
      }
    });

    searchResultsListView.setCellFactory(cell -> new ListCell<User>() {
      @Override
      protected void updateItem(User user, boolean empty) {
        super.updateItem(user, empty);
        if (empty || user == null) {
          setText(null);
        } else {
          setText(user.getUsername());
        }
      }
    });

    messagesContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
      Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    });
  }

  private void loadUserChats() {
    // TODO: загрузить чаты с сервера

    chatItems = new ArrayList<>();
    chatItems.add(new ChatItem(1, "Алиса", "Привет! Как дела?", "15:30", true));
    chatItems.add(new ChatItem(2, "Боб", "Увидимся завтра", "14:22", false));
    chatItems.add(new ChatItem(3, "Кэрол", "Спасибо за помощь!", "13:45", true));

    chatListView.getItems().setAll(chatItems);
  }

  private void setupEventHandlers() {
    chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
      if (newSelection != null) {
        selectChat(newSelection);
      }
    });

    searchResultsListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 2) {
        User selectedUser = (User) searchResultsListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
          log.info("СCreated chat with "+ selectedUser.getUsername());
          // createChatWithUser(selectedUser); TODO:
        }
      }
    });
  }

  private void selectChat(ChatItem chat) {
    currentChat = chat;
    chatTitleLabel.setText(chat.getChatName());
    chatStatusLabel.setText("online"); // TODO: получать реальный статус

    messageInputField.setDisable(false);
    sendButton.setDisable(false);

    loadChatMessages(String.valueOf(chat.getId())); // TODO: подумать над id чата
  }

  private void loadChatMessages(String chatId) {
    // TODO: загрузить сообщения с сервера

    messagesContainer.getChildren().clear();

    List<Message> messages = new ArrayList<>();
    messages.add(new Message("1", "Алиса", "Привет! Как твои дела?", LocalDateTime.now().minusHours(2), "id_1"));
    messages.add(new Message("2", currentUserName, "Привет! Все отлично, спасибо!", LocalDateTime.now().minusHours(1), "id_2"));
    messages.add(new Message("3", "Алиса", "Рад слышать! Что планируешь на выходные?", LocalDateTime.now().minusMinutes(30), "id_3"));
    messages.add(new Message("4", currentUserName, "Думаю погулять в парке, если погода будет хорошая", LocalDateTime.now().minusMinutes(15), "id_4"));

    for (Message message : messages) {
      addMessageToUI(message);
    }
  }

  private void addMessageToUI(Message message) {
    HBox messageBox = new HBox();
    VBox messageBubble = new VBox(2);

    Label messageText = new Label(message.getContent());
    messageText.setWrapText(true);
    messageText.setMaxWidth(300);

    Label timeLabel = new Label(message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")));
    timeLabel.getStyleClass().add("message-time");

    messageBubble.getChildren().addAll(messageText, timeLabel);

    boolean isCurrentUser = message.getSender().equals(currentUserName);
    if (isCurrentUser) {
      messageBubble.getStyleClass().add("message-sent");
      messageBox.setAlignment(Pos.CENTER_RIGHT);
      messageBox.getChildren().add(messageBubble);
    } else {
      messageBubble.getStyleClass().add("message-received");
      messageBox.setAlignment(Pos.CENTER_LEFT);

      Label senderLabel = new Label(message.getSender());
      senderLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
      VBox leftContainer = new VBox(2);
      leftContainer.getChildren().addAll(senderLabel, messageBubble);
      messageBox.getChildren().add(leftContainer);
    }

    messageBubble.getStyleClass().add("message-bubble");
    messageBox.setPadding(new Insets(5));

    messagesContainer.getChildren().add(messageBox);
  }



  @FXML
  private void onLogoutClick() {}

  @FXML
  private void onSearchClick() {}

  @FXML
  private void onSendMessage() {}

  @FXML
  private void onCloseSearchClick() {}


}
