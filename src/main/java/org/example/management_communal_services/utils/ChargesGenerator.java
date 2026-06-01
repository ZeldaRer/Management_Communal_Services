package org.example.management_communal_services.utils;

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
        // Получаем текущие показания счётчиков за этот месяц
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

        // Получаем предыдущие показания (до текущего месяца)
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

        // Расчёт расхода воды (для водоотведения и ГВС тепловая энергия)
        double hotWaterConsumption = currHot - prevHot;
        double coldWaterConsumption = currCold - prevCold;
        double totalWaterConsumption = hotWaterConsumption + coldWaterConsumption;

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

                // ЛОГИКА РАСЧЁТА ДЛЯ КАЖДОЙ УСЛУГИ
                if (serviceName.contains("Электричество") || serviceName.contains("Электро")) {
                    // Счётчик: разница показаний (если есть показания)
                    if (hasReadings) {
                        volume = currElec - prevElec;
                    } else {
                        // Если нет показаний, считаем по нормативу
                        volume = normative > 0 ? normative * area : area;
                    }

                } else if (serviceName.contains("ГВС") && serviceName.contains("теплоноситель")) {
                    // ГВС (вода): разница показаний (если есть показания)
                    if (hasReadings) {
                        volume = currHot - prevHot;
                    } else {
                        // Если нет показаний, считаем по нормативу
                        volume = normative > 0 ? normative * area : area;
                    }

                } else if (serviceName.contains("ХВС")) {
                    // ХВС (вода): разница показаний (если есть показания)
                    if (hasReadings) {
                        volume = currCold - prevCold;
                    } else {
                        // Если нет показаний, считаем по нормативу
                        volume = normative > 0 ? normative * area : area;
                    }

                } else if (serviceName.contains("Водоотведение")) {
                    // Водоотведение: сумма ХВС и ГВС × норматив
                    volume = totalWaterConsumption * normative;

                } else if (serviceName.contains("ГВС") && serviceName.contains("тепловая энергия")) {
                    // ГВС (тепловая энергия): расход ГВС × норматив
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

                amount = volume * tariffPrice;

                // Вставляем начисление
                String insertSql = "INSERT INTO Charges (owner_id, tariff_id, volume, tariff_price, amount, month, year) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

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