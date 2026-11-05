package edu.physics.ui;

import javafx.scene.layout.BorderPane;

final class KinetikApplicationShell extends BorderPane {
    private final ProjectileSimulationController controller = new ProjectileSimulationController();

    KinetikApplicationShell() {
        getStyleClass().add("kinetik-shell");
        setLeft(new ChapterRail());
        setCenter(new NormalBenchView(controller));
    }

    void dispose() {
        controller.dispose();
    }
}
