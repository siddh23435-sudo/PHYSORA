package edu.physics.ui;

import edu.physics.relative.RiverBoatCrossingType;
import edu.physics.relative.RiverBoatResult;
import edu.physics.relative.RiverBoatSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Dedicated 2D canvas renderer for the River-Boat Interactive Vector Workbench.
 *
 * <p>Visualizes:
 * <ul>
 *   <li>Lush riverbanks (South Launch Bank and North Target Bank).</li>
 *   <li>Flowing river water with animated horizontal streamlines proportional to river current.</li>
 *   <li>Launch dock at (0, 0), zero-drift marker at (0, w), and actual landing touchdown marker.</li>
 *   <li>Dimension lines for downstream drift along the north bank.</li>
 *   <li>Boat hull oriented along the steering heading angle &theta;.</li>
 *   <li>Velocity vector trio: engine heading v_{b/r}, river flow u, and resultant ground velocity v_{b/g}.</li>
 *   <li>Real-time ground trajectory trail and landing HUD badges.</li>
 * </ul>
 * </p>
 */
public final class RiverBoatCanvasRenderer {

    private static final Color COLOR_V_REL = Color.web("#059669");    // Emerald Green (Engine / Water)
    private static final Color COLOR_U = Color.web("#e56843");        // Flame Orange (River Current)
    private static final Color COLOR_V_GROUND = Color.web("#2453d4"); // Cobalt Blue (Resultant Ground)
    private static final Color COLOR_TRAIL = Color.web("#2563eb", 0.65);

    private final Canvas canvas = new Canvas(840, 520);

    public Canvas canvas() {
        return canvas;
    }

    public void setDimensions(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    public void render(RiverBoatSimulationModel model, double time) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        if (model == null) {
            return;
        }

        double riverWidth = model.riverWidth();
        double riverSpeed = model.riverSpeed();
        double boatSpeed = model.boatSpeed();
        RiverBoatResult result = model.analyticalResult();
        double expectedDrift = result.downstreamDrift().orElse(0.0);

        // Determine World Bounds to ensure everything fits comfortably
        double minX = Math.min(-20.0, Math.min(0.0, expectedDrift) - 25.0);
        double maxX = Math.max(riverWidth * 0.8, Math.max(0.0, expectedDrift) + 35.0);
        double worldSpanX = maxX - minX;

        double minY = -20.0;
        double maxY = riverWidth + 25.0;
        double worldSpanY = maxY - minY;

        double padX = 40.0;
        double padY = 65.0;
        double availW = Math.max(100.0, w - 2 * padX);
        double availH = Math.max(100.0, h - 2 * padY);

        double scale = Math.min(availW / worldSpanX, availH / worldSpanY);

        // Coordinate transforms: world (x, y) -> screen (sx, sy)
        // physical positive y is up, screen y is down
        double originScreenX = padX + (0.0 - minX) * scale;
        double southBankScreenY = padY + (maxY - 0.0) * scale;
        double northBankScreenY = padY + (maxY - riverWidth) * scale;

        // 1. Draw River Water & Banks
        drawEnvironment(gc, w, h, southBankScreenY, northBankScreenY, time, riverSpeed, scale);

        // 2. Draw Bank Landmarks: Dock, Target (0, w), Touchdown (drift, w), Drift Dimension
        drawLandmarks(gc, originScreenX, southBankScreenY, northBankScreenY, scale, expectedDrift, result);

        // 3. Draw Ground Trajectory Path
        drawTrajectory(gc, originScreenX, southBankScreenY, northBankScreenY, scale, model, time);

        // 4. Draw Boat and Velocity Vectors
        Vector2D boatPos = model.boatPosition(time);
        double boatSx = originScreenX + boatPos.x() * scale;
        double boatSy = southBankScreenY - boatPos.y() * scale;

        drawBoat(gc, boatSx, boatSy, model.headingAngleRad(), model.isLanded(time));
        drawVelocityVectors(gc, boatSx, boatSy, model);

        // 5. HUD Status & Educational Badges
        drawHud(gc, w, h, model, time);
    }

    private void drawEnvironment(GraphicsContext gc, double w, double h,
                                 double southY, double northY, double time, double riverSpeed, double scale) {
        // North Bank (Grassy Shore)
        gc.setFill(Color.web("#e2eed8"));
        gc.fillRect(0, 0, w, northY);

        // River Water Body
        LinearGradient waterGrad = new LinearGradient(
                0, northY, 0, southY, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#dbeafe")),
                new Stop(0.5, Color.web("#bfdbfe")),
                new Stop(1.0, Color.web("#dbeafe"))
        );
        gc.setFill(waterGrad);
        gc.fillRect(0, northY, w, southY - northY);

        // South Bank (Launch Shore)
        gc.setFill(Color.web("#e2eed8"));
        gc.fillRect(0, southY, w, h - southY);

        // Bank Shorelines
        gc.setStroke(Color.web("#86efac"));
        gc.setLineWidth(3.0);
        gc.strokeLine(0, northY, w, northY);
        gc.strokeLine(0, southY, w, southY);

        // Animated Water Streamlines
        gc.setStroke(Color.web("#60a5fa", 0.45));
        gc.setLineWidth(1.5);
        gc.setLineDashes(18.0, 14.0);

        double streamSpacing = 28.0;
        double streamSpeedPx = riverSpeed * scale * 0.7;
        double streamOffset = (time * streamSpeedPx) % 32.0;

        for (double y = northY + streamSpacing; y < southY - 10.0; y += streamSpacing) {
            gc.setLineDashOffset(-streamOffset);
            gc.strokeLine(0, y, w, y);
        }
        gc.setLineDashes(null); // Reset dashes
    }

    private void drawLandmarks(GraphicsContext gc, double originX, double southY, double northY,
                              double scale, double drift, RiverBoatResult result) {
        // Launch Dock at (0, 0)
        gc.setFill(Color.web("#78350f"));
        gc.fillRect(originX - 14, southY, 28, 12);
        gc.setStroke(Color.web("#451a03"));
        gc.setLineWidth(1.5);
        gc.strokeRect(originX - 14, southY, 28, 12);

        gc.setFill(Color.web("#451a03"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.fillText("DOCK (0, 0)", originX + 18, southY + 12);

        // Zero-Drift Reference Point on North Bank: (0, w)
        double targetX = originX;
        gc.setStroke(Color.web("#94a3b8"));
        gc.setLineWidth(1.5);
        gc.setLineDashes(4.0, 4.0);
        gc.strokeLine(originX, southY, originX, northY);
        gc.setLineDashes(null);

        gc.setFill(Color.web("#64748b"));
        gc.fillOval(targetX - 5, northY - 5, 10, 10);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
        gc.fillText("ZERO-DRIFT NORMAL", targetX + 8, northY - 6);

        // Touchdown Landing Point on North Bank: (drift, w)
        if (result.reachesOppositeBank()) {
            double landX = originX + drift * scale;

            // Landing Flag / Marker
            gc.setStroke(Color.web("#dc2626"));
            gc.setLineWidth(2.0);
            gc.strokeLine(landX, northY - 18, landX, northY);

            gc.setFill(Color.web("#ef4444"));
            gc.fillPolygon(
                    new double[]{landX, landX + 14, landX},
                    new double[]{northY - 18, northY - 12, northY - 6},
                    3
            );

            // Downstream Drift Dimension Line (if noticeable drift)
            if (Math.abs(drift) > 0.5) {
                double dimY = northY - 20;
                gc.setStroke(Color.web("#b91c1c"));
                gc.setLineWidth(1.2);
                gc.strokeLine(Math.min(originX, landX), dimY, Math.max(originX, landX), dimY);
                // End ticks
                gc.strokeLine(originX, dimY - 4, originX, dimY + 4);
                gc.strokeLine(landX, dimY - 4, landX, dimY + 4);

                String driftText = String.format(Locale.US, "Drift Δx = %.1f m", drift);
                gc.setFill(Color.web("#b91c1c"));
                gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11.0));
                double midX = (originX + landX) / 2.0;
                gc.fillText(driftText, midX - 35, dimY - 5);
            }
        }
    }

    private void drawTrajectory(GraphicsContext gc, double originX, double southY, double northY,
                               double scale, RiverBoatSimulationModel model, double time) {
        Vector2D currentPos = model.boatPosition(time);
        double curSx = originX + currentPos.x() * scale;
        double curSy = southY - currentPos.y() * scale;

        // Trace line from dock to current position
        gc.setStroke(COLOR_TRAIL);
        gc.setLineWidth(2.5);
        gc.setLineDashes(6.0, 4.0);
        gc.strokeLine(originX, southY, curSx, curSy);
        gc.setLineDashes(null);

        // If not landed yet and crossing completes, draw projected future trajectory
        RiverBoatResult result = model.analyticalResult();
        if (result.reachesOppositeBank() && !model.isLanded(time)) {
            double finalDrift = result.downstreamDrift().orElse(0.0);
            double finalSx = originX + finalDrift * scale;
            gc.setStroke(Color.web("#93c5fd", 0.6));
            gc.setLineWidth(1.5);
            gc.setLineDashes(3.0, 3.0);
            gc.strokeLine(curSx, curSy, finalSx, northY);
            gc.setLineDashes(null);
        }
    }

    private void drawBoat(GraphicsContext gc, double sx, double sy, double headingRad, boolean isLanded) {
        gc.save();
        gc.translate(sx, sy);

        // Heading angle theta is measured upstream (+theta = -x).
        // Standard screen rotation: 0 radians points straight across (+y = screen UP = -90 deg rotation)
        // Upstream heading rotates counter-clockwise (-theta)
        double rotationDeg = -90.0 - Math.toDegrees(headingRad);
        gc.rotate(rotationDeg);

        // Boat Hull Polygon (pointed prow, flat stern)
        double halfL = 16.0;
        double halfW = 8.0;

        double[] xPoints = {halfL, -halfL * 0.7, -halfL, -halfL * 0.7};
        double[] yPoints = {0.0, -halfW, 0.0, halfW};

        // Hull Body
        gc.setFill(isLanded ? Color.web("#047857") : Color.web("#1e293b"));
        gc.fillPolygon(xPoints, yPoints, 4);

        // Wood Deck
        gc.setFill(Color.web("#fde68a"));
        gc.fillPolygon(
                new double[]{halfL * 0.7, -halfL * 0.5, -halfL * 0.7, -halfL * 0.5},
                new double[]{0.0, -halfW * 0.6, 0.0, halfW * 0.6},
                4
        );

        gc.setStroke(Color.web("#0f172a"));
        gc.setLineWidth(1.5);
        gc.strokePolygon(xPoints, yPoints, 4);

        gc.restore();
    }

    private void drawVelocityVectors(GraphicsContext gc, double sx, double sy, RiverBoatSimulationModel model) {
        Vector2D vRel = model.velocityBoatRelative();
        Vector2D u = model.velocityRiverGround();
        Vector2D vGround = model.velocityBoatGround();

        // Arrow display scale (e.g. 1 m/s = 14 px)
        double vScale = 14.0;

        // 1. Boat Engine Velocity in Water: v_{b/r} (Emerald Green)
        double vRelEndSx = sx + vRel.x() * vScale;
        double vRelEndSy = sy - vRel.y() * vScale;
        drawArrow(gc, sx, sy, vRelEndSx, vRelEndSy, COLOR_V_REL, "v_b/r");

        // 2. River Current Velocity: u (Flame Orange)
        double uEndSx = sx + u.x() * vScale;
        double uEndSy = sy - u.y() * vScale;
        drawArrow(gc, sx, sy, uEndSx, uEndSy, COLOR_U, "u");

        // 3. Resultant Ground Velocity: v_{b/g} (Cobalt Blue)
        double vGroundEndSx = sx + vGround.x() * vScale;
        double vGroundEndSy = sy - vGround.y() * vScale;
        drawArrow(gc, sx, sy, vGroundEndSx, vGroundEndSy, COLOR_V_GROUND, "v_ground");
    }

    private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color, String label) {
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

        // Label with deconfliction
        if (label != null && !label.isEmpty()) {
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
            gc.setFill(color);
            gc.fillText(label, x2 + 6, y2 - 4);
        }
    }

    private void drawHud(GraphicsContext gc, double w, double h, RiverBoatSimulationModel model, double time) {
        // Overlay HUD Card (Top-Left, positioned well above the north shoreline)
        gc.setFill(Color.web("#fcfbf7", 0.94));
        gc.fillRoundRect(16, 10, 260, 72, 6, 6);
        gc.setStroke(Color.web("#cbc4b4"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(16, 10, 260, 72, 6, 6);

        Vector2D pos = model.boatPosition(time);
        Vector2D vG = model.velocityBoatGround();

        gc.setFill(Color.web("#0f172a"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
        gc.fillText(String.format(Locale.US, "SIMULATION TIME: t = %.2f s", time), 24, 26);

        gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
        gc.setFill(Color.web("#334155"));
        gc.fillText(String.format(Locale.US, "Position : (x = %.1f m, y = %.1f m)", pos.x(), pos.y()), 24, 42);
        gc.fillText(String.format(Locale.US, "v_ground : (%.2f i + %.2f j) m/s", vG.x(), vG.y()), 24, 57);
        gc.fillText(String.format(Locale.US, "Steering : θ = %.1f° upstream", model.headingAngleDeg()), 24, 72);

        // Top-Right Landing or Asymptotic Badge
        if (model.isLanded(time)) {
            gc.setFill(Color.web("#d1fae5", 0.95));
            gc.fillRoundRect(w - 290, 16, 274, 46, 6, 6);
            gc.setStroke(Color.web("#10b981"));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(w - 290, 16, 274, 46, 6, 6);

            gc.setFill(Color.web("#065f46"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
            gc.fillText("✔ OPPOSITE BANK REACHED", w - 276, 34);

            double tCross = model.analyticalResult().crossingTime().orElse(time);
            double drift = model.analyticalResult().downstreamDrift().orElse(0.0);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
            gc.fillText(String.format(Locale.US, "t = %.2f s  |  Drift = %.1f m", tCross, drift), w - 276, 50);
        } else if (model.analyticalResult().crossingType() == RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC) {
            gc.setFill(Color.web("#fef3c7", 0.95));
            gc.fillRoundRect(w - 330, 16, 314, 52, 6, 6);
            gc.setStroke(Color.web("#f59e0b"));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(w - 330, 16, 314, 52, 6, 6);

            gc.setFill(Color.web("#92400e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
            gc.fillText("⚠ ASYMPTOTIC ZERO DRIFT BOUNDARY", w - 316, 34);
            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10.0));
            gc.fillText("Boat cancels flow directly upstream (v_net = 0).\nFinite crossing time is impossible (t → ∞).", w - 316, 48);
        }
    }
}
