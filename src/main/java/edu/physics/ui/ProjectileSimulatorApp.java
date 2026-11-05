package edu.physics.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ProjectileSimulatorApp extends Application {
    @Override
    public void start(Stage stage) {
        KinetikApplicationShell shell = new KinetikApplicationShell();
        Scene scene = new Scene(shell, 1440, 810);
        var stylesheet = getClass().getResource("/edu/physics/ui/kinetik.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("KINETIK — Physics, in motion");
        stage.setMinWidth(1120);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnHidden(event -> shell.dispose());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
