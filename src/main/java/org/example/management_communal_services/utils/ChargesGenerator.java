package org.example.management_communal_services.utils;

import java.sql.*;
import java.time.LocalDate;

// Автоматическая генерация начислений за месяц
// Вызывается при сохранении показаний или при открытии квитанции
public class ChargesGenerator {

    // Генерация начислений за текущий месяц
    // Генерируем ТОЛЬКО для тех, кто ввёл показания
    public static void generateChargesForCurrentMonth() {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        String sql = "SELECT o.id, o.area FROM Owners o " +
                "INNER JOIN MeterReadings mr ON o.id = mr.owner_id " +
                "WHERE mr.month = ? AND mr.year = ? " +
                "GROUP BY o.id";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentMonth);
            pstmt.setInt(2, currentYear);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int ownerId = rs.getInt("id");
                double area = rs.getDouble("area");

                // Проверяем, есть ли уже начисления за этот месяц
                if (!hasChargesForMonth(conn, ownerId, currentMonth, currentYear)) {
                    // Получаем последние показания
                    getLatestReadingsAndGenerate(conn, ownerId, area, currentMonth, currentYear);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Получение последних показаний и генерация начислений
    private static void getLatestReadingsAndGenerate(Connection conn, int ownerId, double area, int month, int year) throws SQLException {
        String readingsSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                "WHERE owner_id = ? AND month = ? AND year = ? " +
                "ORDER BY reading_date DESC LIMIT 1";

        double currElec = 0, currHot = 0, currCold = 0;
        boolean hasReadings = false;

        try (PreparedStatement pstmt = conn.prepareStatement(readingsSql)) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, month);
            pstmt.setInt(3, year);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                currElec = rs.getDouble("electricity");
                currHot = rs.getDouble("hot_water");
                currCold = rs.getDouble("cold_water");
                hasReadings = true;
            }
        }

        // Получаем предыдущие показания
        String lastReadingsSql = "SELECT electricity, hot_water, cold_water FROM MeterReadings " +
                "WHERE owner_id = ? AND (year < ? OR (year = ? AND month < ?)) " +
                "ORDER BY year DESC, month DESC LIMIT 1";

        double prevElec = 0, prevHot = 0, prevCold = 0;

        try (PreparedStatement pstmt = conn.prepareStatement(lastReadingsSql)) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, year);
            pstmt.setInt(3, year);
            pstmt.setInt(4, month);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                prevElec = rs.getDouble("electricity");
                prevHot = rs.getDouble("hot_water");
                prevCold = rs.getDouble("cold_water");
            }
        }

        // Генерируем начисления
        if (hasReadings) {
            generateChargesForOwner(conn, ownerId, area, month, year, currElec, currHot, currCold, prevElec, prevHot, prevCold);
        }
    }

    // Генерация начислений для конкретного владельца
    private static void generateChargesForOwner(Connection conn, int ownerId, double area, int month, int year,
                                                double currElec, double currHot, double currCold,
                                                double prevElec, double prevHot, double prevCold) throws SQLException {

        double hotWaterConsumption = currHot - prevHot;
        double coldWaterConsumption = currCold - prevCold;
        double totalWaterConsumption = hotWaterConsumption + coldWaterConsumption;

        String tariffsSql = "SELECT id, service_name, unit, price, normative, category FROM Tariffs WHERE is_active = 1";
        String insertSql = "INSERT INTO Charges (owner_id, tariff_id, volume, tariff_price, amount, month, year) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(tariffsSql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int tariffId = rs.getInt("id");
                String serviceName = rs.getString("service_name");
                String unit = rs.getString("unit");
                double tariffPrice = rs.getDouble("price");
                double normative = rs.getDouble("normative");

                double volume = 0;

                // Логика расчёта для каждой услуги
                if (serviceName.toLowerCase().contains("электро")) {
                    volume = currElec - prevElec;
                } else if (serviceName.contains("ГВС") && serviceName.contains("теплоноситель")) {
                    volume = hotWaterConsumption;
                } else if (serviceName.contains("ХВС")) {
                    volume = coldWaterConsumption;
                } else if (serviceName.contains("Водоотведение")) {
                    volume = totalWaterConsumption * normative;
                } else if (serviceName.contains("ГВС") && serviceName.contains("тепловая энергия")) {
                    // ИСПРАВЛЕНО: если нет потребления ГВС, считаем от площади
                    if (normative > 0) {
                        if (hotWaterConsumption > 0) {
                            volume = hotWaterConsumption * normative;
                        } else {
                            volume = area * normative;
                        }
                    }
                } else if (serviceName.contains("Отопление")) {
                    // ИСПРАВЛЕНО: явный расчёт от площади
                    if (normative > 0) {
                        volume = area * normative;
                    }
                } else if (serviceName.contains("Газ")) {
                    volume = area * normative;
                } else if (serviceName.contains("ТКО") || serviceName.contains("обращение")) {
                    volume = area * normative;
                } else if (serviceName.contains("Содержание жилья")) {
                    volume = area;
                }

                double amount = volume * tariffPrice;

                // Проверка на дубликаты
                String checkSql = "SELECT COUNT(*) FROM Charges WHERE owner_id = ? AND tariff_id = ? AND month = ? AND year = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, ownerId);
                    checkStmt.setInt(2, tariffId);
                    checkStmt.setInt(3, month);
                    checkStmt.setInt(4, year);
                    ResultSet checkRs = checkStmt.executeQuery();
                    if (checkRs.next() && checkRs.getInt(1) > 0) {
                        continue; // Пропускаем дубликат
                    }
                }

                // Вставка в БД
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
}