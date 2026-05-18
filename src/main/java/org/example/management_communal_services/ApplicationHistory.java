package org.example.management_communal_services;

// Модель данных для истории заявок на ремонт
public class ApplicationHistory {
    private int rowNum;  // Порядковый номер для пользователя
    private String category;  // Категория услуги
    private String serviceName;  // Название услуги
    private String date;
    private String description;
    private String status;

    public ApplicationHistory(int rowNum, String category, String serviceName,
                              String date, String description, String status) {
        this.rowNum = rowNum;
        this.category = category;
        this.serviceName = serviceName;
        this.date = date;
        this.description = description;
        this.status = status;
    }

    public int getRowNum() {
        return rowNum;
    }

    public void setRowNum(int rowNum) {
        this.rowNum = rowNum;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}