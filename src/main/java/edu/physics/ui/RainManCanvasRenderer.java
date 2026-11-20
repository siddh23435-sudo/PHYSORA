package edu.physics.ui;

import edu.physics.relative.RainManSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Dynamic canvas renderer for Experiment 2.3: Rain-Man & Rain-Umbrella Relative Motion.
 *
 * <p>Renders:
 * <ul>
 *   <li><b>Primary Scene (Walker Frame):</b> Pedestrian remains stationary at center.
 *       Rain streaks fall at apparent velocity v_{r/m}. Pavement markings scroll horizontally
 *       at velocity -v_m. Umbrella tilts at student-controlled angle &phi;_{actual}.</li>
 *   <li><b>Supplementary Inset (Ground Frame):</b> Miniature inertial reference frame showing
 *       true ground rain velocity v_r and walking velocity v_m relative to a stationary lamp-post.</li>
 * </ul>
 * </p>
 *
 * <p>Adheres strictly to the physics specification: no particle interception, collision detection,
 * or dry-zone claims are simulated.</p>
 */
public final class RainManCanvasRenderer {

    private final Canvas canvas = new Canvas(720, 440);

    // Fixed deterministic particle seeds (160 particles)
    private static final int PARTICLE_COUNT = 160;
    private static final double[] SEED_X = new double[PARTICLE_COUNT];
    private static final double[] SEED_Y = new double[PARTICLE_COUNT];
    private static final double[] SEED_SPEED_FACTOR = new double[PARTICLE_COUNT];

    static {
        // Deterministic pseudo-random generation using linear congruential sequence
        long lcg = 123456789L;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            lcg = (lcg * 1103515245L + 12345L) & 0x7fffffffL;
            SEED_X[i] = (double) lcg / 0x7fffffffL;

            lcg = (lcg * 1103515245L + 12345L) & 0x7fffffffL;
            SEED_Y[i] = (double) lcg / 0x7fffffffL;

            lcg = (lcg * 1103515245L + 12345L) & 0x7fffffffL;
            SEED_SPEED_FACTOR[i] = 0.85 + 0.30 * ((double) lcg / 0x7fffffffL);
        }
    }

    public Canvas canvas() {
        return canvas;
    }

    public void render(RainManSimulationModel model, double timeSec) {
        if (model == null) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        // 1. Sky Background (Atmospheric slate/indigo gradient)
        LinearGradient skyGradient = new LinearGradient(
                0, 0, 0, h - 80, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#0f172a")),
                new Stop(0.7, Color.web("#1e293b")),
                new Stop(1.0, Color.web("#334155"))
        );
        gc.setFill(skyGradient);
        gc.fillRect(0, 0, w, h);

        double groundY = h - 75.0;

        // 2. Deterministic Apparent Rain Particle System (Walker Frame)
        renderApparentRain(gc, model, timeSec, w, groundY);

        // 3. Pavement / Ground (Scrolling with -v_m)
        renderPavement(gc, model, timeSec, w, h, groundY);

        // 4. Pedestrian / Runner (Centered in Walker Frame)
        double walkerX = w / 2.0;
        renderPedestrian(gc, model, walkerX, groundY);

        // 5. Umbrella (Held by pedestrian, tilted at actual angle)
        renderUmbrella(gc, model, walkerX, groundY);

        // 6. Observer Frame Identification Header & Badges
        renderFrameBadges(gc, model, w, groundY);

        // 7. Supplementary Ground Frame Inset (Top-Right)
        renderGroundFrameMiniature(gc, model, timeSec, w);
    }

    private void renderApparentRain(GraphicsContext gc, RainManSimulationModel model, double timeSec, double w, double groundY) {
        Vector2D vrm = model.apparentRainVelocity();
        double speed = model.apparentRainSpeed();
        if (speed <= 0.0) return;

        // Visual scale: map 1 m/s to ~ 25 pixels/sec
        double scale = 26.0;
        double vx = vrm.x() * scale;
        double vy = model.vDown() * scale; // downward speed in screen pixels/sec

        double normX = vrm.x() / speed;
        double normY = model.vDown() / speed; // positive downward in screen coords

        double streakLen = Math.min(26.0, Math.max(12.0, speed * 1.8));

        gc.setLineWidth(1.4);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(Color.rgb(186, 230, 253, 0.65)); // Translucent icy blue

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double varSpeed = SEED_SPEED_FACTOR[i];
            double rawX = SEED_X[i] * w + vx * varSpeed * timeSec;
            double rawY = SEED_Y[i] * groundY + vy * varSpeed * timeSec;

            // Mathematical modulo wrapping ensuring positive values
            double px = ((rawX % w) + w) % w;
            double py = ((rawY % groundY) + groundY) % groundY;

            double pEndx = px + normX * streakLen;
            double pEndy = py + normY * streakLen;

            gc.strokeLine(px, py, pEndx, pEndy);
        }
    }

    private void renderPavement(GraphicsContext gc, RainManSimulationModel model, double timeSec,
                                double w, double h, double groundY) {
        // Ground asphalt
        gc.setFill(Color.web("#1e293b"));
        gc.fillRect(0, groundY, w, h - groundY);

        // Curb separator line
        gc.setStroke(Color.web("#94a3b8"));
        gc.setLineWidth(2.5);
        gc.strokeLine(0, groundY, w, groundY);

        // Pavement stripes scrolling with -v_m to represent walker frame
        double pavementSpeed = -model.vPerson() * 26.0; // pixels/sec
        double period = 60.0; // pixels between stripes
        double offset = ((pavementSpeed * timeSec) % period + period) % period;

        gc.setStroke(Color.rgb(254, 240, 138, 0.70)); // Yellow road dash
        gc.setLineWidth(3.0);
        for (double x = -period + offset; x < w + period; x += period) {
            gc.strokeLine(x, groundY + 22.0, x + 30.0, groundY + 22.0);
        }

        // Sidewalk pavement tile grid lines
        gc.setStroke(Color.rgb(71, 85, 105, 0.45));
        gc.setLineWidth(1.0);
        for (double x = -period + offset; x < w + period; x += 40.0) {
            gc.strokeLine(x, groundY, x - 15.0, h);
        }
    }

    private void renderPedestrian(GraphicsContext gc, RainManSimulationModel model, double x, double groundY) {
        // Body dimensions
        double headRadius = 10.0;
        double headY = groundY - 82.0;
        double torsoStartY = headY + headRadius;
        double torsoEndY = groundY - 38.0;
        double feetY = groundY - 2.0;

        // Shadow under walker
        gc.setFill(Color.rgb(0, 0, 0, 0.35));
        gc.fillOval(x - 18.0, groundY - 4.0, 36.0, 8.0);

        // Head
        gc.setFill(Color.web("#fde047")); // Bright yellow jacket/person
        gc.fillOval(x - headRadius, headY - headRadius, headRadius * 2.0, headRadius * 2.0);
        gc.setStroke(Color.web("#0f172a"));
        gc.setLineWidth(1.5);
        gc.strokeOval(x - headRadius, headY - headRadius, headRadius * 2.0, headRadius * 2.0);

        // Torso
        gc.setStroke(Color.web("#fde047"));
        gc.setLineWidth(7.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeLine(x, torsoStartY + 2.0, x, torsoEndY);

        // Legs (Walking posture)
        gc.setStroke(Color.web("#38bdf8")); // Blue jeans
        gc.setLineWidth(4.0);
        gc.strokeLine(x, torsoEndY, x - 8.0, feetY); // Left leg
        gc.strokeLine(x, torsoEndY, x + 8.0, feetY); // Right leg

        // Arms holding umbrella shaft: arm extends to hand at (x + 10, groundY - 56)
        gc.setStroke(Color.web("#fde047"));
        gc.setLineWidth(3.5);
        double handX = x + 10.0;
        double handY = groundY - 56.0;
        gc.strokeLine(x, torsoStartY + 8.0, handX, handY);

        // Hand grip dot
        gc.setFill(Color.web("#f59e0b"));
        gc.fillOval(handX - 3.0, handY - 3.0, 6.0, 6.0);
    }

    private void renderUmbrella(GraphicsContext gc, RainManSimulationModel model, double walkerX, double groundY) {
        double handX = walkerX + 10.0;
        double handY = groundY - 56.0;

        double actualAngleRad = model.umbrellaAngleRad();
        double optimalAngleRad = model.optimalAngleRad();

        // 1. Draw dashed guideline for the optimal incoming rain direction
        double guideLen = 85.0;
        double optVx = handX + guideLen * Math.sin(optimalAngleRad);
        double optVy = handY - guideLen * Math.cos(optimalAngleRad);

        gc.setStroke(Color.rgb(52, 211, 153, 0.70)); // Emerald dashed line
        gc.setLineWidth(1.8);
        gc.setLineDashes(4.0, 4.0);
        gc.strokeLine(handX, handY, optVx, optVy);
        gc.setLineDashes((double[]) null); // Reset dashes

        // Label for optimal direction
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
        gc.setFill(Color.rgb(52, 211, 153, 0.90));
        gc.fillText(String.format(Locale.ROOT, "u_optimal (%.1f°)", model.optimalAngleDeg()), optVx + 6.0, optVy);

        // 2. Umbrella Shaft (Along actual angle)
        double shaftLen = 58.0;
        double shaftTipX = handX + shaftLen * Math.sin(actualAngleRad);
        double shaftTipY = handY - shaftLen * Math.cos(actualAngleRad);

        gc.setStroke(Color.web("#94a3b8")); // Steel rod
        gc.setLineWidth(3.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeLine(handX, handY, shaftTipX, shaftTipY);

        // Umbrella Handle (Curved J-hook at bottom)
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(2.5);
        gc.strokeArc(handX - 5.0, handY, 10.0, 10.0, 0, 180, ArcType.OPEN);

        // 3. Umbrella Canopy (Perpendicular to shaft)
        // Unit vector along shaft: (sin φ, -cos φ)
        // Unit vector perpendicular to shaft (canopy tangent): (cos φ, sin φ)
        double uTangentX = Math.cos(actualAngleRad);
        double uTangentY = Math.sin(actualAngleRad);

        double canopyHalfWidth = 38.0;
        double canopyArcHeight = 16.0;

        // Points for canopy rim
        double leftRimX = shaftTipX - canopyHalfWidth * uTangentX;
        double leftRimY = shaftTipY - canopyHalfWidth * uTangentY;

        double rightRimX = shaftTipX + canopyHalfWidth * uTangentX;
        double rightRimY = shaftTipY + canopyHalfWidth * uTangentY;

        double apexX = shaftTipX + canopyArcHeight * Math.sin(actualAngleRad);
        double apexY = shaftTipY - canopyArcHeight * Math.cos(actualAngleRad);

        // Determine canopy color according to alignment status
        RainManSimulationModel.AlignmentStatus status = model.alignmentStatus();
        Color canopyColor;
        Color canopyStroke;
        switch (status) {
            case WELL_ALIGNED -> {
                canopyColor = Color.web("#10b981"); // Vibrant Emerald
                canopyStroke = Color.web("#059669");
            }
            case SLIGHTLY_MISALIGNED -> {
                canopyColor = Color.web("#f59e0b"); // Amber
                canopyStroke = Color.web("#d97706");
            }
            default -> {
                canopyColor = Color.web("#ef4444"); // Coral / Crimson
                canopyStroke = Color.web("#b91c1c");
            }
        }

        // Fill curved canopy path
        gc.setFill(canopyColor);
        gc.beginPath();
        gc.moveTo(leftRimX, leftRimY);
        gc.quadraticCurveTo(apexX, apexY, rightRimX, rightRimY);
        gc.closePath();
        gc.fill();

        // Stroke rim and canopy outline
        gc.setStroke(canopyStroke);
        gc.setLineWidth(2.2);
        gc.stroke();

        // Finial point at the apex
        double finialX = apexX + 5.0 * Math.sin(actualAngleRad);
        double finialY = apexY - 5.0 * Math.cos(actualAngleRad);
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(2.0);
        gc.strokeLine(apexX, apexY, finialX, finialY);
    }

    private void renderFrameBadges(GraphicsContext gc, RainManSimulationModel model, double w, double groundY) {
        // High-contrast, structured HUD card in Upper-Left
        double cardW = 345.0;
        double cardH = 84.0;

        gc.setFill(Color.rgb(15, 23, 42, 0.88));
        gc.fillRoundRect(14, 14, cardW, cardH, 6, 6);
        gc.setStroke(Color.rgb(100, 116, 139, 0.90));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(14, 14, cardW, cardH, 6, 6);

        // Line 1: Frame Title
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.setFill(Color.web("#38bdf8")); // Vibrant cyan
        gc.fillText("⦿ WALKER'S FRAME (OBSERVER STATIONARY AT CENTER)", 22, 30);

        // Line 2: Apparent Rain Vector & Speed
        Vector2D vrm = model.apparentRainVelocity();
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.0));
        gc.setFill(Color.web("#f8fafc"));
        gc.fillText(String.format(Locale.ROOT, "v_(r/m) = v_r - v_m = (%+.1f i - %.1f j) m/s | |v| = %.1f m/s",
                vrm.x(), model.vDown(), model.apparentRainSpeed()), 22, 46);

        // Line 3: Streamline explanation
        String slantDesc;
        if (Math.abs(vrm.x()) < 1e-4) {
            slantDesc = "Streamline: Rain falls purely vertical (φ_opt = 0°)";
        } else if (vrm.x() < 0) {
            slantDesc = String.format(Locale.ROOT, "Streamline: Wind into face at %+.1f m/s (Slants from front)", vrm.x());
        } else {
            slantDesc = String.format(Locale.ROOT, "Streamline: Tailwind at %+.1f m/s (Slants from behind)", vrm.x());
        }
        gc.setFont(Font.font("Segoe UI", 9.5));
        gc.setFill(Color.web("#cbd5e1"));
        gc.fillText(slantDesc, 22, 61);

        // Line 4: Umbrella Shaft vs Needed
        RainManSimulationModel.AlignmentStatus status = model.alignmentStatus();
        Color statusColor = switch (status) {
            case WELL_ALIGNED -> Color.web("#34d399"); // Emerald
            case SLIGHTLY_MISALIGNED -> Color.web("#fbbf24"); // Amber
            default -> Color.web("#f87171"); // Red
        };
        String statusText = switch (status) {
            case WELL_ALIGNED -> "WELL ALIGNED";
            case SLIGHTLY_MISALIGNED -> "SLIGHTLY OFF";
            default -> "MISALIGNED";
        };

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.0));
        gc.setFill(Color.web("#f1f5f9"));
        gc.fillText(String.format(Locale.ROOT, "Shaft: %+.1f°  |  Needed: %+.1f°  |",
                model.umbrellaAngleDeg(), model.optimalAngleDeg()), 22, 77);

        gc.setFill(statusColor);
        gc.fillText(String.format(Locale.ROOT, " Err: %.1f° [%s]", model.angularErrorDeg(), statusText), 205, 77);
    }

    private void renderGroundFrameMiniature(GraphicsContext gc, RainManSimulationModel model, double timeSec, double w) {
        double insetW = 180.0;
        double insetH = 115.0;
        double insetX = w - insetW - 14.0;
        double insetY = 14.0;

        // Container Box
        gc.setFill(Color.rgb(15, 23, 42, 0.85));
        gc.fillRoundRect(insetX, insetY, insetW, insetH, 6, 6);
        gc.setStroke(Color.rgb(71, 85, 105, 0.85));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(insetX, insetY, insetW, insetH, 6, 6);

        // Inset Title
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        gc.setFill(Color.web("#fbbf24")); // Amber header
        gc.fillText("INERTIAL GROUND FRAME", insetX + 10, insetY + 16);

        double miniGroundY = insetY + insetH - 18.0;

        // Ground line
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(1.5);
        gc.strokeLine(insetX + 4, miniGroundY, insetX + insetW - 4, miniGroundY);

        // Stationary Reference Lamp-Post
        double lampX = insetX + 28.0;
        gc.setStroke(Color.web("#94a3b8"));
        gc.setLineWidth(2.0);
        gc.strokeLine(lampX, miniGroundY, lampX, miniGroundY - 45.0);
        gc.setFill(Color.web("#fbbf24"));
        gc.fillOval(lampX - 4.0, miniGroundY - 48.0, 8.0, 8.0);

        // Ground rain streaks (tilted at true ground rain velocity)
        Vector2D vr = model.rainVelocityGround();
        double vrMag = vr.magnitude();
        double streakLen = 10.0;
        double gvx = (vr.x() / vrMag) * streakLen;
        double gvy = (model.vDown() / vrMag) * streakLen;

        gc.setStroke(Color.rgb(251, 191, 36, 0.55)); // Amber rain
        gc.setLineWidth(1.0);
        for (int i = 0; i < 18; i++) {
            double rx = insetX + 8.0 + (i * 9.0);
            double ry = insetY + 26.0 + ((i * 17) % 55);
            gc.strokeLine(rx, ry, rx + gvx, ry + gvy);
        }

        // Moving mini person across ground
        double miniScale = 12.0; // pixels per m/s
        double runSpan = insetW - 70.0;
        double rawMiniX = (model.vPerson() * miniScale * timeSec) % runSpan;
        double miniX = insetX + 50.0 + ((rawMiniX + runSpan) % runSpan);

        gc.setFill(Color.web("#fde047"));
        gc.fillOval(miniX - 4.0, miniGroundY - 26.0, 8.0, 8.0); // Head
        gc.setStroke(Color.web("#fde047"));
        gc.setLineWidth(2.0);
        gc.strokeLine(miniX, miniGroundY - 18.0, miniX, miniGroundY - 8.0); // Body
        gc.strokeLine(miniX, miniGroundY - 8.0, miniX - 3.0, miniGroundY);
        gc.strokeLine(miniX, miniGroundY - 8.0, miniX + 3.0, miniGroundY);

        // Velocity vectors readout in miniature
        gc.setFont(Font.font("Consolas", 8.5));
        gc.setFill(Color.web("#cbd5e1"));
        gc.fillText(String.format(Locale.ROOT, "v_r: (%.1f, -%.1f)", vr.x(), model.vDown()), insetX + 90, insetY + 36);
        gc.fillText(String.format(Locale.ROOT, "v_m: (%.1f, 0.0)", model.vPerson()), insetX + 90, insetY + 50);
    }
}
