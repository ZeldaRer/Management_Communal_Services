package org.example.management_communal_services;

import java.sql.*;
import java.time.LocalDate;

// Автоматическая генерация начислений за месяц
// Вызывается при открытии квитанции или по кнопке "Сформировать начисления"
public class ChargesGenerator {

    // Генерация начислений для всех собственников за текущий месяц
    public static void generateChargesForCurrentMonth() {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        int currentDay = now.getDayOfMonth();

        // Генерируем только 25-го числа или позже (чтобы не дублировать)
        if (currentDay < 25) {
            System.out.println("Сегодня " + currentDay + "-е число. Начисления генерируются 25-го числа.");
            return;
        }

        String sql = "SELECT id, area FROM Owners";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int ownerId = rs.getInt("id");
                double area = rs.getDouble("area");

                // Проверяем, есть ли уже начисления за этот месяц
                if (!hasChargesForMonth(conn, ownerId, currentMonth, currentYear)) {
                    generateChargesForOwner(conn, ownerId, area, currentMonth, currentYear);
                }
            }

            System.out.println("Начисления за " + getMonthName(currentMonth) + " " + currentYear + " сгенерированы");

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка при генерации начислений");
        }
    }

    // Генерация начислений для конкретного владельца
    private static void generateChargesForOwner(Connection conn, int ownerId, double area, int month, int year) throws SQLException {
        // Получаем все активные тарифы
        String tariffsSql = "SELECT id, service_name, unit, price, normative, category FROM Tariffs WHERE is_active = 1";

        try (PreparedStatement pstmt = conn.prepareStatement(tariffsSql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int tariffId = rs.getInt("id");
                String serviceName = rs.getString("service_name");
                String unit = rs.getString("unit");
                double tariffPrice = rs.getDouble("price");
                double normative = rs.getDouble("normative");
                String category = rs.getString("category");

                double volume = 0;
                double amount = 0;

                // Расчёт объёма и суммы
                if (normative > 0) {
                    // Расчёт по нормативу: норматив × площадь
                    volume = normative * area;
                    amount = volume * tariffPrice;
                } else if (tariffPrice > 0) {
                    // Фиксированная услуга (например, содержание жилья)
                    volume = area;
                    amount = volume * tariffPrice;
                }

                // Вставляем начисление
                String insertSql = "INSERT INTO Charges (owner_id, tariff_id, volume, tariff_price, amount, month, year, is_paid) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, ownerId);
                    insertStmt.setInt(2, tariffId);
                    insertStmt.setDouble(3, volume);
                    insertStmt.setDouble(4, tariffPrice);
                    insertStmt.setDouble(5, amount);
                    insertStmt.setInt(6, month);
                    insertStmt.setInt(7, year);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    // Проверка, есть ли уже начисления за месяц
    private static boolean hasChargesForMonth(Connection conn, int ownerId, int month, int year) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Charges WHERE owner_id = ? AND month = ? AND year = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, month);
            pstmt.setInt(3, year);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }

        return false;
    }

    private static String getMonthName(int month) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return months[month - 1];
    }
}