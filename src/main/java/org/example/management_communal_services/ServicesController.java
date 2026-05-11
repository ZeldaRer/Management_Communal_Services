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

public class ServicesController {

    @FXML
    private TableView<Service> servicesTable;

    @FXML
    private TableColumn<Service, String> serviceNameColumn;

    @FXML
    private TableColumn<Service, Double> servicePriceColumn;

    private ObservableList<Service> servicesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        serviceNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        servicePriceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));

        // УБРАНЫ эти строки:
        // servicesTable.setMaxWidth(Double.MAX_VALUE);
        // servicesTable.setMaxHeight(Double.MAX_VALUE);

        // Растягиваем колонки
        servicesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        loadServices();
        servicesTable.setItems(servicesList);
    }

    private void loadServices() {
        String sql = "SELECT name, price FROM Services";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString("name");
                double price = rs.getDouble("price");
                servicesList.add(new Service(name, price));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}