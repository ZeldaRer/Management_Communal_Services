package org.example.management_communal_services.controllers.owner;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.management_communal_services.models.ServiceHistory;
import org.example.management_communal_services.utils.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HistoryController {

    @FXML
    private TableView<ServiceHistory> applicationsTable;

    @FXML
    private TableColumn<ServiceHistory, Integer> appNumColumn;  // Порядковый номер

    @FXML
    private TableColumn<ServiceHistory, String> appCategoryColumn;  // Категория

    @FXML
    private TableColumn<ServiceHistory, String> appServiceColumn;  // Услуга

    @FXML
    private TableColumn<ServiceHistory, String> appDateColumn;

    @FXML
    private TableColumn<ServiceHistory, String> appDescColumn;

    @FXML
    private TableColumn<ServiceHistory, String> appStatusColumn;

    private ObservableList<ServiceHistory> applicationsList = FXCollections.observableArrayList();
    private int currentOwnerId;

    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
        loadApplications();
    }

    @FXML
    public void initialize() {
        // Привязка колонок к полям модели
        appNumColumn.setCellValueFactory(new PropertyValueFactory<>("rowNum"));
        appCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        appServiceColumn.setCellValueFactory(new PropertyValueFactory<>("serviceName"));
        appDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        appDescColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        appStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Растягиваем колонки
        applicationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        applicationsTable.setItems(applicationsList);
    }

    private void loadApplications() {
        if (currentOwnerId == 0) return;

        // Сначала проверяем просроченные заявки
        checkExpiredApplications();

        // SQL с JOIN для получения категории и названия услуги
        // Сортировка от старых к новым (ASC), нумерация через переменную
        String sql = """
            SELECT 
                s.category,
                s.name as service_name,
                a.created_at,
                a.description,
                a.status
            FROM Applications a
            JOIN Services s ON a.service_id = s.id
            WHERE a.owner_id = ?
            ORDER BY a.created_at ASC
        """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            applicationsList.clear();
            int rowNum = 1;  // Нумерация начинается с 1

            while (rs.next()) {
                String category = rs.getString("category");
                String serviceName = rs.getString("service_name");
                String date = rs.getString("created_at");
                String description = rs.getString("description");
                String status = rs.getString("status");

                applicationsList.add(new ServiceHistory(
                        rowNum++,
                        category,
                        serviceName,
                        date,
                        description,
                        status
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Проверка и обновление просроченных заявок (старше 10 дней)
    private void checkExpiredApplications() {
        String sql = "UPDATE Applications SET status = 'Просрочено' " +
                "WHERE status = 'На рассмотрении' AND created_at < date('now', '-10 days')";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                System.out.println("Обновлено просроченных заявок: " + updated);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}