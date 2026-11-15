package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import edu.physics.simulation.Vector2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * High-precision canvas renderer for two-projectile relative motion.
 *
 * <p>Supports Ground Frame S (inertial laboratory) and Observer-Attached Frames S_A and S_B
 * (non-inertial accelerating frames). Features collision-aware label placement to ensure
 * zero label overlap during near-collision and collision states.</p>
 */
public final class TwoProjectileCanvasRenderer {

    private static final Color COLOR_BODY_A = Color.web("#2453d4");       // Cobalt Blue
    private static final Color COLOR_BODY_B = Color.web("#e56843");       // Flame Orange
    private static final Color COLOR_RELATIVE = Color.web("#059669");     // Emerald Green
    private static final Color COLOR_COLLISION = Color.web("#d97706");    // Warm Amber Gold
    private static final Color COLOR_LINE_OF_SIGHT = Color.web("#64748b"); // Slate Gray
    private static final Color COLOR_R_REL = Color.web("#0284c7");        // Sky Blue for r_rel

    private final Canvas canvas = new Canvas(850, 540);

    public Canvas canvas() {
        return canvas;
    }

    public void render(TwoProjectilePursuitModel model, double time, ReferenceFrame frame) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width < 100 || height < 100 || model == null) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(PhysoraTheme.CANVAS);
        gc.fillRect(0, 0, width, height);

        // 1. Calculate physical bounding box for current reference frame
        ViewportBox box = computeViewportBox(model, frame, width, height);

        // 2. Draw Background Grid and Metric Scale
        drawGridAndAxes(gc, box, frame);

        // 3. Render Frame-Specific Trajectories and Entities
        switch (frame) {
            case GROUND -> renderGroundFrame(gc, model, time, box);
            case OBSERVER_A -> renderObserverFrame(gc, model, time, box, true);
            case OBSERVER_B -> renderObserverFrame(gc, model, time, box, false);
        }

        // 4. Draw Header Overlay Badge for Current Frame
        drawFrameBadge(gc, frame);
    }

    private void renderGroundFrame(GraphicsContext gc, TwoProjectilePursuitModel model, double time, ViewportBox box) {
        double duration = model.effectiveDuration();

        // A. Draw Ground Surface Line
        double screenGroundY = box.toScreenY(0.0);
        gc.setStroke(PhysoraTheme.RULE);
        gc.setLineWidth(2.5);
        gc.strokeLine(0, screenGroundY, canvas.getWidth(), screenGroundY);

        // Ground hash lines
        gc.setStroke(Color.web("#cbc4b4", 0.7));
        gc.setLineWidth(1.2);
        for (double gx = 0; gx < canvas.getWidth(); gx += 20) {
            gc.strokeLine(gx, screenGroundY, gx - 8, screenGroundY + 8);
        }

        // B. Draw Parabolic Trajectory Trails
        drawParabolicTrail(gc, model, true, duration, box, COLOR_BODY_A);
        drawParabolicTrail(gc, model, false, duration, box, COLOR_BODY_B);

        // C. Current Positions and Line of Sight
        Vector2D posA = model.positionA(time);
        Vector2D posB = model.positionB(time);
        double screenAx = box.toScreenX(posA.x());
        double screenAy = box.toScreenY(posA.y());
        double screenBx = box.toScreenX(posB.x());
        double screenBy = box.toScreenY(posB.y());

        double dist = posA.distanceTo(posB);

        // Line-of-sight segment connecting A and B
        gc.setStroke(COLOR_LINE_OF_SIGHT);
        gc.setLineWidth(1.5);
        gc.setLineDashes(5.0, 5.0);
        gc.strokeLine(screenAx, screenAy, screenBx, screenBy);
        gc.setLineDashes((double[]) null);

        // Only draw line-of-sight distance label when separation > 10 m to prevent collision clutter
        if (dist > 10.0) {
            double midX = (screenAx + screenBx) / 2.0;
            double midY = (screenAy + screenBy) / 2.0 - 10;
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
            gc.setFill(Color.web("#1e293b"));
            gc.fillText(String.format(Locale.ROOT, "d = %.1f m", dist), midX - 25, midY);
        }

        // D. Draw Velocity Vectors
        drawVelocityVector(gc, posA, model.velocityA(time), box, COLOR_BODY_A, "v_A");
        drawVelocityVector(gc, posB, model.velocityB(time), box, COLOR_BODY_B, "v_B");

        // E. Draw Projectile Disks
        boolean closeToCollision = dist < 6.0;
        drawBodyDisk(gc, screenAx, screenAy, COLOR_BODY_A, closeToCollision ? "" : "A");
        drawBodyDisk(gc, screenBx, screenBy, COLOR_BODY_B, closeToCollision ? "" : "B");

        // F. Collision Marker or Near-Miss Marker
        if (model.hasCollision() && model.collisionTime().isPresent()) {
            double tCol = model.collisionTime().getAsDouble();
            if (time >= tCol - 0.2) {
                Vector2D colPos = model.collisionPositionGround();
                drawCollisionMarker(gc, box.toScreenX(colPos.x()), box.toScreenY(colPos.y()), tCol, "COLLISION POINT");
            }
        } else {
            // Near-miss closest approach marker
            double tMin = model.analyticalResult().tMinInterval();
            double dMin = model.analyticalResult().dMinInterval();
            Vector2D posAClose = model.positionA(tMin);
            Vector2D posBClose = model.positionB(tMin);
            drawNearMissMarker(gc, box.toScreenX(posAClose.x()), box.toScreenY(posAClose.y()),
                    box.toScreenX(posBClose.x()), box.toScreenY(posBClose.y()), dMin, tMin);
        }
    }

    private void renderObserverFrame(GraphicsContext gc,
                                     TwoProjectilePursuitModel model,
                                     double time,
                                     ViewportBox box,
                                     boolean observerIsA) {
        double duration = model.effectiveDuration();

        // A. Full Canvas Crosshairs through Origin (0, 0)
        double originScreenX = box.toScreenX(0.0);
        double originScreenY = box.toScreenY(0.0);

        gc.setStroke(Color.web("#94a3b8", 0.7));
        gc.setLineWidth(1.2);
        gc.strokeLine(0, originScreenY, canvas.getWidth(), originScreenY);
        gc.strokeLine(originScreenX, 0, originScreenX, canvas.getHeight());

        // B. Draw Straight-Line Relative Motion Trail of Moving Body
        Vector2D rRelStart = observerIsA ? model.relativePositionBA(0.0) : model.relativePositionAB(0.0);
        Vector2D rRelEnd = observerIsA ? model.relativePositionBA(duration) : model.relativePositionAB(duration);

        double startX = box.toScreenX(rRelStart.x());
        double startY = box.toScreenY(rRelStart.y());
        double endX = box.toScreenX(rRelEnd.x());
        double endY = box.toScreenY(rRelEnd.y());

        // Solid Emerald Straight-Line Path
        gc.setStroke(COLOR_RELATIVE);
        gc.setLineWidth(3.0);
        gc.strokeLine(startX, startY, endX, endY);

        // Timestamp Tick Marks along the straight-line path to demonstrate uniform speed
        drawTrajectoryTickMarks(gc, model, box, observerIsA, duration);

        // C. Current Moving Body & Separation
        Vector2D rRelCurrent = observerIsA ? model.relativePositionBA(time) : model.relativePositionAB(time);
        Vector2D vRelCurrent = observerIsA ? model.relativeVelocityBA(time) : model.relativeVelocityAB(time);

        double currentX = box.toScreenX(rRelCurrent.x());
        double currentY = box.toScreenY(rRelCurrent.y());
        double dist = rRelCurrent.magnitude();

        Color movingColor = observerIsA ? COLOR_BODY_B : COLOR_BODY_A;
        String movingLabel = observerIsA ? "B" : "A";

        // D. Relative Displacement Vector r_{rel}(t) from (0, 0) to moving body
        drawDisplacementVector(gc, originScreenX, originScreenY, currentX, currentY,
                rRelCurrent, dist, observerIsA ? "r_{B/A}" : "r_{A/B}");

        // E. Relative velocity arrow (strictly CONSTANT vector!)
        String vLabel = observerIsA ? "v_{B/A} (const)" : "v_{A/B} (const)";
        drawRelativeVelocityVector(gc, currentX, currentY, vRelCurrent, box, COLOR_RELATIVE, vLabel);

        // F. Draw moving body disk (omit text when close to origin to avoid overlap)
        boolean nearOrigin = dist < 7.0;
        drawBodyDisk(gc, currentX, currentY, movingColor, nearOrigin ? "" : movingLabel);

        // G. Prominent Observer Anchor Pin at Origin (0, 0) - positioned on LEFT side away from incoming path
        Color observerColor = observerIsA ? COLOR_BODY_A : COLOR_BODY_B;
        String observerName = observerIsA ? "OBSERVER A (ORIGIN)" : "OBSERVER B (ORIGIN)";
        drawObserverOriginPin(gc, originScreenX, originScreenY, observerColor, observerName, rRelStart.x() > 0);

        // H. Collision or Near-Miss Closest Approach Marker
        if (model.hasCollision() && model.collisionTime().isPresent()) {
            double tCol = model.collisionTime().getAsDouble();
            if (time >= tCol - 0.2) {
                drawCollisionMarker(gc, originScreenX, originScreenY, tCol, "COLLISION AT ORIGIN");
            }
        } else {
            // Near-miss perpendicular segment to origin
            double tMin = model.analyticalResult().tMinInterval();
            double dMin = model.analyticalResult().dMinInterval();
            Vector2D rClose = observerIsA ? model.relativePositionBA(tMin) : model.relativePositionAB(tMin);
            double closeX = box.toScreenX(rClose.x());
            double closeY = box.toScreenY(rClose.y());

            // Perpendicular dropped from origin to straight line
            gc.setStroke(Color.web("#e11d48"));
            gc.setLineWidth(1.8);
            gc.setLineDashes(4.0, 4.0);
            gc.strokeLine(originScreenX, originScreenY, closeX, closeY);
            gc.setLineDashes((double[]) null);

            // Right-angle marker circle at closest approach point
            gc.setFill(Color.web("#e11d48"));
            gc.fillOval(closeX - 3.5, closeY - 3.5, 7, 7);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
            gc.fillText(String.format(Locale.ROOT, "d_min = %.2f m (t* = %.2fs)", dMin, tMin),
                    closeX + 8, closeY - 8);
        }
    }

    private void drawTrajectoryTickMarks(GraphicsContext gc,
                                         TwoProjectilePursuitModel model,
                                         ViewportBox box,
                                         boolean observerIsA,
                                         double duration) {
        int intervals = Math.min(8, Math.max(4, (int) Math.round(duration * 1.5)));
        gc.setFill(Color.web("#065f46"));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9.5));

        for (int i = 0; i <= intervals; i++) {
            double t = duration * i / intervals;
            Vector2D r = observerIsA ? model.relativePositionBA(t) : model.relativePositionAB(t);
            double sx = box.toScreenX(r.x());
            double sy = box.toScreenY(r.y());

            // Tick dot
            gc.fillOval(sx - 2.5, sy - 2.5, 5, 5);

            // Time label only on alternate ticks to prevent crowding
            if (i > 0 && i < intervals && (i % 2 == 0)) {
                gc.fillText(String.format(Locale.ROOT, "t=%.1fs", t), sx - 14, sy + 14);
            }
        }
    }

    private void drawObserverOriginPin(GraphicsContext gc,
                                       double sx,
                                       double sy,
                                       Color color,
                                       String name,
                                       boolean incomingFromRight) {
        double r = 11.0;

        // Glowing outer halo
        gc.setFill(color.deriveColor(0, 1, 1, 0.20));
        gc.fillOval(sx - r - 5, sy - r - 5, (r + 5) * 2, (r + 5) * 2);

        // Concentric target rings
        gc.setStroke(color);
        gc.setLineWidth(2.0);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
        gc.strokeOval(sx - r * 0.5, sy - r * 0.5, r, r);

        // Solid center pin
        gc.setFill(color);
        gc.fillOval(sx - 3, sy - 3, 6, 6);

        // Origin text placed away from incoming trajectory side to prevent any overlap
        double labelX = incomingFromRight ? (sx - 180) : (sx + r + 8);
        double labelY = sy + 18;

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        gc.setFill(PhysoraTheme.INK);
        gc.fillText("⦿ " + name + " (0, 0)", labelX, labelY);

        gc.setFont(Font.font("Consolas", 9.5));
        gc.setFill(Color.web("#64748b"));
        gc.fillText("[a_origin = g \u2193]", labelX, labelY + 13);
    }

    private void drawDisplacementVector(GraphicsContext gc,
                                        double ox, double oy,
                                        double ex, double ey,
                                        Vector2D rRel,
                                        double dist,
                                        String label) {
        if (dist < 0.2) return;

        // Translucent directed segment for r_rel
        gc.setStroke(COLOR_R_REL);
        gc.setLineWidth(1.8);
        gc.setLineDashes(5.0, 4.0);
        gc.strokeLine(ox, oy, ex, ey);
        gc.setLineDashes((double[]) null);

        // Arrowhead
        double angle = Math.atan2(ey - oy, ex - ox);
        double headLen = 8.0;
        gc.setFill(COLOR_R_REL);
        gc.fillPolygon(
                new double[]{ex, ex - headLen * Math.cos(angle - Math.PI / 6), ex - headLen * Math.cos(angle + Math.PI / 6)},
                new double[]{ey, ey - headLen * Math.sin(angle - Math.PI / 6), ey - headLen * Math.sin(angle + Math.PI / 6)},
                3
        );

        // Only draw distance text when separation > 14 m to prevent crowding near origin
        if (dist >= 14.0) {
            double midX = (ox + ex) / 2.0;
            double midY = (oy + ey) / 2.0 - 8;
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
            gc.setFill(COLOR_R_REL);
            gc.fillText(String.format(Locale.ROOT, "d = %.1f m", dist), midX - 25, midY);
        }
    }

    private void drawRelativeVelocityVector(GraphicsContext gc,
                                            double sx, double sy,
                                            Vector2D vel,
                                            ViewportBox box,
                                            Color color,
                                            String label) {
        double velScale = 1.2;
        double ex = sx + vel.x() * box.scale * (velScale / 10.0);
        double ey = sy - vel.y() * box.scale * (velScale / 10.0);

        gc.setStroke(color);
        gc.setLineWidth(2.6);
        gc.strokeLine(sx, sy, ex, ey);

        double angle = Math.atan2(ey - sy, ex - sx);
        double headLen = 9.0;
        gc.setFill(color);
        gc.fillPolygon(
                new double[]{ex, ex - headLen * Math.cos(angle - Math.PI / 6), ex - headLen * Math.cos(angle + Math.PI / 6)},
                new double[]{ey, ey - headLen * Math.sin(angle - Math.PI / 6), ey - headLen * Math.sin(angle + Math.PI / 6)},
                3
        );

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        // Offset perpendicular to arrow to avoid line overlap
        double perpAngle = angle - Math.PI / 2.0;
        gc.fillText(label, ex + 8 * Math.cos(perpAngle), ey + 8 * Math.sin(perpAngle));
    }

    private void drawParabolicTrail(GraphicsContext gc,
                                    TwoProjectilePursuitModel model,
                                    boolean forBodyA,
                                    double duration,
                                    ViewportBox box,
                                    Color color) {
        int steps = 120;
        gc.setStroke(color);
        gc.setLineWidth(2.2);

        Vector2D prev = forBodyA ? model.positionA(0.0) : model.positionB(0.0);
        for (int i = 1; i <= steps; i++) {
            double t = duration * i / steps;
            Vector2D curr = forBodyA ? model.positionA(t) : model.positionB(t);
            gc.strokeLine(box.toScreenX(prev.x()), box.toScreenY(prev.y()),
                    box.toScreenX(curr.x()), box.toScreenY(curr.y()));
            prev = curr;
        }
    }

    private void drawBodyDisk(GraphicsContext gc, double sx, double sy, Color color, String label) {
        double r = 8.0;
        // Outer halo
        gc.setFill(color.deriveColor(0, 1, 1, 0.28));
        gc.fillOval(sx - r - 4, sy - r - 4, (r + 4) * 2, (r + 4) * 2);

        // Core disk
        gc.setFill(color);
        gc.fillOval(sx - r, sy - r, r * 2, r * 2);

        // White border
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.6);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);

        // Label (if not suppressed)
        if (!label.isEmpty()) {
            gc.setFill(PhysoraTheme.INK);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
            gc.fillText(label, sx + r + 5, sy - 6);
        }
    }

    private void drawVelocityVector(GraphicsContext gc,
                                    Vector2D originPos,
                                    Vector2D vel,
                                    ViewportBox box,
                                    Color color,
                                    String label) {
        double sx = box.toScreenX(originPos.x());
        double sy = box.toScreenY(originPos.y());

        double velScale = 1.2;
        double targetX = originPos.x() + vel.x() * velScale;
        double targetY = originPos.y() + vel.y() * velScale;
        double ex = box.toScreenX(targetX);
        double ey = box.toScreenY(targetY);

        gc.setStroke(color);
        gc.setLineWidth(2.0);
        gc.strokeLine(sx, sy, ex, ey);

        // Arrowhead
        double angle = Math.atan2(ey - sy, ex - sx);
        double headLen = 8.0;
        gc.setFill(color);
        gc.fillPolygon(
                new double[]{ex, ex - headLen * Math.cos(angle - Math.PI / 6), ex - headLen * Math.cos(angle + Math.PI / 6)},
                new double[]{ey, ey - headLen * Math.sin(angle - Math.PI / 6), ey - headLen * Math.sin(angle + Math.PI / 6)},
                3
        );

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.fillText(label, ex + 6 * Math.cos(angle), ey + 6 * Math.sin(angle));
    }

    private void drawCollisionMarker(GraphicsContext gc, double sx, double sy, double tCol, String title) {
        // Outer pulsing ring
        gc.setStroke(COLOR_COLLISION);
        gc.setLineWidth(2.4);
        gc.strokeOval(sx - 16, sy - 16, 32, 32);

        // Glowing star disk
        gc.setFill(COLOR_COLLISION.deriveColor(0, 1, 1, 0.45));
        gc.fillOval(sx - 10, sy - 10, 20, 20);
        gc.setFill(Color.web("#92400e"));
        gc.fillOval(sx - 4, sy - 4, 8, 8);

        // Compact pill banner centered above marker
        String text = String.format(Locale.ROOT, "★ %s (t* = %.2fs)", title, tCol);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        gc.setFill(Color.web("#78350f"));
        gc.fillText(text, sx - 68, sy - 22);
    }

    private void drawNearMissMarker(GraphicsContext gc,
                                    double ax, double ay,
                                    double bx, double by,
                                    double dMin, double tMin) {
        // Line connecting closest approach
        gc.setStroke(Color.web("#e11d48"));
        gc.setLineWidth(1.8);
        gc.setLineDashes(4.0, 4.0);
        gc.strokeLine(ax, ay, bx, by);
        gc.setLineDashes((double[]) null);

        double midX = (ax + bx) / 2.0;
        double midY = (ay + by) / 2.0 - 10;
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        gc.setFill(Color.web("#be123c"));
        gc.fillText(String.format(Locale.ROOT, "d_min = %.2f m at t* = %.2fs", dMin, tMin),
                midX - 60, midY);
    }

    private void drawFrameBadge(GraphicsContext gc, ReferenceFrame frame) {
        // Clean Pedagogical Overlay Card in Upper Left
        double boxW = 440;
        double boxH = 52;

        gc.setFill(Color.web("#142e4b", 0.94));
        gc.fillRoundRect(16, 16, boxW, boxH, 6, 6);

        gc.setStroke(Color.web("#3b82f6"));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(16, 16, boxW, boxH, 6, 6);

        // Classification Pill
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        gc.setFill(Color.web("#d9f26a"));
        gc.fillText("VIEWING: " + frame.displayName().toUpperCase(), 26, 34);

        // Core Physics Principle
        gc.setFont(Font.font("Segoe UI", 10.5));
        gc.setFill(Color.web("#e2e8f0"));
        gc.fillText(frame.pedagogicalPrinciple(), 26, 52);
    }

    private void drawGridAndAxes(GraphicsContext gc, ViewportBox box, ReferenceFrame frame) {
        gc.setStroke(PhysoraTheme.GRID);
        gc.setLineWidth(0.8);

        // 10-meter grid lines
        double step = 10.0;
        double startX = Math.floor(box.minX / step) * step;
        double endX = Math.ceil(box.maxX / step) * step;
        double startY = Math.floor(box.minY / step) * step;
        double endY = Math.ceil(box.maxY / step) * step;

        gc.setFont(Font.font("Consolas", 9.5));
        gc.setFill(Color.web("#94a3b8"));

        for (double x = startX; x <= endX; x += step) {
            double sx = box.toScreenX(x);
            if (sx >= 0 && sx <= canvas.getWidth()) {
                gc.strokeLine(sx, 0, sx, canvas.getHeight());
                if (Math.abs(x) > 1e-6) {
                    gc.fillText(String.format(Locale.ROOT, "%.0fm", x), sx + 2, canvas.getHeight() - 6);
                }
            }
        }

        for (double y = startY; y <= endY; y += step) {
            double sy = box.toScreenY(y);
            if (sy >= 0 && sy <= canvas.getHeight()) {
                gc.strokeLine(0, sy, canvas.getWidth(), sy);
                if (Math.abs(y) > 1e-6) {
                    gc.fillText(String.format(Locale.ROOT, "%.0fm", y), 4, sy - 2);
                }
            }
        }
    }

    private ViewportBox computeViewportBox(TwoProjectilePursuitModel model,
                                          ReferenceFrame frame,
                                          double canvasW,
                                          double canvasH) {
        double duration = model.effectiveDuration();
        double minX;
        double maxX;
        double minY;
        double maxY;

        if (frame == ReferenceFrame.GROUND) {
            minX = Math.min(model.bodyA().initialPosition().x(), model.bodyB().initialPosition().x());
            maxX = Math.max(model.bodyA().initialPosition().x(), model.bodyB().initialPosition().x());
            minY = 0.0;
            maxY = 0.0;

            for (double t = 0; t <= duration; t += duration / 50.0) {
                Vector2D a = model.positionA(t);
                Vector2D b = model.positionB(t);
                minX = Math.min(minX, Math.min(a.x(), b.x()));
                maxX = Math.max(maxX, Math.max(a.x(), b.x()));
                maxY = Math.max(maxY, Math.max(a.y(), b.y()));
            }
        } else if (frame == ReferenceFrame.OBSERVER_A) {
            minX = 0.0;
            maxX = 0.0;
            minY = 0.0;
            maxY = 0.0;

            for (double t = 0; t <= duration; t += duration / 20.0) {
                Vector2D r = model.relativePositionBA(t);
                minX = Math.min(minX, r.x());
                maxX = Math.max(maxX, r.x());
                minY = Math.min(minY, r.y());
                maxY = Math.max(maxY, r.y());
            }
        } else {
            minX = 0.0;
            maxX = 0.0;
            minY = 0.0;
            maxY = 0.0;

            for (double t = 0; t <= duration; t += duration / 20.0) {
                Vector2D r = model.relativePositionAB(t);
                minX = Math.min(minX, r.x());
                maxX = Math.max(maxX, r.x());
                minY = Math.min(minY, r.y());
                maxY = Math.max(maxY, r.y());
            }
        }

        double spanX = Math.max(30.0, maxX - minX);
        double spanY = Math.max(25.0, maxY - minY);
        double paddingFactor = 1.35; // 35% margin padding

        double padSpanX = spanX * paddingFactor;
        double padSpanY = spanY * paddingFactor;

        double scaleX = canvasW / padSpanX;
        double scaleY = canvasH / padSpanY;
        double scale = Math.min(scaleX, scaleY);

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;

        return new ViewportBox(minX, maxX, minY, maxY, scale, centerX, centerY, canvasW, canvasH);
    }

    private static final class ViewportBox {
        final double minX, maxX, minY, maxY;
        final double scale;
        final double originX, originY;

        ViewportBox(double minX, double maxX, double minY, double maxY,
                    double scale, double centerX, double centerY,
                    double canvasW, double canvasH) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.scale = scale;
            this.originX = (canvasW / 2.0) - centerX * scale;
            this.originY = (canvasH / 2.0) + centerY * scale;
        }

        double toScreenX(double physX) {
            return originX + physX * scale;
        }

        double toScreenY(double physY) {
            return originY - physY * scale;
        }
    }
}
