package org.example.frontend.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class StartApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 700, 700);
        stage.setTitle("Secure Chat - Login");
        stage.setScene(scene);

        stage.setResizable(true);
        stage.setMinHeight(scene.getHeight());
        stage.setMinWidth(scene.getWidth());

        ImageView view = new ImageView();
        Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png")));
        view.setImage(image);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}