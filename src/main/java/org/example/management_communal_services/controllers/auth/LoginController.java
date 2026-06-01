package org.example.management_communal_services.controllers.auth;

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
import org.example.management_communal_services.utils.DatabaseConnector;
import org.example.management_communal_services.controllers.owner.OwnerController;

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

        // Проверка на пустые поля
        if (login.trim().isEmpty() || password.trim().isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }

        // Проверяем логин и пароль, получаем ID
        int ownerId = authenticateUser(login, password);

        if (ownerId > 0) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/management_communal_services/fxml/owner/owner.fxml"));
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
                showError("Ошибка при загрузке окна кабинета");
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/management_communal_services/fxml/auth/registration.fxml"));
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
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}