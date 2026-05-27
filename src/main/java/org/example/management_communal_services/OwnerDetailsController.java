package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Контроллер для окна редактирования данных собственника
// Позволяет изменять личные данные и пароль
public class OwnerDetailsController {

    // Поля для ввода личных данных
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

    // Поля для работы с логином и паролем
    @FXML
    private TextField tfLogin;

    @FXML
    private PasswordField pfOldPassword;

    @FXML
    private PasswordField pfNewPassword;

    // ID текущего владельца и флаг режима редактирования
    private int currentOwnerId;
    private boolean editMode = false;

    // Установка ID текущего владельца и загрузка данных
    // Вызывается из OwnerController после загрузки FXML
    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
        loadOwnerData();
    }

    // Загрузка данных владельца из базы данных
    // Заполняет все текстовые поля информацией из таблицы Owners
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
    // Разблокирует поля для изменения личных данных
    @FXML
    private void handleEdit() {
        setFieldsEditable(true);
        editMode = true;
    }

    // Кнопка "Подтвердить" — сохранение изменений
    // Проверяет валидацию и обновляет данные в БД
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
    // Разблокирует поля для ввода старого и нового пароля
    @FXML
    private void handleEditPassword() {
        pfOldPassword.setEditable(true);
        pfNewPassword.setEditable(true);
    }

    // Кнопка "Подтвердить" для пароля
    // Проверяет старый пароль и обновляет на новый
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

    // Включение/отключение редактирования полей личных данных
    // Не влияет на поля логина и пароля
    private void setFieldsEditable(boolean editable) {
        tfFullName.setEditable(editable);
        tfPhone.setEditable(editable);
        tfEmail.setEditable(editable);
        tfStreet.setEditable(editable);
        tfBuilding.setEditable(editable);
        tfApartment.setEditable(editable);
        tfArea.setEditable(editable);
    }

    // Валидация заполненных полей
    // Проверяет, что ФИО, телефон и email не пустые
    private boolean validateFields() {
        if (tfFullName.getText().trim().isEmpty()) {
            showError("Введите ФИО");
            return false;
        }
        if (tfPhone.getText().trim().isEmpty()) {
            showError("Введите номер телефона");
            return false;
        }
        if (tfEmail.getText().trim().isEmpty()) {
            showError("Введите email");
            return false;
        }
        return true;
    }

    // Сохранение изменений в базу данных
    // Обновляет поля в таблице Owners по ID владельца
    private void saveChanges() {
        String sql = "UPDATE Owners SET full_name = ?, phone = ?, email = ?, " +
                "street = ?, building = ?, apartment_number = ?, area = ? " +
                "WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tfFullName.getText().trim());
            pstmt.setString(2, tfPhone.getText().trim());
            pstmt.setString(3, tfEmail.getText().trim());
            pstmt.setString(4, tfStreet.getText().trim());
            pstmt.setString(5, tfBuilding.getText().trim());
            pstmt.setString(6, tfApartment.getText().trim());
            pstmt.setDouble(7, Double.parseDouble(tfArea.getText()));
            pstmt.setInt(8, currentOwnerId);

            pstmt.executeUpdate();

        } catch (SQLException | NumberFormatException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении данных");
        }
    }

    // Изменение пароля
    // Проверяет старый пароль и обновляет на новый в БД
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
    // Использует стандартный Alert с типом ERROR
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Показ сообщения об успехе
    // Использует стандартный Alert с типом INFORMATION
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setContentText(message);
        alert.showAndWait();
    }
}