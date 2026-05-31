package org.example.management_communal_services;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OwnerController {

    // Поля для отображения контента в правой панели
    @FXML
    private AnchorPane contentArea;

    // Лейблы для отображения данных профиля
    @FXML
    private Label lblFullName;

    @FXML
    private Label lblAccountNumber;

    @FXML
    private Label lblAddress;

    // Лейблы для отображения времени и даты в боковой панели
    @FXML
    private Label lblTime;

    @FXML
    private Label lblDate;

    // Лейбл для отображения ФИО в верхней панели
    @FXML
    private Label lblOwnerFullName;

    // Кнопки бокового меню для подсветки активной
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

    // Кнопка "Данные собственника" в верхней панели
    @FXML
    private Button btnOwnerDetails;

    // ID и имя текущего пользователя
    private int currentOwnerId;
    private String currentOwnerName;

    // Таймер для обновления времени в интерфейсе
    private Timeline clockTimeline;

    // Метод для инициализации данных владельца после успешного входа
    // Устанавливает ID, загружает данные из БД и запускает таймер
    public void setCurrentOwner(int ownerId, String ownerName) {
        this.currentOwnerId = ownerId;
        this.currentOwnerName = ownerName;

        // Запускаем обновление времени
        startClock();

        // Загружаем данные владельца (включая ФИО) из БД
        loadOwnerData();

        // Проверяем и генерируем начисления при необходимости
        checkAndGenerateCharges();

        // Загружаем профиль (раздел "Главное")
        loadProfile();
    }

    // Инициализация контроллера: установка активной кнопки при старте
    @FXML
    public void initialize() {
        setActiveButton("profile");
    }

    // Запуск таймера для обновления времени и даты каждую секунду
    // Формат времени: ЧЧ:ММ, формат даты: ДД.ММ.ГГГГ
    private void startClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            if (lblTime != null) {
                lblTime.setText(now.format(timeFormatter));
            }
            if (lblDate != null) {
                lblDate.setText(now.format(dateFormatter));
            }
        }));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    // Остановка таймера при выходе из системы
    // Вызывается в handleLogout() для освобождения ресурсов
    public void stopClock() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
    }

    // Загрузка персональных данных владельца из базы данных
    // Заполняет лейблы ФИО, номер счёта и адрес
    private void loadOwnerData() {
        if (currentOwnerId == 0) return;

        String sql = "SELECT full_name, account_number, street, building, apartment_number, area " +
                "FROM Owners WHERE id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, currentOwnerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String fullName = rs.getString("full_name");

                // Устанавливаем ФИО в верхнюю панель
                if (lblOwnerFullName != null) {
                    lblOwnerFullName.setText(fullName);
                }

                // Устанавливаем данные в профиль
                if (lblFullName != null) lblFullName.setText(fullName);
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

    // Обработчик кнопки "Главное": загружает профиль и подсвечивает кнопку
    @FXML
    private void handleProfile() {
        setActiveButton("profile");
        loadProfile();
    }

    // Обработчик кнопки "Показания счётчиков": загружает окно с передачей показаний
    @FXML
    private void handleMeters() {
        setActiveButton("meters");
        loadMeters();
    }

    // Обработчик кнопки "Услуги": загружает таблицу доступных услуг
    @FXML
    private void handleServices() {
        setActiveButton("services");
        loadFXML("services.fxml");
    }

    // Обработчик кнопки "Подать заявку": загружает форму создания заявки
    // Передаёт ID владельца в контроллер заявки
    @FXML
    private void handleRequest() {
        setActiveButton("request");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("request.fxml"));
            Parent root = loader.load();

            // Передаём ID владельца в контроллер заявки
            RequestController requestController = loader.getController();
            requestController.setCurrentOwnerId(currentOwnerId);

            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Обработчик кнопки "История заявок": загружает таблицу заявок пользователя
    // Передаёт ID владельца в контроллер истории
    @FXML
    private void handleHistory() {
        setActiveButton("history");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("my-statement.fxml"));
            Parent root = loader.load();

            // Передаём ID владельца в контроллер истории
            HistoryController historyController = loader.getController();
            historyController.setCurrentOwnerId(currentOwnerId);

            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Обработчик кнопки "Данные собственника": открывает окно редактирования профиля
    // Подсвечивает кнопку как активную и загружает owner_details.fxml
    @FXML
    private void handleOwnerDetails() {
        setActiveButton("ownerDetails");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("owner_details.fxml"));
            Parent root = loader.load();

            // Передаём ID владельца в контроллер
            OwnerDetailsController detailsController = loader.getController();
            detailsController.setCurrentOwnerId(currentOwnerId);

            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Метод для подсветки активной кнопки меню
    // Снимает класс "active" со всех кнопок и добавляет нужной
    private void setActiveButton(String buttonName) {
        // Сбрасываем все кнопки (и боковой панели, и верхней)
        if (btnProfile != null) btnProfile.getStyleClass().remove("active");
        if (btnMeters != null) btnMeters.getStyleClass().remove("active");
        if (btnServices != null) btnServices.getStyleClass().remove("active");
        if (btnRequest != null) btnRequest.getStyleClass().remove("active");
        if (btnHistory != null) btnHistory.getStyleClass().remove("active");
        if (btnOwnerDetails != null) btnOwnerDetails.getStyleClass().remove("active");

        // Добавляем класс active выбранной кнопке
        switch (buttonName) {
            case "profile":
                if (btnProfile != null) btnProfile.getStyleClass().add("active");
                break;
            case "meters":
                if (btnMeters != null) btnMeters.getStyleClass().add("active");
                break;
            case "services":
                if (btnServices != null) btnServices.getStyleClass().add("active");
                break;
            case "request":
                if (btnRequest != null) btnRequest.getStyleClass().add("active");
                break;
            case "history":
                if (btnHistory != null) btnHistory.getStyleClass().add("active");
                break;
            case "ownerDetails":
                if (btnOwnerDetails != null) btnOwnerDetails.getStyleClass().add("active");
                break;
        }
    }

    // Обработчик кнопки "Выйти": завершает сессию и возвращает к окну входа
    @FXML
    private void handleLogout() {
        stopClock();  // Останавливаем таймер
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            contentArea.getScene().setRoot(loader.load());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Универсальный метод загрузки FXML-файлов в рабочую область
    // Загружает файл и применяет растягивание контента
    private void loadFXML(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();
            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Загрузка окна показаний счётчиков с передачей ID владельца
    // Используется в handleMeters()
    private void loadMeters() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("meters.fxml"));
            Parent root = loader.load();

            MetersController metersController = loader.getController();
            metersController.setCurrentOwnerId(currentOwnerId);

            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Универсальный метод: загружает контент в contentArea и закрепляет его
    // Если корневой элемент — AnchorPane, растягивает его на всю область
    private void loadContent(Parent root) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(root);

        if (root instanceof AnchorPane) {
            AnchorPane.setTopAnchor(root, 0.0);
            AnchorPane.setBottomAnchor(root, 0.0);
            AnchorPane.setLeftAnchor(root, 0.0);
            AnchorPane.setRightAnchor(root, 0.0);
        }
    }

    // метод для проверки и генерации начислений
    private void checkAndGenerateCharges() {
        // Проверяем, есть ли начисления за текущий месяц
        String checkSql = "SELECT COUNT(*) FROM Charges WHERE owner_id = ? AND month = ? AND year = ?";

        java.time.LocalDate now = java.time.LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkSql)) {

            pstmt.setInt(1, currentOwnerId);
            pstmt.setInt(2, currentMonth);
            pstmt.setInt(3, currentYear);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                // Начислений за текущий месяц нет, генерируем их
                System.out.println("Начислений за текущий месяц нет. Генерация...");
                ChargesGenerator.generateChargesForCurrentMonth();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка при проверке начислений: " + e.getMessage());
        }
    }

    // Загрузка профиля (раздел "Главное") с передачей ID владельца
    // Вызывается при входе и при выборе раздела "Главное"
    private void loadProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("profile.fxml"));
            Parent root = loader.load();

            ProfileController profileController = loader.getController();
            profileController.setCurrentOwnerId(currentOwnerId);

            loadContent(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}