package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

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
            // 1. Сначала принудительно генерируем все начисления за текущий месяц
            ChargesGenerator.generateChargesForCurrentMonth();

            // 2. Определяем текущий месяц (тот, за который смотрим квитанцию)
            java.time.LocalDate now = java.time.LocalDate.now();
            int targetMonth = now.getMonthValue();
            int targetYear = now.getYear();

            // Проверяем, есть ли показания за текущий месяц в базе
            String checkSql = "SELECT month, year FROM MeterReadings WHERE owner_id = ? AND month = ? AND year = ?";
            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setInt(1, currentOwnerId);
                pstmt.setInt(2, targetMonth);
                pstmt.setInt(3, targetYear);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    targetMonth = rs.getInt("month");
                    targetYear = rs.getInt("year");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // 3. Открываем окно квитанции
            FXMLLoader loader = new FXMLLoader(getClass().getResource("receipt.fxml"));
            Parent root = loader.load();

            ReceiptController receiptController = loader.getController();
            receiptController.setOwnerData(currentOwnerId, targetMonth, targetYear);

            Stage stage = new Stage();
            stage.setTitle("Квитанция");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Вспомогательный метод для генерации начислений "на лету"
    // Он берет показания из базы (или нули), считает разницу и пишет в таблицу Charges
    private void generateChargesForOwnerAndPeriod(int ownerId) {
        // Определяем период: текущий месяц
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        // 1. Получаем текущие показания (последние введенные за текущий месяц)
        double currElec = 0, currHot = 0, currCold = 0;
        String readingsSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                "WHERE owner_id = ? AND month = ? AND year = ? ORDER BY reading_date DESC LIMIT 1";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(readingsSql)) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, currentMonth);
            pstmt.setInt(3, currentYear);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                currElec = rs.getDouble("electricity");
                currHot = rs.getDouble("hot_water");
                currCold = rs.getDouble("cold_water");
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // Если за текущий месяц показаний нет, пробуем найти самые последние доступные
        if (currElec == 0 && currHot == 0 && currCold == 0) {
            String lastSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                    "WHERE owner_id = ? ORDER BY reading_date DESC LIMIT 1";
            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(lastSql)) {
                pstmt.setInt(1, ownerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    currElec = rs.getDouble("electricity");
                    currHot = rs.getDouble("hot_water");
                    currCold = rs.getDouble("cold_water");
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        // 2. Получаем предыдущие показания (до текущего месяца)
        double prevElec = 0, prevHot = 0, prevCold = 0;
        String prevSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                "WHERE owner_id = ? AND (year < ? OR (year = ? AND month < ?)) " +
                "ORDER BY year DESC, month DESC LIMIT 1";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(prevSql)) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, currentYear);
            pstmt.setInt(3, currentYear);
            pstmt.setInt(4, currentMonth);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                prevElec = rs.getDouble("electricity");
                prevHot = rs.getDouble("hot_water");
                prevCold = rs.getDouble("cold_water");
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // 3. Получаем площадь
        double area = 0;
        String areaSql = "SELECT area FROM Owners WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(areaSql)) {
            pstmt.setInt(1, ownerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) area = rs.getDouble("area");
        } catch (SQLException e) { e.printStackTrace(); }

        // 4. Считаем и сохраняем начисления
        double hotConsumption = currHot - prevHot;
        double coldConsumption = currCold - prevCold;
        double totalWater = hotConsumption + coldConsumption;

        String tariffsSql = "SELECT id, service_name, price, normative FROM Tariffs WHERE is_active = 1";
        String insertSql = "INSERT INTO Charges (owner_id, tariff_id, volume, tariff_price, amount, month, year, is_paid) VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement tStmt = conn.prepareStatement(tariffsSql)) {

            ResultSet tRs = tStmt.executeQuery();
            while (tRs.next()) {
                int tariffId = tRs.getInt("id");
                String name = tRs.getString("service_name");
                double price = tRs.getDouble("price");
                double norm = tRs.getDouble("normative");

                double volume = 0;

                if (name.contains("Электричество")) volume = currElec - prevElec;
                else if (name.contains("ГВС") && name.contains("теплоноситель")) volume = hotConsumption;
                else if (name.contains("ХВС")) volume = coldConsumption;
                else if (name.contains("Водоотведение")) volume = totalWater * norm;
                else if (name.contains("ГВС") && name.contains("тепловая")) volume = hotConsumption * norm;
                else if (name.contains("Газ")) volume = area * norm;
                else if (name.contains("ТКО")) volume = area * norm;
                else if (name.contains("Содержание")) volume = area;
                else if (name.contains("Отопление")) volume = area * norm;

                double amount = volume * price;

                // Проверяем, нет ли уже такой записи, чтобы не дублировать
                String checkCharges = "SELECT COUNT(*) FROM Charges WHERE owner_id = ? AND tariff_id = ? AND month = ? AND year = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkCharges)) {
                    checkStmt.setInt(1, ownerId);
                    checkStmt.setInt(2, tariffId);
                    checkStmt.setInt(3, currentMonth);
                    checkStmt.setInt(4, currentYear);
                    ResultSet cRs = checkStmt.executeQuery();
                    if (cRs.next() && cRs.getInt(1) > 0) continue;
                }

                try (PreparedStatement iStmt = conn.prepareStatement(insertSql)) {
                    iStmt.setInt(1, ownerId);
                    iStmt.setInt(2, tariffId);
                    iStmt.setDouble(3, volume);
                    iStmt.setDouble(4, price);
                    iStmt.setDouble(5, amount);
                    iStmt.setInt(6, currentMonth);
                    iStmt.setInt(7, currentYear);
                    iStmt.executeUpdate();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
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
                    // Используем %.3f для вывода дробной части
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

            // Генерируем начисления за текущий месяц
            ChargesGenerator.generateChargesForCurrentMonth();

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
            // Парсим текущие значения из Label, убирая возможные пробелы и заменяя запятую
            double lastElec = Double.parseDouble(lblLastElectricity.getText().replace(',', '.').trim());
            double lastHot = Double.parseDouble(lblLastHotWater.getText().replace(',', '.').trim());
            double lastCold = Double.parseDouble(lblLastColdWater.getText().replace(',', '.').trim());

            return electricity >= lastElec && hotWater >= lastHot && coldWater >= lastCold;
        } catch (NumberFormatException e) {
            return true; // Если не удалось распарсить старые, разрешаем (на всякий случай)
        }
    }

    // Проверка интервала в 15 дней
    private boolean validateDateInterval() {
        String lastDateStr = lblLastElectricityDate.getText();
        if (lastDateStr.equals("-")) return true; // Если нет предыдущих, можно вводить

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

    // Сохранение показаний в базу данных + АВТОМАТИЧЕСКИЙ РАСЧЁТ НАЧИСЛЕНИЙ
    private void saveReadings(double electricity, double hotWater, double coldWater) {
        LocalDate now = LocalDate.now();
        String sql = "INSERT INTO MeterReadings (owner_id, electricity, hot_water, cold_water, " +
                "reading_date, month, year) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 1. Сохраняем сами показания (ИСПОЛЬЗУЕМ setDouble!)
            pstmt.setInt(1, currentOwnerId);
            pstmt.setDouble(2, electricity);
            pstmt.setDouble(3, hotWater);
            pstmt.setDouble(4, coldWater);
            pstmt.setString(5, now.toString());
            pstmt.setInt(6, now.getMonthValue());
            pstmt.setInt(7, now.getYear());

            pstmt.executeUpdate();

            // 2. Сразу после сохранения запускаем генерацию квитанции за этот месяц
            generateChargesForCurrentMonth(conn, currentOwnerId, now.getMonthValue(), now.getYear(),
                    electricity, hotWater, coldWater);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении показаний");
        }
    }

    // Метод для расчёта и вставки данных в таблицу Charges
    private void generateChargesForCurrentMonth(Connection conn, int ownerId, int month, int year,
                                                double currElec, double currHot, double currCold) {
        try {
            // Проверка на дубликаты
            String checkSql = "SELECT COUNT(*) FROM Charges WHERE owner_id = ? AND month = ? AND year = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, ownerId);
                checkStmt.setInt(2, month);
                checkStmt.setInt(3, year);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }

            // Получаем площадь квартиры
            double area = 0;
            String ownerSql = "SELECT area FROM Owners WHERE id = ?";
            try (PreparedStatement ownerStmt = conn.prepareStatement(ownerSql)) {
                ownerStmt.setInt(1, ownerId);
                ResultSet rs = ownerStmt.executeQuery();
                if (rs.next()) area = rs.getDouble("area");
            }

            // Получаем предыдущие показания
            String lastReadingsSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                    "WHERE owner_id = ? AND (year < ? OR (year = ? AND month < ?)) " +
                    "ORDER BY year DESC, month DESC LIMIT 1";

            double prevElec = 0, prevHot = 0, prevCold = 0;
            try (PreparedStatement lastStmt = conn.prepareStatement(lastReadingsSql)) {
                lastStmt.setInt(1, ownerId);
                lastStmt.setInt(2, year);
                lastStmt.setInt(3, year);
                lastStmt.setInt(4, month);
                ResultSet rs = lastStmt.executeQuery();
                if (rs.next()) {
                    prevElec = rs.getDouble("electricity");
                    prevHot = rs.getDouble("hot_water");
                    prevCold = rs.getDouble("cold_water");
                }
            }

            // Расчёт расхода воды (для водоотведения и ГВС тепловая энергия)
            double hotWaterConsumption = currHot - prevHot;
            double coldWaterConsumption = currCold - prevCold;
            double totalWaterConsumption = hotWaterConsumption + coldWaterConsumption;

            // Получаем все тарифы
            String tariffsSql = "SELECT id, service_name, price, normative FROM Tariffs WHERE is_active = 1";
            String insertSql = "INSERT INTO Charges (owner_id, tariff_id, volume, tariff_price, amount, month, year, is_paid) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

            try (PreparedStatement tariffStmt = conn.prepareStatement(tariffsSql);
                 ResultSet tariffRs = tariffStmt.executeQuery()) {

                while (tariffRs.next()) {
                    int tariffId = tariffRs.getInt("id");
                    String serviceName = tariffRs.getString("service_name");
                    double price = tariffRs.getDouble("price"); // Цена тарифа
                    double normative = tariffRs.getDouble("normative");
                    double volume = 0;

                    // Логика расчета объема
                    if (serviceName.contains("Электричество") || serviceName.contains("Электро")) {
                        volume = currElec - prevElec;
                    } else if (serviceName.contains("ГВС") && serviceName.contains("теплоноситель")) {
                        volume = currHot - prevHot;
                    } else if (serviceName.contains("ХВС")) {
                        volume = currCold - prevCold;
                    } else if (serviceName.contains("Водоотведение")) {
                        volume = totalWaterConsumption * normative;
                    } else if (serviceName.contains("Газ")) {
                        volume = area * normative;
                    } else if (serviceName.contains("ТКО") || serviceName.contains("обращение")) {
                        volume = area * normative;
                    } else if (serviceName.contains("Содержание жилья")) {
                        volume = area;
                    }

                    // РАСЧЕТ СУММЫ
                    double amount = volume * price;

                    // Вставка в БД
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, ownerId);
                        insertStmt.setInt(2, tariffId);
                        insertStmt.setDouble(3, volume);
                        insertStmt.setDouble(4, price);
                        insertStmt.setDouble(5, amount); // Сохраняем рассчитанную сумму
                        insertStmt.setInt(6, month);
                        insertStmt.setInt(7, year);
                        insertStmt.executeUpdate();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
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