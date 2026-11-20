package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;
import java.util.OptionalDouble;

/** Renders model-derived positions in JavaFX's downward-positive screen coordinates. */
final class ProjectileCanvasRenderer {
    private static final int PATH_SAMPLES = 120;
    private final Canvas canvas = new Canvas(800, 520);
    private boolean showVelocityComponents = true;
    private ProjectileMotion sampledModel;
    private double sampledEndTime = Double.NaN;
    private double[] sampledX = new double[0];
    private double[] sampledY = new double[0];
    private TrajectoryViewport lastViewport;

    Canvas canvas() {
        return canvas;
    }

    TrajectoryViewport lastViewport() {
        return lastViewport;
    }

    void setShowVelocityComponents(boolean show) {
        showVelocityComponents = show;
    }

    void render(ProjectileMotion model, double time, double openDuration) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width < 100 || height < 100) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(PhysoraTheme.CANVAS);
        gc.fillRect(0, 0, width, height);

        OptionalDouble flightTime = model.timeOfFlight();
        double endTime = flightTime.isPresent()
                ? Math.max(0.0, flightTime.getAsDouble())
                : Math.max(openDuration, time);
        samplePathIfNeeded(model, endTime);
        ProjectileMotion.State current = model.stateAt(time);

        TrajectoryViewport viewport = TrajectoryViewport.compute(
                model, sampledX, sampledY, current, width, height);
        this.lastViewport = viewport;

        drawGrid(gc, viewport);
        drawAxes(gc, viewport);
        drawLandingSurface(gc, viewport, model.landingY());
        drawTrajectory(gc, viewport);
        drawBadges(gc, viewport);
        drawProjectile(gc, current, viewport);
        if (showVelocityComponents) {
            drawVelocityComponents(gc, current, viewport);
        }
    }

    private void samplePathIfNeeded(ProjectileMotion model, double endTime) {
        if (model == sampledModel && Double.compare(endTime, sampledEndTime) == 0) return;
        sampledX = new double[PATH_SAMPLES + 1];
        sampledY = new double[PATH_SAMPLES + 1];
        for (int i = 0; i <= PATH_SAMPLES; i++) {
            ProjectileMotion.State point = model.stateAt(endTime * i / PATH_SAMPLES);
            sampledX[i] = point.x();
            sampledY[i] = point.y();
        }
        sampledModel = model;
        sampledEndTime = endTime;
    }

    private static void drawGrid(GraphicsContext gc, TrajectoryViewport vp) {
        gc.setStroke(PhysoraTheme.GRID);
        gc.setLineWidth(0.8);
        for (TrajectoryViewport.Tick tick : vp.xTicks()) {
            double x = tick.screenCoordinate();
            if (x >= vp.left() && x <= vp.right()) {
                gc.strokeLine(x, vp.top(), x, vp.bottom());
            }
        }
        for (TrajectoryViewport.Tick tick : vp.yTicks()) {
            double y = tick.screenCoordinate();
            if (y >= vp.top() && y <= vp.bottom()) {
                gc.strokeLine(vp.left(), y, vp.right(), y);
            }
        }
    }

    private static void drawAxes(GraphicsContext gc, TrajectoryViewport vp) {
        double groundY = vp.groundScreenY();
        double originX = vp.originX();

        gc.setStroke(PhysoraTheme.INK);
        gc.setLineWidth(1.25);

        // Ground axis (y = 0)
        if (groundY >= vp.top() && groundY <= vp.bottom()) {
            gc.strokeLine(vp.left(), groundY, vp.right(), groundY);
        } else {
            double clampedGround = Math.max(vp.top(), Math.min(vp.bottom(), groundY));
            gc.strokeLine(vp.left(), clampedGround, vp.right(), clampedGround);
        }

        // Origin vertical axis (x = 0)
        if (originX >= vp.left() && originX <= vp.right()) {
            gc.strokeLine(originX, vp.top(), originX, vp.bottom());
        }

        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        gc.fillText("x / m", vp.right() - 32, Math.min(vp.bottom() - 5, groundY + 24));
        gc.fillText("y / m", Math.max(vp.left() + 4, originX + 7), vp.top() + 11);

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9.5));
        // X-axis ticks
        for (TrajectoryViewport.Tick tick : vp.xTicks()) {
            double sx = tick.screenCoordinate();
            if (sx >= vp.left() && sx <= vp.right()) {
                gc.strokeLine(sx, groundY - 3, sx, groundY + 3);
                gc.fillText(tick.label(), sx - 8, Math.min(vp.bottom() + 12, groundY + 15));
            }
        }

        // Y-axis ticks
        for (TrajectoryViewport.Tick tick : vp.yTicks()) {
            double sy = tick.screenCoordinate();
            if (sy >= vp.top() && sy <= vp.bottom()) {
                gc.strokeLine(originX - 3, sy, originX + 3, sy);
                gc.fillText(tick.label(), Math.max(4.0, originX - 36), sy + 3);
            }
        }
    }

    private static void drawLandingSurface(GraphicsContext gc, TrajectoryViewport vp, double landingY) {
        if (landingY == 0.0) return;
        double screenY = vp.toScreenY(landingY);
        gc.save();
        gc.setStroke(PhysoraTheme.ORANGE);
        gc.setLineWidth(1.4);
        gc.setLineDashes(7, 5);
        gc.strokeLine(vp.left(), screenY, vp.right(), screenY);
        gc.setLineDashes();
        gc.setFill(PhysoraTheme.ORANGE);
        gc.setFont(Font.font("Segoe UI", 10));
        double labelY = screenY > vp.top() + 18 ? screenY - 8 : screenY + 14;
        if (labelY > vp.bottom()) labelY = vp.bottom() - 4;
        gc.fillText(String.format(Locale.ROOT, "LANDING SURFACE  /  y = %.2f m", landingY), vp.left() + 8, labelY);
        gc.restore();
    }

    private void drawTrajectory(GraphicsContext gc, TrajectoryViewport vp) {
        if (sampledX.length == 0) return;
        gc.setStroke(PhysoraTheme.COBALT);
        gc.setLineWidth(2.8);
        gc.beginPath();
        gc.moveTo(vp.toScreenX(sampledX[0]), vp.toScreenY(sampledY[0]));
        for (int i = 1; i < sampledX.length; i++) {
            gc.lineTo(vp.toScreenX(sampledX[i]), vp.toScreenY(sampledY[i]));
        }
        gc.stroke();
    }

    private static void drawBadges(GraphicsContext gc, TrajectoryViewport vp) {
        vp.apexBadge().ifPresent(badge -> drawBadge(gc, badge, PhysoraTheme.LIME, vp));
        vp.landingBadge().ifPresent(badge -> drawBadge(gc, badge, PhysoraTheme.INK, vp));
    }

    private static void drawBadge(GraphicsContext gc, TrajectoryViewport.EventBadge badge, Color color, TrajectoryViewport vp) {
        double x = badge.screenX();
        double y = badge.screenY();

        gc.setFill(color);
        gc.fillOval(x - 5.5, y - 5.5, 11, 11);
        gc.setStroke(PhysoraTheme.PAPER);
        gc.setLineWidth(1.4);
        gc.strokeOval(x - 5.5, y - 5.5, 11, 11);

        gc.setFill(PhysoraTheme.INK);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));

        double labelX = badge.badgeScreenX();
        double labelY = badge.badgeScreenY();
        double estimatedWidth = badge.label().length() * 6.0;

        if (labelX + estimatedWidth > vp.right() - 4) {
            labelX = x - estimatedWidth - 8;
        }
        if (labelY < vp.top() + 10) {
            labelY = y + 17;
        }
        if (labelY > vp.bottom()) {
            labelY = vp.bottom() - 4;
        }

        gc.fillText(badge.label(), labelX, labelY);
    }

    private static void drawProjectile(GraphicsContext gc, ProjectileMotion.State state, TrajectoryViewport vp) {
        double x = vp.toScreenX(state.x());
        double y = vp.toScreenY(state.y());
        gc.setFill(PhysoraTheme.ORANGE);
        gc.fillOval(x - 6.5, y - 6.5, 13, 13);
        gc.setStroke(PhysoraTheme.PAPER);
        gc.setLineWidth(1.5);
        gc.strokeOval(x - 6.5, y - 6.5, 13, 13);
    }

    private static void drawVelocityComponents(GraphicsContext gc, ProjectileMotion.State state, TrajectoryViewport vp) {
        double x = vp.toScreenX(state.x());
        double y = vp.toScreenY(state.y());
        double velocityScale = Math.min(4.0, 84.0 / Math.max(1.0,
                Math.max(Math.abs(state.vx()), Math.abs(state.vy()))));
        double vxLength = state.vx() * velocityScale;
        double vyLength = state.vy() * velocityScale;
        gc.setLineWidth(2.4);
        drawArrow(gc, x, y, x + vxLength, y, PhysoraTheme.COBALT);
        drawArrow(gc, x + vxLength, y, x + vxLength, y - vyLength, PhysoraTheme.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        gc.setFill(PhysoraTheme.COBALT);
        gc.fillText(String.format(Locale.ROOT, "vx %.2f m/s", state.vx()),
                x + vxLength + 6, y + 15);
        gc.setFill(PhysoraTheme.ORANGE);
        gc.fillText(String.format(Locale.ROOT, "vy %.2f m/s", state.vy()),
                x + vxLength + 6, y - vyLength - 7);
    }

    private static void drawArrow(GraphicsContext gc, double x1, double y1,
                                  double x2, double y2, Color color) {
        gc.setStroke(color);
        gc.setFill(color);
        gc.strokeLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLength = 7.0;
        double leftX = x2 - headLength * Math.cos(angle - Math.PI / 6.0);
        double leftY = y2 - headLength * Math.sin(angle - Math.PI / 6.0);
        double rightX = x2 - headLength * Math.cos(angle + Math.PI / 6.0);
        double rightY = y2 - headLength * Math.sin(angle + Math.PI / 6.0);
        gc.strokeLine(x2, y2, leftX, leftY);
        gc.strokeLine(x2, y2, rightX, rightY);
    }

    void renderComparison(
            ProjectileMotion modelA,
            double[] samplesA_X,
            double[] samplesA_Y,
            ProjectileMotion modelB,
            double[] samplesB_X,
            double[] samplesB_Y,
            String labelA,
            String labelB) {

        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width < 100 || height < 100) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(PhysoraTheme.CANVAS);
        gc.fillRect(0, 0, width, height);

        TrajectoryViewport vp = TrajectoryViewport.computeCombined(
                modelA, samplesA_X, samplesA_Y,
                modelB, samplesB_X, samplesB_Y,
                width, height);
        this.lastViewport = vp;

        drawGrid(gc, vp);
        drawAxes(gc, vp);
        drawLandingSurface(gc, vp, modelA.landingY());
        if (Math.abs(modelA.landingY() - modelB.landingY()) > 1e-4) {
            drawLandingSurface(gc, vp, modelB.landingY());
        }

        // Draw Path A (Solid Cobalt)
        gc.save();
        gc.setStroke(PhysoraTheme.COBALT);
        gc.setLineWidth(2.4);
        gc.beginPath();
        if (samplesA_X != null && samplesA_X.length > 0) {
            gc.moveTo(vp.toScreenX(samplesA_X[0]), vp.toScreenY(samplesA_Y[0]));
            for (int i = 1; i < samplesA_X.length; i++) {
                gc.lineTo(vp.toScreenX(samplesA_X[i]), vp.toScreenY(samplesA_Y[i]));
            }
        }
        gc.stroke();
        gc.restore();

        // Draw Path B (Dashed Crimson / Amber)
        gc.save();
        gc.setStroke(Color.web("#c2410c"));
        gc.setLineWidth(2.4);
        gc.setLineDashes(6.0, 4.0);
        gc.beginPath();
        if (samplesB_X != null && samplesB_X.length > 0) {
            gc.moveTo(vp.toScreenX(samplesB_X[0]), vp.toScreenY(samplesB_Y[0]));
            for (int i = 1; i < samplesB_X.length; i++) {
                gc.lineTo(vp.toScreenX(samplesB_X[i]), vp.toScreenY(samplesB_Y[i]));
            }
        }
        gc.stroke();
        gc.restore();

        // Draw Apex Markers for both
        modelA.apexState().ifPresent(apex -> drawPointBadge(gc, vp, apex.x(), apex.y(), "Apex A", PhysoraTheme.COBALT, -12));
        modelB.apexState().ifPresent(apex -> drawPointBadge(gc, vp, apex.x(), apex.y(), "Apex B", Color.web("#c2410c"), 15));

        // Draw Landing Markers for both
        modelA.timeOfFlight().ifPresent(t -> {
            ProjectileMotion.State s = modelA.stateAt(t);
            drawPointBadge(gc, vp, s.x(), s.y(), "Land A", PhysoraTheme.COBALT, -12);
        });
        modelB.timeOfFlight().ifPresent(t -> {
            ProjectileMotion.State s = modelB.stateAt(t);
            drawPointBadge(gc, vp, s.x(), s.y(), "Land B", Color.web("#c2410c"), 15);
        });

        // Comparison Legend on Top Right
        drawComparisonLegend(gc, vp, labelA, labelB);
    }

    private static void drawPointBadge(GraphicsContext gc, TrajectoryViewport vp, double px, double py,
                                       String text, Color color, double yOffset) {
        double sx = vp.toScreenX(px);
        double sy = vp.toScreenY(py);
        gc.setFill(color);
        gc.fillOval(sx - 3.5, sy - 3.5, 7, 7);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9.5));
        gc.fillText(text, sx - 15, sy + yOffset);
    }

    private static void drawComparisonLegend(GraphicsContext gc, TrajectoryViewport vp, String labelA, String labelB) {
        double legendWidth = Math.min(210.0, Math.max(140.0, vp.right() - vp.left() - 20.0));
        double lx = Math.max(vp.left() + 10, vp.right() - legendWidth - 10);
        double ly = vp.top() + 6;
        gc.setFill(Color.web("#f5f1e5"));
        gc.setStroke(PhysoraTheme.RULE);
        gc.setLineWidth(1.0);
        gc.fillRoundRect(lx, ly, legendWidth, 42, 3, 3);
        gc.strokeRoundRect(lx, ly, legendWidth, 42, 3, 3);

        // A entry
        gc.setStroke(PhysoraTheme.COBALT);
        gc.setLineWidth(2.5);
        gc.strokeLine(lx + 8, ly + 14, lx + 26, ly + 14);
        gc.setFill(PhysoraTheme.COBALT);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9.0));
        gc.fillText("Exp A: " + labelA, lx + 32, ly + 17);

        // B entry
        gc.setStroke(Color.web("#c2410c"));
        gc.setLineWidth(2.5);
        gc.setLineDashes(4.0, 3.0);
        gc.strokeLine(lx + 8, ly + 28, lx + 26, ly + 28);
        gc.setLineDashes((double[]) null);
        gc.setFill(Color.web("#c2410c"));
        gc.fillText("Exp B: " + labelB, lx + 32, ly + 31);
    }
}
