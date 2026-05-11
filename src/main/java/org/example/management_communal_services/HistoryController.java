package org.example.management_communal_services;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HistoryController {

    @FXML
    private TableView<ApplicationHistory> applicationsTable;

    @FXML
    private TableColumn<ApplicationHistory, Integer> appIdColumn;

    @FXML
    private TableColumn<ApplicationHistory, String> appDateColumn;

    @FXML
    private TableColumn<ApplicationHistory, String> appDescColumn;

    @FXML
    private TableColumn<ApplicationHistory, String> appStatusColumn;

    private ObservableList<ApplicationHistory> applicationsList = FXCollections.observableArrayList();
    private int currentOwnerId;

    public void setCurrentOwnerId(int ownerId) {
        this.currentOwnerId = ownerId;
        loadApplications();
    }

    @FXML
    public void initialize() {
        // Привязка колонок к полям модели
        appIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        appDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        appDescColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        appStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Растягиваем колонки
        applicationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        applicationsTable.setItems(applicationsList);
    }

    private void loadApplications() {
        if (currentOwnerId == 0) return;
        String sql = "SELECT id, created_at, description, status FROM Applications WHERE owner_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();
            applicationsList.clear();
            while (rs.next()) {
                applicationsList.add(new ApplicationHistory(
                        rs.getInt("id"),
                        rs.getString("created_at"),
                        rs.getString("description"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}