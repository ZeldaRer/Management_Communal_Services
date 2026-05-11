package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OwnerController {

    @FXML
    private AnchorPane contentArea;

    @FXML
    private Label lblFullName;

    @FXML
    private Label lblAccountNumber;

    @FXML
    private Label lblAddress;

    // ID текущего пользователя
    private int currentOwnerId;
    private String currentOwnerName;

    // Метод для инициализации данных владельца после входа
    public void setCurrentOwner(int ownerId, String ownerName) {
        this.currentOwnerId = ownerId;
        this.currentOwnerName = ownerName;
        if (lblFullName != null) {
            lblFullName.setText(ownerName);
        }
        loadOwnerData();
    }

    @FXML
    public void initialize() {
        // По умолчанию открываем личный кабинет
        loadProfile();
    }

    // Загрузка данных владельца из БД для отображения в профиле
    private void loadOwnerData() {
        if (currentOwnerId == 0) return;

        String sql = "SELECT full_name, account_number, street, building, apartment_number " +
                "FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                if (lblFullName != null) lblFullName.setText(rs.getString("full_name"));
                if (lblAccountNumber != null) lblAccountNumber.setText(rs.getString("account_number"));
                if (lblAddress != null) {
                    lblAddress.setText(rs.getString("street") + ", д. " +
                            rs.getString("building") + ", кв. " +
                            rs.getString("apartment_number"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleProfile() {
        loadProfile();
    }

    @FXML
    private void handleMeters() {
        loadFXML("meters.fxml");
    }

    @FXML
    private void handleServices() {
        loadFXML("services.fxml");
    }

    @FXML
    private void handleRequest() {
        loadFXML("request.fxml");
    }

    @FXML
    private void handleHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("my-statement.fxml"));
            Parent root = loader.load();

            // Передаем ID текущего владельца в контроллер истории заявок
            HistoryController historyController = loader.getController();
            historyController.setCurrentOwnerId(currentOwnerId);

            // Загружаем контент с автоматическим растягиванием
            loadContent(root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            contentArea.getScene().setRoot(loader.load());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Универсальный метод загрузки FXML с применением растягивания
    private void loadFXML(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();
            // Применяем универсальное растягивание для любого загруженного контента
            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Универсальный метод: загружает контент и растягивает его на всю область
    private void loadContent(Parent root) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(root);

        // Если загруженный корневой элемент — AnchorPane, закрепляем его по всем сторонам
        if (root instanceof AnchorPane) {
            AnchorPane.setTopAnchor(root, 0.0);
            AnchorPane.setBottomAnchor(root, 0.0);
            AnchorPane.setLeftAnchor(root, 0.0);
            AnchorPane.setRightAnchor(root, 0.0);
        }
    }

    // Загрузка профиля через универсальный метод
    private void loadProfile() {
        loadFXML("profile.fxml");
    }
}