package org.example.management_communal_services;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;

public class OwnerController {

    @FXML
    private Pane contentArea;  // Правая панель для загрузки контента

    @FXML
    public void initialize() {
        // При загрузке окна сразу показываем "Личный кабинет"
        loadContent("profile.fxml");
    }

    // Универсальный метод для загрузки FXML в правую панель
    private void loadContent(String fxmlFile) {
        try {
            // Очищаем текущее содержимое
            contentArea.getChildren().clear();

            // Загружаем новый FXML файл
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent view = loader.load();

            // Добавляем загруженный вид в правую панель
            contentArea.getChildren().add(view);

            // Растягиваем на всю доступную область
            if (view instanceof javafx.scene.layout.Region) {
                javafx.scene.layout.Region region = (javafx.scene.layout.Region) view;
                region.prefWidthProperty().bind(contentArea.widthProperty());
                region.prefHeightProperty().bind(contentArea.heightProperty());
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка загрузки файла: " + fxmlFile);

            // Показываем сообщение об ошибке
            Label errorLabel = new Label("Ошибка загрузки раздела");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
            contentArea.getChildren().add(errorLabel);
        }
    }

    //  ОБРАБОТЧИКИ КНОПОК МЕНЮ

    @FXML
    private void handleProfile() {
        System.out.println("→ Загрузка: Личный кабинет");
        loadContent("profile.fxml");
    }

    @FXML
    private void handleMeters() {
        System.out.println("→ Загрузка: Показания счётчиков");
        loadContent("meters.fxml");
    }

    @FXML
    private void handleServices() {
        System.out.println("→ Загрузка: Услуги");
        loadContent("services.fxml");
    }

    @FXML
    private void handleRequest() {
        System.out.println("→ Загрузка: Подать заявку");
        loadContent("request.fxml");
    }

    @FXML
    private void handleHistory() {
        System.out.println("→ Загрузка: История заявок");
        loadContent("my-statement.fxml");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            System.out.println("→ Выход из системы");

            // Загружаем окно входа
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Вход в систему");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка при выходе из системы");
        }
    }
}