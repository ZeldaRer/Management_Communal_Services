module org.example.management_communal_services {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.sql;
    requires org.apache.poi.ooxml;

    opens org.example.management_communal_services.controllers to javafx.fxml;
    exports org.example.management_communal_services.controllers;
    exports org.example.management_communal_services.controllers.owner;
    opens org.example.management_communal_services.controllers.owner to javafx.fxml;
    exports org.example.management_communal_services.controllers.auth;
    opens org.example.management_communal_services.controllers.auth to javafx.fxml;
    exports org.example.management_communal_services.utils;
    opens org.example.management_communal_services.utils to javafx.fxml;
    exports org.example.management_communal_services.models;
    opens org.example.management_communal_services.models to javafx.fxml;
}