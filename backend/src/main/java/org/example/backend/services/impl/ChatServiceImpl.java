package org.example.backend.services.impl;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@GrpcService
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    // потокобезопасный список подписавшихся клиентов
    private final List<StreamObserver<ChatProto.ChatMessage>> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public void receiveMessages(ChatProto.Empty request,
                                StreamObserver<ChatProto.ChatMessage> responseObserver) {
        // добавляем в список, когда клиент вызывает ReceiveMessages
        subscribers.add(responseObserver);
        // при закрытии стрима — удаляем
        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver<ChatProto.ChatMessage> serverObserver =
                    (ServerCallStreamObserver<ChatProto.ChatMessage>) responseObserver;

            serverObserver.setOnCancelHandler(() -> {
                // Клиент закрыл соединение — убираем из списка
                subscribers.remove(responseObserver);
            });
        }
    }

    @Override
    public void sendMessage(ChatProto.ChatMessage request,
                            StreamObserver<ChatProto.Empty> responseObserver) {
        // рассылка пришедшего сообщения всем подписчикам
        for (var sub : subscribers) {
            sub.onNext(request);
        }
        // подтверждаем отправителю
        responseObserver.onNext(ChatProto.Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
