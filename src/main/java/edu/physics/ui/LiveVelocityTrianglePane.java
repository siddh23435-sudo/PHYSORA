package edu.physics.ui;

import edu.physics.relative.TwoProjectilePursuitModel;
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
 * Dedicated visual widget demonstrating dynamic vector addition:
 * 
 * <pre>
 *   v_B(t) = v_A(t) + v_{B/A}
 * </pre>
 * 
 * Visually proves that while ground velocities curve downward under gravity,
 * their vector difference v_{B/A} remains strictly constant over time.
 */
public final class LiveVelocityTrianglePane extends BorderPane {

    private static final Color COLOR_VA = Color.web("#2453d4"); // Cobalt Blue
    private static final Color COLOR_VB = Color.web("#e56843"); // Flame Orange
    private static final Color COLOR_VBA = Color.web("#059669"); // Emerald Green

    private final Canvas canvas = new Canvas(340, 180);

    // Formatted readouts: Vector name, components, magnitude, and constancy badge
    private final Label valVAComponents = new Label();
    private final Label valVAMagnitude = new Label();

    private final Label valVBComponents = new Label();
    private final Label valVBMagnitude = new Label();

    private final Label valVBAComponents = new Label();
    private final Label valVBAMagnitude = new Label();

    public LiveVelocityTrianglePane() {
        setId("liveVelocityTrianglePane");
        setStyle(
                "-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(10, 10, 10, 10));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
        title.setStyle("-fx-text-fill: #142e4b;");

        Label formula = new Label("v⃗_B(t) = v⃗_A(t) + v⃗_B/A");
        formula.setFont(Font.font("Consolas", FontWeight.BOLD, 11.0));
        formula.setStyle(
                "-fx-background-color: #e2e8f0; -fx-padding: 2 6 2 6; -fx-background-radius: 4px; -fx-text-fill: #0f172a;");
        formula.setMinWidth(Region.USE_PREF_SIZE);

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

        // 3. Structured Vector Readouts Table (3 clean columns, no truncation)
        VBox bottom = buildReadoutsTable();
        setBottom(bottom);
    }

    public void update(TwoProjectilePursuitModel model, double time) {
        if (model == null)
            return;

        Vector2D vA = model.velocityA(time);
        Vector2D vB = model.velocityB(time);
        Vector2D vBA = model.relativeVelocityBA(time);

        // Update readouts cleanly without long text strings that cause truncation
        valVAComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vA.x(), vA.y()));
        valVAMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vA.magnitude()));

        valVBComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vB.x(), vB.y()));
        valVBMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vB.magnitude()));

        valVBAComponents.setText(String.format(Locale.ROOT, "(%.1f, %.1f)", vBA.x(), vBA.y()));
        valVBAMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vBA.magnitude()));

        // Draw Triangle Canvas
        drawTriangle(vA, vB, vBA);
    }

    private void drawTriangle(Vector2D vA, Vector2D vB, Vector2D vBA) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#f5f1e5"));
        gc.fillRect(0, 0, w, h);

        // Grid dots
        gc.setFill(Color.web("#d1d5db"));
        for (double gx = 15; gx < w; gx += 20) {
            for (double gy = 15; gy < h; gy += 20) {
                gc.fillOval(gx, gy, 1.8, 1.8);
            }
        }

        // Bounding box of vector triangle:
        // Origin O = (0, 0)
        // Tip A = vA
        // Tip B = vA + vBA = vB
        double minX = Math.min(0.0, Math.min(vA.x(), vB.x()));
        double maxX = Math.max(0.0, Math.max(vA.x(), vB.x()));
        double minY = Math.min(0.0, Math.min(vA.y(), vB.y()));
        double maxY = Math.max(0.0, Math.max(vA.y(), vB.y()));

        double spanX = Math.max(25.0, maxX - minX);
        double spanY = Math.max(25.0, maxY - minY);
        double padding = 1.45;

        double scale = Math.min((w - 50) / (spanX * padding), (h - 50) / (spanY * padding));
        double originScreenX = w / 2.0 - ((minX + maxX) / 2.0) * scale;
        double originScreenY = h / 2.0 + ((minY + maxY) / 2.0) * scale;

        // Draw Origin Cross
        gc.setStroke(Color.web("#94a3b8"));
        gc.setLineWidth(1.2);
        gc.strokeLine(originScreenX - 8, originScreenY, originScreenX + 8, originScreenY);
        gc.strokeLine(originScreenX, originScreenY - 8, originScreenX, originScreenY + 8);
        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        gc.fillText("O", originScreenX - 12, originScreenY + 14);

        // Point A screen coordinate = O + vA * scale
        double ax = originScreenX + vA.x() * scale;
        double ay = originScreenY - vA.y() * scale;

        // Point B screen coordinate = O + vB * scale = Point A + vBA * scale
        double bx = originScreenX + vB.x() * scale;
        double by = originScreenY - vB.y() * scale;

        // 1. Draw Vector v_A: O -> Point A
        drawArrow(gc, originScreenX, originScreenY, ax, ay, COLOR_VA, "v_A(t) [Ground S]");

        // 2. Draw Vector v_{B/A}: Point A -> Point B (Head-to-tail addition!)
        drawArrow(gc, ax, ay, bx, by, COLOR_VBA, "v_{B/A} [Observed by A]");

        // 3. Draw Resultant Vector v_B: O -> Point B (Closing the triangle)
        drawArrow(gc, originScreenX, originScreenY, bx, by, COLOR_VB, "v_B(t) [Ground S]");
    }

    private void drawArrow(GraphicsContext gc, double sx, double sy, double ex, double ey, Color color, String label) {
        gc.setStroke(color);
        gc.setLineWidth(2.6);
        gc.strokeLine(sx, sy, ex, ey);

        double angle = Math.atan2(ey - sy, ex - sx);
        double arrowHeadLen = 9.0;

        gc.setFill(color);
        gc.fillPolygon(
                new double[] { ex, ex - arrowHeadLen * Math.cos(angle - Math.PI / 6),
                        ex - arrowHeadLen * Math.cos(angle + Math.PI / 6) },
                new double[] { ey, ey - arrowHeadLen * Math.sin(angle - Math.PI / 6),
                        ey - arrowHeadLen * Math.sin(angle + Math.PI / 6) },
                3);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.fillText(label, ex + 7 * Math.cos(angle), ey + 7 * Math.sin(angle));
    }

    private VBox buildReadoutsTable() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        grid.setPadding(new Insets(3, 0, 4, 0));

        // Header Row
        Label hVector = new Label("QUANTITY");
        hVector.getStyleClass().add("metric-name");
        hVector.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        hVector.setStyle("-fx-text-fill: #64748b;");

        Label hFrame = new Label("FRAME");
        hFrame.getStyleClass().add("metric-frame");
        hFrame.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        hFrame.setStyle("-fx-text-fill: #64748b;");

        Label hComp = new Label("(vx, vy)");
        hComp.getStyleClass().add("metric-name");
        hComp.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        hComp.setStyle("-fx-text-fill: #64748b;");

        Label hSpeed = new Label("SPEED & STATUS");
        hSpeed.getStyleClass().add("metric-name");
        hSpeed.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        hSpeed.setStyle("-fx-text-fill: #64748b;");

        grid.add(hVector, 0, 0);
        grid.add(hFrame, 1, 0);
        grid.add(hComp, 2, 0);
        grid.add(hSpeed, 3, 0);

        // Row 1: v_A(t)
        Label lblA = new Label("v_A(t)");
        lblA.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        lblA.getStyleClass().add("vector-cobalt");
        lblA.setStyle("-fx-text-fill: #2453d4;");

        Label frameA = new Label("Ground (S)");
        frameA.getStyleClass().add("metric-frame");
        frameA.setFont(Font.font("Segoe UI", 9.5));
        frameA.setStyle("-fx-text-fill: #64748b;");

        valVAComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        valVAComponents.getStyleClass().add("vector-cobalt");
        valVAComponents.setStyle("-fx-text-fill: #2453d4;");

        valVAMagnitude.setFont(Font.font("Consolas", FontWeight.BOLD, 10.0));
        valVAMagnitude.getStyleClass().add("vector-cobalt");
        valVAMagnitude.setStyle("-fx-text-fill: #2453d4;");

        grid.add(lblA, 0, 1);
        grid.add(frameA, 1, 1);
        grid.add(valVAComponents, 2, 1);
        grid.add(valVAMagnitude, 3, 1);

        // Row 2: v_B(t)
        Label lblB = new Label("v_B(t)");
        lblB.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        lblB.getStyleClass().add("vector-orange");
        lblB.setStyle("-fx-text-fill: #c2410c;");

        Label frameB = new Label("Ground (S)");
        frameB.getStyleClass().add("metric-frame");
        frameB.setFont(Font.font("Segoe UI", 9.5));
        frameB.setStyle("-fx-text-fill: #64748b;");

        valVBComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        valVBComponents.getStyleClass().add("vector-orange");
        valVBComponents.setStyle("-fx-text-fill: #c2410c;");

        valVBMagnitude.setFont(Font.font("Consolas", FontWeight.BOLD, 10.0));
        valVBMagnitude.getStyleClass().add("vector-orange");
        valVBMagnitude.setStyle("-fx-text-fill: #c2410c;");

        grid.add(lblB, 0, 2);
        grid.add(frameB, 1, 2);
        grid.add(valVBComponents, 2, 2);
        grid.add(valVBMagnitude, 3, 2);

        // Row 3: v_{B/A}
        Label lblBA = new Label("v_{B/A}");
        lblBA.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        lblBA.getStyleClass().add("vector-emerald");
        lblBA.setStyle("-fx-text-fill: #047857;");

        Label frameBA = new Label("Observer A (S_A)");
        frameBA.getStyleClass().add("metric-frame");
        frameBA.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        frameBA.setStyle("-fx-text-fill: #047857;");

        valVBAComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        valVBAComponents.getStyleClass().add("vector-emerald");
        valVBAComponents.setStyle("-fx-text-fill: #047857;");

        Label constBadge = new Label("CONSTANT");
        constBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 9.0));
        constBadge.setStyle(
                "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-padding: 1 4 1 4; -fx-background-radius: 3px; -fx-border-color: #a7f3d0; -fx-border-radius: 3px;");

        HBox vbaMagBox = new HBox(4, valVBAMagnitude, constBadge);
        vbaMagBox.setAlignment(Pos.CENTER_LEFT);
        valVBAMagnitude.setFont(Font.font("Consolas", FontWeight.BOLD, 10.0));
        valVBAMagnitude.getStyleClass().add("vector-emerald");
        valVBAMagnitude.setStyle("-fx-text-fill: #047857;");

        grid.add(lblBA, 0, 3);
        grid.add(frameBA, 1, 3);
        grid.add(valVBAComponents, 2, 3);
        grid.add(vbaMagBox, 3, 3);

        // Explanatory Note below grid (Fictitious acceleration & Relative invariance)
        Label noteTitle = new Label("PHYSICS INSIGHT: WHY RELATIVE VELOCITY IS INVARIANT");
        noteTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        noteTitle.setStyle("-fx-text-fill: #1e293b;");

        Label noteBody = new Label(
                "• Gravitational Equality: Both projectiles accelerate under identical downward gravity: a_A = a_B = -g j.\n" +
                "• Fictitious Force Balance: In Observer A's accelerating frame (S_A), a fictitious acceleration (+g j) balances gravity: a_eff = (-g j) + (+g j) = 0.\n" +
                "• Rectilinear Invariance: Relative acceleration a_{B/A} = a_B - a_A = 0, so relative velocity v_{B/A} is strictly constant in time.");
        noteBody.setFont(Font.font("Segoe UI", 9.5));
        noteBody.setStyle("-fx-text-fill: #334155; -fx-line-spacing: 2px;");

        VBox noteBox = new VBox(2, noteTitle, noteBody);
        noteBox.setStyle(
                "-fx-background-color: #f8fafc; -fx-padding: 6 8 6 8; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

        VBox container = new VBox(4, grid, noteBox);
        return container;
    }
}