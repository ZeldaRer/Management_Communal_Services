package org.example.management_communal_services;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class LoginController {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private void handleLogin(ActionEvent event) {
        String login = loginField.getText();
        String password = passwordField.getText();

        // Проверяем логин и пароль, получаем ID
        int ownerId = authenticateUser(login, password);

        if (ownerId > 0) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("owner.fxml"));
                Parent root = loader.load();

                // ПЕРЕДАЁМ ID ВЛАДЕЛЬЦА!
                OwnerController ownerController = loader.getController();
                ownerController.setCurrentOwner(ownerId, login);

                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Кабинет собственника");
                stage.show();

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Ошибка авторизации
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setContentText("Неверный логин или пароль");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("registration.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) loginField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Регистрация собственника");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showError("Ошибка при открытии окна регистрации");
        }
    }


    // Метод для проверки логина/пароля и получения ID
    private int authenticateUser(String login, String password) {
        String sql = "SELECT id FROM Owners WHERE login = ? AND password = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, login);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");  // Возвращаем ID
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;  // Ошибка
    }

    // Вспомогательный класс для хранения данных владельца
    private static class OwnerData {
        private final int id;
        private final String fullName;

        public OwnerData(int id, String fullName) {
            this.id = id;
            this.fullName = fullName;
        }

        public int getId() {
            return id;
        }

        public String getFullName() {
            return fullName;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка авторизации");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}