package edu.physics.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class ProjectileSimulationView extends BorderPane {
    private final ProjectileSimulationController controller;
    private final ProjectileCanvasRenderer renderer = new ProjectileCanvasRenderer();
    private final Canvas canvas = renderer.canvas();

    ProjectileSimulationView(ProjectileSimulationController controller) {
        this.controller = controller;
        setPadding(new Insets(10, 16, 8, 16));
        getStyleClass().add("simulation-view");

        Label heading = new Label("THE PATH");
        heading.getStyleClass().add("simulation-heading");
        Label note = new Label("Position and velocity are sampled from the same model.");
        note.getStyleClass().add("muted-copy");
        VBox title = new VBox(4, heading, note);

        Label xLegend = legend("—", "trajectory", "legend-cobalt");
        Label vxLegend = legend("+x", "velocity component", "legend-blue");
        Label vyLegend = legend("+y", "velocity component", "legend-orange");
        HBox legend = new HBox(17, xLegend, vxLegend, vyLegend);
        legend.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(title, legend);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        setTop(top);

        StackPane canvasHost = new StackPane(canvas);
        canvasHost.setMinSize(340, 180);
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        canvasHost.getStyleClass().add("plot-surface");
        setCenter(canvasHost);
        BorderPane.setMargin(canvasHost, new Insets(10, 0, 8, 0));

        Label noteLine = new Label("Horizontal velocity stays constant; gravity changes the vertical component.");
        noteLine.getStyleClass().add("simulation-caption");
        setBottom(noteLine);

        canvas.widthProperty().addListener(obs -> redraw());
        canvas.heightProperty().addListener(obs -> redraw());
        controller.modelProperty().addListener((obs, oldModel, newModel) -> redraw());
        controller.simulationTimeProperty().addListener((obs, oldTime, newTime) -> redraw());
        redraw();
    }

    void setShowVelocityComponents(boolean show) {
        renderer.setShowVelocityComponents(show);
        redraw();
    }

    private void redraw() {
        if (canvas.getWidth() > 0 && canvas.getHeight() > 0) {
            renderer.render(controller.model(), controller.simulationTimeProperty().get(),
                    controller.timelineDuration());
        }
    }

    private static Label legend(String symbol, String label, String styleClass) {
        Label node = new Label(symbol + "  " + label);
        node.getStyleClass().addAll("legend-item", styleClass);
        return node;
    }
}
