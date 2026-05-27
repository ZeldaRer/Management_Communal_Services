package org.example.management_communal_services;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Контроллер для окна подачи заявки на услугу
 * Обеспечивает выбор категории и услуги из справочника
 */
public class RequestController {

    @FXML
    private TextField tfPhone;

    @FXML
    private ComboBox<String> cbCategory;

    @FXML
    private ComboBox<ServiceItem> cbService;

    @FXML
    private TextArea taDescription;

    private int currentOwnerId;
    private ObservableList<String> categories = FXCollections.observableArrayList();
    private ObservableList<ServiceItem> services = FXCollections.observableArrayList();

    /**
     * Внутренний класс для хранения услуги с ID
     */
    private class ServiceItem {
        private int id;
        private String name;
        private String category;

        public ServiceItem(int id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }

        @Override
        public String toString() {
            return name; // Отображаем только название в ComboBox
        }
    }

    /**
     * Установка ID текущего владельца
     */
    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
    }

    /**
     * Инициализация контроллера: загрузка категорий
     */
    @FXML
    public void initialize() {
        loadCategories();

        // Обработчик выбора категории
        cbCategory.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadServicesByCategory(newVal);
            }
        });
    }

    /**
     * Загрузка уникальных категорий из таблицы Services
     */
    private void loadCategories() {
        String sql = "SELECT DISTINCT category FROM Services ORDER BY category";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
            cbCategory.setItems(categories);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке категорий");
        }
    }

    /**
     * Загрузка услуг для выбранной категории
     */
    private void loadServicesByCategory(String category) {
        services.clear();
        String sql = "SELECT id, name, category FROM Services WHERE category = ? ORDER BY name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String cat = rs.getString("category");
                services.add(new ServiceItem(id, name, cat));
            }
            cbService.setItems(services);
            cbService.setDisable(false);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке услуг");
        }
    }

    /**
     * Обработчик кнопки "Отправить"
     */
    @FXML
    private void handleSubmit() {
        // Валидация полей
        if (tfPhone.getText().trim().isEmpty()) {
            showError("Введите номер телефона");
            return;
        }
        if (cbCategory.getValue() == null) {
            showError("Выберите категорию услуги");
            return;
        }
        if (cbService.getValue() == null) {
            showError("Выберите услугу");
            return;
        }
        if (taDescription.getText().trim().isEmpty()) {
            showError("Введите описание проблемы");
            return;
        }

        // Получаем выбранную услугу
        ServiceItem selectedService = cbService.getValue();

        // Сохраняем заявку
        saveApplication(selectedService.getId());
    }

    /**
     * Сохранение заявки в базу данных
     */
    private void saveApplication(int serviceId) {
        String sql = "INSERT INTO Applications (owner_id, service_id, description, phone, status, created_at) " +
                "VALUES (?, ?, ?, ?, 'На рассмотрении', ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, serviceId);
            pstmt.setString(3, taDescription.getText().trim());
            pstmt.setString(4, tfPhone.getText().trim());
            pstmt.setString(5, LocalDate.now().toString());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                showSuccess("Заявка успешно создана!");
                clearFields();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении заявки");
        }
    }

    /**
     * Очистка полей формы
     */
    private void clearFields() {
        tfPhone.clear();
        cbCategory.setValue(null);
        cbService.setValue(null);
        cbService.setDisable(true);
        taDescription.clear();
    }

    /**
     * Показ сообщения об ошибке
     */
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Показ сообщения об успехе
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}