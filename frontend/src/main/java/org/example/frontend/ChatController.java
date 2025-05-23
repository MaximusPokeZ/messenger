package org.example.frontend;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
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
    private final String username = "Client_" + (int)(Math.random() * 1000);

    public void initialize() {
        // 1) Строим канал и стабы
        channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        asyncStub    = ChatServiceGrpc.newStub(channel);
        blockingStub = ChatServiceGrpc.newBlockingStub(channel);

        // 2) Подписываемся на получение входящих сообщений
        asyncStub.receiveMessages(
                ChatProto.Empty.newBuilder().build(),
                new io.grpc.stub.StreamObserver<ChatProto.ChatMessage>() {
                    @Override
                    public void onNext(ChatProto.ChatMessage msg) {
                        Platform.runLater(() ->
                                messageList.getItems().add(
                                        "[" + msg.getTimestamp() + "] " +
                                                msg.getSender() + ": " + msg.getContent()
                                )
                        );
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                        // Сервер закрыл поток
                    }
                }
        );
    }

    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        // 3) Строим сообщение и заполняем timestamp
        ChatProto.ChatMessage msg = ChatProto.ChatMessage.newBuilder()
                .setSender(username)
                .setContent(text)
                .setTimestamp(Instant.now().toString())
                .build();

        // 4) Отправляем через blockingStub — проще и без StreamObserver
        try {
            blockingStub.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Вызывайте этот метод при закрытии приложения, чтобы чисто закрыть канал */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }
}
