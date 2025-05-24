package org.example.frontend;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.time.Instant;

public class ChatController {
    @FXML private ListView<String> messageList;
    @FXML private TextField inputField;

    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;

    private String username = null;

    private String secondName = null;

    public ChatController(String username, String secondName) {
        this.username = username;
        this.secondName = secondName;
    }

    public void initialize() {
        channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        asyncStub    = ChatServiceGrpc.newStub(channel);
        blockingStub = ChatServiceGrpc.newBlockingStub(channel);

        ChatProto.ConnectRequest connectRequest = ChatProto.ConnectRequest.newBuilder().setUserName(username).build();
        asyncStub.connect(
                connectRequest,
                new io.grpc.stub.StreamObserver<ChatProto.ChatMessage>() {
                    @Override
                    public void onNext(ChatProto.ChatMessage msg) {
                        Platform.runLater(() ->
                                messageList.getItems().add(
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
    }

    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        ChatProto.SendMessageRequest request = ChatProto.SendMessageRequest.newBuilder()
                .setFromUserName(username)
                .setToUserName(secondName)
                .setDateTime(Instant.now().toString())
                .setText(text)
                .build();


        ChatProto.SendMessageResponse response = blockingStub.sendMessage(request);
        if(response.getDelivered()) {
            Platform.runLater(() ->
                    messageList.getItems().add(
                            "[" + request.getDateTime() + "] " +
                                    "from: " + request.getFromUserName() + ": " + request.getText()
                    )
            );
        }

    }

    /** Вызывайте этот метод при закрытии приложения, чтобы чисто закрыть канал */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }
}
