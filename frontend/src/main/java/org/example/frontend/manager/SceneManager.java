package org.example.frontend.manager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.Objects;

public class SceneManager {

  private SceneManager () {}

  @Getter
  @Setter
  private static Stage currentStage;

  public static void switchToLoginScene() throws IOException {
    switchScene("/view/login_1.fxml", "TalkSafe - Login", 440, 560);

    currentStage.setResizable(false);
    ImageView view = new ImageView();
    Image image = new Image(Objects.requireNonNull(SceneManager.class.getResourceAsStream("/images/logo.png")));
    view.setImage(image);
  }

  public static void switchToRegisterScene() throws IOException {
    switchScene("/view/register_1.fxml", "TalkSafe - Register", 440, 560);
    currentStage.setResizable(false);
  }

  public static void switchToMainScene() throws IOException {
    switchScene("/view/main_chat_1.fxml", "TalkSafe - Main", 1100, 700);
  }

  private static void switchScene(String fxmlPath, String title, double width , double height) throws IOException {
    if (currentStage == null) {
      throw new RuntimeException("Current stage not set need call setCurrentStage() first");
    }

    FXMLLoader fxmlLoader = new FXMLLoader(SceneManager.class.getResource(fxmlPath));
    Scene scene = new Scene(fxmlLoader.load(), width, height);

    String stylesPath = Objects.requireNonNull(SceneManager.class.getResource("/css/styles.css")).toExternalForm();
    scene.getStylesheets().add(stylesPath);

    currentStage.setTitle(title);
    currentStage.setScene(scene);
    currentStage.setMinHeight(height);
    currentStage.setMinWidth(width);

    currentStage.centerOnScreen();
  }

}
