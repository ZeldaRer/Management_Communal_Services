module org.example.management_communal_services {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.sql;
    requires org.apache.poi.ooxml;

    opens org.example.management_communal_services to javafx.fxml;
    exports org.example.management_communal_services;
}