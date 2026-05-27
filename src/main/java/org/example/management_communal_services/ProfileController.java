package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

                if (lblFullName != null) {
                    lblFullName.setText(fullName);
                    System.out.println("lblFullName установлен: " + fullName);
                } else {
                    System.out.println("ОШИБКА: lblFullName = null!");
                }

                if (lblAccountNumber != null) {
                    lblAccountNumber.setText(accountNumber);
                    System.out.println("lblAccountNumber установлен: " + accountNumber);
                } else {
                    System.out.println("ОШИБКА: lblAccountNumber = null!");
                }

                if (lblAddress != null) {
                    lblAddress.setText(address);
                    System.out.println("lblAddress установлен: " + address);
                } else {
                    System.out.println("ОШИБКА: lblAddress = null!");
                }

                if (lblArea != null) {
                    lblArea.setText(String.valueOf(area));
                    System.out.println("lblArea установлен: " + area);
                } else {
                    System.out.println("ОШИБКА: lblArea = null!");
                }
            } else {
                System.out.println("ProfileController: данные не найдены для owner_id = " + currentOwnerId);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("ProfileController: ошибка при загрузке данных профиля");
        }
    }

    // Загрузка последнего начисления
    private void loadLastCharge() {
        if (currentOwnerId == 0) return;

        // SQL-запрос для получения суммы последнего начисления
        String sql = "SELECT amount, month, year FROM Charges " +
                "WHERE owner_id = ? " +
                "ORDER BY year DESC, month DESC LIMIT 1";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                double amount = rs.getDouble("amount");
                int month = rs.getInt("month");
                int year = rs.getInt("year");

                // Форматируем сумму
                if (lblAmount != null) {
                    lblAmount.setText(String.format("%.2f руб.", amount));
                    System.out.println("lblAmount установлен: " + amount);
                }

                // Форматируем период с правильным склонением месяца
                if (lblPeriod != null) {
                    String monthName = getMonthNameInAccusative(month); // Именительный падеж
                    lblPeriod.setText("Счёт за " + monthName + " " + year);
                    System.out.println("lblPeriod установлен: " + monthName + " " + year);
                }
            } else {
                System.out.println("ProfileController: начисления не найдены");
                if (lblAmount != null) lblAmount.setText("0.00 руб.");
                if (lblPeriod != null) lblPeriod.setText("Счёт за *прошедший месяц*");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            if (lblAmount != null) lblAmount.setText("Ошибка");
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