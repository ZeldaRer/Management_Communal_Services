package org.example.management_communal_services.controllers.owner;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.management_communal_services.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Контроллер для окна редактирования данных собственника
// Позволяет изменять личные данные и пароль
public class ProfileController {

    // Поля для ввода личных данных
    @FXML private TextField tfFullName;
    @FXML private TextField tfAccountNumber;
    @FXML private TextField tfPhone;
    @FXML private TextField tfEmail;

    // Поля адреса (заблокированы для редактирования)
    @FXML private TextField tfStreet;
    @FXML private TextField tfBuilding;
    @FXML private TextField tfApartment;
    @FXML private TextField tfArea;

    // Поля для работы с логином и паролем
    @FXML private TextField tfLogin;
    @FXML private PasswordField pfOldPassword;
    @FXML private PasswordField pfNewPassword;

    // ID текущего владельца и флаг режима редактирования
    private int currentOwnerId;
    private boolean editMode = false;

    // Установка ID текущего владельца и загрузка данных
    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
        loadOwnerData();
    }

    // Загрузка данных владельца из базы данных
    private void loadOwnerData() {
        if (currentOwnerId == 0) return;

        String sql = "SELECT full_name, account_number, phone, email, street, building, " +
                "apartment_number, area, login FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                tfFullName.setText(rs.getString("full_name"));
                tfAccountNumber.setText(rs.getString("account_number"));
                tfPhone.setText(rs.getString("phone"));
                tfEmail.setText(rs.getString("email"));
                tfStreet.setText(rs.getString("street"));
                tfBuilding.setText(rs.getString("building"));
                tfApartment.setText(rs.getString("apartment_number"));
                tfArea.setText(String.valueOf(rs.getDouble("area")));
                tfLogin.setText(rs.getString("login"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке данных");
        }
    }

    // Кнопка "Редактировать" — включение режима редактирования
    @FXML
    private void handleEdit() {
        setFieldsEditable(true);
        editMode = true;
    }

    // Кнопка "Подтвердить" — сохранение изменений
    @FXML
    private void handleSave() {
        if (!editMode) {
            showError("Сначала нажмите 'Редактировать'");
            return;
        }

        if (validateFields()) {
            saveChanges();
            setFieldsEditable(false);
            editMode = false;
            showSuccess("Данные успешно сохранены");
        }
    }

    // Кнопка "Изменить" для пароля
    @FXML
    private void handleEditPassword() {
        pfOldPassword.setEditable(true);
        pfNewPassword.setEditable(true);
    }

    // Кнопка "Подтвердить" для пароля
    @FXML
    private void handleSavePassword() {
        if (pfOldPassword.getText().isEmpty() || pfNewPassword.getText().isEmpty()) {
            showError("Заполните все поля пароля");
            return;
        }

        if (changePassword()) {
            pfOldPassword.clear();
            pfNewPassword.clear();
            pfOldPassword.setEditable(false);
            pfNewPassword.setEditable(false);
            showSuccess("Пароль успешно изменён");
        }
    }

    // Включение/отключение редактирования полей
    // Адресные поля остаются заблокированными всегда
    private void setFieldsEditable(boolean editable) {
        tfFullName.setEditable(editable);
        tfPhone.setEditable(editable);
        tfEmail.setEditable(editable);
        // Адрес и площадь недоступны для изменения
    }

    // Валидация заполненных полей (аналогично регистрации)
    private boolean validateFields() {
        // Проверка ФИО
        if (tfFullName.getText().trim().isEmpty()) {
            showError("Введите ФИО");
            tfFullName.requestFocus();
            return false;
        }

        // Проверка телефона (как в регистрации)
        String phone = tfPhone.getText().trim();
        if (phone.isEmpty()) {
            showError("Введите номер телефона");
            tfPhone.requestFocus();
            return false;
        }
        String digits = phone.replaceAll("\\D", "");
        if (!digits.matches("(8|7|\\+7)\\d{10}")) {
            showError("Введите корректный номер телефона (например: 88005553535)");
            tfPhone.requestFocus();
            return false;
        }

        // Проверка Email
        String email = tfEmail.getText().trim();
        if (email.isEmpty()) {
            showError("Введите email");
            tfEmail.requestFocus();
            return false;
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showError("Введите корректный email");
            tfEmail.requestFocus();
            return false;
        }

        return true;
    }

    // Сохранение изменений в базу данных
    // Адресные поля исключены из обновления
    private void saveChanges() {
        String sql = "UPDATE Owners SET full_name = ?, phone = ?, email = ? WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tfFullName.getText().trim());

            // Нормализация телефона перед сохранением (приведение к +7...)
            String phone = tfPhone.getText().trim();
            String digits = phone.replaceAll("\\D", "");
            if (digits.startsWith("8")) {
                digits = "+7" + digits.substring(1);
            } else if (digits.startsWith("7") && digits.length() == 11) {
                digits = "+7" + digits.substring(1);
            }
            pstmt.setString(2, digits);

            pstmt.setString(3, tfEmail.getText().trim().toLowerCase());
            pstmt.setInt(4, currentOwnerId);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении данных");
        }
    }

    // Изменение пароля
    private boolean changePassword() {
        String sql = "SELECT password FROM Owners WHERE id = ?";
        String updateSql = "UPDATE Owners SET password = ? WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next() && !rs.getString("password").equals(pfOldPassword.getText())) {
                showError("Неверный текущий пароль");
                return false;
            }

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, pfNewPassword.getText());
                updateStmt.setInt(2, currentOwnerId);
                updateStmt.executeUpdate();
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при изменении пароля");
            return false;
        }
    }

    // Показ сообщения об ошибке
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Показ сообщения об успехе
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setContentText(message);
        alert.showAndWait();
    }
}