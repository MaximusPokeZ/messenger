package org.example.frontend.manager;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher_ykwais.constants.CipherMode;
import org.example.frontend.cipher_ykwais.constants.PaddingMode;
import org.example.frontend.cipher_ykwais.context.Context;
import org.example.frontend.cipher_ykwais.rc6.RC6;
import org.example.frontend.cipher_ykwais.rc6.enums.RC6KeyLength;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
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

  public boolean sendMessage(String from, String to, String text, String token) {
    ChatProto.SendMessageRequest request = ChatProto.SendMessageRequest.newBuilder()
            .setFromUserName(from)
            .setToUserName(to)
            .setText(text)
            .setDateTime(System.currentTimeMillis())
            .setType(ChatProto.MessageType.TEXT)
            .setToken(token)
            .build();

    ChatProto.SendMessageResponse response = blockingStub.sendMessage(request);
    return response.getDelivered();
  }

  public CompletableFuture<Boolean> sendFile(File file, String fromUser, String toUser) {
    int chunkSize = 1024 * 512; // 512 KB
    byte[] buffer = new byte[chunkSize];
    int index = 0;
    CompletableFuture<Boolean> deliveryFuture = new CompletableFuture<>();
    byte[] previous = null;

    Context context = new Context(new RC6(RC6KeyLength.KEY_128, new byte[16]), CipherMode.ECB, PaddingMode.ANSI_X923, new byte[16]);

    StreamObserver<ChatProto.SendMessageResponse> respObs = new StreamObserver<>() {
      @Override public void onNext(ChatProto.SendMessageResponse r) {
        if (r.getFullyDeliveredFile()) {
          deliveryFuture.complete(true);
        }
        //TODO здесь можно обновлять прогресс бар для того, кто отправляет
//        Platform.runLater(() -> {
//          progressBar.setProgress(r.getChunkNumber() / (double) r.getAmountChunks());
//        });
      }
      @Override public void onError(Throwable t) { t.printStackTrace(); }
      @Override public void onCompleted() {
      }
    };

    StreamObserver<ChatProto.FileChunk> reqObs = asyncStub.sendFile(respObs);
    try (FileInputStream fis = new FileInputStream(file)) {
      int read;
      long amountBytes = file.length();
      log.info("amount bytes: {}", amountBytes);
      long amountChunks = (long) Math.ceil((double)amountBytes / (double)chunkSize) + 1; //так как есть 1 пустой еще //TODO уже не актуально
      log.info("amount chunks: {}", amountChunks);
      byte[] lastBlock = null;

      while ((read = fis.read(buffer)) != -1) {

        if (lastBlock != null) {
          index++;
          Pair<byte[], byte[]> encryptedPart = context.encryptDecryptInner(lastBlock, previous,true);
          previous = encryptedPart.getValue();

          ChatProto.FileChunk chunk = ChatProto.FileChunk.newBuilder()
                  .setFromUserName(fromUser)
                  .setToUserName(toUser)
                  .setFileName(file.getName())
                  .setData(ByteString.copyFrom(encryptedPart.getKey()))
                  .setChunkNumber(index)
                  .setIsLast(false)
                  .setAmountChunks(amountChunks)
                  .build();
          reqObs.onNext(chunk);
        }

        lastBlock = Arrays.copyOf(buffer, read);
      }

      Pair<byte[], byte[]> encryptedPart;

      if (lastBlock != null) {
        byte[] paddedBlock = context.addPadding(lastBlock);
        encryptedPart = context.encryptDecryptInner(paddedBlock, previous,true);

      } else {
        byte[] emptyPadded = context.addPadding(new byte[0]);
        encryptedPart = context.encryptDecryptInner(emptyPadded, previous,true);

      }

      ChatProto.FileChunk last = ChatProto.FileChunk.newBuilder()
              .setFromUserName(fromUser)
              .setToUserName(toUser)
              .setFileName(file.getName())
              .setData(ByteString.copyFrom(encryptedPart.getKey()))
              .setChunkNumber(index + 1)
              .setIsLast(true)
              .setAmountChunks(amountChunks)
              .build();
      reqObs.onNext(last);
      reqObs.onCompleted();
    } catch (IOException e) {
      reqObs.onError(e);
    }
    return deliveryFuture;
  }

  public boolean sendInitRoomRequest(String fromUser, String toUser, String token, String publicComponent) {
    ChatProto.InitRoomRequest request = ChatProto.InitRoomRequest.newBuilder()
            .setFromUserName(fromUser)
            .setToUserName(toUser)
            .setToken(token)
            .setPublicComponent(publicComponent)
            .setType(ChatProto.MessageType.INIT_ROOM)
            .build();

    ChatProto.InitRoomResponse response = blockingStub.initRoom(request);
    return response.getIsDelivered();
  }


}
