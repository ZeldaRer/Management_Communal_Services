package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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

    // Кнопки меню для подсветки активной
    @FXML
    private Button btnProfile;

    @FXML
    private Button btnMeters;

    @FXML
    private Button btnServices;

    @FXML
    private Button btnRequest;

    @FXML
    private Button btnHistory;

    @FXML
    private Button btnLogout;

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
        // Устанавливаем активную кнопку "Главное" при начальном входе
        setActiveButton("profile");
        // По умолчанию открываем личный кабинет
        loadProfile();
    }

    // Загрузка данных владельца из БД для отображения в профиле
    private void loadOwnerData() {
        if (currentOwnerId == 0) return;

        // SQL-запрос для получения данных собственника по ID
        String sql = "SELECT full_name, account_number, street, building, apartment_number " +
                "FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Заполняем лейблы данными из результата запроса
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

    // Обработчики кнопок меню: устанавливают активную кнопку и загружают соответствующий контент
    @FXML
    private void handleProfile() {
        setActiveButton("profile");
        loadProfile();
    }

    @FXML
    private void handleMeters() {
        setActiveButton("meters");
        loadFXML("meters.fxml");
    }

    @FXML
    private void handleServices() {
        setActiveButton("services");
        loadFXML("services.fxml");
    }

    @FXML
    private void handleRequest() {
        setActiveButton("request");
        loadFXML("request.fxml");
    }

    @FXML
    private void handleHistory() {
        setActiveButton("history");
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

    // Метод для установки активной кнопки: снимает класс "active" со всех и добавает нужной
    private void setActiveButton(String buttonName) {
        // Сбрасываем все кнопки (убираем класс active)
        btnProfile.getStyleClass().remove("active");
        btnMeters.getStyleClass().remove("active");
        btnServices.getStyleClass().remove("active");
        btnRequest.getStyleClass().remove("active");
        btnHistory.getStyleClass().remove("active");

        // Добавляем класс active нужной кнопке через switch
        switch (buttonName) {
            case "profile":
                btnProfile.getStyleClass().add("active");
                break;
            case "meters":
                btnMeters.getStyleClass().add("active");
                break;
            case "services":
                btnServices.getStyleClass().add("active");
                break;
            case "request":
                btnRequest.getStyleClass().add("active");
                break;
            case "history":
                btnHistory.getStyleClass().add("active");
                break;
        }
    }

    @FXML
    private void handleLogout() {
        try {
            // Загружаем окно входа и заменяем текущую сцену
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            contentArea.getScene().setRoot(loader.load());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Универсальный метод загрузки FXML: загружает файл и применяет растягивание контента
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

    // Универсальный метод: загружает контент в contentArea и закрепляет его по всем сторонам AnchorPane
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

    // Загрузка профиля через универсальный метод loadFXML
    private void loadProfile() {
        loadFXML("profile.fxml");
    }
}