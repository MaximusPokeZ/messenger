package org.example.frontend.manager;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.time.Instant;
import java.util.function.Consumer;

public class GrpcClient {
  private final ManagedChannel channel;

  @Getter
  private final ChatServiceGrpc.ChatServiceStub asyncStub;

  @Getter
  private final ChatServiceGrpc.ChatServiceBlockingStub blockingStub;

  private static GrpcClient instance;

  private GrpcClient(String host, int port) {
    channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
    asyncStub = ChatServiceGrpc.newStub(channel);
    blockingStub = ChatServiceGrpc.newBlockingStub(channel);
  }

  public static GrpcClient getInstance() {
    if (instance == null) {
      instance = new GrpcClient("localhost", 50051);
    }
    return instance;
  }

  public static GrpcClient getInstance(String host, int port) {
    if (instance == null) {
      instance = new GrpcClient(host, port);
    }
    return instance;
  }

  public void shutdown() {
    if (!channel.isShutdown()) {
      channel.shutdown();
    }
  }

  public void connect(String username, Consumer<ChatProto.ChatMessage> onMessage, Runnable onCompleted, Consumer<Throwable> onError) {
    ChatProto.ConnectRequest request = ChatProto.ConnectRequest.newBuilder()
            .setUserName(username)
            .build();

    asyncStub.connect(request, new StreamObserver<>() {
      @Override
      public void onNext(ChatProto.ChatMessage msg) {
        onMessage.accept(msg);
      }

      @Override
      public void onError(Throwable t) {
        onError.accept(t);
      }

      @Override
      public void onCompleted() {
        onCompleted.run();
      }
    });
  }

  public boolean sendMessage(String from, String to, String text) {
    ChatProto.SendMessageRequest request = ChatProto.SendMessageRequest.newBuilder()
            .setFromUserName(from)
            .setToUserName(to)
            .setText(text)
            .setDateTime(Instant.now().toString())
            .build();

    ChatProto.SendMessageResponse response = blockingStub.sendMessage(request);
    return response.getDelivered();
  }


}
