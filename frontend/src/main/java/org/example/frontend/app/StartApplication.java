package org.example.frontend.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.example.frontend.manager.SceneManager;

import java.io.IOException;
import java.util.Objects;

public class StartApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        SceneManager.setCurrentStage(stage);
        SceneManager.switchToLoginScene();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}