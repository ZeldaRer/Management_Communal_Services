package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.xwpf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class ReceiptController {

    @FXML private Label lblAccountNumber;
    @FXML private Label lblFullName;
    @FXML private Label lblAddress;
    @FXML private Label lblPeriod;
    @FXML private Label lblPeriodDates;

    @FXML private Label lblDebt;
    @FXML private Label lblCurrentCharges;
    @FXML private Label lblTotal;
    @FXML private Label lblInfoText;
    @FXML private Label lblTableTotal;

    @FXML private TableView<ChargeRow> chargesTable;
    @FXML private TableColumn<ChargeRow, String> serviceNameColumn;
    @FXML private TableColumn<ChargeRow, String> unitColumn;
    @FXML private TableColumn<ChargeRow, String> readingsColumn;
    @FXML private TableColumn<ChargeRow, String> volumeColumn;
    @FXML private TableColumn<ChargeRow, String> tariffColumn;
    @FXML private TableColumn<ChargeRow, String> normativeColumn;
    @FXML private TableColumn<ChargeRow, String> calcMonthColumn;
    @FXML private TableColumn<ChargeRow, String> amountColumn;

    @FXML private VBox housingContainer;

    private int currentOwnerId;
    private int currentMonth;
    private int currentYear;
    private double totalAmount = 0.0;

    public static class ChargeRow {
        private final String serviceName;
        private final String unit;
        private final String readings;
        private final String volume;
        private final String tariff;
        private final String normative;
        private final String amount;
        private final int month;
        private final int year;

        public ChargeRow(String serviceName, String unit, String readings, String volume,
                         String tariff, String normative, String amount, int month, int year) {
            this.serviceName = serviceName;
            this.unit = unit;
            this.readings = readings;
            this.volume = volume;
            this.tariff = tariff;
            this.normative = normative;
            this.amount = amount;
            this.month = month;
            this.year = year;
        }

        public String getServiceName() { return serviceName; }
        public String getUnit() { return unit; }
        public String getReadings() { return readings; }
        public String getVolume() { return volume; }
        public String getTariff() { return tariff; }
        public String getNormative() { return normative; }
        public String getAmount() { return amount; }
        public int getMonth() { return month; }
        public int getYear() { return year; }
    }

    public void setOwnerData(int ownerId, int month, int year) {
        this.currentOwnerId = ownerId;
        this.currentMonth = month;
        this.currentYear = year;
        loadReceiptData();
    }

    private void loadReceiptData() {
        loadOwnerInfo();
        loadChargesTable();
        loadHousingServices();
        calculateTotal();
        loadInfoText();
    }

    private void loadOwnerInfo() {
        String sql = "SELECT account_number, full_name, street, building, apartment_number FROM Owners WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                lblAccountNumber.setText("Лицевой счёт: " + rs.getString("account_number"));
                lblFullName.setText("ФИО: " + rs.getString("full_name"));
                lblAddress.setText("Адрес: " + rs.getString("street") + ", д. " + rs.getString("building") + ", кв. " + rs.getString("apartment_number"));
                formatPeriodWithDates();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке данных собственника");
        }
    }

    // ИСПРАВЛЕНИЕ 1: Период оплаты теперь берет дату из MeterReadings
    private void formatPeriodWithDates() {
        // 1. Начало периода: 25 число предыдущего месяца (как было)
        YearMonth previousMonth = YearMonth.of(currentYear, currentMonth).minusMonths(1);
        int startDay = 25;
        int daysInPreviousMonth = previousMonth.lengthOfMonth();
        if (startDay > daysInPreviousMonth) startDay = daysInPreviousMonth;
        LocalDate startDate = LocalDate.of(previousMonth.getYear(), previousMonth.getMonthValue(), startDay);

        // 2. Конец периода: ДАТА ПОСЛЕДНЕГО ВВОДА ПОКАЗАНИЙ за текущий месяц
        LocalDate endDate = null;
        String dateSql = "SELECT reading_date FROM MeterReadings WHERE owner_id = ? AND month = ? AND year = ? ORDER BY reading_date DESC LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(dateSql)) {
            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, currentMonth);
            pstmt.setInt(3, currentYear);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                endDate = LocalDate.parse(rs.getString("reading_date"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Если показаний нет, берем последнее число месяца
        if (endDate == null) {
            YearMonth currentYM = YearMonth.of(currentYear, currentMonth);
            endDate = LocalDate.of(currentYear, currentMonth, currentYM.lengthOfMonth());
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String periodText = "Период оплаты: " + startDate.format(formatter) + " - " + endDate.format(formatter);
        lblPeriod.setText(periodText);
    }

    private void loadChargesTable() {
        // Получаем ПОСЛЕДНИЕ доступные показания (не обязательно за этот месяц)
        String currentReadingsSql = "SELECT electricity, hot_water, cold_water, month, year " +
                "FROM MeterReadings " +
                "WHERE owner_id = ? AND (year < ? OR (year = ? AND month <= ?)) " +
                "ORDER BY year DESC, month DESC LIMIT 1";
        String lastReadingsSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings WHERE owner_id = ? AND (year < ? OR (year = ? AND month < ?)) ORDER BY year DESC, month DESC LIMIT 1";
        String chargesSql = "SELECT c.volume, c.amount, c.tariff_price, c.month, c.year, t.service_name, t.unit, t.normative, t.category FROM Charges c LEFT JOIN Tariffs t ON c.tariff_id = t.id WHERE c.owner_id = ? AND c.month = ? AND c.year = ? ORDER BY t.category, t.service_name";

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Текущие показания
            PreparedStatement currStmt = conn.prepareStatement(currentReadingsSql);
            currStmt.setInt(1, currentOwnerId);
            currStmt.setInt(2, currentYear);
            currStmt.setInt(3, currentYear);
            currStmt.setInt(4, currentMonth);
            ResultSet currRs = currStmt.executeQuery();
            double currElectricity = 0, currHotWater = 0, currColdWater = 0;
            boolean hasCurrentReadings = false;
            if (currRs.next()) {
                hasCurrentReadings = true;
                currElectricity = currRs.getDouble("electricity");
                currHotWater = currRs.getDouble("hot_water");
                currColdWater = currRs.getDouble("cold_water");
            }
            currRs.close(); currStmt.close();

            // Предыдущие показания
            PreparedStatement lastStmt = conn.prepareStatement(lastReadingsSql);
            lastStmt.setInt(1, currentOwnerId);
            lastStmt.setInt(2, currentYear);
            lastStmt.setInt(3, currentYear);
            lastStmt.setInt(4, currentMonth);
            ResultSet lastRs = lastStmt.executeQuery();
            double lastElectricity = 0, lastHotWater = 0, lastColdWater = 0;
            boolean hasLastReadings = false;
            if (lastRs.next()) {
                hasLastReadings = true;
                lastElectricity = lastRs.getDouble("electricity");
                lastHotWater = lastRs.getDouble("hot_water");
                lastColdWater = lastRs.getDouble("cold_water");
            }
            lastRs.close(); lastStmt.close();

            // Начисления
            PreparedStatement chargesStmt = conn.prepareStatement(chargesSql);
            chargesStmt.setInt(1, currentOwnerId);
            chargesStmt.setInt(2, currentMonth);
            chargesStmt.setInt(3, currentYear);
            ResultSet chargesRs = chargesStmt.executeQuery();

            chargesTable.getItems().clear();

            while (chargesRs.next()) {
                String serviceName = chargesRs.getString("service_name");
                double volumeFromCharges = chargesRs.getDouble("volume");
                double tariffPrice = chargesRs.getDouble("tariff_price");
                double amount = chargesRs.getDouble("amount");
                String unit = chargesRs.getString("unit");
                double normative = chargesRs.getDouble("normative");
                int month = chargesRs.getInt("month");
                int year = chargesRs.getInt("year");

                String readings = "-";
                String volumeText = "-";
                boolean isMeteredService = false;
                double consumption = 0;

                // Логика для счетчиков
                if (serviceName.toLowerCase().contains("электро")) {
                    isMeteredService = true;
                    if (hasCurrentReadings) {
                        readings = String.format("%.3f", currElectricity);
                        consumption = hasLastReadings ? currElectricity - lastElectricity : currElectricity;
                    }
                } else if (serviceName.contains("ГВС") && serviceName.contains("теплоноситель")) {
                    isMeteredService = true;
                    if (hasCurrentReadings) {
                        readings = String.format("%.3f", currHotWater);
                        consumption = hasLastReadings ? currHotWater - lastHotWater : currHotWater;
                    }
                } else if (serviceName.contains("ХВС")) {
                    isMeteredService = true;
                    if (hasCurrentReadings) {
                        readings = String.format("%.3f", currColdWater);
                        consumption = hasLastReadings ? currColdWater - lastColdWater : currColdWater;
                    }
                }

                if (isMeteredService && hasCurrentReadings) {
                    volumeText = String.format("%.3f", consumption);
                } else {
                    volumeText = (volumeFromCharges > 0) ? String.format("%.3f", volumeFromCharges) : "-";
                }

                String tariffText = (tariffPrice > 0) ? String.format("%.2f", tariffPrice) : "-";
                String normativeText = (normative > 0) ? String.format("%.4f", normative) : "-";
                String unitText = (unit != null && !unit.isEmpty()) ? unit : "-";
                String amountText = String.format("%.2f", amount);

                ChargeRow row = new ChargeRow(serviceName, unitText, readings, volumeText, tariffText, normativeText, amountText, month, year);
                chargesTable.getItems().add(row);
                totalAmount += amount;

            }

            // Подсчитываем и отображаем общую сумму таблицы
            double tableTotal = 0;
            for (ChargeRow r : chargesTable.getItems()) {
                tableTotal += Double.parseDouble(r.getAmount().replace(",", "."));
            }

            if (lblTableTotal != null) {
                lblTableTotal.setText(String.format("Итого за услуги: %.2f руб.", tableTotal));
            }

            chargesRs.close(); chargesStmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Ошибка при загрузке начислений");
        }
    }

    // Жилищные услуги (регистронезависимый поиск + добавление в сумму)
    private void loadHousingServices() {
        String sql = "SELECT s.name, s.price FROM Applications a JOIN Services s ON a.service_id = s.id WHERE a.owner_id = ? AND LOWER(a.status) = 'в работе'";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();
            housingContainer.getChildren().clear();
            while (rs.next()) {
                String serviceName = rs.getString("name");
                double price = rs.getDouble("price");
                Label label = new Label(String.format("%s: %.2f руб.", serviceName, price));
                label.setStyle("-fx-font-size: 14px;");
                housingContainer.getChildren().add(label);
                totalAmount += price; // Добавляем в общую сумму
            }
            if (housingContainer.getChildren().isEmpty()) {
                Label label = new Label("Нет услуг в работе");
                label.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
                housingContainer.getChildren().add(label);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadInfoText() {
        String infoText = "Показания приборов учёта необходимо передавать ежемесячно. Показания приборов учёта нужно вводить раз в месяц с промежутком не менее 15 дней между предыдущими и текущими показаниями приборов учёта.\n";
        if (lblInfoText != null) {
            lblInfoText.setText(infoText);
            lblInfoText.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666; -fx-wrap-text: true;");
        }
    }

    // Итоговая сумма теперь включает housing services (через totalAmount)
    private void calculateTotal() {
        // Считаем задолженность за прошлые периоды
        String debtSql = "SELECT SUM(amount) FROM Charges WHERE owner_id = ? AND is_paid = 0 " +
                "AND (month < ? OR (month = ? AND year < ?))";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(debtSql)) {
            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, currentMonth);
            pstmt.setInt(3, currentMonth);
            pstmt.setInt(4, currentYear);

            ResultSet rs = pstmt.executeQuery();
            double debt = 0.0;
            if (rs.next()) {
                debt = rs.getDouble(1);
            }

            // Считаем платежи
            String paymentsSql = "SELECT SUM(amount) FROM Payments WHERE owner_id = ?";
            try (PreparedStatement payStmt = conn.prepareStatement(paymentsSql)) {
                payStmt.setInt(1, currentOwnerId);
                ResultSet payRs = payStmt.executeQuery();
                double payments = 0.0;
                if (payRs.next()) {
                    payments = payRs.getDouble(1);
                }

                // Рассчитываем баланс
                double balance = payments - debt;

                // Итоговая сумма УЖЕ включает жилищные услуги (из totalAmount)
                // Поэтому НЕ добавляем housingServicesTotal повторно!
                double totalToPay = totalAmount;

                if (balance > 0) {
                    // Переплата - уменьшаем сумму
                    lblDebt.setText(String.format("Переплата: %.2f руб.", balance));
                    lblDebt.setStyle("-fx-font-size: 16px; -fx-text-fill: green;");
                    totalToPay = Math.max(0, totalToPay - balance);
                } else if (balance < 0) {
                    // Долг - увеличиваем сумму
                    lblDebt.setText(String.format("Задолженность: %.2f руб.", Math.abs(balance)));
                    lblDebt.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                    totalToPay += Math.abs(balance);
                } else {
                    lblDebt.setText("Задолженность отсутствует");
                    lblDebt.setStyle("-fx-font-size: 16px; -fx-text-fill: black;");
                }

                lblCurrentCharges.setText(String.format("Начислено за текущий период: %.2f руб.", totalAmount));
                lblTotal.setText(String.format("ИТОГО К ОПЛАТЕ: %.2f руб.", totalToPay));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            lblTotal.setText(String.format("ИТОГО К ОПЛАТЕ: %.2f руб.", totalAmount));
        }
    }

    @FXML
    private void generateReceipt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить квитанцию");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Document", "*.docx"));
        fileChooser.setInitialFileName("Квитанция_" + currentMonth + "_" + currentYear + ".docx");
        var file = fileChooser.showSaveDialog(lblTotal.getScene().getWindow());
        if (file == null) return;
        try {
            createDOCX(file.getAbsolutePath());
            showSuccess("Квитанция успешно сохранена!");
        } catch (IOException e) {
            e.printStackTrace();
            showError("Ошибка при сохранении квитанции");
        }
    }

    private void createDOCX(String filePath) throws IOException {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("ЕДИНЫЙ ПЛАТЁЖНЫЙ ДОКУМЕНТ");
        titleRun.setBold(true);
        titleRun.setFontSize(16);

        addParagraph(document, lblAccountNumber.getText());
        addParagraph(document, lblFullName.getText());
        addParagraph(document, lblAddress.getText());
        addParagraph(document, lblPeriod.getText());
        addEmptyLine(document);

        addSectionTitle(document, "Начисления по услугам");
        XWPFTable table = document.createTable(chargesTable.getItems().size() + 1, 8);
        XWPFTableRow headerRow = table.getRow(0);
        headerRow.getCell(0).setText("Услуга");
        headerRow.getCell(1).setText("Ед.изм.");
        headerRow.getCell(2).setText("Показания");
        headerRow.getCell(3).setText("Объём");
        headerRow.getCell(4).setText("Тариф, руб.");
        headerRow.getCell(5).setText("Норматив");
        headerRow.getCell(6).setText("Расчётный месяц");
        headerRow.getCell(7).setText("Сумма, руб.");

        for (int i = 0; i < chargesTable.getItems().size(); i++) {
            ChargeRow row = chargesTable.getItems().get(i);
            XWPFTableRow tableRow = table.getRow(i + 1);
            tableRow.getCell(0).setText(row.getServiceName());
            tableRow.getCell(1).setText(row.getUnit());
            tableRow.getCell(2).setText(row.getReadings());
            tableRow.getCell(3).setText(row.getVolume());
            tableRow.getCell(4).setText(row.getTariff());
            tableRow.getCell(5).setText(row.getNormative());
            tableRow.getCell(6).setText(getMonthName(row.getMonth()) + " " + row.getYear());
            tableRow.getCell(7).setText(row.getAmount());
        }

        // После заполнения таблицы начислений, перед addEmptyLine(document);
        // Подсчитываем общую сумму таблицы
        double tableTotal = 0;
        for (ChargeRow row : chargesTable.getItems()) {
            tableTotal += Double.parseDouble(row.getAmount().replace(",", "."));
        }

        // Добавляем итог под таблицей
        XWPFParagraph tableTotalPara = document.createParagraph();
        XWPFRun tableTotalRun = tableTotalPara.createRun();
        tableTotalRun.setText(String.format("Итого за услуги: %.2f руб.", tableTotal));
        tableTotalRun.setBold(false);
        tableTotalRun.setFontSize(12);

        // Добавляем справочную информацию из lblInfoText
        if (lblInfoText != null && !lblInfoText.getText().isEmpty()) {
            XWPFParagraph infoPara = document.createParagraph();
            XWPFRun infoRun = infoPara.createRun();
            infoRun.setText(lblInfoText.getText());
            infoRun.setFontSize(10);
            infoRun.setColor("666666");
        }

        addEmptyLine(document);
        addSectionTitle(document, "Жилищные услуги");
        for (var node : housingContainer.getChildren()) {
            if (node instanceof Label) addParagraph(document, ((Label) node).getText());
        }
        addEmptyLine(document);

        addParagraph(document, lblDebt.getText());
        addParagraph(document, lblCurrentCharges.getText());
        XWPFParagraph totalPara = document.createParagraph();
        XWPFRun totalRun = totalPara.createRun();
        totalRun.setText(lblTotal.getText());
        totalRun.setBold(true);
        totalRun.setFontSize(14);

        try (FileOutputStream out = new FileOutputStream(filePath)) {
            document.write(out);
        }
        document.close();
    }

    @FXML
    public void initialize() {
        serviceNameColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getServiceName()));
        unitColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getUnit()));
        readingsColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getReadings()));
        volumeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getVolume()));
        tariffColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTariff()));
        normativeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNormative()));
        calcMonthColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(getMonthName(cellData.getValue().getMonth()) + " " + cellData.getValue().getYear()));
        amountColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getAmount()));
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(12);
    }

    private void addSectionTitle(XWPFDocument document, String title) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(14);
    }

    private void addEmptyLine(XWPFDocument document) {
        document.createParagraph();
    }

    private String getMonthName(int month) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return months[month - 1];
    }

    @FXML
    private void closeWindow() {
        Stage stage = (Stage) lblTotal.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setContentText(message);
        alert.showAndWait();
    }
}