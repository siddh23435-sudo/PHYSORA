package edu.physics.projectile;

/**
 * Immutable planar landing surface in standard SI coordinates (+x right, +y up).
 *
 * <p>A surface is defined either by a constant horizontal height {@code y = landingY}
 * or by an infinite tilted line {@code y(x) = anchorY + (x - anchorX) * tan(beta)}
 * with inclination angle {@code beta} strictly inside {@code (-90, 90)} degrees.
 */
public sealed interface LandingSurface
        permits LandingSurface.HorizontalPlane, LandingSurface.InclinedPlane {

    /**
     * Horizontal datum {@code y = landingY}.
     *
     * @param landingY vertical coordinate of the horizontal surface in metres
     */
    record HorizontalPlane(double landingY) implements LandingSurface {
        public HorizontalPlane {
            if (!Double.isFinite(landingY)) {
                throw new IllegalArgumentException("landing y must be finite");
            }
            if (landingY == 0.0) {
                landingY = 0.0;
            }
        }

        @Override
        public double anchorX() {
            return 0.0;
        }

        @Override
        public double anchorY() {
            return landingY;
        }

        @Override
        public double angleDegrees() {
            return 0.0;
        }

        @Override
        public double slopeTangent() {
            return 0.0;
        }

        @Override
        public double cosine() {
            return 1.0;
        }

        @Override
        public double sine() {
            return 0.0;
        }

        @Override
        public boolean isHorizontal() {
            return true;
        }

        @Override
        public double heightAt(double x) {
            requireFinite("x coordinate", x);
            return landingY;
        }

        @Override
        public double signedPerpendicularDistance(double x, double y) {
            requireFinite("x coordinate", x);
            requireFinite("y coordinate", y);
            return finite(y - landingY, "perpendicular distance");
        }

        @Override
        public double signedDistanceAlongSurface(double fromX, double toX) {
            requireFinite("starting x coordinate", fromX);
            requireFinite("ending x coordinate", toX);
            return normalizeZero(finite(toX - fromX, "surface displacement"));
        }
    }

    /**
     * Inclined planar surface passing through {@code (anchorX, anchorY)} at angle
     * {@code angleDegrees} measured counterclockwise from {@code +x}.
     * Positive angles rise along {@code +x}; negative angles fall along {@code +x}.
     *
     * @param anchorX horizontal reference coordinate on the plane in metres
     * @param anchorY vertical reference coordinate on the plane in metres
     * @param angleDegrees slope angle in degrees strictly within {@code (-90, 90)}
     */
    record InclinedPlane(double anchorX, double anchorY, double angleDegrees) implements LandingSurface {
        public InclinedPlane {
            requireFinite("surface anchor x", anchorX);
            requireFinite("surface anchor y", anchorY);
            requireFinite("incline angle", angleDegrees);
            if (angleDegrees <= -90.0 || angleDegrees >= 90.0) {
                throw new IllegalArgumentException(
                        "Incline angle must lie strictly between -90 and 90 degrees");
            }
            if (anchorX == 0.0) anchorX = 0.0;
            if (anchorY == 0.0) anchorY = 0.0;
            if (angleDegrees == 0.0) angleDegrees = 0.0;
        }

        @Override
        public double slopeTangent() {
            if (angleDegrees == 0.0) {
                return 0.0;
            }
            return finite(Math.tan(Math.toRadians(angleDegrees)), "surface slope tangent");
        }

        @Override
        public double cosine() {
            if (angleDegrees == 0.0) {
                return 1.0;
            }
            return Math.cos(Math.toRadians(angleDegrees));
        }

        @Override
        public double sine() {
            if (angleDegrees == 0.0) {
                return 0.0;
            }
            return Math.sin(Math.toRadians(angleDegrees));
        }

        @Override
        public boolean isHorizontal() {
            return angleDegrees == 0.0;
        }

        @Override
        public double heightAt(double x) {
            requireFinite("x coordinate", x);
            if (angleDegrees == 0.0) {
                return anchorY;
            }
            return finite(Math.fma(x - anchorX, slopeTangent(), anchorY), "surface height");
        }

        @Override
        public double signedPerpendicularDistance(double x, double y) {
            requireFinite("x coordinate", x);
            requireFinite("y coordinate", y);
            if (angleDegrees == 0.0) {
                return finite(y - anchorY, "perpendicular distance");
            }
            double dx = finite(x - anchorX, "surface horizontal offset");
            double dy = finite(y - anchorY, "surface vertical offset");
            return finite(Math.fma(dy, cosine(), -dx * sine()), "perpendicular distance");
        }

        @Override
        public double signedDistanceAlongSurface(double fromX, double toX) {
            requireFinite("starting x coordinate", fromX);
            requireFinite("ending x coordinate", toX);
            double dx = finite(toX - fromX, "surface horizontal span");
            if (dx == 0.0) {
                return 0.0;
            }
            return normalizeZero(finite(dx / cosine(), "surface displacement"));
        }
    }

    /** Creates a horizontal landing surface at {@code y = landingY}. */
    static HorizontalPlane horizontal(double landingY) {
        return new HorizontalPlane(landingY);
    }

    /** Creates an inclined landing surface anchored at {@code (anchorX, anchorY)}. */
    static InclinedPlane incline(double anchorX, double anchorY, double angleDegrees) {
        return new InclinedPlane(anchorX, anchorY, angleDegrees);
    }

    double anchorX();

    double anchorY();

    double angleDegrees();

    double slopeTangent();

    double cosine();

    double sine();

    boolean isHorizontal();

    /** Surface vertical coordinate {@code y_s(x)} in metres. */
    double heightAt(double x);

    /**
     * Signed perpendicular distance from the surface to {@code (x, y)}.
     * Positive values lie above {@code y_s(x)}, zero on the surface, and negative below it.
     */
    double signedPerpendicularDistance(double x, double y);

    /**
     * Signed coordinate distance along the surface tangent directed toward {@code +x}
     * between vertical projections at {@code fromX} and {@code toX}.
     */
    double signedDistanceAlongSurface(double fromX, double toX);

    /** Signed distance along the surface from its anchor {@code anchorX()} to {@code x}. */
    default double signedCoordinateFromAnchor(double x) {
        return signedDistanceAlongSurface(anchorX(), x);
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static double finite(double value, String calculation) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException(calculation + " is outside the supported finite numeric range");
        }
        return value;
    }

    private static double normalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}

