package org.example.frontend.manager;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher.context.Context;
import org.example.frontend.controller.FileTransferProgressController;
import org.example.frontend.factory.ContextFactory;
import org.example.frontend.model.main.ChatRoom;
import org.example.shared.ChatProto;
import org.example.shared.ChatServiceGrpc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class GrpcClient {
  private final FileTransferProgressController fileController = new FileTransferProgressController();

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

  public void shutdown() throws StatusRuntimeException {
    if (channel != null && !channel.isShutdown()) {
      channel.shutdownNow();
    }
//    channel.shutdown();
//    try {
//      if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
//        channel.shutdownNow();
//      }
//    } catch (InterruptedException e) {
//      channel.shutdownNow();
//    }
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

  public boolean sendMessage(String from, String to, String text, String token, String secret) {
    ChatProto.SendMessageRequest request = ChatProto.SendMessageRequest.newBuilder()
            .setFromUserName(from)
            .setToUserName(to)
            .setText(text)
            .setDateTime(System.currentTimeMillis())
            .setType(ChatProto.MessageType.TEXT)
            .setToken(token)
            .setPublicExponent(secret)
            .build();

    ChatProto.SendMessageResponse response = blockingStub.sendMessage(request);
    return response.getDelivered();
  }

  public boolean sendControlMessage(String from, String to, String token, ChatProto.MessageType type) {
    ChatProto.InitRoomRequest request = ChatProto.InitRoomRequest.newBuilder()
            .setFromUserName(from)
            .setToUserName(to)
            .setToken(token)
            .setPublicComponent("")
            .setType(type)
            .build();

    ChatProto.InitRoomResponse response = blockingStub.initRoom(request);
    return response.getIsDelivered();
  }


  public CompletableFuture<Boolean> sendFile(File file, String fromUser, String toUser, ChatRoom room, String token) throws IOException {
    int chunkSize = 1024 * 512; // 512 KB
    byte[] buffer = new byte[chunkSize];
    int index = 0;
    CompletableFuture<Boolean> deliveryFuture = new CompletableFuture<>();
    byte[] previous = null;

    Context context;
    try{
      context = ContextFactory.getContext(room);
    } catch (Exception e) {
      log.info("there no key for cipher");
      deliveryFuture.complete(false);
      return deliveryFuture;
    }

    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/progress-bar.fxml"));
    Parent root = fxmlLoader.load();
    FileTransferProgressController fileController = fxmlLoader.getController();
    Scene scene = new Scene(root);

    Stage progressStage = new Stage();
    progressStage.setScene(scene);
    progressStage.setTitle("Sending file");
    progressStage.initModality(Modality.APPLICATION_MODAL);
    progressStage.setOnCloseRequest(e -> deliveryFuture.cancel(true));
    Platform.runLater(progressStage::show);

    StreamObserver<ChatProto.SendMessageResponse> respObs = new StreamObserver<>() {
      @Override
      public void onNext(ChatProto.SendMessageResponse r) {
        if (r.getFullyDeliveredFile()) {
          Platform.runLater(fileController::setComplete);
          deliveryFuture.complete(true);
        }
      }

      @Override
      public void onError(Throwable t) {
        Platform.runLater(() -> fileController.setError(t.getMessage()));
        deliveryFuture.completeExceptionally(t);
      }

      @Override
      public void onCompleted() {}
    };

    StreamObserver<ChatProto.FileChunk> reqObs = asyncStub.sendFile(respObs);
    try (FileInputStream fis = new FileInputStream(file)) {
      int read;
      long amountBytes = file.length();
      log.info("amount bytes: {}", amountBytes);
      long amountChunks = (long) Math.ceil((double) amountBytes / (double) chunkSize) + 1;
      log.info("amount chunks: {}", amountChunks);
      byte[] lastBlock = null;

      while ((read = fis.read(buffer)) != -1) {
        if (lastBlock != null) {
          index++;
          Pair<byte[], byte[]> encryptedPart = context.encryptDecryptInner(lastBlock, previous, true);
          previous = encryptedPart.getValue();

          ChatProto.FileChunk chunk = ChatProto.FileChunk.newBuilder()
                  .setFromUserName(fromUser)
                  .setToUserName(toUser)
                  .setFileName(file.getName())
                  .setData(ByteString.copyFrom(encryptedPart.getKey()))
                  .setChunkNumber(index)
                  .setIsLast(false)
                  .setToken(token)
                  .setAmountChunks(amountChunks)
                  .build();
          reqObs.onNext(chunk);

          int finalIndex = index;
          Platform.runLater(() -> fileController.updateProgress(finalIndex, amountChunks));
        }

        lastBlock = Arrays.copyOf(buffer, read);
      }

      Pair<byte[], byte[]> encryptedPart;
      if (lastBlock != null) {
        byte[] paddedBlock = context.addPadding(lastBlock);
        encryptedPart = context.encryptDecryptInner(paddedBlock, previous, true);
      } else {
        byte[] emptyPadded = context.addPadding(new byte[0]);
        encryptedPart = context.encryptDecryptInner(emptyPadded, previous, true);
      }

      ChatProto.FileChunk last = ChatProto.FileChunk.newBuilder()
              .setFromUserName(fromUser)
              .setToUserName(toUser)
              .setFileName(file.getName())
              .setData(ByteString.copyFrom(encryptedPart.getKey()))
              .setChunkNumber(index + 1)
              .setIsLast(true)
              .setToken(token)
              .setAmountChunks(amountChunks)
              .build();
      reqObs.onNext(last);
      reqObs.onCompleted();

    } catch (Exception e) {
      reqObs.onError(e);
      throw e;
    } finally {
      deliveryFuture.whenComplete((res, ex) -> {
        Platform.runLater(() -> {
          if (ex != null) {
            fileController.setError(ex.getMessage());
          }
          new Thread(() -> {
            try {
              Thread.sleep(2000); // Задержка перед закрытием
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
            Platform.runLater(progressStage::close);
          }).start();
        });
      });
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

  public static void resetInstance() throws StatusRuntimeException {
    if (instance != null) {
      instance.shutdown();
    }
    instance = null;
  }
}
