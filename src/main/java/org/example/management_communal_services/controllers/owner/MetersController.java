package org.example.management_communal_services.controllers.owner;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import org.example.management_communal_services.utils.ChargesGenerator;
import org.example.management_communal_services.utils.DatabaseConnector;

import java.io.IOException;
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

    // Обработчик кнопки "Оформить квитанцию"
    @FXML
    private void handleGenerateReceipt() {
        try {
            // 1. ПРОВЕРЯЕМ наличие минимум 2 разных дат показаний
            if (!hasEnoughReadings()) {
                showError("Невозможно сформировать квитанцию: необходимо ввести показания минимум 2 раза " +
                        "с интервалом не менее 15 дней для расчёта расхода.");
                return;
            }

            // 2. Генерируем начисления ТОЛЬКО если есть показания
            ChargesGenerator.generateChargesForCurrentMonth();

            // 3. Определяем период для квитанции (последний месяц с начислениями)
            int[] monthYear = getLastChargeMonth();
            int targetMonth = monthYear[0];
            int targetYear = monthYear[1];

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/management_communal_services/fxml/owner/receipt.fxml"));
            Parent root = loader.load();

            ReceiptController receiptController = loader.getController();
            receiptController.setOwnerData(currentOwnerId, targetMonth, targetYear);

            Stage stage = new Stage();
            stage.setTitle("Квитанция");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showError("Ошибка при открытии квитанции");
        }
    }

    // Вспомогательный метод: проверка наличия минимум 2 разных дат показаний
    private boolean hasEnoughReadings() {
        String sql = "SELECT COUNT(DISTINCT reading_date) as date_count " +
                "FROM MeterReadings WHERE owner_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int dateCount = rs.getInt("date_count");
                return dateCount >= 2;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Получить последний месяц с начислениями
    private int[] getLastChargeMonth() {
        String sql = "SELECT month, year FROM Charges WHERE owner_id = ? " +
                "ORDER BY year DESC, month DESC LIMIT 1";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new int[]{rs.getInt("month"), rs.getInt("year")};
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Если нет начислений, возвращаем текущий месяц
        LocalDate now = LocalDate.now();
        return new int[]{now.getMonthValue(), now.getYear()};
    }

    // Инициализация контроллера
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
                    lblLastElectricity.setText(String.format("%.3f", rs.getDouble("electricity")));
                }
                if (lblLastElectricityDate != null) {
                    LocalDate date = LocalDate.parse(rs.getString("reading_date"));
                    lblLastElectricityDate.setText(date.format(dateFormatter));
                }

                if (lblLastHotWater != null) {
                    lblLastHotWater.setText(String.format("%.3f", rs.getDouble("hot_water")));
                }
                if (lblLastHotWaterDate != null) {
                    LocalDate date = LocalDate.parse(rs.getString("reading_date"));
                    lblLastHotWaterDate.setText(date.format(dateFormatter));
                }

                if (lblLastColdWater != null) {
                    lblLastColdWater.setText(String.format("%.3f", rs.getDouble("cold_water")));
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
        if (lblLastElectricity != null) lblLastElectricity.setText("0.000");
        if (lblLastElectricityDate != null) lblLastElectricityDate.setText("-");
        if (lblLastHotWater != null) lblLastHotWater.setText("0.000");
        if (lblLastHotWaterDate != null) lblLastHotWaterDate.setText("-");
        if (lblLastColdWater != null) lblLastColdWater.setText("0.000");
        if (lblLastColdWaterDate != null) lblLastColdWaterDate.setText("-");
    }

    // Обработчик кнопки "Отправить"
    @FXML
    private void handleSubmit() {
        try {
            // 1. Парсим как double (заменяем запятую на точку для надежности)
            double electricity = Double.parseDouble(tfElectricity.getText().replace(',', '.'));
            double hotWater = Double.parseDouble(tfHotWater.getText().replace(',', '.'));
            double coldWater = Double.parseDouble(tfColdWater.getText().replace(',', '.'));

            // 2. Проверка: не меньше предыдущих
            if (!validateReadings(electricity, hotWater, coldWater)) {
                showError("Новые показания не могут быть меньше предыдущих!");
                return;
            }

            // 3. Проверка: прошло ли 15 дней с последнего ввода
            if (!validateDateInterval()) {
                showError("Показания можно вводить не чаще, чем раз в 15 дней!");
                return;
            }

            // 4. Проверка дробной части (не более 3 знаков)
            if (!validateDecimalPlaces(tfElectricity.getText()) ||
                    !validateDecimalPlaces(tfHotWater.getText()) ||
                    !validateDecimalPlaces(tfColdWater.getText())) {
                showError("Показания могут содержать не более 3 знаков после запятой!");
                return;
            }

            // Сохраняем показания
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
            showError("Пожалуйста, введите корректные числовые значения (например: 123.456)");
        }
    }

    // Проверка корректности показаний (сравнение с предыдущими)
    private boolean validateReadings(double electricity, double hotWater, double coldWater) {
        try {
            double lastElec = Double.parseDouble(lblLastElectricity.getText().replace(',', '.').trim());
            double lastHot = Double.parseDouble(lblLastHotWater.getText().replace(',', '.').trim());
            double lastCold = Double.parseDouble(lblLastColdWater.getText().replace(',', '.').trim());

            return electricity >= lastElec && hotWater >= lastHot && coldWater >= lastCold;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    // Проверка интервала в 15 дней
    private boolean validateDateInterval() {
        String lastDateStr = lblLastElectricityDate.getText();
        if (lastDateStr.equals("-")) return true;

        try {
            LocalDate lastDate = LocalDate.parse(lastDateStr, dateFormatter);
            LocalDate today = LocalDate.now();

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today);
            return daysBetween >= 15;
        } catch (Exception e) {
            return true;
        }
    }

    // Проверка количества знаков после запятой
    private boolean validateDecimalPlaces(String text) {
        if (text.contains(".") || text.contains(",")) {
            String[] parts = text.split("[.,]");
            if (parts.length > 1 && parts[1].length() > 3) {
                return false;
            }
        }
        return true;
    }

    // Сохранение показаний в базу данных
    private void saveReadings(double electricity, double hotWater, double coldWater) {
        LocalDate now = LocalDate.now();
        String sql = "INSERT INTO MeterReadings (owner_id, electricity, hot_water, cold_water, " +
                "reading_date, month, year) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 1. Сохраняем сами показания
            pstmt.setInt(1, currentOwnerId);
            pstmt.setDouble(2, electricity);
            pstmt.setDouble(3, hotWater);
            pstmt.setDouble(4, coldWater);
            pstmt.setString(5, now.toString());
            pstmt.setInt(6, now.getMonthValue());
            pstmt.setInt(7, now.getYear());

            pstmt.executeUpdate();

            // 2. Вызываем общий генератор для создания начислений
            ChargesGenerator.generateChargesForCurrentMonth();

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