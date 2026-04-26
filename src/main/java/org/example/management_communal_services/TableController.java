package org.example.management_communal_services;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;

public class TableController {

    @FXML
    private TableView<?> tableView;

    @FXML
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }
}
