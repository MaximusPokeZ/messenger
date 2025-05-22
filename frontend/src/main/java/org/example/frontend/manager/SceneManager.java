package org.example.frontend.manager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class SceneManager {
  private static Stage currentStage;

  public static void switchToLoginScene() throws IOException {
    switchScene("/view/login.fxml", "Super-Secure-Chat - Login", 700, 700);

    currentStage.setResizable(true);
    ImageView view = new ImageView();
    Image image = new Image(Objects.requireNonNull(SceneManager.class.getResourceAsStream("/images/logo.png")));
    view.setImage(image);
  }

  public static void switchToRegisterScene() throws IOException {
    switchScene("/view/register.fxml", "Super-Secure-Chat - Register", 700, 700);
  }

  private static void switchScene(String fxmlPath, String title, double width , double height) throws IOException {
    if (currentStage == null) {
      throw new RuntimeException("Current stage not set need call setCurrentStage() first");
    }

    FXMLLoader fxmlLoader = new FXMLLoader(SceneManager.class.getResource(fxmlPath));
    Scene scene = new Scene(fxmlLoader.load(), width, height);

    // TODO: добавить css

    currentStage.setTitle(title);
    currentStage.setScene(scene);
    currentStage.setMinHeight(height);
    currentStage.setMinWidth(width);

    currentStage.centerOnScreen();
  }

  public static Stage getCurrentStage() {
    return currentStage;
  }

  public static void setCurrentStage(Stage stage) {
    currentStage = stage;
  }
}
