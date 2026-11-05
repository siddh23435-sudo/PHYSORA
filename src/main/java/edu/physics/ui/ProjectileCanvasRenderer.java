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
        gc.setFill(KinetikTheme.CANVAS);
        gc.fillRect(0, 0, width, height);

        OptionalDouble flightTime = model.timeOfFlight();
        double endTime = flightTime.isPresent()
                ? Math.max(0.0, flightTime.getAsDouble())
                : Math.max(openDuration, time);
        ProjectileMotion.State[] path = samplePath(model, endTime);
        ProjectileMotion.State current = model.stateAt(time);

        double minX = 0.0;
        double maxX = 0.0;
        double minY = 0.0;
        double maxY = 0.0;
        for (ProjectileMotion.State point : path) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
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
        drawTrajectory(gc, path, originX, groundScreenY, scale);
        drawProjectile(gc, current, originX, groundScreenY, scale);
        if (showVelocityComponents) {
            drawVelocityComponents(gc, current, originX, groundScreenY, scale);
        }
    }

    private static ProjectileMotion.State[] samplePath(ProjectileMotion model, double endTime) {
        ProjectileMotion.State[] points = new ProjectileMotion.State[PATH_SAMPLES + 1];
        for (int i = 0; i <= PATH_SAMPLES; i++) {
            points[i] = model.stateAt(endTime * i / PATH_SAMPLES);
        }
        return points;
    }

    private static void drawGrid(GraphicsContext gc, double left, double right,
                                  double top, double bottom) {
        gc.setStroke(KinetikTheme.GRID);
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
        gc.setStroke(KinetikTheme.INK);
        gc.setLineWidth(1.25);
        gc.strokeLine(left, groundY, right, groundY);
        gc.strokeLine(originX, top, originX, bottom);
        gc.setFill(KinetikTheme.MUTED);
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
        for (int i = 0; i <= 4; i++) {
            double y = minY + (maxY - minY) * i / 4.0;
            double screenY = groundY - y * scale;
            gc.strokeLine(originX - 3, screenY, originX + 3, screenY);
            gc.fillText(String.format(Locale.ROOT, "%.1f", y), originX - 34, screenY + 3);
        }
        gc.setFill(KinetikTheme.MUTED);
        gc.setFont(Font.font("Segoe UI", 10));
        gc.fillText("GROUND  /  y = 0", left + 8, Math.max(top + 15, groundY - 9));
    }

    private static void drawTrajectory(GraphicsContext gc, ProjectileMotion.State[] path,
                                       double originX, double groundY, double scale) {
        gc.setStroke(KinetikTheme.COBALT);
        gc.setLineWidth(2.4);
        gc.beginPath();
        gc.moveTo(originX + path[0].x() * scale, groundY - path[0].y() * scale);
        for (int i = 1; i < path.length; i++) {
            gc.lineTo(originX + path[i].x() * scale, groundY - path[i].y() * scale);
        }
        gc.stroke();
    }

    private static void drawProjectile(GraphicsContext gc, ProjectileMotion.State state,
                                       double originX, double groundY, double scale) {
        double x = originX + state.x() * scale;
        double y = groundY - state.y() * scale;
        gc.setFill(KinetikTheme.ORANGE);
        gc.fillOval(x - 6.5, y - 6.5, 13, 13);
        gc.setStroke(KinetikTheme.PAPER);
        gc.setLineWidth(1.5);
        gc.strokeOval(x - 6.5, y - 6.5, 13, 13);
        gc.setFill(KinetikTheme.INK);
        gc.setFont(Font.font("Consolas", 10));
        gc.fillText(String.format(Locale.ROOT, "(%.2f, %.2f) m", state.x(), state.y()), x + 11, y - 10);
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
        drawArrow(gc, x, y, x + vxLength, y, KinetikTheme.COBALT);
        drawArrow(gc, x + vxLength, y, x + vxLength, y - vyLength, KinetikTheme.ORANGE);
        gc.setFont(Font.font("Consolas", 10));
        gc.setFill(KinetikTheme.COBALT);
        gc.fillText(String.format(Locale.ROOT, "vx %.2f m/s", state.vx()),
                x + vxLength + 6, y + 15);
        gc.setFill(KinetikTheme.ORANGE);
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
