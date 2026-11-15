package edu.physics.ui;

import edu.physics.relative.RiverBoatCrossingType;
import edu.physics.relative.RiverBoatResult;
import edu.physics.relative.RiverBoatSimulationModel;
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
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Dedicated visual widget demonstrating head-to-tail vector addition for river-boat motion:
 * <pre>
 *   v_{b/g} = v_{b/r} + u
 * </pre>
 * Visually confirms how steering angle and river flow compose to form the resultant ground velocity.
 */
public final class RiverBoatVelocityPane extends BorderPane {

    private static final Color COLOR_V_REL = Color.web("#059669");    // Emerald Green
    private static final Color COLOR_U = Color.web("#e56843");        // Flame Orange
    private static final Color COLOR_V_GROUND = Color.web("#2453d4"); // Cobalt Blue

    private final Canvas canvas = new Canvas(390, 215);

    // Formatted readouts: Vector name, components, magnitude, and status badge
    private final Label valVRelComponents = new Label();
    private final Label valVRelMagnitude = new Label();
    private final Label valVRelBadge = new Label();

    private final Label valUComponents = new Label();
    private final Label valUMagnitude = new Label();
    private final Label valUBadge = new Label();

    private final Label valVGroundComponents = new Label();
    private final Label valVGroundMagnitude = new Label();
    private final Label valVGroundBadge = new Label();

    private final Label insightCard = new Label();

    public RiverBoatVelocityPane() {
        setId("riverBoatVelocityPane");
        setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(12, 14, 12, 14));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        title.setTextFill(PhysoraTheme.INK);

        Label formula = new Label("v_b/g = v_b/r + u");
        formula.setFont(Font.font("Consolas", FontWeight.BOLD, 12.0));
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

        // 3. Structured Vector Readouts Table & Educational Card
        VBox bottom = buildBottomArea();
        setBottom(bottom);
    }

    public void update(RiverBoatSimulationModel model) {
        if (model == null) {
            return;
        }

        Vector2D vRel = model.velocityBoatRelative();
        Vector2D u = model.velocityRiverGround();
        Vector2D vGround = model.velocityBoatGround();
        RiverBoatResult result = model.analyticalResult();

        // 1. Update text readouts
        valVRelComponents.setText(String.format(Locale.US, "(%.2f, %.2f) m/s", vRel.x(), vRel.y()));
        valVRelMagnitude.setText(String.format(Locale.US, "%.2f m/s", model.boatSpeed()));
        valVRelBadge.setText(String.format(Locale.US, "θ = %.1f° upstream", model.headingAngleDeg()));

        valUComponents.setText(String.format(Locale.US, "(%.2f, 0.00) m/s", u.x()));
        valUMagnitude.setText(String.format(Locale.US, "%.2f m/s", model.riverSpeed()));
        valUBadge.setText("downstream (+x)");

        valVGroundComponents.setText(String.format(Locale.US, "(%.2f, %.2f) m/s", vGround.x(), vGround.y()));
        valVGroundMagnitude.setText(String.format(Locale.US, "%.2f m/s", vGround.magnitude()));

        double groundAngleDeg = Math.toDegrees(Math.atan2(vGround.y(), vGround.x()));
        valVGroundBadge.setText(String.format(Locale.US, "ground path (%.1f°)", groundAngleDeg));

        // 2. Update educational insight card
        updateInsightCard(result, model);

        // 3. Redraw vector triangle canvas
        renderTriangleCanvas(vRel, u, vGround);
    }

    private void updateInsightCard(RiverBoatResult result, RiverBoatSimulationModel model) {
        RiverBoatCrossingType type = result.crossingType();
        switch (type) {
            case SHORTEST_TIME -> {
                insightCard.setText("SHORTEST TIME (θ = 0°): Directing the boat perpendicular to the current maximizes across-bank velocity vy = vb. The river current does not delay crossing time: t_min = w / vb.");
                insightCard.setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-font-size: 11px; -fx-padding: 8 10 8 10; -fx-border-color: #a7f3d0; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_ACHIEVABLE -> {
                insightCard.setText("ZERO DRIFT (vb > u): Steering upstream at sin(θ) = u/vb cancels downstream current completely (vx = 0). The boat traverses directly across the bank along the normal.");
                insightCard.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1e40af; -fx-font-size: 11px; -fx-padding: 8 10 8 10; -fx-border-color: #bfdbfe; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_UNACHIEVABLE -> {
                insightCard.setText("MINIMUM DRIFT (vb < u): Current exceeds boat speed, so zero drift is physically impossible. Drift is minimized when sin(θ) = vb/u, making ground velocity tangent to the velocity circle.");
                insightCard.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-padding: 8 10 8 10; -fx-border-color: #fecaca; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_ONLY_ASYMPTOTIC -> {
                insightCard.setText("ASYMPTOTIC BOUNDARY (vb = u): Holding directly upstream (θ = 90°) cancels current entirely (v_net = 0). The boat remains stationary and zero drift is never reached in finite time (t → ∞).");
                insightCard.setStyle("-fx-background-color: #fefce8; -fx-text-fill: #854d0e; -fx-font-size: 11px; -fx-padding: 8 10 8 10; -fx-border-color: #fef08a; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
            default -> {
                insightCard.setText(String.format(Locale.US, "GENERAL CROSSING: Ground velocity is the vector sum v_ground = v_{b/r} + u. Crossing duration is t = w / (vb cos θ) = %.2f s.",
                        result.crossingTime().orElse(0.0)));
                insightCard.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-padding: 8 10 8 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
        }
    }

    private void renderTriangleCanvas(Vector2D vRel, Vector2D u, Vector2D vGround) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        // Vector addition vertices:
        // Vertex O: (0, 0)
        // Vertex A: vRel = (vRel.x, vRel.y)
        // Vertex B: vRel + u = vGround = (vGround.x, vGround.y)
        double minX = Math.min(0.0, Math.min(vRel.x(), vGround.x()));
        double maxX = Math.max(0.0, Math.max(vRel.x(), vGround.x()));
        double minY = Math.min(0.0, Math.min(vRel.y(), vGround.y()));
        double maxY = Math.max(0.0, Math.max(vRel.y(), vGround.y()));

        double spanX = Math.max(4.0, maxX - minX);
        double spanY = Math.max(4.0, maxY - minY);

        double pad = 35.0;
        double scale = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);

        double originX = pad + (0.0 - minX) * scale;
        double originY = pad + (maxY - 0.0) * scale;

        double ax = originX + vRel.x() * scale;
        double ay = originY - vRel.y() * scale;

        double bx = originX + vGround.x() * scale;
        double by = originY - vGround.y() * scale;

        // Subtle background grid lines
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, originY, w, originY);
        gc.strokeLine(originX, 0, originX, h);

        // 1. Vector v_{b/r} from O to A (Emerald Green)
        drawVector(gc, originX, originY, ax, ay, COLOR_V_REL, "v_b/r");

        // 2. Vector u from A to B (Flame Orange, head-to-tail addition)
        drawVector(gc, ax, ay, bx, by, COLOR_U, "u");

        // 3. Resultant Vector v_{b/g} from O to B (Cobalt Blue)
        drawVector(gc, originX, originY, bx, by, COLOR_V_GROUND, "v_ground");

        // Vertex dots
        gc.setFill(Color.web("#0f172a"));
        gc.fillOval(originX - 3, originY - 3, 6, 6);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
        gc.fillText("O", originX - 12, originY + 12);
    }

    private void drawVector(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color, String label) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.hypot(dx, dy);
        if (dist < 4.0) {
            return;
        }

        gc.setStroke(color);
        gc.setLineWidth(2.4);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeLine(x1, y1, x2, y2);

        // Arrowhead
        double headLen = 8.0;
        double angle = Math.atan2(dy, dx);
        double arrowAngle = Math.PI / 6.0;

        double x3 = x2 - headLen * Math.cos(angle - arrowAngle);
        double y3 = y2 - headLen * Math.sin(angle - arrowAngle);
        double x4 = x2 - headLen * Math.cos(angle + arrowAngle);
        double y4 = y2 - headLen * Math.sin(angle + arrowAngle);

        gc.setFill(color);
        gc.fillPolygon(new double[]{x2, x3, x4}, new double[]{y2, y3, y4}, 3);

        // Label
        if (label != null) {
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
            gc.fillText(label, x2 + 5, y2 - 4);
        }
    }

    private VBox buildBottomArea() {
        GridPane table = new GridPane();
        table.setHgap(8);
        table.setVgap(4);
        table.setPadding(new Insets(6, 4, 8, 4));

        table.add(createHeaderLabel("VECTOR"), 0, 0);
        table.add(createHeaderLabel("COMPONENTS (vx, vy)"), 1, 0);
        table.add(createHeaderLabel("SPEED & ORIENTATION"), 2, 0);

        // Row 1: v_{b/r}
        table.add(createVectorBadge("v_b/r (water)", COLOR_V_REL), 0, 1);
        table.add(valVRelComponents, 1, 1);
        HBox row1Box = new HBox(6, valVRelMagnitude, valVRelBadge);
        row1Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row1Box, 2, 1);

        // Row 2: u
        table.add(createVectorBadge("u (current)", COLOR_U), 0, 2);
        table.add(valUComponents, 1, 2);
        HBox row2Box = new HBox(6, valUMagnitude, valUBadge);
        row2Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row2Box, 2, 2);

        // Row 3: v_{b/g}
        table.add(createVectorBadge("v_b/g (ground)", COLOR_V_GROUND), 0, 3);
        table.add(valVGroundComponents, 1, 3);
        HBox row3Box = new HBox(6, valVGroundMagnitude, valVGroundBadge);
        row3Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row3Box, 2, 3);

        styleRow(valVRelComponents, valVRelMagnitude, valVRelBadge);
        styleRow(valUComponents, valUMagnitude, valUBadge);
        styleRow(valVGroundComponents, valVGroundMagnitude, valVGroundBadge);

        insightCard.setWrapText(true);

        return new VBox(6, table, insightCard);
    }

    private Label createHeaderLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        lbl.setTextFill(PhysoraTheme.MUTED);
        return lbl;
    }

    private Label createVectorBadge(String text, Color color) {
        Label badge = new Label(text);
        badge.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        String hex = String.format("#%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255));
        badge.setStyle(String.format("-fx-background-color: %s20; -fx-text-fill: %s; -fx-padding: 2 6 2 6; -fx-background-radius: 3px;", hex, hex));
        return badge;
    }

    private void styleRow(Label comp, Label mag, Label badge) {
        comp.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
        comp.setTextFill(PhysoraTheme.INK);
        mag.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        mag.setTextFill(PhysoraTheme.INK);
        badge.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-text-fill: #64748b;");
    }
}
