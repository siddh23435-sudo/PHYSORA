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

    // Formatted readouts: Vector name, frame, components, magnitude, and role
    private final Label valVRComponents = new Label();
    private final Label valVRMagnitude = new Label();
    private final Label valVRRole = new Label();

    private final Label valVMComponents = new Label();
    private final Label valVMMagnitude = new Label();
    private final Label valVMRole = new Label();

    private final Label valVRMComponents = new Label();
    private final Label valVRMMagnitude = new Label();
    private final Label valVRMRole = new Label();

    private final Label insightTitle = new Label("💡 WHAT DO THESE VALUES SHOW?");
    private final Label insightBody = new Label();
    private final VBox insightCard = new VBox(4, insightTitle, insightBody);

    public RainManVelocityPane() {
        setId("rainManVelocityPane");
        setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        setPadding(new Insets(10, 10, 10, 10));

        // 1. Header with Formula
        Label title = new Label("DYNAMIC VELOCITY TRIANGLE");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
        title.setStyle("-fx-text-fill: #142e4b;");

        Label formula = new Label("v⃗_r = v⃗_r/m + v⃗_m");
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
        VBox bottom = buildReadoutsTable();
        setBottom(bottom);
    }

    public void update(RainManSimulationModel model) {
        if (model == null) return;

        Vector2D vr = model.rainVelocityGround();
        Vector2D vm = model.personVelocityGround();
        Vector2D vrm = model.apparentRainVelocity();

        valVRComponents.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f)", vr.x(), vr.y()));
        valVRMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vr.magnitude()));
        valVRRole.setText(String.format(Locale.ROOT, "Fall: %.1f, Wind: %+.1f", model.vDown(), model.vRainX()));

        valVMComponents.setText(String.format(Locale.ROOT, "(%+.1f, 0.0)", vm.x()));
        valVMMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vm.magnitude()));
        valVMRole.setText(model.vPerson() >= 0 ? "Forward walk (+x)" : "Backward walk (-x)");

        valVRMComponents.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f)", vrm.x(), vrm.y()));
        valVRMMagnitude.setText(String.format(Locale.ROOT, "%.1f m/s", vrm.magnitude()));
        valVRMRole.setText(String.format(Locale.ROOT, "Apparent slant: %+.1f°", model.optimalAngleDeg()));

        // Update educational insight
        updateInsightCard(model, vr, vm, vrm);

        drawTriangle(vr, vm, vrm);
    }

    private void updateInsightCard(RainManSimulationModel model, Vector2D vr, Vector2D vm, Vector2D vrm) {
        double vRelX = vrm.x();
        double optAngle = model.optimalAngleDeg();
        double actAngle = model.umbrellaAngleDeg();
        double errAngle = model.angularErrorDeg();
        StringBuilder sb = new StringBuilder();

        // Step 1: Given Motion in Ground Frame
        String windDesc = Math.abs(model.vRainX()) < 1e-4 ? "no horizontal wind" :
                (model.vRainX() > 0 ? String.format(Locale.ROOT, "wind blows forward at +%.1f m/s", model.vRainX()) :
                        String.format(Locale.ROOT, "wind blows backward at %.1f m/s", model.vRainX()));
        String walkDesc = Math.abs(model.vPerson()) < 1e-4 ? "walker stationary" :
                (model.vPerson() > 0 ? String.format(Locale.ROOT, "walking forward at +%.1f m/s", model.vPerson()) :
                        String.format(Locale.ROOT, "walking backward at %.1f m/s", model.vPerson()));

        sb.append(String.format(Locale.ROOT,
                "1. GIVEN GROUND MOTION (Laboratory Frame S):\n" +
                "   • Rain falls vertically at %.1f m/s (%s) ⟹ v_r = (%+.1f i - %.1f j) m/s.\n" +
                "   • Person moves along road: %s ⟹ v_m = (%+.1f i + 0.0 j) m/s.\n\n",
                model.vDown(), windDesc, vr.x(), model.vDown(), walkDesc, vm.x()));

        // Step 2: Galilean Vector Subtraction
        String horizontalFeel;
        if (Math.abs(vRelX) < 1e-4) {
            horizontalFeel = "Zero relative wind (person speed equals horizontal rain speed; rain appears purely vertical)";
        } else if (vRelX < 0) {
            horizontalFeel = String.format(Locale.ROOT, "Negative horizontal component (%+.1f m/s): rain rushes into walker's face from the front", vRelX);
        } else {
            horizontalFeel = String.format(Locale.ROOT, "Positive horizontal component (%+.1f m/s): rain catches up from behind the walker", vRelX);
        }

        sb.append(String.format(Locale.ROOT,
                "2. APPARENT RAIN IN WALKER'S MOVING FRAME (S_m):\n" +
                "   • Relative velocity formula: v_{r/m} = v_r - v_m\n" +
                "   • Horizontal component: vx = v_rx - v_m = (%.1f) - (%.1f) = %+.1f m/s\n" +
                "   • Vertical component: vy = -%.1f m/s (unaffected by horizontal walking)\n" +
                "   • Apparent rain speed: |v_{r/m}| = √(vx² + vy²) = √(%.1f² + %.1f²) = %.2f m/s.\n" +
                "   • Physical sensation: %s.\n\n",
                vr.x(), vm.x(), vRelX, model.vDown(), Math.abs(vRelX), model.vDown(), model.apparentRainSpeed(), horizontalFeel));

        // Step 3: Umbrella Shielding Rule
        sb.append(String.format(Locale.ROOT,
                "3. OPTIMAL UMBRELLA ORIENTATION (JEE Derivation):\n" +
                "   • Incoming rain approaches along v_{r/m}. The umbrella shaft must point in the OPPOSITE direction: -v_{r/m} = (%+.1f i + %.1f j) m/s.\n" +
                "   • Tilt from vertical: tan(φ_opt) = |vx| / vy = %.1f / %.1f = %.3f ⟹ φ_opt = %+.1f° (%s).\n\n",
                -vRelX, model.vDown(), Math.abs(vRelX), model.vDown(), Math.abs(vRelX) / Math.max(1e-4, model.vDown()),
                optAngle, optAngle >= 0 ? "tilted forward" : "tilted backward"));

        // Step 4: Current Alignment Status
        RainManSimulationModel.AlignmentStatus status = model.alignmentStatus();
        String statusLabel = switch (status) {
            case WELL_ALIGNED -> "WELL ALIGNED (Shaft points directly opposite incoming rain streamlines)";
            case SLIGHTLY_MISALIGNED -> "SLIGHTLY MISALIGNED (Minor error; canopy partially off-streamline)";
            default -> "MISALIGNED (Rain hits front/rear of body; umbrella fails to block oncoming slant)";
        };

        sb.append(String.format(Locale.ROOT,
                "4. CURRENT EXPERIMENT STATUS:\n" +
                "   • Shaft held at φ_act = %+.1f°  vs  Needed φ_opt = %+.1f°  ⟹  Error |Δφ| = %.1f°\n" +
                "   • Status: %s.",
                actAngle, optAngle, errAngle, statusLabel));

        insightTitle.setText("💡 WHAT DO THESE VALUES SHOW? (STEP-BY-STEP DECOMPOSITION)");
        insightTitle.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        insightBody.setText(MathTypography.formatText(sb.toString()));
        insightBody.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-text-fill: #1e293b; -fx-line-spacing: 2.2px;");
        insightBody.setWrapText(true);
        insightCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");
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
        GridPane table = new GridPane();
        table.setHgap(8);
        table.setVgap(4);
        table.setPadding(new Insets(4, 2, 4, 2));

        table.add(createHeaderLabel("QUANTITY"), 0, 0);
        table.add(createHeaderLabel("FRAME"), 1, 0);
        table.add(createHeaderLabel("COMPONENTS (vx, vy)"), 2, 0);
        table.add(createHeaderLabel("SPEED & ROLE / APPROACH"), 3, 0);

        // Row 1: v_r
        table.add(createVectorBadge("v_r", COLOR_VR), 0, 1);
        table.add(createFrameBadge("Ground (S)"), 1, 1);
        table.add(valVRComponents, 2, 1);
        HBox row1 = new HBox(6, valVRMagnitude, valVRRole);
        row1.setAlignment(Pos.CENTER_LEFT);
        table.add(row1, 3, 1);

        // Row 2: v_m
        table.add(createVectorBadge("v_m", COLOR_VM), 0, 2);
        table.add(createFrameBadge("Ground (S)"), 1, 2);
        table.add(valVMComponents, 2, 2);
        HBox row2 = new HBox(6, valVMMagnitude, valVMRole);
        row2.setAlignment(Pos.CENTER_LEFT);
        table.add(row2, 3, 2);

        // Row 3: v_{r/m}
        table.add(createVectorBadge("v_{r/m}", COLOR_VRM), 0, 3);
        table.add(createFrameBadge("Walker (S_m)"), 1, 3);
        table.add(valVRMComponents, 2, 3);
        HBox row3 = new HBox(6, valVRMMagnitude, valVRMRole);
        row3.setAlignment(Pos.CENTER_LEFT);
        table.add(row3, 3, 3);

        styleRow(valVRComponents, valVRMagnitude, valVRRole);
        styleRow(valVMComponents, valVMMagnitude, valVMRole);
        styleRow(valVRMComponents, valVRMMagnitude, valVRMRole);

        Label conventionLabel = new Label("📐 CONVENTIONS: +x = forward along road | +y = upward | -y = rain fall | φ = shaft tilt forward from vertical");
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

    private void styleRow(Label comp, Label mag, Label role) {
        comp.getStyleClass().add("metric-value");
        comp.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
        comp.setStyle("-fx-text-fill: #0f172a;");

        mag.getStyleClass().add("metric-value");
        mag.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        mag.setStyle("-fx-text-fill: #0f172a;");

        role.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #475569;");
    }
}
