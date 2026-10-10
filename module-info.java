module JCKC_App {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.web; 
    requires transitive javafx.graphics;
    requires java.sql;
    requires jbcrypt;
    requires jakarta.mail;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.apache.xmlbeans;
    requires org.apache.commons.io;
    requires org.apache.commons.compress;
    
    exports app;
    opens app.views to javafx.fxml;
}
