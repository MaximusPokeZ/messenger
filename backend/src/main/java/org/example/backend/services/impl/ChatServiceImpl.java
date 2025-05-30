package org.example.backend.services.impl;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.backend.dto.responses.UsernameResponse;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@GrpcService
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

            } catch (StatusRuntimeException e) {
                clients.remove(toUser);
            }

        }
        isDelivered = true;
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
    public StreamObserver<ChatProto.FileChunk> sendFile(StreamObserver<ChatProto.SendMessageResponse> rep) {
        return new StreamObserver<>() {
            String to;
            String from;
            String fname;

            @Override
            public void onNext(ChatProto.FileChunk chunk) {
                // Сохраняем мета из первого чанка
                if (from == null) {
                    from  = chunk.getFromUserName();
                    to    = chunk.getToUserName();
                    fname = chunk.getFileName();
                }
                // формируем ChatMessage-чанк
                ChatProto.ChatMessage msg = ChatProto.ChatMessage.newBuilder()
                        .setFromUserName(from)
                        .setDateTime(Instant.now().toString())
                        .setType(ChatProto.MessageType.FILE)
                        .setFileName(fname)
                        .setChunk(chunk.getData())
                        .setChunkNumber(chunk.getChunkNumber())
                        .setIsLast(chunk.getIsLast())
                        .build();

                var target = clients.get(to);
                if (target != null) {
                    target.onNext(msg);
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                rep.onNext(ChatProto.SendMessageResponse.newBuilder().setDelivered(true).build());
                rep.onCompleted();
            }
        };
    }
}
