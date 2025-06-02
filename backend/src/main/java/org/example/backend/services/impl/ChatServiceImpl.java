package org.example.backend.services.impl;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.backend.dto.responses.UsernameResponse;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@GrpcService
@Slf4j
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {

    private final Map<String, StreamObserver<ChatProto.ChatMessage>> clients = new ConcurrentHashMap<>();

    @Override
    public void connect(ChatProto.ConnectRequest request, StreamObserver<ChatProto.ChatMessage> responseObserver) {
        String userId = request.getUserName();
        clients.put(userId, responseObserver);


        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver<ChatProto.ChatMessage> serverObserver =
                    (ServerCallStreamObserver<ChatProto.ChatMessage>) responseObserver;

            serverObserver.setOnCancelHandler(() -> {
                clients.remove(userId);
                System.out.println("User " + userId + " disconnected");
            });
        }
    }

    @Override
    public void initRoom(ChatProto.InitRoomRequest request, StreamObserver<ChatProto.InitRoomResponse> responseObserver) {
        String toUser = request.getToUserName();
        StreamObserver<ChatProto.ChatMessage> recipientStream = clients.get(toUser);
        boolean isDelivered = false;

        if (recipientStream != null) {
            try {
                ChatProto.ChatMessage message = ChatProto.ChatMessage.newBuilder()
                        .setFromUserName(request.getFromUserName())
                        .setType(request.getType())
                        .setToken(request.getToken())
                        .setPublicExponent(request.getPublicComponent())
                        .build();


                recipientStream.onNext(message);
                isDelivered = true;

            } catch (StatusRuntimeException e) {
                clients.remove(toUser);
            }

        }
        ChatProto.InitRoomResponse response = ChatProto.InitRoomResponse.newBuilder()
                .setIsDelivered(isDelivered)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void sendMessage(ChatProto.SendMessageRequest request, StreamObserver<ChatProto.SendMessageResponse> responseObserver) {
        String recipientId = request.getToUserName();
        StreamObserver<ChatProto.ChatMessage> recipientStream = clients.get(recipientId);
        boolean delivered = false;

        if (recipientStream != null) {
            try {
                ChatProto.ChatMessage message = ChatProto.ChatMessage.newBuilder()
                        .setFromUserName(request.getFromUserName())
                        .setText(request.getText())
                        .setDateTime(request.getDateTime())
                        .setToken(request.getToken())
                        .setPublicExponent(request.getPublicExponent())
                        .build();


                recipientStream.onNext(message);
                delivered = true;
            } catch (StatusRuntimeException e) {

                clients.remove(recipientId);
            }
        }

        // Возвращаем результат отправителю
        ChatProto.SendMessageResponse response = ChatProto.SendMessageResponse.newBuilder()
                .setDelivered(delivered)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public List<UsernameResponse> getOnlineUsernames() {
        return clients.keySet().stream().map(UsernameResponse::new).toList();
    }


    @Override
    public StreamObserver<ChatProto.FileChunk> sendFile(StreamObserver<ChatProto.SendMessageResponse> responseObserver) {
        return new StreamObserver<>() {
            private String toUser;
            private String fromUser;
            private String fileName;
            private long totalChunks;
            private long receivedChunks = 0;
            private boolean isCompleted = false;

            @Override
            public void onNext(ChatProto.FileChunk chunk) {
                try {

                    if (fromUser == null) {
                        fromUser = chunk.getFromUserName();
                        toUser = chunk.getToUserName();
                        fileName = chunk.getFileName();
                        totalChunks = chunk.getAmountChunks();
                        log.info("total chunks: {}", totalChunks);
                    }

                    receivedChunks++;
                    log.info("current received chunks amount: {}",receivedChunks);

                    var targetClient = clients.get(toUser);
                    if (targetClient != null) {
                        ChatProto.ChatMessage msg = ChatProto.ChatMessage.newBuilder()
                                .setFromUserName(fromUser)
                                .setDateTime(System.currentTimeMillis())
                                .setType(ChatProto.MessageType.FILE)
                                .setFileName(fileName)
                                .setChunk(chunk.getData())
                                .setChunkNumber(chunk.getChunkNumber())
                                .setIsLast(chunk.getIsLast())
                                .setAmountChunks(totalChunks)
                                .setToken(chunk.getToken())
                                .build();

                        targetClient.onNext(msg);
                    }


                    if (chunk.getIsLast()) {
                        if (receivedChunks + 1 == totalChunks) {
                            log.info("Сюда попадаем????");

                            sendSuccessResponse(responseObserver, true);
                        } else {
                            sendErrorResponse(responseObserver, "Missing chunks");
                        }
                        isCompleted = true;
                    }
                } catch (Exception e) {
                    sendErrorResponse(responseObserver, "Server error: " + e.getMessage());
                    isCompleted = true;
                }
            }

            @Override
            public void onError(Throwable t) {
                if (!isCompleted) {
                    log.error("File transfer error", t);
                    sendErrorResponse(responseObserver, "Transfer failed");
                }
            }

            @Override
            public void onCompleted() {
                if (!isCompleted && receivedChunks == totalChunks) {
                    sendSuccessResponse(responseObserver, false);
                }
            }

            private void sendSuccessResponse(StreamObserver<ChatProto.SendMessageResponse> obs, boolean fullyDelivered) {
                log.info("на сервере успешное отправка всего файла!!!!!");
                obs.onNext(ChatProto.SendMessageResponse.newBuilder()
                        .setDelivered(true)
                        .setFullyDeliveredFile(fullyDelivered)
                        .build());
                obs.onCompleted();
                isCompleted = true;
            }

            private void sendErrorResponse(StreamObserver<ChatProto.SendMessageResponse> obs, String error) {
                obs.onError(Status.INTERNAL
                        .withDescription(error)
                        .asRuntimeException());
                isCompleted = true;
            }
        };
    }
}
