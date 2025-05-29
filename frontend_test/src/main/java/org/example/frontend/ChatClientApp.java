package org.example.frontend;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.Scanner;

public class ChatClientApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Получаем данные через диалоги
        TextInputDialog userDialog = new TextInputDialog();
        userDialog.setTitle("Login");
        Optional<String> username = userDialog.showAndWait();

        TextInputDialog partnerDialog = new TextInputDialog();
        Optional<String> partner = partnerDialog.showAndWait();

        if (!username.isPresent() || !partner.isPresent()) {
            Platform.exit();
            return;
        }

        // Создаем контроллер явно
        ChatController controller = new ChatController(
                username.get(),
                partner.get()
        );

        // Настраиваем загрузчик
        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
        loader.setController(controller); // Устанавливаем контроллер

        // Загружаем FXML
        Parent root = loader.load();

        // Настраиваем сцену
        Scene scene = new Scene(root, 400, 300);
        stage.setScene(scene);
        stage.setTitle("gRPC Chat Client");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
