package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

 // Контроллер для окна передачи показаний счётчиков
 // Отображает последние показания и позволяет ввести новые
public class MetersController {

    @FXML
    private Label lblLastElectricity;

    @FXML
    private Label lblLastElectricityDate;

    @FXML
    private Label lblLastHotWater;

    @FXML
    private Label lblLastHotWaterDate;

    @FXML
    private Label lblLastColdWater;

    @FXML
    private Label lblLastColdWaterDate;

    @FXML
    private Label lblCurrentDate;

    @FXML
    private TextField tfElectricity;

    @FXML
    private TextField tfHotWater;

    @FXML
    private TextField tfColdWater;

    private int currentOwnerId;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");


    // Установка ID текущего владельца и загрузка данных
    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
        loadLastReadings();
        updateCurrentDate();
    }


    //  Инициализация контроллера
    @FXML
    public void initialize() {
        updateCurrentDate();
    }

    // Обновление текущей даты в интерфейсе
    private void updateCurrentDate() {
        if (lblCurrentDate != null) {
            String currentDate = LocalDate.now().format(dateFormatter);
            lblCurrentDate.setText("Введите показания счётчиков за " + currentDate);
        }
    }

    // Загрузка последних показаний счётчиков из базы данных
    private void loadLastReadings() {
        if (currentOwnerId == 0) return;

        // SQL-запрос для получения последних показаний
        String sql = "SELECT electricity, hot_water, cold_water, reading_date " +
                "FROM MeterReadings " +
                "WHERE owner_id = ? " +
                "ORDER BY reading_date DESC, year DESC, month DESC " +
                "LIMIT 1";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Заполняем label последними показаниями
                if (lblLastElectricity != null) {
                    lblLastElectricity.setText(String.valueOf(rs.getInt("electricity")));
                }
                if (lblLastElectricityDate != null) {
                    LocalDate date = LocalDate.parse(rs.getString("reading_date"));
                    lblLastElectricityDate.setText(date.format(dateFormatter));
                }

                if (lblLastHotWater != null) {
                    lblLastHotWater.setText(String.valueOf(rs.getInt("hot_water")));
                }
                if (lblLastHotWaterDate != null) {
                    LocalDate date = LocalDate.parse(rs.getString("reading_date"));
                    lblLastHotWaterDate.setText(date.format(dateFormatter));
                }

                if (lblLastColdWater != null) {
                    lblLastColdWater.setText(String.valueOf(rs.getInt("cold_water")));
                }
                if (lblLastColdWaterDate != null) {
                    LocalDate date = LocalDate.parse(rs.getString("reading_date"));
                    lblLastColdWaterDate.setText(date.format(dateFormatter));
                }
            } else {
                // Если показаний ещё нет
                setDefaultValues();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке показаний счётчиков");
        }
    }

    // Установка значений по умолчанию, если показаний ещё нет
    private void setDefaultValues() {
        if (lblLastElectricity != null) lblLastElectricity.setText("0");
        if (lblLastElectricityDate != null) lblLastElectricityDate.setText("-");
        if (lblLastHotWater != null) lblLastHotWater.setText("0");
        if (lblLastHotWaterDate != null) lblLastHotWaterDate.setText("-");
        if (lblLastColdWater != null) lblLastColdWater.setText("0");
        if (lblLastColdWaterDate != null) lblLastColdWaterDate.setText("-");
    }


    // Обработчик кнопки "Отправить"
    @FXML
    private void handleSubmit() {
        try {
            // Получаем значения из полей ввода
            int electricity = Integer.parseInt(tfElectricity.getText());
            int hotWater = Integer.parseInt(tfHotWater.getText());
            int coldWater = Integer.parseInt(tfColdWater.getText());

            // Проверяем, что новые показания не меньше предыдущих
            if (!validateReadings(electricity, hotWater, coldWater)) {
                showError("Новые показания не могут быть меньше предыдущих!");
                return;
            }

            // Сохраняем показания в базу данных
            saveReadings(electricity, hotWater, coldWater);

            // Очищаем поля
            tfElectricity.clear();
            tfHotWater.clear();
            tfColdWater.clear();

            // Показываем успешное сохранение
            showSuccess("Показания успешно сохранены!");

            // Обновляем отображение последних показаний
            loadLastReadings();

        } catch (NumberFormatException e) {
            showError("Пожалуйста, введите корректные числовые значения");
        }
    }

    // Проверка корректности показаний
    private boolean validateReadings(int electricity, int hotWater, int coldWater) {
        // Здесь можно добавить дополнительную логику проверки
        return electricity >= 0 && hotWater >= 0 && coldWater >= 0;
    }

    // Сохранение показаний в базу данных
    private void saveReadings(int electricity, int hotWater, int coldWater) {
        LocalDate now = LocalDate.now();
        String sql = "INSERT INTO MeterReadings (owner_id, electricity, hot_water, cold_water, " +
                "reading_date, month, year) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, electricity);
            pstmt.setInt(3, hotWater);
            pstmt.setInt(4, coldWater);
            pstmt.setString(5, now.toString());
            pstmt.setInt(6, now.getMonthValue());
            pstmt.setInt(7, now.getYear());

            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении показаний");
        }
    }


    // Показ сообщения об ошибке
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Показ сообщения об успехе
    private void showSuccess(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}