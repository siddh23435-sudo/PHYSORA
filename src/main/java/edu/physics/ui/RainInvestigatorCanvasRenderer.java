package edu.physics.ui;

import edu.physics.relative.RainInvestigatorResult;
import edu.physics.relative.RainInvestigatorSessionModel;
import edu.physics.relative.RainInvestigatorStage;
import edu.physics.relative.RainInvestigatorStatus;
import edu.physics.simulation.Vector2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Locale;

/**
 * Velocity-space interactive 2D canvas renderer for the Inverse Rain Investigator.
 *
 * <p>Coordinates and Visual Geometry:
 * <ul>
 *   <li>Horizontal Axis: Ground-frame horizontal rain velocity {@code v_rx} (m/s). Positive is rightward/east.</li>
 *   <li>Vertical Axis: Ground-frame vertical rain velocity {@code v_ry} (m/s). Physical falling rain has {@code v_ry < 0} (downward).</li>
 *   <li>Observation Line 1: {@code v_rx - v_ry * tan(phi_1) = v_1}. Passes through observer velocity {@code (v_1, 0)}.</li>
 *   <li>Observation Line 2: {@code v_rx - v_ry * tan(phi_2) = v_2}. Passes through observer velocity {@code (v_2, 0)}.</li>
 *   <li>Physical Admissible Region: Shaded lower half-plane {@code v_ry < 0} (downward speed {@code v_d = -v_ry > 0}).</li>
 *   <li>Unphysical Region: Upper half-plane {@code v_ry > 0} (upward falling rain).</li>
 *   <li>Stage 2 (Ambiguity): Observation 2 is hidden; displays candidate solutions along Line 1.</li>
 *   <li>Stage 3+ (Geometry): Displays both lines and highlights their intersection or degeneracy.</li>
 * </ul>
 * </p>
 */
public final class RainInvestigatorCanvasRenderer {

    // Palette
    private static final Color BG_DARK = Color.web("#0f172a");
    private static final Color ADMISSIBLE_BG = Color.web("#131f37");
    private static final Color UNPHYSICAL_BG = Color.web("#1c1917");
    private static final Color GRID_COLOR = Color.web("#1e293b");
    private static final Color AXIS_COLOR = Color.web("#475569");
    private static final Color LINE1_COLOR = Color.web("#38bdf8"); // Sky Blue
    private static final Color LINE2_COLOR = Color.web("#f59e0b"); // Vibrant Amber
    private static final Color RECONSTRUCTED_COLOR = Color.web("#10b981"); // Emerald Green
    private static final Color CANDIDATE_COLOR = Color.web("#22d3ee"); // Cyan
    private static final Color ERROR_COLOR = Color.web("#ef4444"); // Red

    private final Canvas canvas = new Canvas(600, 380);
    private double candidateVDown = 8.0;

    public RainInvestigatorCanvasRenderer() {
        canvas.setId("rainInvestigatorCanvas");
    }

    public Canvas canvas() {
        return canvas;
    }

    public double candidateVDown() {
        return candidateVDown;
    }

    public void setCandidateVDown(double candidateVDown) {
        this.candidateVDown = Math.max(1.0, candidateVDown);
    }

    /**
     * Renders the complete velocity-space diagram for the current session model state.
     */
    public void render(RainInvestigatorSessionModel model) {
        if (model == null) return;

        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 10 || height <= 10) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);

        // 1. Determine bounding box in velocity space (vx, vy in m/s)
        double v1 = model.vPerson1();
        double phi1 = model.angleRad1();
        double v2 = model.vPerson2();
        double phi2 = model.angleRad2();
        boolean obs2Visible = model.isObservation2Visible();
        RainInvestigatorResult result = model.evaluatorResult();
        RainInvestigatorStage stage = model.stage();

        // Bounds gathering
        double minVx = Math.min(-15.0, Math.min(v1 - 5.0, obs2Visible ? v2 - 5.0 : v1 - 5.0));
        double maxVx = Math.max(15.0, Math.max(v1 + 5.0, obs2Visible ? v2 + 5.0 : v1 + 5.0));
        double minVy = -25.0; // Downward rain (negative vy)
        double maxVy = 8.0;   // Upward rain margin (positive vy)

        if (result != null && result.isSolvable() && result.reconstructedRainVelocity() != null) {
            Vector2D rec = result.reconstructedRainVelocity();
            minVx = Math.min(minVx, rec.x() - 6.0);
            maxVx = Math.max(maxVx, rec.x() + 6.0);
            minVy = Math.min(minVy, rec.y() - 6.0);
            maxVy = Math.max(maxVy, Math.max(8.0, rec.y() + 6.0));
        }

        if (stage == RainInvestigatorStage.STAGE_2_AMBIGUITY) {
            try {
                Vector2D cand = model.getCandidateRainVelocityForDownwardSpeed(candidateVDown);
                minVx = Math.min(minVx, cand.x() - 5.0);
                maxVx = Math.max(maxVx, cand.x() + 5.0);
                minVy = Math.min(minVy, -candidateVDown - 5.0);
            } catch (Exception ignored) {}
        }

        double spanX = maxVx - minVx;
        double spanVy = maxVy - minVy;

        double padding = 45.0;
        double availW = width - (padding * 2.0);
        double availH = height - (padding * 2.0);

        // Isotropic scale: pixels per (m/s)
        double scale = Math.min(availW / spanX, availH / spanVy);
        scale = Math.max(5.0, Math.min(scale, 28.0)); // sensible bounds

        // Center mapping
        double midVx = (minVx + maxVx) / 2.0;
        double midVy = (minVy + maxVy) / 2.0;
        double originX = (width / 2.0) - (midVx * scale);
        double originY = (height / 2.0) + (midVy * scale); // Screen y is inverted (vy > 0 is UP)

        // 2. Background Shading
        // Base dark
        gc.setFill(BG_DARK);
        gc.fillRect(0, 0, width, height);

        // Admissible region (vy <= 0, below y=originY on screen)
        double yZeroScreen = originY;
        if (yZeroScreen < height) {
            gc.setFill(ADMISSIBLE_BG);
            gc.fillRect(0, Math.max(0, yZeroScreen), width, height - Math.max(0, yZeroScreen));
        }

        // Unphysical region (vy > 0, above y=originY on screen)
        if (yZeroScreen > 0) {
            gc.setFill(UNPHYSICAL_BG);
            gc.fillRect(0, 0, width, Math.min(height, yZeroScreen));
        }

        // Region Watermark Badges
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);

        gc.setFill(Color.web("#334155"));
        gc.fillText("▲ UNPHYSICAL REGION (Upward Rain: v_y > 0 / v_down < 0)", 14, 12);

        gc.setFill(Color.web("#38bdf8", 0.5));
        gc.fillText("▼ PHYSICALLY ADMISSIBLE REGION (Falling Rain: v_y < 0 / v_down > 0)", 14, Math.max(26.0, yZeroScreen + 8.0));

        // 3. Coordinate Grid
        gc.setLineWidth(0.8);
        gc.setStroke(GRID_COLOR);
        gc.setFill(Color.web("#64748b"));
        gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 9.5));

        // Grid step: 5 m/s or 2 m/s based on scale
        double step = scale > 14.0 ? 2.0 : 5.0;

        // Vertical grid lines (constant vx)
        double startX = Math.floor(minVx / step) * step;
        for (double vx = startX; vx <= maxVx + step; vx += step) {
            double sx = originX + (vx * scale);
            if (sx >= 0 && sx <= width) {
                gc.strokeLine(sx, 0, sx, height);
                if (Math.abs(vx) > 1e-6) {
                    gc.fillText(String.format(Locale.ROOT, "%.0f", vx), sx + 2, originY + 3);
                }
            }
        }

        // Horizontal grid lines (constant vy)
        double startY = Math.floor(minVy / step) * step;
        for (double vy = startY; vy <= maxVy + step; vy += step) {
            double sy = originY - (vy * scale);
            if (sy >= 0 && sy <= height) {
                gc.strokeLine(0, sy, width, sy);
                if (Math.abs(vy) > 1e-6) {
                    gc.fillText(String.format(Locale.ROOT, "%.0f", vy), originX + 3, sy - 2);
                }
            }
        }

        // 4. Main Axes (v_rx, v_ry)
        gc.setLineWidth(1.6);
        gc.setStroke(AXIS_COLOR);

        // Horizontal axis (v_ry = 0)
        gc.strokeLine(0, originY, width, originY);
        // Arrow for +v_rx
        drawArrowHead(gc, width - 4, originY, width, originY, 6.0, AXIS_COLOR);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.web("#94a3b8"));
        gc.fillText("v_rx (m/s) [Ground Horizontal Rain]", width - 12, originY - 8);

        // Vertical axis (v_rx = 0)
        gc.strokeLine(originX, height, originX, 0);
        // Arrow for +v_ry
        drawArrowHead(gc, originX, 6, originX, 0, 6.0, AXIS_COLOR);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("v_ry (m/s)", originX + 8, 12);

        // Origin label
        gc.fillText("(0,0)", originX + 4, originY + 12);

        // 5. Ground Rain Boundary Line (v_ry = 0) highlight
        gc.setLineWidth(1.2);
        gc.setLineDashes(4.0, 4.0);
        gc.setStroke(Color.web("#64748b"));
        gc.strokeLine(0, originY, width, originY);
        gc.setLineDashes(null);

        // 6. Draw Observation Line 1
        // Equation: v_rx - v_ry * tan(phi_1) = v_1  ==>  vx = v_1 + vy * tan(phi_1)
        double tan1 = Math.tan(phi1);
        double lineYTop = maxVy + 5.0;
        double lineYBottom = minVy - 5.0;

        double line1XTop = v1 + lineYTop * tan1;
        double line1XBottom = v1 + lineYBottom * tan1;

        double sLine1X1 = originX + (line1XTop * scale);
        double sLine1Y1 = originY - (lineYTop * scale);
        double sLine1X2 = originX + (line1XBottom * scale);
        double sLine1Y2 = originY - (lineYBottom * scale);

        gc.setLineWidth(2.4);
        gc.setStroke(LINE1_COLOR);
        gc.strokeLine(sLine1X1, sLine1Y1, sLine1X2, sLine1Y2);

        // Observer 1 Marker at (v1, 0)
        double sObs1X = originX + (v1 * scale);
        double sObs1Y = originY;
        gc.setFill(LINE1_COLOR);
        gc.fillOval(sObs1X - 5.0, sObs1Y - 5.0, 10.0, 10.0);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.strokeOval(sObs1X - 5.0, sObs1Y - 5.0, 10.0, 10.0);

        // Observer 1 Callout
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(LINE1_COLOR);
        gc.fillText(String.format(Locale.ROOT, "Observer 1 (v₁ = %.1f m/s, φ₁ = %.1f°)", v1, Math.toDegrees(phi1)),
                sObs1X, sObs1Y - 14.0);

        // 7. Stage 2: Ambiguity Demonstration
        if (stage == RainInvestigatorStage.STAGE_2_AMBIGUITY) {
            renderAmbiguityStage(gc, model, originX, originY, scale);
            return;
        }

        // 8. Draw Observation Line 2 (Stages 1, 3, 4, 5)
        if (obs2Visible) {
            double tan2 = Math.tan(phi2);
            double line2XTop = v2 + lineYTop * tan2;
            double line2XBottom = v2 + lineYBottom * tan2;

            double sLine2X1 = originX + (line2XTop * scale);
            double sLine2Y1 = originY - (lineYTop * scale);
            double sLine2X2 = originX + (line2XBottom * scale);
            double sLine2Y2 = originY - (lineYBottom * scale);

            gc.setLineWidth(2.4);
            gc.setStroke(LINE2_COLOR);
            gc.strokeLine(sLine2X1, sLine2Y1, sLine2X2, sLine2Y2);

            // Observer 2 Marker at (v2, 0)
            double sObs2X = originX + (v2 * scale);
            double sObs2Y = originY;
            gc.setFill(LINE2_COLOR);
            gc.fillOval(sObs2X - 5.0, sObs2Y - 5.0, 10.0, 10.0);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.2);
            gc.strokeOval(sObs2X - 5.0, sObs2Y - 5.0, 10.0, 10.0);

            // Observer 2 Callout
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(LINE2_COLOR);
            gc.fillText(String.format(Locale.ROOT, "Observer 2 (v₂ = %.1f m/s, φ₂ = %.1f°)", v2, Math.toDegrees(phi2)),
                    sObs2X, sObs2Y + 20.0);
        }

        // 9. Intersection & Reconstructed Vector
        if (result != null && result.isSolvable() && result.reconstructedRainVelocity() != null && obs2Visible) {
            Vector2D rec = result.reconstructedRainVelocity();
            double sRecX = originX + (rec.x() * scale);
            double sRecY = originY - (rec.y() * scale);

            // Ground rain vector arrow from (0,0) to (v_rx, v_ry)
            gc.setLineWidth(3.0);
            gc.setStroke(RECONSTRUCTED_COLOR);
            gc.strokeLine(originX, originY, sRecX, sRecY);
            drawArrowHead(gc, originX, originY, sRecX, sRecY, 9.0, RECONSTRUCTED_COLOR);

            // Dashed projection lines to axes
            gc.setLineWidth(1.0);
            gc.setLineDashes(3.0, 3.0);
            gc.setStroke(Color.web("#10b981", 0.6));
            gc.strokeLine(sRecX, sRecY, sRecX, originY); // to vx axis
            gc.strokeLine(sRecX, sRecY, originX, sRecY); // to vy axis
            gc.setLineDashes(null);

            // Concentric target rings at intersection
            gc.setFill(Color.web("#10b981", 0.25));
            gc.fillOval(sRecX - 12, sRecY - 12, 24, 24);
            gc.setStroke(RECONSTRUCTED_COLOR);
            gc.setLineWidth(1.8);
            gc.strokeOval(sRecX - 12, sRecY - 12, 24, 24);
            gc.strokeOval(sRecX - 6, sRecY - 6, 12, 12);
            gc.setFill(Color.WHITE);
            gc.fillOval(sRecX - 3, sRecY - 3, 6, 6);

            // Callout Badge for Reconstructed Rain
            String labelText = String.format(Locale.ROOT,
                    "RECONSTRUCTED TRUE RAIN: v_r = (%.2f, %.2f) m/s [|v_r| = %.2f m/s]",
                    rec.x(), rec.y(), rec.magnitude());
            drawPillBadge(gc, sRecX + 16, sRecY - 14, labelText,
                    Color.web("#064e3b"), Color.web("#059669"), Color.web("#ecfdf5"));

            // Ill-conditioned sensitivity warning badge
            if (result.status() == RainInvestigatorStatus.ILL_CONDITIONED) {
                String sensText = String.format(Locale.ROOT,
                        "⚠ ILL-CONDITIONED: κ_down = %.1f (High Angular Sensitivity)",
                        result.downwardConditionNumber());
                drawPillBadge(gc, sRecX + 16, sRecY + 12, sensText,
                        Color.web("#78350f"), Color.web("#d97706"), Color.web("#fef3c7"));
            }
        } else if (result != null && !result.isSolvable() && obs2Visible) {
            // Unsolvable state handling
            renderUnsolvableDiagnostics(gc, result, model, originX, originY, scale, width, height);
        }
    }

    private void renderAmbiguityStage(GraphicsContext gc, RainInvestigatorSessionModel model,
                                      double originX, double originY, double scale) {
        double v1 = model.vPerson1();
        double phi1 = model.angleRad1();

        // 1. Prominent Stage 2 Banner on Canvas
        drawPillBadge(gc, 20, 36,
                "STAGE 2: AMBIGUITY — Observation 2 is hidden. 1 Observation permits infinitely many solutions along Line 1.",
                Color.web("#164e63"), Color.web("#0891b2"), Color.web("#cffafe"));

        // 2. Sample candidate points along Line 1 for downward speeds: 4, 8, 12, 16 m/s
        double[] sampleVDown = {4.0, 8.0, 12.0, 16.0};
        char[] letters = {'A', 'B', 'C', 'D'};

        for (int i = 0; i < sampleVDown.length; i++) {
            double vd = sampleVDown[i];
            try {
                Vector2D cand = model.getCandidateRainVelocityForDownwardSpeed(vd);
                double sx = originX + (cand.x() * scale);
                double sy = originY - (cand.y() * scale);

                // Dashed vector from origin
                gc.setLineWidth(1.2);
                gc.setLineDashes(4.0, 4.0);
                gc.setStroke(Color.web("#06b6d4", 0.6));
                gc.strokeLine(originX, originY, sx, sy);
                gc.setLineDashes(null);

                // Candidate marker
                gc.setFill(Color.web("#0891b2"));
                gc.fillOval(sx - 4.5, sy - 4.5, 9, 9);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.2);
                gc.strokeOval(sx - 4.5, sy - 4.5, 9, 9);

                // Label
                gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9.5));
                gc.setFill(Color.web("#a5f3fc"));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(String.format(Locale.ROOT, "Candidate %c: (%.1f, -%.1f) m/s", letters[i], cand.x(), vd),
                        sx + 8, sy - 4);
            } catch (Exception ignored) {}
        }

        // 3. Highlighted active candidate point
        try {
            Vector2D activeCand = model.getCandidateRainVelocityForDownwardSpeed(candidateVDown);
            double sActX = originX + (activeCand.x() * scale);
            double sActY = originY - (activeCand.y() * scale);

            // Active candidate vector
            gc.setLineWidth(2.4);
            gc.setStroke(CANDIDATE_COLOR);
            gc.strokeLine(originX, originY, sActX, sActY);
            drawArrowHead(gc, originX, originY, sActX, sActY, 8.0, CANDIDATE_COLOR);

            // Pulsing target circle
            gc.setFill(Color.web("#22d3ee", 0.3));
            gc.fillOval(sActX - 10, sActY - 10, 20, 20);
            gc.setStroke(CANDIDATE_COLOR);
            gc.setLineWidth(1.6);
            gc.strokeOval(sActX - 10, sActY - 10, 20, 20);
            gc.setFill(Color.WHITE);
            gc.fillOval(sActX - 3, sActY - 3, 6, 6);

            // Active badge
            String actText = String.format(Locale.ROOT,
                    "ACTIVE CANDIDATE: v_r = (%.2f, -%.2f) m/s [Apparent angle for Obs 1 is exactly %.1f°!]",
                    activeCand.x(), candidateVDown, Math.toDegrees(phi1));
            drawPillBadge(gc, sActX + 14, sActY + 12, actText,
                    Color.web("#155e75"), Color.web("#06b6d4"), Color.web("#ecfeff"));
        } catch (Exception ignored) {}
    }

    private void renderUnsolvableDiagnostics(GraphicsContext gc, RainInvestigatorResult result,
                                             RainInvestigatorSessionModel model,
                                             double originX, double originY, double scale,
                                             double width, double height) {
        RainInvestigatorStatus status = result.status();

        if (status == RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS) {
            drawPillBadge(gc, 20, 36,
                    "PARALLEL OBSERVATION LINES: tan(φ₁) = tan(φ₂). Observation rays do not intersect.",
                    Color.web("#831843"), Color.web("#db2777"), Color.web("#fdf2f8"));
        } else if (status == RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS) {
            drawPillBadge(gc, 20, 36,
                    "IDENTICAL OBSERVER VELOCITIES: v₁ = v₂ provides zero vertical baseline resolution.",
                    Color.web("#78350f"), Color.web("#d97706"), Color.web("#fffbeb"));
        } else if (status == RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION) {
            // Lines intersect in the upper unphysical half-plane (vy > 0)
            double v1 = model.vPerson1();
            double v2 = model.vPerson2();
            double tan1 = Math.tan(model.angleRad1());
            double tan2 = Math.tan(model.angleRad2());
            double denom = tan2 - tan1;

            if (Math.abs(denom) > 1e-9) {
                double vDown = (v2 - v1) / denom; // negative
                double vx = v1 - vDown * tan1;
                double vy = -vDown; // positive (upward)

                double sX = originX + (vx * scale);
                double sY = originY - (vy * scale); // in top region

                // Draw X marker at unphysical intersection
                gc.setLineWidth(2.5);
                gc.setStroke(ERROR_COLOR);
                gc.strokeLine(sX - 8, sY - 8, sX + 8, sY + 8);
                gc.strokeLine(sX - 8, sY + 8, sX + 8, sY - 8);

                drawPillBadge(gc, sX + 12, sY - 10,
                        String.format(Locale.ROOT, "UNPHYSICAL INTERSECTION: v_y = +%.2f m/s (Rain Falling Upward!)", vy),
                        Color.web("#7f1d1d"), Color.web("#ef4444"), Color.web("#fef2f2"));
            }

            drawPillBadge(gc, 20, 36,
                    "INCONSISTENT DIRECTIONS: Mathematical intersection requires upward falling rain (v_down <= 0).",
                    Color.web("#7f1d1d"), Color.web("#dc2626"), Color.web("#fef2f2"));
        } else {
            drawPillBadge(gc, 20, 36,
                    "UNSOLVABLE: " + result.diagnosticMessage(),
                    Color.web("#374151"), Color.web("#6b7280"), Color.web("#f9fafb"));
        }
    }

    private void drawArrowHead(GraphicsContext gc, double fromX, double fromY,
                               double toX, double toY, double headSize, Color color) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        double x1 = toX - headSize * Math.cos(angle - Math.PI / 6.0);
        double y1 = toY - headSize * Math.sin(angle - Math.PI / 6.0);
        double x2 = toX - headSize * Math.cos(angle + Math.PI / 6.0);
        double y2 = toY - headSize * Math.sin(angle + Math.PI / 6.0);

        gc.setFill(color);
        gc.fillPolygon(new double[]{toX, x1, x2}, new double[]{toY, y1, y2}, 3);
    }

    private void drawPillBadge(GraphicsContext gc, double x, double y, String text,
                               Color bgColor, Color borderColor, Color textColor) {
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        double textWidth = text.length() * 6.6;
        double padH = 8.0;
        double padV = 4.0;
        double boxW = textWidth + (padH * 2.0);
        double boxH = 20.0;

        // Keep within canvas bounds
        if (x + boxW > canvas.getWidth() - 8.0) {
            x = canvas.getWidth() - boxW - 8.0;
        }
        if (x < 8.0) x = 8.0;
        if (y + boxH > canvas.getHeight() - 8.0) {
            y = canvas.getHeight() - boxH - 8.0;
        }
        if (y < 8.0) y = 8.0;

        gc.setFill(bgColor);
        gc.fillRoundRect(x, y, boxW, boxH, 6, 6);

        gc.setStroke(borderColor);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x, y, boxW, boxH, 6, 6);

        gc.setFill(textColor);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(text, x + padH, y + (boxH / 2.0));
    }
}

