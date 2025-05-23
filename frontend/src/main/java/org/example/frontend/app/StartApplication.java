package org.example.frontend.app;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.frontend.manager.SceneManager;

import java.io.IOException;

public class StartApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        SceneManager.setCurrentStage(stage);
        SceneManager.switchToMainScene();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}