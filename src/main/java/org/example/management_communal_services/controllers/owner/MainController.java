package org.example.management_communal_services.controllers.owner;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.management_communal_services.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

// Контроллер для окна профиля (главное окно)
// Отображает информацию о собственнике и расчётах
public class MainController {

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
        System.out.println("MainController: setCurrentOwnerId вызван с ownerId = " + ownerId);
        this.currentOwnerId = ownerId;

        // Используем Platform.runLater для гарантии инициализации FXML элементов
        javafx.application.Platform.runLater(() -> {
            System.out.println("MainController: загрузка данных...");
            loadProfileData();
            loadLastCharge();
        });
    }

    // Инициализация контроллера
    @FXML
    public void initialize() {
        System.out.println("MainController: initialize() вызван");
    }

    // Загрузка данных профиля из базы данных
    // Заполняет лейблы ФИО, номер счёта, адрес и площадь
    private void loadProfileData() {
        if (currentOwnerId == 0) {
            System.out.println("MainController: currentOwnerId = 0, пропускаем загрузку");
            return;
        }

        String sql = "SELECT full_name, account_number, street, building, apartment_number, area " +
                "FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                System.out.println("MainController: данные найдены в БД");

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
                System.out.println("MainController: данные не найдены для owner_id = " + currentOwnerId);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Загрузка начислений и расчёт итоговой суммы (как в квитанции)
    // Включает проверку наличия показаний и корректное формирование периода
    private void loadLastCharge() {
        if (currentOwnerId == 0) return;

        try (Connection conn = DatabaseConnector.getConnection()) {

            // 1. ПРОВЕРЯЕМ наличие показаний счётчиков
            String readingsCheckSql = "SELECT COUNT(*) as count FROM MeterReadings WHERE owner_id = ?";
            int readingsCount = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(readingsCheckSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    readingsCount = rs.getInt("count");
                }
            }

            // Если показаний вообще нет — показываем сообщение
            if (readingsCount == 0) {
                lblPeriod.setText("Нет показаний счётчиков");
                lblCurrentAmount.setText("Начислено за текущий период: 0.00 руб.");
                lblHousingServices.setText("Жилищные услуги: 0.00 руб.");
                lblDebt.setText("Задолженность: 0.00 руб.");
                lblTotal.setText("ИТОГО: 0.00 руб.");
                return;
            }

            // 2. Определяем последний месяц с начислениями
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
                lblPeriod.setText("Нет начислений за текущий период");
                lblCurrentAmount.setText("Начислено за текущий период: 0.00 руб.");
                lblHousingServices.setText("Жилищные услуги: 0.00 руб.");
                lblDebt.setText("Задолженность: 0.00 руб.");
                lblTotal.setText("ИТОГО: 0.00 руб.");
                return;
            }

            // 3. ПРОВЕРЯЕМ, хватает ли показаний для формирования периода
            // Нужно минимум 2 разные даты ввода показаний
            String readingsCountSql = "SELECT COUNT(DISTINCT reading_date) as date_count " +
                    "FROM MeterReadings WHERE owner_id = ?";
            int distinctDates = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(readingsCountSql)) {
                pstmt.setInt(1, currentOwnerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    distinctDates = rs.getInt("date_count");
                }
            }

            // Если показаний меньше 2 раз, период сформировать нельзя
            if (distinctDates < 2) {
                lblPeriod.setText("Не хватает показаний счётчиков для формирования периода оплаты");
                lblCurrentAmount.setText("Начислено за текущий период: 0.00 руб.");
                lblHousingServices.setText("Жилищные услуги: 0.00 руб.");
                lblDebt.setText("Задолженность: 0.00 руб.");
                lblTotal.setText("ИТОГО: 0.00 руб.");
                return;
            }

            // 4. Считаем сумму за текущий период (коммунальные услуги)
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

            // 5. Считаем стоимость ЖИЛИЩНЫХ УСЛУГ (только "в работе")
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

            // 6. Считаем услуги "выполнено" — они идут в ОБЩИЙ БАЛАНС как уже начисленные
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

            // 7. Рассчитываем ОБЩИЙ БАЛАНС
            // 7.1. Сумма ВСЕХ начислений за прошлые периоды (коммунальные услуги)
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

            // 7.2. Сумма ВСЕХ платежей
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

            // 7.3. Рассчитываем баланс
            // Включаем: прошлые начисления + выполненные услуги
            double totalOwed = pastCharges + completedServicesAmount;
            double balance = totalPayments - totalOwed;

            // 8. Рассчитываем ИТОГО к оплате
            // Включаем: текущие начисления + услуги "в работе"
            double totalToPay = currentAmount + housingServicesAmount;

            if (balance < 0) {
                // Есть задолженность — добавляем к оплате
                totalToPay += Math.abs(balance);
            } else if (balance > 0) {
                // Есть переплата — вычитаем (но не меньше 0)
                totalToPay = Math.max(0, totalToPay - balance);
            }

            // 9. Обновляем интерфейс
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

    // Метод для форматирования периода оплаты с датами
    // Возвращает период в формате "дд.мм.гггг - дд.мм.гггг"
    // Или сообщение об ошибке, если недостаточно данных
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

            // ПРОВЕРКА: если не хватает показаний для формирования периода
            if (startDate == null || endDate == null) {
                return "Не хватает показаний счётчиков для формирования периода";
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            return startDate.format(formatter) + " - " + endDate.format(formatter);

        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при формировании периода";
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