package edu.physics.ui;

import javafx.scene.layout.BorderPane;

final class PhysoraApplicationShell extends BorderPane {
    private final ProjectileSimulationController controller = new ProjectileSimulationController();
    private final NormalBenchView normalBenchView = new NormalBenchView(controller);

    PhysoraApplicationShell() {
        getStyleClass().add("physora-shell");
        setLeft(new ChapterRail());
        setCenter(normalBenchView);
    }

    void dispose() {
        normalBenchView.dispose();
        controller.dispose();
    }

    ProjectileSimulationController controller() { return controller; }
}
