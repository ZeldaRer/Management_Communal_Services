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
             // Сначала генерируем начисления за текущий месяц (если нужно)
             ChargesGenerator.generateChargesForCurrentMonth();

             // Получаем последний месяц с начислениями (не с показаниями!)
             int[] monthYear = getLastChargeMonth();
             int targetMonth = monthYear[0];
             int targetYear = monthYear[1];

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

     // Получаем последний месяц, за который есть НАЧИСЛЕНИЯ (не показания!)
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

         // Если нет начислений, берём текущий месяц
         LocalDate now = LocalDate.now();
         return new int[]{now.getMonthValue(), now.getYear()};
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

    // Сохранение показаний в базу данных + АВТОМАТИЧЕСКИЙ РАСЧЁТ НАЧИСЛЕНИЙ
    private void saveReadings(int electricity, int hotWater, int coldWater) {
        LocalDate now = LocalDate.now();
        String sql = "INSERT INTO MeterReadings (owner_id, electricity, hot_water, cold_water, " +
                "reading_date, month, year) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 1. Сохраняем сами показания
            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, electricity);
            pstmt.setInt(3, hotWater);
            pstmt.setInt(4, coldWater);
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
                     double price = tariffRs.getDouble("price");
                     double normative = tariffRs.getDouble("normative");

                     double volume = 0;

                     // ЛОГИКА РАСЧЁТА ДЛЯ КАЖДОЙ УСЛУГИ
                     if (serviceName.contains("Электричество") || serviceName.contains("Электро")) {
                         // Счётчик: разница показаний
                         volume = currElec - prevElec;

                     } else if (serviceName.contains("ГВС") && serviceName.contains("теплоноситель")) {
                         // ГВС (вода): разница показаний
                         volume = currHot - prevHot;

                     } else if (serviceName.contains("ХВС")) {
                         // ХВС (вода): разница показаний
                         volume = currCold - prevCold;

                     } else if (serviceName.contains("Водоотведение")) {
                         // Водоотведение: сумма ХВС и ГВС × норматив (обычно 1.0)
                         volume = totalWaterConsumption * normative;

                     } else if (serviceName.contains("ГВС") && serviceName.contains("тепловая энергия")) {
                         // ГВС (тепловая энергия): расход ГВС × норматив (0.065 Гкал/м³)
                         volume = hotWaterConsumption * normative;

                     } else if (serviceName.contains("Отопление")) {
                         // Отопление: площадь × норматив
                         volume = area * normative;

                     } else if (serviceName.contains("Газ")) {
                         // Газ: площадь × норматив
                         volume = area * normative;

                     } else if (serviceName.contains("ТКО") || serviceName.contains("обращение")) {
                         // ТКО: площадь × норматив
                         volume = area * normative;

                     } else if (serviceName.contains("Содержание жилья")) {
                         // Содержание жилья: площадь × тариф (норматив не используется)
                         volume = area;
                     }

                     double amount = volume * price;

                     // Вставляем в базу
                     try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                         insertStmt.setInt(1, ownerId);
                         insertStmt.setInt(2, tariffId);
                         insertStmt.setDouble(3, volume);
                         insertStmt.setDouble(4, price);
                         insertStmt.setDouble(5, amount);
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