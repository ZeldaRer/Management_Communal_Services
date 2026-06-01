package org.example.management_communal_services.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {

    // Адрес БД SQLite
    private static final String URL = "jdbc:sqlite:besenok.db";

    // Метод для получения соединения
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            return DriverManager.getConnection(URL);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("JDBC драйвер для БД SQLite не найден!");
            return null;
        }
    }

    // Метод для проверки подключения
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                System.out.println("Соединение с БД besenok.db выполнено успешно!");
                conn.close();
                System.out.println("Отключение от БД выполнено.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Ошибка подключения к БД");
        }
    }
}