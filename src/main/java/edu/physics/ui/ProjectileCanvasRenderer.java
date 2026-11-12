package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

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

    Canvas canvas() {
        return canvas;
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

        double minX = Math.min(0.0, model.initialX());
        double maxX = Math.max(0.0, model.initialX());
        double minY = Math.min(0.0, Math.min(model.initialY(), model.landingY()));
        double maxY = Math.max(0.0, Math.max(model.initialY(), model.landingY()));
        for (int i = 0; i < sampledX.length; i++) {
            minX = Math.min(minX, sampledX[i]);
            maxX = Math.max(maxX, sampledX[i]);
            minY = Math.min(minY, sampledY[i]);
            maxY = Math.max(maxY, sampledY[i]);
        }
        minX = Math.min(minX, current.x());
        maxX = Math.max(maxX, current.x());
        minY = Math.min(minY, current.y());
        maxY = Math.max(maxY, current.y());

        double xRange = Math.max(2.0, maxX - minX);
        double yRange = Math.max(2.0, maxY - minY);
        double left = 64.0;
        double right = width - 24.0;
        double top = 22.0;
        double bottom = height - 46.0;
        double scale = Math.min((right - left) / (xRange * 1.12),
                (bottom - top) / (yRange * 1.18));
        double originX = left + (right - left - xRange * scale) / 2.0 - minX * scale;
        double groundScreenY = bottom - (0.0 - minY) * scale
                - Math.max(0.0, bottom - top - yRange * scale) / 2.0;

        drawGrid(gc, left, right, top, bottom);
        drawAxes(gc, left, right, top, bottom, originX, groundScreenY, minX, maxX, minY, maxY, scale);
        drawLandingSurface(gc, left, right, groundScreenY - model.landingY() * scale, model.landingY(), top, bottom);
        drawTrajectory(gc, originX, groundScreenY, scale);
        model.apexState().ifPresent(apex -> drawEventMarker(gc, apex, originX, groundScreenY,
                scale, "APEX", PhysoraTheme.LIME, right, top, bottom));
        flightTime.ifPresent(landingTime -> drawEventMarker(gc, model.stateAt(landingTime),
                originX, groundScreenY, scale, "LANDING", PhysoraTheme.INK, right, top, bottom));
        drawProjectile(gc, current, originX, groundScreenY, scale);
        if (showVelocityComponents) {
            drawVelocityComponents(gc, current, originX, groundScreenY, scale);
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

    private static void drawGrid(GraphicsContext gc, double left, double right,
                                  double top, double bottom) {
        gc.setStroke(PhysoraTheme.GRID);
        gc.setLineWidth(0.8);
        for (int i = 0; i <= 8; i++) {
            double x = left + (right - left) * i / 8.0;
            gc.strokeLine(x, top, x, bottom);
        }
        for (int i = 0; i <= 6; i++) {
            double y = top + (bottom - top) * i / 6.0;
            gc.strokeLine(left, y, right, y);
        }
    }

    private static void drawAxes(GraphicsContext gc, double left, double right,
                                 double top, double bottom, double originX,
                                 double groundY, double minX, double maxX,
                                 double minY, double maxY, double scale) {
        gc.setStroke(PhysoraTheme.INK);
        gc.setLineWidth(1.25);
        gc.strokeLine(left, groundY, right, groundY);
        gc.strokeLine(originX, top, originX, bottom);
        gc.setFill(PhysoraTheme.MUTED);
        gc.setFont(Font.font("Consolas", 10));
        gc.fillText("x / m", right - 32, Math.min(bottom - 5, groundY + 24));
        gc.fillText("y / m", originX + 7, top + 11);
        gc.setFont(Font.font("Consolas", 9));
        for (int i = 0; i <= 4; i++) {
            double x = minX + (maxX - minX) * i / 4.0;
            double screenX = originX + x * scale;
            gc.strokeLine(screenX, groundY - 3, screenX, groundY + 3);
            gc.fillText(String.format(Locale.ROOT, "%.0f", x), screenX - 5, groundY + 15);
        }
        boolean zeroHeightTickPresent = false;
        for (int i = 0; i <= 4; i++) {
            double y = minY + (maxY - minY) * i / 4.0;
            if (Math.abs(y) <= 8.0 * Math.ulp(Math.max(1.0, Math.max(Math.abs(minY), Math.abs(maxY))))) {
                zeroHeightTickPresent = true;
            }
            double screenY = groundY - y * scale;
            gc.strokeLine(originX - 3, screenY, originX + 3, screenY);
            gc.fillText(String.format(Locale.ROOT, "%.1f", y), originX - 34, screenY + 3);
        }
        if (!zeroHeightTickPresent && minY < 0.0 && maxY > 0.0) {
            gc.strokeLine(originX - 4, groundY, originX + 4, groundY);
            gc.fillText("0.0", originX - 34, groundY + 3);
        }
    }

    private static void drawLandingSurface(GraphicsContext gc, double left, double right,
                                           double screenY, double landingY, double top, double bottom) {
        if (landingY == 0.0) return;
        gc.save();
        gc.setStroke(PhysoraTheme.ORANGE);
        gc.setLineWidth(1.4);
        gc.setLineDashes(7, 5);
        gc.strokeLine(left, screenY, right, screenY);
        gc.setLineDashes();
        gc.setFill(PhysoraTheme.ORANGE);
        gc.setFont(Font.font("Segoe UI", 10));
        double labelY = screenY > top + 18 ? screenY - 8 : screenY + 14;
        if (labelY > bottom) labelY = bottom - 4;
        gc.fillText(String.format(Locale.ROOT, "LANDING SURFACE  /  y = %.2f m", landingY), left + 8, labelY);
        gc.restore();
    }

    private void drawTrajectory(GraphicsContext gc, double originX, double groundY, double scale) {
        gc.setStroke(PhysoraTheme.COBALT);
        gc.setLineWidth(2.4);
        gc.beginPath();
        gc.moveTo(originX + sampledX[0] * scale, groundY - sampledY[0] * scale);
        for (int i = 1; i < sampledX.length; i++) {
            gc.lineTo(originX + sampledX[i] * scale, groundY - sampledY[i] * scale);
        }
        gc.stroke();
    }

    private static void drawEventMarker(GraphicsContext gc, ProjectileMotion.State state,
                                        double originX, double groundY, double scale,
                                        String label, Color color, double right, double top, double bottom) {
        double x = originX + state.x() * scale;
        double y = groundY - state.y() * scale;
        gc.setFill(color);
        gc.fillOval(x - 5.5, y - 5.5, 11, 11);
        gc.setStroke(PhysoraTheme.PAPER);
        gc.setLineWidth(1.4);
        gc.strokeOval(x - 5.5, y - 5.5, 11, 11);
        gc.setFill(PhysoraTheme.INK);
        gc.setFont(Font.font("Segoe UI", 9));
        double labelY = y - 9;
        if (labelY < top + 10) labelY = y + 17;
        if (labelY > bottom) labelY = bottom - 4;
        double estimatedWidth = label.length() * 6.0;
        double labelX = x + 8;
        if (labelX + estimatedWidth > right - 4) labelX = x - estimatedWidth - 8;
        gc.fillText(label, labelX, labelY);
    }

    private static void drawProjectile(GraphicsContext gc, ProjectileMotion.State state,
                                       double originX, double groundY, double scale) {
        double x = originX + state.x() * scale;
        double y = groundY - state.y() * scale;
        gc.setFill(PhysoraTheme.ORANGE);
        gc.fillOval(x - 6.5, y - 6.5, 13, 13);
        gc.setStroke(PhysoraTheme.PAPER);
        gc.setLineWidth(1.5);
        gc.strokeOval(x - 6.5, y - 6.5, 13, 13);
    }

    private static void drawVelocityComponents(GraphicsContext gc, ProjectileMotion.State state,
                                               double originX, double groundY, double scale) {
        double x = originX + state.x() * scale;
        double y = groundY - state.y() * scale;
        double velocityScale = Math.min(4.0, 84.0 / Math.max(1.0,
                Math.max(Math.abs(state.vx()), Math.abs(state.vy()))));
        double vxLength = state.vx() * velocityScale;
        double vyLength = state.vy() * velocityScale;
        gc.setLineWidth(2.1);
        drawArrow(gc, x, y, x + vxLength, y, PhysoraTheme.COBALT);
        drawArrow(gc, x + vxLength, y, x + vxLength, y - vyLength, PhysoraTheme.ORANGE);
        gc.setFont(Font.font("Consolas", 10));
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
}
