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

    private final Canvas canvas = new Canvas(340, 175);

    public Canvas canvas() {
        return canvas;
    }

    public void setDimensions(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Formatted readouts: Vector name, frame, components, magnitude, and status badge
    private final Label valVRelComponents = new Label();
    private final Label valVRelMagnitude = new Label();
    private final Label valVRelBadge = new Label();

    private final Label valUComponents = new Label();
    private final Label valUMagnitude = new Label();
    private final Label valUBadge = new Label();

    private final Label valVGroundComponents = new Label();
    private final Label valVGroundMagnitude = new Label();
    private final Label valVGroundBadge = new Label();

    private final Label insightTitle = new Label("💡 WHAT DO THESE VALUES SHOW?");
    private final Label insightBody = new Label();
    private final VBox insightCard = new VBox(4, insightTitle, insightBody);

    public RiverBoatVelocityPane() {
        setId("riverBoatVelocityPane");
        setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(10, 10, 10, 10));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        title.setStyle("-fx-text-fill: #142e4b;");

        Label formula = new Label("v⃗_b/g = v⃗_b/r + u⃗");
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

        double vxClean = Math.abs(vGround.x()) < 1e-6 ? 0.0 : vGround.x();
        double courseDeg = Math.toDegrees(Math.atan2(vxClean, Math.max(1e-6, vGround.y())));

        // 1. Update text readouts
        valVRelComponents.setText(String.format(Locale.US, "(%+.1f, %+.1f)", vRel.x(), vRel.y()));
        valVRelMagnitude.setText(String.format(Locale.US, "%.1f m/s", model.boatSpeed()));
        valVRelBadge.setText(String.format(Locale.US, "Heading θ=%.1f° upstream", model.headingAngleDeg()));

        valUComponents.setText(String.format(Locale.US, "(%+.1f, 0.0)", u.x()));
        valUMagnitude.setText(String.format(Locale.US, "%.1f m/s", model.riverSpeed()));
        valUBadge.setText("Current downstream (+x)");

        valVGroundComponents.setText(String.format(Locale.US, "(%+.1f, %+.1f)", vxClean, vGround.y()));
        valVGroundMagnitude.setText(String.format(Locale.US, "%.1f m/s", vGround.magnitude()));
        if (Math.abs(courseDeg) < 0.05) {
            valVGroundBadge.setText("Course α=0.0° (Direct Across)");
        } else {
            valVGroundBadge.setText(String.format(Locale.US, "Course α=%+.1f° from normal", courseDeg));
        }

        // 2. Update educational insight card
        updateInsightCard(result, model, vRel, u, vGround, vxClean, courseDeg);

        // 3. Redraw vector triangle canvas
        renderTriangleCanvas(vRel, u, vGround);
    }

    private void updateInsightCard(RiverBoatResult result, RiverBoatSimulationModel model,
                                   Vector2D vRel, Vector2D u, Vector2D vGround, double vxClean, double courseDeg) {
        RiverBoatCrossingType type = result.crossingType();
        StringBuilder sb = new StringBuilder();

        // Step 1: Given Parameters & Water Heading Decomposition
        sb.append(String.format(Locale.US,
                "1. STEERING THROUGH WATER (Moving Medium Frame S_w):\n" +
                "   • Engine speed vb = %.1f m/s steered at θ = %.1f° upstream.\n" +
                "   • Upstream component (opposing flow): vx_rel = -vb·sin(θ) = -%.1f·sin(%.1f°) = %+.2f m/s.\n" +
                "   • Across-bank component (crossing flow): vy = +vb·cos(θ) = +%.1f·cos(%.1f°) = %+.2f m/s.\n\n",
                model.boatSpeed(), model.headingAngleDeg(), model.boatSpeed(), model.headingAngleDeg(), vRel.x(),
                model.boatSpeed(), model.headingAngleDeg(), vRel.y()));

        // Step 2: River Current & Ground Velocity Composition
        sb.append(String.format(Locale.US,
                "2. GROUND VELOCITY COMPOSITION (Laboratory Frame S):\n" +
                "   • Vector sum: v_ground = v_{b/r} + u\n" +
                "   • Net horizontal drift speed: vx = vx_rel + u = (%+.2f) + (+%.2f) = %+.2f m/s.\n" +
                "   • Net across-bank speed: vy = +%.2f m/s (River current has u_y = 0; river flow never affects vy).\n" +
                "   • Resultant ground course: α = %+.1f° from perpendicular normal | Ground speed: %.2f m/s.\n\n",
                vRel.x(), u.x(), vxClean, vGround.y(), courseDeg, vGround.magnitude()));

        // Step 3: Crossing Duration & Total Drift
        if (result.crossingTime().isPresent()) {
            sb.append(String.format(Locale.US,
                    "3. CROSSING TIME & DOWNSTREAM DRIFT:\n" +
                    "   • Crossing time is governed strictly by across-bank velocity vy: t = w / vy = %.1f m / %.2f m/s = %.2f s.\n" +
                    "   • Total drift along opposite bank: x = vx · t = (%+.2f m/s) · %.2f s = %+.2f m (%s).\n\n",
                    model.riverWidth(), vGround.y(), result.crossingTime().getAsDouble(),
                    vxClean, result.crossingTime().getAsDouble(), result.downstreamDrift().orElse(0.0),
                    Math.abs(result.downstreamDrift().orElse(0.0)) < 1e-4 ? "Direct landing opposite dock" : "drifted downstream"));
        } else {
            sb.append(
                    "3. CROSSING TIME & DOWNSTREAM DRIFT:\n" +
                    "   • Across-bank velocity vy = 0.00 m/s ⟹ Boat hovers indefinitely and never reaches the opposite bank (t → ∞).\n\n");
        }

        // Step 4: JEE Scenario Interpretation
        sb.append("4. JEE EXAM STRATEGY & CLASSIFICATION:\n");
        switch (type) {
            case ZERO_DRIFT_ACHIEVABLE -> {
                insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? [ZERO DRIFT / SHORTEST PATH]");
                sb.append(String.format(Locale.US,
                        "   • Shortest Path Condition (vb > u): Boat speed through water (%.1f m/s) exceeds current (%.1f m/s).\n" +
                        "   • Exact upstream steering angle: sin(θ) = u / vb = %.1f / %.1f = %.3f ⟹ θ = %.1f°.\n" +
                        "   • Drift cancels completely (vx = 0.00 m/s); boat travels perpendicular to the banks along the shortest path.",
                        model.boatSpeed(), model.riverSpeed(), model.riverSpeed(), model.boatSpeed(),
                        model.riverSpeed() / model.boatSpeed(), model.headingAngleDeg()));
                insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                insightTitle.setStyle("-fx-text-fill: #1e40af; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
            case SHORTEST_TIME -> {
                insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? [SHORTEST TIME CROSSING]");
                sb.append(String.format(Locale.US,
                        "   • Minimum Time Condition (θ = 0°): Boat steers straight across perpendicular to the bank.\n" +
                        "   • Maximizes across-bank velocity: vy = vb = %.1f m/s ⟹ t_min = w / vb = %.1f / %.1f = %.2f s.\n" +
                        "   • Downstream drift occurs inevitably: x = u · t_min = %.1f · %.2f = %.1f m downstream.",
                        model.boatSpeed(), model.riverWidth(), model.boatSpeed(), result.crossingTime().orElse(0.0),
                        model.riverSpeed(), result.crossingTime().orElse(0.0), result.downstreamDrift().orElse(0.0)));
                insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                insightTitle.setStyle("-fx-text-fill: #065f46; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
            case ZERO_DRIFT_UNACHIEVABLE -> {
                insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? [MINIMUM DRIFT (vb < u)]");
                sb.append(String.format(Locale.US,
                        "   • Current Exceeds Boat Speed (u = %.1f m/s > vb = %.1f m/s): Zero drift is physically impossible.\n" +
                        "   • Minimum Drift Strategy: Steer at sin(θ) = vb / u = %.1f / %.1f = %.3f ⟹ θ = %.1f° upstream.\n" +
                        "   • Ground velocity vector becomes tangent to the velocity circle, minimizing downstream drift to x_min = %.2f m.",
                        model.riverSpeed(), model.boatSpeed(), model.boatSpeed(), model.riverSpeed(),
                        model.boatSpeed() / model.riverSpeed(), model.headingAngleDeg(), result.downstreamDrift().orElse(0.0)));
                insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                insightTitle.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
            case ZERO_DRIFT_ONLY_ASYMPTOTIC -> {
                insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? [ASYMPTOTIC LIMIT (vb = u)]");
                sb.append(
                        "   • Equal Speed Boundary (vb = u, θ = 90°): Steering directly upstream cancels river flow completely (vx = 0),\n" +
                        "     but leaves zero across-bank velocity (vy = 0). The boat hovers indefinitely relative to the ground.");
                insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                insightTitle.setStyle("-fx-text-fill: #854d0e; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
            default -> {
                insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? [GENERAL CROSSING]");
                sb.append(String.format(Locale.US,
                        "   • Resultant Ground Motion: Steering θ = %.1f° through water produces ground course α = %+.1f°,\n" +
                        "     landing at opposite bank with drift x = %.2f m in t = %.2f s.",
                        model.headingAngleDeg(), courseDeg, result.downstreamDrift().orElse(0.0), result.crossingTime().orElse(0.0)));
                insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                insightTitle.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
        }

        insightBody.setText(sb.toString());
        insightBody.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-text-fill: #1e293b; -fx-line-spacing: 2.2px;");
        insightBody.setWrapText(true);
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
        table.setPadding(new Insets(4, 2, 4, 2));

        table.add(createHeaderLabel("QUANTITY"), 0, 0);
        table.add(createHeaderLabel("FRAME"), 1, 0);
        table.add(createHeaderLabel("COMPONENTS (vx, vy)"), 2, 0);
        table.add(createHeaderLabel("SPEED & COURSE / ROLE"), 3, 0);

        // Row 1: v_{b/r}
        table.add(createVectorBadge("v_b/r", COLOR_V_REL), 0, 1);
        Label frame1 = createFrameBadge("Water (S_w)");
        table.add(frame1, 1, 1);
        table.add(valVRelComponents, 2, 1);
        HBox row1Box = new HBox(6, valVRelMagnitude, valVRelBadge);
        row1Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row1Box, 3, 1);

        // Row 2: u
        table.add(createVectorBadge("u", COLOR_U), 0, 2);
        Label frame2 = createFrameBadge("Ground (S)");
        table.add(frame2, 1, 2);
        table.add(valUComponents, 2, 2);
        HBox row2Box = new HBox(6, valUMagnitude, valUBadge);
        row2Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row2Box, 3, 2);

        // Row 3: v_{b/g}
        table.add(createVectorBadge("v_b/g", COLOR_V_GROUND), 0, 3);
        Label frame3 = createFrameBadge("Ground (S)");
        table.add(frame3, 1, 3);
        table.add(valVGroundComponents, 2, 3);
        HBox row3Box = new HBox(6, valVGroundMagnitude, valVGroundBadge);
        row3Box.setAlignment(Pos.CENTER_LEFT);
        table.add(row3Box, 3, 3);

        styleRow(valVRelComponents, valVRelMagnitude, valVRelBadge);
        styleRow(valUComponents, valUMagnitude, valUBadge);
        styleRow(valVGroundComponents, valVGroundMagnitude, valVGroundBadge);

        // Convention banner
        Label conventionLabel = new Label("📐 CONVENTIONS: +x = downstream current | +y = across river to north bank | θ = steering angle upstream from normal");
        conventionLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        conventionLabel.setStyle("-fx-text-fill: #475569; -fx-background-color: #f1f5f9; -fx-padding: 3 6 3 6; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

        return new VBox(6, table, conventionLabel, insightCard);
    }

    private Label createHeaderLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("metric-name");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        lbl.setStyle("-fx-text-fill: #475569;");
        return lbl;
    }

    private Label createFrameBadge(String text) {
        Label badge = new Label(text);
        badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        badge.setStyle("-fx-text-fill: #64748b; -fx-background-color: #e2e8f0; -fx-padding: 1 5 1 5; -fx-background-radius: 3px;");
        return badge;
    }

    private Label createVectorBadge(String text, Color color) {
        Label badge = new Label(text);
        badge.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        String hex = String.format("#%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255));
        badge.setStyle(String.format("-fx-background-color: %s25; -fx-text-fill: %s; -fx-padding: 2 6 2 6; -fx-background-radius: 3px; -fx-border-color: %s50; -fx-border-radius: 3px;", hex, hex, hex));
        return badge;
    }

    private void styleRow(Label comp, Label mag, Label badge) {
        comp.getStyleClass().add("metric-value");
        comp.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
        comp.setStyle("-fx-text-fill: #0f172a;");

        mag.getStyleClass().add("metric-value");
        mag.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        mag.setStyle("-fx-text-fill: #0f172a;");

        badge.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #475569;");
    }
}
