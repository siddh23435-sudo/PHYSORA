package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Pure, testable geometric viewport transformer for 2D projectile trajectory rendering.
 *
 * <p>Separates physical trajectory bounds, isotropic scaling, 1-2-5 decade metric grid ticks,
 * and screen-space event-marker layout from graphics context drawing.
 */
final class TrajectoryViewport {

    static final double MIN_PHYSICAL_SPAN_X = 0.10; // minimum physical horizontal span (meters)
    static final double MIN_PHYSICAL_SPAN_Y = 0.08; // minimum physical vertical span (meters)
    static final double PADDING_FACTOR_X = 1.15;   // 15% horizontal margin padding
    static final double PADDING_FACTOR_Y = 1.20;   // 20% vertical margin padding
    static final double BADGE_COLLISION_DISTANCE = 48.0; // screen pixels threshold for badge collision

    enum BadgePlacement {
        STANDARD,
        STAGGERED_UP,
        STAGGERED_DOWN,
        COMBINED
    }

    record EventBadge(
            String type,
            String label,
            double physicalX,
            double physicalY,
            double screenX,
            double screenY,
            double badgeScreenX,
            double badgeScreenY,
            BadgePlacement placement) {
    }

    record Tick(double value, String label, double screenCoordinate) {
    }

    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double spanX;
    private final double spanY;
    private final double scale;
    private final double originX;
    private final double groundScreenY;
    private final double left;
    private final double right;
    private final double top;
    private final double bottom;
    private final double xStep;
    private final double yStep;
    private final List<Tick> xTicks;
    private final List<Tick> yTicks;
    private final Optional<EventBadge> apexBadge;
    private final Optional<EventBadge> landingBadge;

    private TrajectoryViewport(
            double minX, double maxX, double minY, double maxY,
            double spanX, double spanY, double scale,
            double originX, double groundScreenY,
            double left, double right, double top, double bottom,
            double xStep, double yStep,
            List<Tick> xTicks, List<Tick> yTicks,
            Optional<EventBadge> apexBadge, Optional<EventBadge> landingBadge) {

        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.spanX = spanX;
        this.spanY = spanY;
        this.scale = scale;
        this.originX = originX;
        this.groundScreenY = groundScreenY;
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.xStep = xStep;
        this.yStep = yStep;
        this.xTicks = List.copyOf(xTicks);
        this.yTicks = List.copyOf(yTicks);
        this.apexBadge = apexBadge;
        this.landingBadge = landingBadge;
    }

    /**
     * Computes the adaptive viewport from physical trajectory samples and canvas dimensions.
     */
    public static TrajectoryViewport compute(
            ProjectileMotion model,
            double[] sampledX,
            double[] sampledY,
            ProjectileMotion.State current,
            double canvasWidth,
            double canvasHeight) {

        Objects.requireNonNull(model, "model is required");

        double left = 68.0;
        double right = Math.max(left + 100.0, canvasWidth - 28.0);
        double top = 28.0;
        double bottom = Math.max(top + 80.0, canvasHeight - 48.0);
        double availW = right - left;
        double availH = bottom - top;

        // 1. Compute physical bounding box encompassing initial, landing, apex, current, and all samples
        double bMinX = Math.min(0.0, model.initialX());
        double bMaxX = Math.max(0.0, model.initialX());
        double bMinY = Math.min(0.0, Math.min(model.initialY(), model.landingY()));
        double bMaxY = Math.max(0.0, Math.max(model.initialY(), model.landingY()));

        if (sampledX != null && sampledY != null && sampledX.length > 0) {
            for (int i = 0; i < sampledX.length; i++) {
                bMinX = Math.min(bMinX, sampledX[i]);
                bMaxX = Math.max(bMaxX, sampledX[i]);
                bMinY = Math.min(bMinY, sampledY[i]);
                bMaxY = Math.max(bMaxY, sampledY[i]);
            }
        }
        if (current != null) {
            bMinX = Math.min(bMinX, current.x());
            bMaxX = Math.max(bMaxX, current.x());
            bMinY = Math.min(bMinY, current.y());
            bMaxY = Math.max(bMaxY, current.y());
        }

        double rawSpanX = bMaxX - bMinX;
        double rawSpanY = bMaxY - bMinY;

        // 2. Enforce non-zero physical span floors to avoid division by zero
        double spanX = Math.max(MIN_PHYSICAL_SPAN_X, rawSpanX);
        double spanY = Math.max(MIN_PHYSICAL_SPAN_Y, rawSpanY);

        // Center the viewport if the raw span was collapsed (e.g. pure vertical launch)
        double centerX = (bMinX + bMaxX) / 2.0;
        double centerY = (bMinY + bMaxY) / 2.0;
        double effMinX = (rawSpanX < MIN_PHYSICAL_SPAN_X) ? (centerX - spanX / 2.0) : bMinX;
        double effMaxX = (rawSpanX < MIN_PHYSICAL_SPAN_X) ? (centerX + spanX / 2.0) : bMaxX;
        double effMinY = (rawSpanY < MIN_PHYSICAL_SPAN_Y) ? (centerY - spanY / 2.0) : bMinY;
        double effMaxY = (rawSpanY < MIN_PHYSICAL_SPAN_Y) ? (centerY + spanY / 2.0) : bMaxY;

        // 3. Calculate isotropic scale (pixels per meter identical on both axes)
        double scaleX = availW / (spanX * PADDING_FACTOR_X);
        double scaleY = availH / (spanY * PADDING_FACTOR_Y);
        double scale = Math.min(scaleX, scaleY);

        // 4. Calculate screen anchor coordinates
        double screenCenterX = left + availW / 2.0;
        double screenCenterY = top + availH / 2.0;
        double originX = screenCenterX - centerX * scale;
        double groundScreenY = screenCenterY + centerY * scale;

        // 5. Generate 1-2-5 decade metric grid ticks
        double xStep = calculateNiceStep(spanX, 5);
        double yStep = calculateNiceStep(spanY, 5);
        List<Tick> xTicks = generateTicks(effMinX, effMaxX, xStep, originX, scale, true);
        List<Tick> yTicks = generateTicks(effMinY, effMaxY, yStep, groundScreenY, scale, false);

        // 6. Calculate event marker positions and collision avoidance
        Optional<EventBadge> apexBadge = Optional.empty();
        Optional<EventBadge> landingBadge = Optional.empty();

        Optional<ProjectileMotion.State> apexState = model.apexState();
        OptionalDouble flightTime = model.timeOfFlight();

        if (apexState.isPresent() && flightTime.isPresent()) {
            ProjectileMotion.State apex = apexState.get();
            ProjectileMotion.State land = model.stateAt(flightTime.getAsDouble());

            double apexScreenX = originX + apex.x() * scale;
            double apexScreenY = groundScreenY - apex.y() * scale;
            double landScreenX = originX + land.x() * scale;
            double landScreenY = groundScreenY - land.y() * scale;

            double dist = Math.hypot(apexScreenX - landScreenX, apexScreenY - landScreenY);

            if (dist < 20.0) {
                // Extremely close: combined badge
                EventBadge combined = new EventBadge("APEX/LANDING", "APEX & LANDING",
                        apex.x(), apex.y(), apexScreenX, apexScreenY,
                        apexScreenX + 8, apexScreenY - 14, BadgePlacement.COMBINED);
                apexBadge = Optional.of(combined);
            } else if (dist < BADGE_COLLISION_DISTANCE) {
                // Close collision: stagger APEX upward, LANDING downward
                EventBadge aBadge = new EventBadge("APEX", "APEX",
                        apex.x(), apex.y(), apexScreenX, apexScreenY,
                        apexScreenX + 8, apexScreenY - 18, BadgePlacement.STAGGERED_UP);
                EventBadge lBadge = new EventBadge("LANDING", "LANDING",
                        land.x(), land.y(), landScreenX, landScreenY,
                        landScreenX + 8, landScreenY + 16, BadgePlacement.STAGGERED_DOWN);
                apexBadge = Optional.of(aBadge);
                landingBadge = Optional.of(lBadge);
            } else {
                // Standard placement
                EventBadge aBadge = new EventBadge("APEX", "APEX",
                        apex.x(), apex.y(), apexScreenX, apexScreenY,
                        apexScreenX + 8, apexScreenY - 9, BadgePlacement.STANDARD);
                EventBadge lBadge = new EventBadge("LANDING", "LANDING",
                        land.x(), land.y(), landScreenX, landScreenY,
                        landScreenX + 8, landScreenY - 9, BadgePlacement.STANDARD);
                apexBadge = Optional.of(aBadge);
                landingBadge = Optional.of(lBadge);
            }
        } else if (apexState.isPresent()) {
            ProjectileMotion.State apex = apexState.get();
            double aScreenX = originX + apex.x() * scale;
            double aScreenY = groundScreenY - apex.y() * scale;
            apexBadge = Optional.of(new EventBadge("APEX", "APEX",
                    apex.x(), apex.y(), aScreenX, aScreenY,
                    aScreenX + 8, aScreenY - 9, BadgePlacement.STANDARD));
        } else if (flightTime.isPresent()) {
            ProjectileMotion.State land = model.stateAt(flightTime.getAsDouble());
            double lScreenX = originX + land.x() * scale;
            double lScreenY = groundScreenY - land.y() * scale;
            landingBadge = Optional.of(new EventBadge("LANDING", "LANDING",
                    land.x(), land.y(), lScreenX, lScreenY,
                    lScreenX + 8, lScreenY - 9, BadgePlacement.STANDARD));
        }

        return new TrajectoryViewport(
                effMinX, effMaxX, effMinY, effMaxY,
                spanX, spanY, scale, originX, groundScreenY,
                left, right, top, bottom, xStep, yStep,
                xTicks, yTicks, apexBadge, landingBadge);
    }

    /**
     * Calculates the nearest 1-2-5 decade step for a given physical span and target tick count.
     */
    public static double calculateNiceStep(double span, int targetTicks) {
        if (!Double.isFinite(span) || span <= 0.0) return 1.0;
        int target = Math.max(1, targetTicks);
        double rawStep = span / target;
        double power = Math.floor(Math.log10(rawStep));
        double fraction = rawStep / Math.pow(10.0, power);
        double niceFraction;
        if (fraction < 1.5) {
            niceFraction = 1.0;
        } else if (fraction < 3.0) {
            niceFraction = 2.0;
        } else if (fraction < 7.0) {
            niceFraction = 5.0;
        } else {
            niceFraction = 10.0;
        }
        return niceFraction * Math.pow(10.0, power);
    }

    /**
     * Formats a tick value consistently according to its decade step.
     */
    public static String formatTick(double value, double step) {
        if (Math.abs(value) < 1.0e-12) return "0";
        if (step >= 1.0) {
            return String.format(Locale.ROOT, "%.0f", value);
        } else if (step >= 0.1) {
            return String.format(Locale.ROOT, "%.1f", value);
        } else if (step >= 0.01) {
            return String.format(Locale.ROOT, "%.2f", value);
        } else {
            return String.format(Locale.ROOT, "%.3f", value);
        }
    }

    private static List<Tick> generateTicks(
            double min, double max, double step, double origin, double scale, boolean isHorizontal) {

        List<Tick> ticks = new ArrayList<>();
        if (!Double.isFinite(step) || step <= 0.0) return ticks;

        long startIdx = (long) Math.ceil((min - 1e-9) / step);
        long endIdx = (long) Math.floor((max + 1e-9) / step);

        // Guard against runaway loops on degenerate inputs
        if (endIdx - startIdx > 50) {
            endIdx = startIdx + 50;
        }

        for (long i = startIdx; i <= endIdx; i++) {
            double val = i * step;
            double screen = isHorizontal ? (origin + val * scale) : (origin - val * scale);
            ticks.add(new Tick(val, formatTick(val, step), screen));
        }
        return ticks;
    }

    public double toScreenX(double physicalX) {
        return originX + physicalX * scale;
    }

    public double toScreenY(double physicalY) {
        return groundScreenY - physicalY * scale;
    }

    public double minX() { return minX; }
    public double maxX() { return maxX; }
    public double minY() { return minY; }
    public double maxY() { return maxY; }
    public double spanX() { return spanX; }
    public double spanY() { return spanY; }
    public double scale() { return scale; }
    public double originX() { return originX; }
    public double groundScreenY() { return groundScreenY; }
    public double left() { return left; }
    public double right() { return right; }
    public double top() { return top; }
    public double bottom() { return bottom; }
    public double xStep() { return xStep; }
    public double yStep() { return yStep; }
    public List<Tick> xTicks() { return xTicks; }
    public List<Tick> yTicks() { return yTicks; }
    public Optional<EventBadge> apexBadge() { return apexBadge; }
    public Optional<EventBadge> landingBadge() { return landingBadge; }

    /**
     * Computes a combined adaptive viewport encompassing two experiments on the exact same coordinate system.
     */
    public static TrajectoryViewport computeCombined(
            ProjectileMotion modelA,
            double[] sampledA_X,
            double[] sampledA_Y,
            ProjectileMotion modelB,
            double[] sampledB_X,
            double[] sampledB_Y,
            double canvasWidth,
            double canvasHeight) {

        Objects.requireNonNull(modelA, "modelA is required");
        Objects.requireNonNull(modelB, "modelB is required");

        double left = 68.0;
        double right = Math.max(left + 100.0, canvasWidth - 28.0);
        double top = 28.0;
        double bottom = Math.max(top + 80.0, canvasHeight - 48.0);
        double availW = right - left;
        double availH = bottom - top;

        double bMinX = Math.min(0.0, Math.min(modelA.initialX(), modelB.initialX()));
        double bMaxX = Math.max(0.0, Math.max(modelA.initialX(), modelB.initialX()));
        double bMinY = Math.min(0.0, Math.min(Math.min(modelA.initialY(), modelA.landingY()),
                                             Math.min(modelB.initialY(), modelB.landingY())));
        double bMaxY = Math.max(0.0, Math.max(Math.max(modelA.initialY(), modelA.landingY()),
                                             Math.max(modelB.initialY(), modelB.landingY())));

        if (sampledA_X != null && sampledA_Y != null) {
            for (int i = 0; i < sampledA_X.length; i++) {
                bMinX = Math.min(bMinX, sampledA_X[i]);
                bMaxX = Math.max(bMaxX, sampledA_X[i]);
                bMinY = Math.min(bMinY, sampledA_Y[i]);
                bMaxY = Math.max(bMaxY, sampledA_Y[i]);
            }
        }
        if (sampledB_X != null && sampledB_Y != null) {
            for (int i = 0; i < sampledB_X.length; i++) {
                bMinX = Math.min(bMinX, sampledB_X[i]);
                bMaxX = Math.max(bMaxX, sampledB_X[i]);
                bMinY = Math.min(bMinY, sampledB_Y[i]);
                bMaxY = Math.max(bMaxY, sampledB_Y[i]);
            }
        }

        double rawSpanX = bMaxX - bMinX;
        double rawSpanY = bMaxY - bMinY;

        double spanX = Math.max(MIN_PHYSICAL_SPAN_X, rawSpanX);
        double spanY = Math.max(MIN_PHYSICAL_SPAN_Y, rawSpanY);

        double centerX = (bMinX + bMaxX) / 2.0;
        double centerY = (bMinY + bMaxY) / 2.0;
        double effMinX = (rawSpanX < MIN_PHYSICAL_SPAN_X) ? (centerX - spanX / 2.0) : bMinX;
        double effMaxX = (rawSpanX < MIN_PHYSICAL_SPAN_X) ? (centerX + spanX / 2.0) : bMaxX;
        double effMinY = (rawSpanY < MIN_PHYSICAL_SPAN_Y) ? (centerY - spanY / 2.0) : bMinY;
        double effMaxY = (rawSpanY < MIN_PHYSICAL_SPAN_Y) ? (centerY + spanY / 2.0) : bMaxY;

        double scaleX = availW / (spanX * PADDING_FACTOR_X);
        double scaleY = availH / (spanY * PADDING_FACTOR_Y);
        double scale = Math.min(scaleX, scaleY);

        double screenCenterX = left + availW / 2.0;
        double screenCenterY = top + availH / 2.0;
        double originX = screenCenterX - centerX * scale;
        double groundScreenY = screenCenterY + centerY * scale;

        double xStep = calculateNiceStep(spanX, 5);
        double yStep = calculateNiceStep(spanY, 5);
        List<Tick> xTicks = generateTicks(effMinX, effMaxX, xStep, originX, scale, true);
        List<Tick> yTicks = generateTicks(effMinY, effMaxY, yStep, groundScreenY, scale, false);

        return new TrajectoryViewport(
                bMinX, bMaxX, bMinY, bMaxY,
                spanX, spanY, scale,
                originX, groundScreenY,
                left, right, top, bottom,
                xStep, yStep,
                xTicks, yTicks,
                Optional.empty(), Optional.empty());
    }
}

