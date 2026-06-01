package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

// Контроллер для окна профиля (главное окно)
// Отображает информацию о собственнике и расчётах
public class ProfileController {

    @FXML
    private Label lblAmount;

    @FXML
    private Label lblPeriod;

    @FXML
    private Label lblFullName;

    @FXML
    private Label lblAccountNumber;

    @FXML
    private Label lblAddress;

    @FXML
    private Label lblArea;

    @FXML
    private Label lblCurrentAmount;

    @FXML
    private Label lblHousingServices;

    @FXML
    private Label lblDebt;

    @FXML
    private Label lblTotal;

    private int currentOwnerId;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", new java.util.Locale("ru"));

    // Установка ID текущего владельца и загрузка данных
    public void setCurrentOwnerId(int ownerId) {
        System.out.println("ProfileController: setCurrentOwnerId вызван с ownerId = " + ownerId);
        this.currentOwnerId = ownerId;

        // Используем Platform.runLater для гарантии инициализации FXML элементов
        javafx.application.Platform.runLater(() -> {
            System.out.println("ProfileController: загрузка данных...");
            loadProfileData();
            loadLastCharge();
        });
    }

    // Инициализация контроллера
    @FXML
    public void initialize() {
        System.out.println("ProfileController: initialize() вызван");
    }

    // Загрузка данных профиля из базы данных
    private void loadProfileData() {
        if (currentOwnerId == 0) {
            System.out.println("ProfileController: currentOwnerId = 0, пропускаем загрузку");
            return;
        }

        String sql = "SELECT full_name, account_number, street, building, apartment_number, area " +
                "FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                System.out.println("ProfileController: данные найдены в БД");

                String fullName = rs.getString("full_name");
                String accountNumber = rs.getString("account_number");
                String address = rs.getString("street") + ", д. " +
                        rs.getString("building") + ", кв. " +
                        rs.getString("apartment_number");
                double area = rs.getDouble("area");

                if (lblFullName != null) lblFullName.setText(fullName);
                if (lblAccountNumber != null) lblAccountNumber.setText(accountNumber);
                if (lblAddress != null) lblAddress.setText(address);
                if (lblArea != null) lblArea.setText(String.valueOf(area));

            } else {
                System.out.println("ProfileController: данные не найдены для owner_id = " + currentOwnerId);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Загрузка начислений и расчёт итоговой суммы (как в квитанции)
    private void loadLastCharge() {
        if (currentOwnerId == 0) return;

        try (Connection conn = DatabaseConnector.getConnection()) {
            // 1. Определяем последний месяц с начислениями
            String periodSql = "SELECT year, month FROM Charges WHERE owner_id = ? " +
                    "ORDER BY year DESC, month DESC LIMIT 1";

            int lastYear = 0;
            int lastMonth = 0;
            boolean hasCharges = false;

            try (PreparedStatement pstmt = conn.prepareStatement(periodSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    lastYear = rs.getInt("year");
                    lastMonth = rs.getInt("month");
                    hasCharges = true;
                }
            }

            if (!hasCharges) {
                lblPeriod.setText("Счёт за * прошедший месяц *");
                lblCurrentAmount.setText("Начислено за текущий период: 0.00 руб.");
                lblHousingServices.setText("Жилищные услуги: 0.00 руб.");
                lblDebt.setText("Задолженность: 0.00 руб.");
                lblTotal.setText("ИТОГО: 0.00 руб.");
                return;
            }

            // 2. Считаем сумму за текущий период (коммунальные услуги)
            String sumSql = "SELECT SUM(amount) as total_amount FROM Charges " +
                    "WHERE owner_id = ? AND year = ? AND month = ?";

            double currentAmount = 0.0;
            try (PreparedStatement pstmt = conn.prepareStatement(sumSql)) {
                pstmt.setInt(1, currentOwnerId);
                pstmt.setInt(2, lastYear);
                pstmt.setInt(3, lastMonth);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    currentAmount = rs.getDouble("total_amount");
                }
            }

            // 3. Считаем стоимость ЖИЛИЩНЫХ УСЛУГ (только "в работе")
            double housingServicesAmount = 0.0;
            String servicesSql = "SELECT SUM(s.price) as services_total " +
                    "FROM Applications a " +
                    "JOIN Services s ON a.service_id = s.id " +
                    "WHERE a.owner_id = ? AND LOWER(a.status) = 'в работе'";

            try (PreparedStatement pstmt = conn.prepareStatement(servicesSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    double val = rs.getDouble("services_total");
                    if (!rs.wasNull()) {
                        housingServicesAmount = val;
                    }
                }
            }

            // 4. Считаем услуги "выполнено" — они идут в ОБЩИЙ БАЛАНС как уже начисленные
            double completedServicesAmount = 0.0;
            String completedSql = "SELECT SUM(s.price) as completed_total " +
                    "FROM Applications a " +
                    "JOIN Services s ON a.service_id = s.id " +
                    "WHERE a.owner_id = ? AND LOWER(a.status) = 'выполнено'";

            try (PreparedStatement pstmt = conn.prepareStatement(completedSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    double val = rs.getDouble("completed_total");
                    if (!rs.wasNull()) {
                        completedServicesAmount = val;
                    }
                }
            }

            // 5. Рассчитываем ОБЩИЙ БАЛАНС
            // 5.1. Сумма ВСЕХ начислений за прошлые периоды (коммунальные услуги)
            String pastChargesSql = "SELECT SUM(amount) FROM Charges WHERE owner_id = ? " +
                    "AND (year < ? OR (year = ? AND month < ?))";
            double pastCharges = 0.0;
            try (PreparedStatement pstmt = conn.prepareStatement(pastChargesSql)) {
                pstmt.setInt(1, currentOwnerId);
                pstmt.setInt(2, lastYear);
                pstmt.setInt(3, lastYear);
                pstmt.setInt(4, lastMonth);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    pastCharges = rs.getDouble(1);
                    if (rs.wasNull()) pastCharges = 0.0;
                }
            }

            // 5.2. Сумма ВСЕХ платежей
            String paymentsSql = "SELECT SUM(amount) FROM Payments WHERE owner_id = ?";
            double totalPayments = 0.0;
            try (PreparedStatement pstmt = conn.prepareStatement(paymentsSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    totalPayments = rs.getDouble(1);
                    if (rs.wasNull()) totalPayments = 0.0;
                }
            }

            // 5.3. Рассчитываем баланс
            // Включаем: прошлые начисления + выполненные услуги
            double totalOwed = pastCharges + completedServicesAmount;
            double balance = totalPayments - totalOwed;

            // 6. Рассчитываем ИТОГО к оплате
            // Включаем: текущие начисления + услуги "в работе"
            double totalToPay = currentAmount + housingServicesAmount;

            if (balance < 0) {
                // Есть задолженность — добавляем к оплате
                totalToPay += Math.abs(balance);
            } else if (balance > 0) {
                // Есть переплата — вычитаем (но не меньше 0)
                totalToPay = Math.max(0, totalToPay - balance);
            }

            // 7. Обновляем интерфейс
            String monthName = getMonthNameInAccusative(lastMonth);

            lblPeriod.setText("Период оплаты: " + formatPeriodWithDates(conn, currentOwnerId, lastMonth, lastYear));
            lblCurrentAmount.setText("Начислено за текущий период: " + String.format("%.2f руб.", currentAmount));

            // Показываем только услуги "в работе"
            lblHousingServices.setText("Жилищные услуги: " +
                    String.format("%.2f руб.", housingServicesAmount));

            if (balance < 0) {
                lblDebt.setText("Задолженность: " + String.format("%.2f руб.", Math.abs(balance)));
                lblDebt.setStyle("-fx-text-fill: #ff4d4d;");
            } else if (balance > 0) {
                lblDebt.setText("Переплата: " + String.format("%.2f руб.", balance));
                lblDebt.setStyle("-fx-text-fill: #2ecc71;");
            } else {
                lblDebt.setText("Задолженность: 0.00 руб.");
                lblDebt.setStyle("-fx-text-fill: #333333;");
            }

            lblTotal.setText("ИТОГО: " + String.format("%.2f руб.", totalToPay));

        } catch (SQLException e) {
            e.printStackTrace();
            lblPeriod.setText("Ошибка расчёта");
        }
    }

    // метод для форматирования периода
    private String formatPeriodWithDates(Connection conn, int ownerId, int month, int year) {
        try {
            // Ищем дату ПРЕДЫДУЩИХ показаний (начало периода)
            String previousReadingSql = "SELECT reading_date FROM MeterReadings " +
                    "WHERE owner_id = ? AND (year < ? OR (year = ? AND month < ?)) " +
                    "ORDER BY year DESC, month DESC, reading_date DESC LIMIT 1";

            LocalDate startDate = null;
            try (PreparedStatement pstmt = conn.prepareStatement(previousReadingSql)) {
                pstmt.setInt(1, ownerId);
                pstmt.setInt(2, year);
                pstmt.setInt(3, year);
                pstmt.setInt(4, month);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    startDate = LocalDate.parse(rs.getString("reading_date"));
                }
            }

            // Если предыдущих показаний нет — берём 25 число предыдущего месяца
            if (startDate == null) {
                YearMonth previousMonth = YearMonth.of(year, month).minusMonths(1);
                int startDay = Math.min(25, previousMonth.lengthOfMonth());
                startDate = LocalDate.of(previousMonth.getYear(), previousMonth.getMonthValue(), startDay);
            }

            // Ищем дату ТЕКУЩИХ показаний (конец периода)
            String currentReadingSql = "SELECT reading_date FROM MeterReadings " +
                    "WHERE owner_id = ? AND month = ? AND year = ? " +
                    "ORDER BY reading_date DESC LIMIT 1";

            LocalDate endDate = null;
            try (PreparedStatement pstmt = conn.prepareStatement(currentReadingSql)) {
                pstmt.setInt(1, ownerId);
                pstmt.setInt(2, month);
                pstmt.setInt(3, year);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    endDate = LocalDate.parse(rs.getString("reading_date"));
                }
            }

            // Если показаний за этот месяц нет — берём последнее число месяца
            if (endDate == null) {
                endDate = LocalDate.of(year, month, YearMonth.of(year, month).lengthOfMonth());
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            return startDate.format(formatter) + " - " + endDate.format(formatter);

        } catch (Exception e) {
            e.printStackTrace();
            return "Счёт за " + getMonthNameInAccusative(month) + " " + year;
        }
    }

    // Вспомогательный метод для получения названия месяца в именительном падеже
    private String getMonthNameInAccusative(int month) {
        String[] months = {
                "январь", "февраль", "март", "апрель", "май", "июнь",
                "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        };
        return months[month - 1];
    }
}