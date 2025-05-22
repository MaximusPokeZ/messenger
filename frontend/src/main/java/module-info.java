module org.example.frontend {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    exports org.example.frontend.app;
    opens org.example.frontend.app to javafx.fxml;
    exports org.example.frontend.controller;
    opens org.example.frontend.controller to javafx.fxml;
}