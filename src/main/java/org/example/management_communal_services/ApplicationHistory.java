package org.example.management_communal_services;

// Модель данных для истории заявок на ремонт

public class ApplicationHistory {
    private int id;
    private String date;
    private String description;
    private String status;

    public ApplicationHistory(int id, String date, String description, String status) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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