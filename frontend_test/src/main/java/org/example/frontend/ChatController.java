package org.example.frontend;


import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import io.grpc.stub.StreamObserver;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ChatController {
    @FXML private ListView<String> messageList;
    @FXML private TextField inputField;

    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;

    private String username = null;

    private String secondName = null;

    private boolean needToContinue = true;

    private final Map<String, OutputStream> fileStreams = new HashMap<>();

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
                        switch (msg.getType()) {
                            case ChatProto.MessageType.TEXT ->
                                Platform.runLater(() ->
                                        messageList.getItems().add(
                                                "[" + msg.getDateTime() + "] " +
                                                        "from: " + msg.getFromUserName() + ": " + msg.getText()
                                        )
                                );

                            case ChatProto.MessageType.INIT_ROOM -> {
                                log.info("get message as invite to room: {}",
                                        msg);
                                if (needToContinue) {
                                    initRoom();
                                }

                            }

                            case FILE -> Platform.runLater(() ->
                            {
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
                                        messageList.getItems().add("[Файл готов] " + name);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }

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
                .setDateTime(System.currentTimeMillis())
                .setText(text)
                .setType(ChatProto.MessageType.TEXT)
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

    public void initRoom() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        ChatProto.InitRoomRequest request = ChatProto.InitRoomRequest.newBuilder()
                .setFromUserName(username)
                .setToUserName(secondName)
                .setToken("smth")
                .setPublicComponent(text)
                .setType(ChatProto.MessageType.INIT_ROOM)
                .build();

        log.info("send to other user: {}", request);

        ChatProto.InitRoomResponse response = blockingStub.initRoom(request);
        log.info("message had been sended: {}", response);

        needToContinue = false;

    }

    /** Вызывайте этот метод при закрытии приложения, чтобы чисто закрыть канал */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    public void sendFile(File file, String toUser) {
        int chunkSize = 1024 * 512; // 512 KB
        byte[] buffer = new byte[chunkSize];
        int index = 0;

        StreamObserver<ChatProto.SendMessageResponse> respObs = new StreamObserver<>() {
            @Override public void onNext(ChatProto.SendMessageResponse r) {
                System.out.println("File send result: " + r.getDelivered());
            }
            @Override public void onError(Throwable t) { t.printStackTrace(); }
            @Override public void onCompleted() { /* готово */ }
        };

        StreamObserver<ChatProto.FileChunk> reqObs = asyncStub.sendFile(respObs);
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            long amountBytes = file.length();
            long amountChunks = amountBytes / chunkSize;
            while ((read = fis.read(buffer)) != -1) {
                index++;
                ChatProto.FileChunk chunk = ChatProto.FileChunk.newBuilder()
                        .setFromUserName(username)
                        .setToUserName(toUser)
                        .setFileName(file.getName())
                        .setData(ByteString.copyFrom(buffer, 0, read))
                        .setChunkNumber(index)
                        .setIsLast(false)
                        .setAmountChunks(amountChunks)
                        .build();
                reqObs.onNext(chunk);
            }

            ChatProto.FileChunk last = ChatProto.FileChunk.newBuilder()
                    .setFromUserName(username)
                    .setToUserName(toUser)
                    .setFileName(file.getName())
                    .setData(ByteString.EMPTY)
                    .setChunkNumber(index + 1)
                    .setIsLast(true)
                    .setAmountChunks(amountChunks)
                    .build();
            reqObs.onNext(last);
            reqObs.onCompleted();
        } catch (IOException e) {
            reqObs.onError(e);
        }
    }


    public void handleChooseFile() {

        Window window = messageList.getScene().getWindow();


        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл для отправки");
        // При желании — ограничить типы:
//        fileChooser.getExtensionFilters().addAll(
//                new FileChooser.ExtensionFilter("Все файлы", "*.*"),
//                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"),
//                new FileChooser.ExtensionFilter("Документы", "*.pdf", "*.docx", "*.txt")
//        );


        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile != null) {

            messageList.getItems().add("Вы выбрали: " + selectedFile.getName());

            sendFile(selectedFile, secondName);
        } else {
            messageList.getItems().add("Выбор файла отменён.");
        }
    }
}


