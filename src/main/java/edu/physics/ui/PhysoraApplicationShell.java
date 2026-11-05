package edu.physics.ui;

import javafx.scene.layout.BorderPane;

final class PhysoraApplicationShell extends BorderPane {
    private final ProjectileSimulationController controller = new ProjectileSimulationController();

    PhysoraApplicationShell() {
        getStyleClass().add("physora-shell");
        setLeft(new ChapterRail());
        setCenter(new NormalBenchView(controller));
    }

    void dispose() {
        controller.dispose();
    }
}
