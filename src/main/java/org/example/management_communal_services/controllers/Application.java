package org.example.management_communal_services.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Application extends javafx.application.Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("/org/example/management_communal_services/fxml/auth/login.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Вход в систему");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
            javafx.application.Application.launch(Application.class, args);
        }
}
