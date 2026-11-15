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
 * <pre>
 *   v_B(t) = v_A(t) + v_{B/A}
 * </pre>
 * Visually proves that while ground velocities curve downward under gravity,
 * their vector difference v_{B/A} remains strictly constant over time.
 */
public final class LiveVelocityTrianglePane extends BorderPane {

    private static final Color COLOR_VA = Color.web("#2453d4");   // Cobalt Blue
    private static final Color COLOR_VB = Color.web("#e56843");   // Flame Orange
    private static final Color COLOR_VBA = Color.web("#059669");  // Emerald Green

    private final Canvas canvas = new Canvas(390, 215);

    // Formatted readouts: Vector name, components, magnitude, and constancy badge
    private final Label valVAComponents = new Label();
    private final Label valVAMagnitude = new Label();

    private final Label valVBComponents = new Label();
    private final Label valVBMagnitude = new Label();

    private final Label valVBAComponents = new Label();
    private final Label valVBAMagnitude = new Label();

    public LiveVelocityTrianglePane() {
        setId("liveVelocityTrianglePane");
        setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(12, 14, 12, 14));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        title.setTextFill(PhysoraTheme.INK);

        Label formula = new Label("v_B(t) = v_A(t) + v_{B/A}");
        formula.setFont(Font.font("Consolas", FontWeight.BOLD, 12.5));
        formula.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-text-fill: #0f172a;");

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
        if (model == null) return;

        Vector2D vA = model.velocityA(time);
        Vector2D vB = model.velocityB(time);
        Vector2D vBA = model.relativeVelocityBA(time);

        // Update readouts cleanly without long text strings that cause truncation
        valVAComponents.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f) m/s", vA.x(), vA.y()));
        valVAMagnitude.setText(String.format(Locale.ROOT, "|v_A| = %.1f m/s", vA.magnitude()));

        valVBComponents.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f) m/s", vB.x(), vB.y()));
        valVBMagnitude.setText(String.format(Locale.ROOT, "|v_B| = %.1f m/s", vB.magnitude()));

        valVBAComponents.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f) m/s", vBA.x(), vBA.y()));
        valVBAMagnitude.setText(String.format(Locale.ROOT, "|v_{B/A}| = %.1f m/s", vBA.magnitude()));

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
        drawArrow(gc, originScreenX, originScreenY, ax, ay, COLOR_VA, "v_A(t)");

        // 2. Draw Vector v_{B/A}: Point A -> Point B (Head-to-tail addition!)
        drawArrow(gc, ax, ay, bx, by, COLOR_VBA, "v_{B/A} (const)");

        // 3. Draw Resultant Vector v_B: O -> Point B (Closing the triangle)
        drawArrow(gc, originScreenX, originScreenY, bx, by, COLOR_VB, "v_B(t) [resultant]");
    }

    private void drawArrow(GraphicsContext gc, double sx, double sy, double ex, double ey, Color color, String label) {
        gc.setStroke(color);
        gc.setLineWidth(2.6);
        gc.strokeLine(sx, sy, ex, ey);

        double angle = Math.atan2(ey - sy, ex - sx);
        double arrowHeadLen = 9.0;

        gc.setFill(color);
        gc.fillPolygon(
                new double[]{ex, ex - arrowHeadLen * Math.cos(angle - Math.PI / 6), ex - arrowHeadLen * Math.cos(angle + Math.PI / 6)},
                new double[]{ey, ey - arrowHeadLen * Math.sin(angle - Math.PI / 6), ey - arrowHeadLen * Math.sin(angle + Math.PI / 6)},
                3
        );

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        gc.fillText(label, ex + 7 * Math.cos(angle), ey + 7 * Math.sin(angle));
    }

    private VBox buildReadoutsTable() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        grid.setPadding(new Insets(4, 0, 4, 0));

        // Row 0: v_A(t)
        Label lblA = new Label("v_A(t):");
        lblA.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        lblA.setTextFill(COLOR_VA);
        lblA.setMinWidth(46);

        valVAComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valVAComponents.setTextFill(COLOR_VA);
        valVAComponents.setMinWidth(115);

        valVAMagnitude.setFont(Font.font("Consolas", 11));
        valVAMagnitude.setTextFill(COLOR_VA);

        grid.add(lblA, 0, 0);
        grid.add(valVAComponents, 1, 0);
        grid.add(valVAMagnitude, 2, 0);

        // Row 1: v_B(t)
        Label lblB = new Label("v_B(t):");
        lblB.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        lblB.setTextFill(COLOR_VB);
        lblB.setMinWidth(46);

        valVBComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valVBComponents.setTextFill(COLOR_VB);
        valVBComponents.setMinWidth(115);

        valVBMagnitude.setFont(Font.font("Consolas", 11));
        valVBMagnitude.setTextFill(COLOR_VB);

        grid.add(lblB, 0, 1);
        grid.add(valVBComponents, 1, 1);
        grid.add(valVBMagnitude, 2, 1);

        // Row 2: v_{B/A}
        Label lblBA = new Label("v_{B/A}:");
        lblBA.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        lblBA.setTextFill(COLOR_VBA);
        lblBA.setMinWidth(46);

        valVBAComponents.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valVBAComponents.setTextFill(COLOR_VBA);
        valVBAComponents.setMinWidth(115);

        Label constBadge = new Label("CONSTANT (a_rel = 0)");
        constBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 9.5));
        constBadge.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-padding: 1 5 1 5; -fx-background-radius: 3px;");

        HBox vbaMagBox = new HBox(6, valVBAMagnitude, constBadge);
        vbaMagBox.setAlignment(Pos.CENTER_LEFT);
        valVBAMagnitude.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valVBAMagnitude.setTextFill(COLOR_VBA);

        grid.add(lblBA, 0, 2);
        grid.add(valVBAComponents, 1, 2);
        grid.add(vbaMagBox, 2, 2);

        // Explanatory Note below grid
        Label note = new Label("dv_A/dt = dv_B/dt = -g j  \u27F9  d(v_{B/A})/dt = 0 (Relative velocity invariant)");
        note.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-text-fill: #334155; " +
                "-fx-background-color: #f1f5f9; -fx-padding: 4 8 4 8; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

        VBox container = new VBox(6, grid, note);
        return container;
    }
}
