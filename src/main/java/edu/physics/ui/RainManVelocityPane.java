package edu.physics.ui;

import edu.physics.relative.RainManSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Dedicated visual widget demonstrating dynamic vector addition for Rain-Man relative motion:
 * <pre>
 *   v_r = v_{r/m} + v_m
 * </pre>
 * Visually displays the ground rain velocity vector, person velocity vector, and apparent
 * rain velocity vector, forming the classic Galilean relative velocity triangle.
 */
public final class RainManVelocityPane extends BorderPane {

    private static final Color COLOR_VR = Color.web("#d97706");   // Amber / Gold (Ground Rain)
    private static final Color COLOR_VM = Color.web("#2453d4");   // Cobalt Blue (Person)
    private static final Color COLOR_VRM = Color.web("#059669");  // Emerald Green (Apparent Rain)

    private final Canvas canvas = new Canvas(340, 180);

    // Formatted readouts
    private final Label valVRComponents = new Label();
    private final Label valVRMagnitude = new Label();

    private final Label valVMComponents = new Label();
    private final Label valVMMagnitude = new Label();

    private final Label valVRMComponents = new Label();
    private final Label valVRMMagnitude = new Label();

    public RainManVelocityPane() {
        setId("rainManVelocityPane");
        setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(10, 10, 10, 10));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
        title.setTextFill(PhysoraTheme.INK);

        Label formula = new Label("v_r = v_{r/m} + v_m");
        formula.setFont(Font.font("Consolas", FontWeight.BOLD, 11.5));
        formula.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 2 6 2 6; -fx-background-radius: 4px; -fx-text-fill: #0f172a;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, title, spacer, formula);
        header.setAlignment(Pos.CENTER_LEFT);
        setTop(header);

        // 2. Center Vector Canvas
        VBox canvasContainer = new VBox(canvas);
        canvasContainer.setAlignment(Pos.CENTER);
        canvasContainer.setPadding(new Insets(6, 0, 6, 0));
        setCenter(canvasContainer);

        // 3. Structured Vector Readouts Table
        VBox bottom = buildReadoutsTable();
        setBottom(bottom);
    }

    public void update(RainManSimulationModel model) {
        if (model == null) return;

        Vector2D vr = model.rainVelocityGround();
        Vector2D vm = model.personVelocityGround();
        Vector2D vrm = model.apparentRainVelocity();

        valVRComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vr.x(), vr.y()));
        valVRMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vr.magnitude()));

        valVMComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vm.x(), vm.y()));
        valVMMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vm.magnitude()));

        valVRMComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vrm.x(), vrm.y()));
        valVRMMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vrm.magnitude()));

        drawTriangle(vr, vm, vrm);
    }

    private void drawTriangle(Vector2D vr, Vector2D vm, Vector2D vrm) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        // Background
        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // Grid lines
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(0.8);
        for (double x = 20; x < w; x += 25) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = 20; y < h; y += 25) {
            gc.strokeLine(0, y, w, y);
        }

        // Determine bounding box of the triangle:
        // Origin O = (0, 0)
        // vm tip = (vm.x, 0)
        // vr tip = (vr.x, vr.y)
        // Notice vrm connects vm tip to vr tip: vr = vm + vrm.
        double minX = Math.min(0.0, Math.min(vm.x(), vr.x()));
        double maxX = Math.max(0.0, Math.max(vm.x(), vr.x()));
        double minY = Math.min(0.0, Math.min(vm.y(), vr.y()));
        double maxY = Math.max(0.0, Math.max(vm.y(), vr.y()));

        double spanX = Math.max(4.0, maxX - minX);
        double spanY = Math.max(4.0, maxY - minY);

        double margin = 32.0;
        double scaleX = (w - 2.0 * margin) / spanX;
        double scaleY = (h - 2.0 * margin) / spanY;
        double scale = Math.min(scaleX, scaleY);

        // Center origin
        double centerX = margin - minX * scale + (w - 2.0 * margin - spanX * scale) / 2.0;
        // In screen coordinates y increases downward. Physics y increases upward.
        double centerY = margin + maxY * scale + (h - 2.0 * margin - spanY * scale) / 2.0;

        double ox = centerX;
        double oy = centerY;

        double vmX = ox + vm.x() * scale;
        double vmY = oy - vm.y() * scale;

        double vrX = ox + vr.x() * scale;
        double vrY = oy - vr.y() * scale;

        // Draw axis lines through origin
        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, oy, w, oy);
        gc.strokeLine(ox, 0, ox, h);

        // 1. Vector v_m (Person velocity: from O to vm tip)
        drawArrow(gc, ox, oy, vmX, vmY, COLOR_VM, "v_m", 2.0, 0, 14);

        // 2. Vector v_{r/m} (Apparent rain: from vm tip to vr tip)
        drawArrow(gc, vmX, vmY, vrX, vrY, COLOR_VRM, "v_{r/m}", 2.2, -18, -4);

        // 3. Vector v_r (Ground rain: from O to vr tip)
        drawArrow(gc, ox, oy, vrX, vrY, COLOR_VR, "v_r", 2.2, 8, -4);

        // Origin marker
        gc.setFill(Color.web("#0f172a"));
        gc.fillOval(ox - 3, oy - 3, 6, 6);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        gc.fillText("O", ox - 10, oy + 12);
    }

    private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2,
                           Color color, String label, double lineWidth, double labelDx, double labelDy) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);

        gc.setStroke(color);
        gc.setLineWidth(lineWidth);
        gc.strokeLine(x1, y1, x2, y2);

        if (len > 3.0) {
            double angle = Math.atan2(dy, dx);
            double arrowHeadLen = 9.0;
            double arrowAngle = Math.PI / 6.0;

            double ax1 = x2 - arrowHeadLen * Math.cos(angle - arrowAngle);
            double ay1 = y2 - arrowHeadLen * Math.sin(angle - arrowAngle);
            double ax2 = x2 - arrowHeadLen * Math.cos(angle + arrowAngle);
            double ay2 = y2 - arrowHeadLen * Math.sin(angle + arrowAngle);

            gc.setFill(color);
            gc.fillPolygon(new double[]{x2, ax1, ax2}, new double[]{y2, ay1, ay2}, 3);
        }

        // Label
        gc.setFill(color);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        double mx = (x1 + x2) / 2.0 + labelDx;
        double my = (y1 + y2) / 2.0 + labelDy;
        gc.fillText(label, mx, my);
    }

    private VBox buildReadoutsTable() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(3);
        grid.setAlignment(Pos.CENTER);

        // Column 1: Ground Rain
        VBox col1 = buildVectorBadge("v_r (Ground)", COLOR_VR, valVRComponents, valVRMagnitude);
        // Column 2: Person Velocity
        VBox col2 = buildVectorBadge("v_m (Person)", COLOR_VM, valVMComponents, valVMMagnitude);
        // Column 3: Apparent Rain
        VBox col3 = buildVectorBadge("v_{r/m} (Apparent)", COLOR_VRM, valVRMComponents, valVRMMagnitude);

        grid.add(col1, 0, 0);
        grid.add(col2, 1, 0);
        grid.add(col3, 2, 0);

        GridPane.setHgrow(col1, Priority.ALWAYS);
        GridPane.setHgrow(col2, Priority.ALWAYS);
        GridPane.setHgrow(col3, Priority.ALWAYS);

        VBox container = new VBox(grid);
        container.setPadding(new Insets(4, 0, 0, 0));
        return container;
    }

    private VBox buildVectorBadge(String name, Color accent, Label compLabel, Label magLabel) {
        Label nameLbl = new Label(name);
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
        nameLbl.setTextFill(accent);

        compLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        compLabel.setTextFill(PhysoraTheme.INK);

        magLabel.setFont(Font.font("Consolas", 10.0));
        magLabel.setTextFill(PhysoraTheme.MUTED);

        VBox box = new VBox(1, nameLbl, compLabel, magLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 4 6 4 6; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;");
        return box;
    }
}
