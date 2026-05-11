module org.example.management_communal_services {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.sql;

    opens org.example.management_communal_services to javafx.fxml;
    exports org.example.management_communal_services;
}