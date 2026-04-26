package org.example.management_communal_services;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private void handleLogin(ActionEvent event) {
        try {
            // Загружаем окно собственника
            FXMLLoader loader = new FXMLLoader(getClass().getResource("owner.fxml"));
            Parent root = loader.load();

            // Получаем текущую сцену и меняем её
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Кабинет собственника");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка при загрузке окна собственника");
        }
    }
}