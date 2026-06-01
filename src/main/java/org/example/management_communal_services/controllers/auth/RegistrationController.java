package org.example.management_communal_services.controllers.auth;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.management_communal_services.utils.DatabaseConnector;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Контроллер для окна регистрации собственника
// Обрабатывает ввод данных и сохранение в базу данных
public class RegistrationController {

    @FXML
    private TextField tfFullName;

    @FXML
    private TextField tfAccountNumber;

    @FXML
    private TextField tfPhone;

    @FXML
    private TextField tfEmail;

    @FXML
    private TextField tfStreet;

    @FXML
    private TextField tfBuilding;

    @FXML
    private TextField tfApartment;

    @FXML
    private TextField tfArea;

    @FXML
    private TextField tfLogin;

    @FXML
    private TextField tfPassword;

    @FXML
    private TextField tfConfirmPassword;

    // Обработчик кнопки "Назад" — возврат к окну входа
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/management_communal_services/fxml/auth/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) tfFullName.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Вход в систему");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Ошибка при возврате к окну входа");
        }
    }

    // Обработчик кнопки "Подтвердить" — регистрация пользователя
    @FXML
    private void handleRegister() {
        // Валидация полей
        if (!validateFields()) {
            return;
        }

        // Проверка уникальности данных
        if (!checkUniqueness()) {
            return;
        }

        // Сохранение в базу данных
        if (saveToDatabase()) {
            showSuccess("Регистрация прошла успешно! Теперь вы можете войти.");
            handleBack();
        }
    }

    // Валидация заполненности и корректности полей
    private boolean validateFields() {
        // Проверка обязательных полей
        if (tfFullName.getText().trim().isEmpty()) {
            showError("Введите ФИО");
            tfFullName.requestFocus();
            return false;
        }

        if (tfAccountNumber.getText().trim().isEmpty()) {
            showError("Введите номер лицевого счёта");
            tfAccountNumber.requestFocus();
            return false;
        }

        // Проверка, что номер счёта — 9 цифр
        if (!tfAccountNumber.getText().matches("\\d{9}")) {
            showError("Номер лицевого счёта должен содержать 9 цифр");
            tfAccountNumber.requestFocus();
            return false;
        }

        if (tfPhone.getText().trim().isEmpty()) {
            showError("Введите номер телефона");
            tfPhone.requestFocus();
            return false;
        }

        // Проверка телефона (должен начинаться с 8 или +7 и содержать 11 цифр)
        String phone = tfPhone.getText().replaceAll("\\D", "");
        if (!phone.matches("(8|7|\\+7)\\d{10}")) {
            showError("Введите корректный номер телефона (например: 88005553535)");
            tfPhone.requestFocus();
            return false;
        }

        if (tfEmail.getText().trim().isEmpty()) {
            showError("Введите email");
            tfEmail.requestFocus();
            return false;
        }

        // Простая проверка email
        if (!tfEmail.getText().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showError("Введите корректный email");
            tfEmail.requestFocus();
            return false;
        }

        if (tfStreet.getText().trim().isEmpty()) {
            showError("Введите улицу");
            tfStreet.requestFocus();
            return false;
        }

        // Проверка улицы (должна начинаться с "ул." или "пр.")
        String street = tfStreet.getText().trim();
        if (!street.toLowerCase().startsWith("ул.") && !street.toLowerCase().startsWith("пр.")) {
            showError("Улица, или Проспект должны начинаться с 'ул.' или 'пр.' (например: ул. Ленина)");
            tfStreet.requestFocus();
            return false;
        }

        if (tfBuilding.getText().trim().isEmpty()) {
            showError("Введите номер дома");
            tfBuilding.requestFocus();
            return false;
        }

        if (tfApartment.getText().trim().isEmpty()) {
            showError("Введите номер квартиры");
            tfApartment.requestFocus();
            return false;
        }

        if (tfArea.getText().trim().isEmpty()) {
            showError("Введите площадь квартиры");
            tfArea.requestFocus();
            return false;
        }

        // Проверка площади (число с точкой)
        try {
            double area = Double.parseDouble(tfArea.getText());
            if (area <= 0) {
                showError("Площадь квартиры должна быть больше 0");
                tfArea.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Площадь должна быть числом (например: 45.7)");
            tfArea.requestFocus();
            return false;
        }

        if (tfLogin.getText().trim().isEmpty()) {
            showError("Введите логин");
            tfLogin.requestFocus();
            return false;
        }

        if (tfPassword.getText().isEmpty()) {
            showError("Введите пароль");
            tfPassword.requestFocus();
            return false;
        }

        if (tfConfirmPassword.getText().isEmpty()) {
            showError("Подтвердите пароль");
            tfConfirmPassword.requestFocus();
            return false;
        }

        if (!tfPassword.getText().equals(tfConfirmPassword.getText())) {
            showError("Пароли не совпадают");
            tfPassword.clear();
            tfConfirmPassword.clear();
            tfPassword.requestFocus();
            return false;
        }

        return true;
    }

    // Проверка уникальности login, account_number и email
    private boolean checkUniqueness() {
        String login = tfLogin.getText().trim();
        String accountNumber = tfAccountNumber.getText().trim();
        String email = tfEmail.getText().trim();

        // Проверка логина
        if (isValueExists("login", login)) {
            showError("Пользователь с таким логином уже существует");
            tfLogin.requestFocus();
            return false;
        }

        // Проверка лицевого счёта
        if (isValueExists("account_number", accountNumber)) {
            showError("Лицевой счёт уже зарегистрирован");
            tfAccountNumber.requestFocus();
            return false;
        }

        // Проверка email
        if (isValueExists("email", email)) {
            showError("Email уже зарегистрирован");
            tfEmail.requestFocus();
            return false;
        }

        return true;
    }

    // Проверка существования значения в указанной колонке таблицы Owners
    private boolean isValueExists(String columnName, String value) {
        String sql = "SELECT COUNT(*) FROM Owners WHERE " + columnName + " = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, value);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Сохранение данных в базу данных
    private boolean saveToDatabase() {
        String sql = "INSERT INTO Owners (login, password, full_name, account_number, " +
                "street, building, apartment_number, phone, email, area) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // try-with-resources автоматически закроет все ресурсы
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tfLogin.getText().trim());
            pstmt.setString(2, tfPassword.getText());
            pstmt.setString(3, tfFullName.getText().trim());
            pstmt.setString(4, tfAccountNumber.getText().trim());
            pstmt.setString(5, tfStreet.getText().trim());
            pstmt.setString(6, tfBuilding.getText().trim());
            pstmt.setString(7, tfApartment.getText().trim());
            pstmt.setString(8, normalizePhone(tfPhone.getText().trim()));
            pstmt.setString(9, tfEmail.getText().trim().toLowerCase());
            pstmt.setDouble(10, Double.parseDouble(tfArea.getText()));

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при регистрации: " + e.getMessage());
            return false;
        }
    }

    // Нормализация номера телефона (приведение к формату +7...)
    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");

        if (digits.startsWith("8")) {
            digits = "+7" + digits.substring(1);
        } else if (digits.startsWith("7") && digits.length() == 11) {
            digits = "+7" + digits.substring(1);
        } else if (digits.length() == 10) {
            digits = "+7" + digits;
        }

        return digits;
    }

    // Показ сообщения об ошибке
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка регистрации");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Показ сообщения об успехе
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Регистрация успешна");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}