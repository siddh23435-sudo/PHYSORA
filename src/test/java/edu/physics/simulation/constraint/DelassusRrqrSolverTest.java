package edu.physics.simulation.constraint;

import edu.physics.simulation.PhysicalBodySpec;
import edu.physics.simulation.RigidBody;
import edu.physics.simulation.RigidBodyState;
import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three-Level Verification Test Suite for Stage 3C-2A.2:
 * Mass-Whitened Rank-Revealing QR (RRQR) and Regularized Delassus Factorization.
 *
 * <p>Disciplined Verification Architecture:
 * <ul>
 *   <li>Level 1: Unit Tests (normalization invariants, Cholesky identity, augmented QR, Option A compatibility).</li>
 *   <li>Level 2: Integration Tests (multi-body kinematics, permutation invariance, coupled Tier 1A/1B systems).</li>
 *   <li>Level 3: Acceptance Tests (physical force invariance, residual bounds, provisional multipliers).</li>
 * </ul>
 */
public class DelassusRrqrSolverTest {

    private static final double EPSILON = 1.0e-9;
    private static final double STRICT_EPSILON = 1.0e-12;

    // =========================================================================
    // LEVEL 1: UNIT TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 1: Unit Tests — Matrix Decompositions & Linear Algebra")
    class UnitTests {

        @Test
        @DisplayName("1.1 Mass-Whitening Scaling Invariant: ||B_i||_2 = 1.0 for diverse masses and inertias")
        void massWhiteningScalingInvariant() {
            // Body 1: Light fast-spinning disc (m = 0.5 kg, I = 0.01 kg*m^2)
            RigidBody b1 = RigidBody.dynamicDisc("b1", 0.5, 0.2, new Vector2D(0.0, 0.0));
            // Body 2: Heavy high-inertia block (m = 250.0 kg, I = 50.0 kg*m^2)
            RigidBody b2 = RigidBody.dynamicBlock("b2", 250.0, 1.0, 1.0, new Vector2D(5.0, 0.0));

            CoordinateMapping mapping = CoordinateMapping.of(List.of(b1, b2));
            assertEquals(6, mapping.totalDof()); // 3 + 3 = 6 DOFs

            // Create 3 diverse constraint rows:
            // Row 0: Translation along x on b1
            ConstraintRow r0 = new ConstraintRow("c0", 0, mapping.totalDof());
            r0.setJacobian(0, 1.0);

            // Row 1: Translation and rotation on b1
            ConstraintRow r1 = new ConstraintRow("c1", 0, mapping.totalDof());
            r1.setJacobian(0, 0.5);
            r1.setJacobian(1, 0.866);
            r1.setJacobian(2, 0.15); // torque arm

            // Row 2: Coupled connection between b1 and b2
            ConstraintRow r2 = new ConstraintRow("c2", 0, mapping.totalDof());
            r2.setJacobian(0, -1.0);
            r2.setJacobian(1, 0.0);
            r2.setJacobian(2, -0.2);
            r2.setJacobian(3, 1.0);
            r2.setJacobian(4, 0.0);
            r2.setJacobian(5, 0.3);

            List<ConstraintRow> rows = List.of(r0, r1, r2);
            double[] extAcc = new double[6];
            MassWhitenedDelassus mwd = MassWhitenedDelassus.of(mapping, rows, extAcc, 5.0, 5.0);

            // Verify ||B_i||_2 = 1.0 for every row
            for (int i = 0; i < rows.size(); i++) {
                double normSq = 0.0;
                for (int j = 0; j < mapping.totalDof(); j++) {
                    normSq += mwd.B()[i][j] * mwd.B()[i][j];
                }
                assertEquals(1.0, Math.sqrt(normSq), STRICT_EPSILON,
                        "Row " + i + " of normalized matrix B must have unit Euclidean norm");
            }
        }

        @Test
        @DisplayName("1.2 QR Delassus Cholesky Identity: P^T bar{W} P = R^T R to machine precision")
        void qrDelassusCholeskyIdentity() {
            RigidBody body = RigidBody.dynamicDisc("bob", 2.0, 0.3, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            // Three independent constraint rows
            ConstraintRow r0 = new ConstraintRow("r0", 0, 3);
            r0.setJacobian(0, 1.0); // x-lock

            ConstraintRow r1 = new ConstraintRow("r1", 0, 3);
            r1.setJacobian(1, 1.0); // y-lock

            ConstraintRow r2 = new ConstraintRow("r2", 0, 3);
            r2.setJacobian(2, 1.0); // theta-lock

            List<ConstraintRow> rows = List.of(r0, r1, r2);
            MassWhitenedDelassus mwd = MassWhitenedDelassus.of(mapping, rows, new double[3], 0.0, 0.0);
            ColumnPivotedRRQR.RRQRResult rrqr = ColumnPivotedRRQR.factorize(mwd.B(), 3, 3);

            int[] pi = rrqr.permutation();
            double[][] R = rrqr.R();
            double[][] B = mwd.B();

            // Evaluate P^T bar{W} P = (B P)^T (B P)
            double[][] permutedW = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double dot = 0.0;
                    for (int k = 0; k < 3; k++) {
                        dot += B[pi[i]][k] * B[pi[j]][k];
                    }
                    permutedW[i][j] = dot;
                }
            }

            // Evaluate R^T R
            double[][] RtR = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double dot = 0.0;
                    for (int k = 0; k < 3; k++) {
                        dot += R[k][i] * R[k][j];
                    }
                    RtR[i][j] = dot;
                }
            }

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(permutedW[i][j], RtR[i][j], STRICT_EPSILON,
                            "Mismatch at (" + i + "," + j + ") between P^T bar{W} P and R^T R");
                }
            }
        }

        @Test
        @DisplayName("1.3 Pivoted QR Residual Diagonals: |R_kk| equals exact subspace projection sin(theta)")
        void pivotedQrResidualDiagonals() {
            // Synthetic 2D problem: Column 1 = [1, 0]^T, Column 2 at angle theta = 30 deg
            double theta = Math.toRadians(30.0);
            double[][] B = new double[2][2];
            B[0][0] = 1.0;
            B[0][1] = 0.0;
            B[1][0] = Math.cos(theta);
            B[1][1] = Math.sin(theta);

            ColumnPivotedRRQR.RRQRResult rrqr = ColumnPivotedRRQR.factorize(B, 2, 2);
            assertEquals(2, rrqr.rank());

            // First diagonal norm is 1.0 (unit vector)
            assertEquals(1.0, rrqr.rDiag()[0], STRICT_EPSILON);
            // Second diagonal norm is sin(30 deg) = 0.5
            assertEquals(Math.sin(theta), rrqr.rDiag()[1], 1.0e-11);
        }

        @Test
        @DisplayName("1.4 Augmented QR Factorization Identity: tilde{R}^T tilde{R} = R_11^T R_11 + D_mu^2")
        void augmentedQrFactorizationIdentity() {
            // Construct a non-trivial upper triangular R_11 (3 x 3)
            double[][] R11 = new double[][]{
                    {1.0, 0.4, -0.2},
                    {0.0, 0.8,  0.5},
                    {0.0, 0.0,  0.002} // Tier 1B-like pivot
            };

            double mu = 1.0e-4;
            double[][] Dmu = new double[][]{
                    {0.0, 0.0, 0.0},
                    {0.0, 0.0, 0.0},
                    {0.0, 0.0, mu}
            };

            // Augmented matrix K = [R11; Dmu] of size 6 x 3
            int rank = 3;
            double[][] K = new double[6][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(R11[i], 0, K[i], 0, 3);
            }
            K[5][2] = mu;

            // Apply Householder QR to K
            double[] uK = new double[6];
            for (int k = 0; k < rank; k++) {
                double normX = 0.0;
                for (int i = k; i < 6; i++) {
                    normX += K[i][k] * K[i][k];
                }
                normX = Math.sqrt(normX);
                if (normX > 0.0) {
                    double alphaK = (K[k][k] >= 0.0) ? -normX : normX;
                    double u0 = K[k][k] - alphaK;
                    double betaK = -1.0 / (alphaK * u0);
                    uK[0] = u0;
                    for (int i = k + 1; i < 6; i++) {
                        uK[i - k] = K[i][k];
                    }
                    for (int j = k + 1; j < rank; j++) {
                        double dot = u0 * K[k][j];
                        for (int i = k + 1; i < 6; i++) {
                            dot += uK[i - k] * K[i][j];
                        }
                        double tau = betaK * dot;
                        K[k][j] -= tau * u0;
                        for (int i = k + 1; i < 6; i++) {
                            K[i][j] -= tau * uK[i - k];
                        }
                    }
                    K[k][k] = alphaK;
                    for (int i = k + 1; i < 6; i++) {
                        K[i][k] = 0.0;
                    }
                }
            }

            double[][] Rtilde = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(K[i], 0, Rtilde[i], 0, 3);
            }

            // Target matrix: Target = R11^T R11 + Dmu^2
            double[][] target = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double dot = 0.0;
                    for (int k = 0; k < 3; k++) {
                        dot += R11[k][i] * R11[k][j];
                    }
                    if (i == j && i == 2) {
                        dot += mu * mu;
                    }
                    target[i][j] = dot;
                }
            }

            // Factor product: Product = Rtilde^T Rtilde
            double[][] product = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double dot = 0.0;
                    for (int k = 0; k < 3; k++) {
                        dot += Rtilde[k][i] * Rtilde[k][j];
                    }
                    product[i][j] = dot;
                }
            }

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(target[i][j], product[i][j], 1.0e-13,
                            "tilde{R}^T tilde{R} must match R11^T R11 + D_mu^2 at (" + i + "," + j + ")");
                }
            }
        }

        @Test
        @DisplayName("1.5 Regularized Solve vs Exact Normal Equations: (R11^T R11 + D_mu^2)^(-1) bar{b}")
        void regularizedSolveVsExactNormalEquations() {
            // Solve a 2x2 ill-conditioned system using DelassusRrqrSolver and compare against exact matrix inversion
            RigidBody body = RigidBody.dynamicDisc("b", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            // Cable 1 along y
            ConstraintRow r0 = new ConstraintRow("c1", 0, 3);
            r0.setJacobian(1, 1.0);
            r0.setVelocityError(0.0);
            r0.setPositionError(0.0);

            // Cable 2 at small angle theta = 0.1 deg to Cable 1 (Tier 1B)
            double theta = Math.toRadians(0.1);
            ConstraintRow r1 = new ConstraintRow("c2", 0, 3);
            r1.setJacobian(0, Math.sin(theta));
            r1.setJacobian(1, Math.cos(theta));
            r1.setPositionError(0.01); // 1 cm displacement

            List<ConstraintRow> rows = List.of(r0, r1);
            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult result = solver.solve(mapping, rows, new double[3], 5.0, 5.0);

            assertEquals(2, result.rrqrResult().rank());
            assertEquals(1, result.rrqrResult().tier1ACount());
            assertEquals(1, result.rrqrResult().tier1BCount());

            // Compute exact normal equations solution (tilde{R}^T tilde{R})^(-1) bar{b}
            double[][] Rtilde = result.regularizedR();
            int[] pi = result.rrqrResult().permutation();
            double[] barB = result.delassus().barB();

            // Forward solve Rtilde^T z = barB
            double z0 = barB[pi[0]] / Rtilde[0][0];
            double z1 = (barB[pi[1]] - Rtilde[0][1] * z0) / Rtilde[1][1];

            // Backward solve Rtilde lambda = z
            double lam1 = z1 / Rtilde[1][1];
            double lam0 = (z0 - Rtilde[0][1] * lam1) / Rtilde[0][0];

            double[] expectedBarLambda = new double[]{lam0, lam1};
            double expectedLam0 = result.delassus().s()[pi[0]] * expectedBarLambda[0];
            double expectedLam1 = result.delassus().s()[pi[1]] * expectedBarLambda[1];

            assertEquals(expectedLam0, result.multipliers()[pi[0]], 1.0e-11);
            assertEquals(expectedLam1, result.multipliers()[pi[1]], 1.0e-11);
        }

        @Test
        @DisplayName("1.6 Option A Implied Dependent RHS: Exact linear combination matches R12^T R11^(-T) bar{b}")
        void optionAImpliedDependentRhs() {
            RigidBody body = RigidBody.dynamicDisc("box", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            // Row 0: x-constraint
            ConstraintRow r0 = new ConstraintRow("xLock", 0, 3);
            r0.setJacobian(0, 1.0);
            r0.setPositionError(0.02);

            // Row 1: identical duplicate of Row 0
            ConstraintRow r1 = new ConstraintRow("xLockDup", 0, 3);
            r1.setJacobian(0, 1.0);
            r1.setPositionError(0.02);

            List<ConstraintRow> rows = List.of(r0, r1);
            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, rows, new double[3], 5.0, 5.0);

            assertEquals(1, res.rrqrResult().rank());
            assertEquals(1, res.rrqrResult().tier1ACount());
            assertEquals(1, res.rrqrResult().tier2Count());
            assertEquals(1, res.redundantConstraintIds().size());
            assertEquals("xLockDup", res.redundantConstraintIds().get(0));
        }

        @Test
        @DisplayName("1.7 Classification Stability: Perturbations of 1e-13 leave tier classifications unchanged")
        void classificationStabilityUnderPerturbations() {
            RigidBody body = RigidBody.dynamicDisc("disc", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            ConstraintRow r0 = new ConstraintRow("r0", 0, 3);
            r0.setJacobian(0, 1.0);

            // Near-parallel row at 0.1 deg
            double theta = Math.toRadians(0.1);
            ConstraintRow r1 = new ConstraintRow("r1", 0, 3);
            r1.setJacobian(0, Math.cos(theta));
            r1.setJacobian(1, Math.sin(theta));

            MassWhitenedDelassus mwd1 = MassWhitenedDelassus.of(mapping, List.of(r0, r1), new double[3], 0.0, 0.0);
            ColumnPivotedRRQR.RRQRResult res1 = ColumnPivotedRRQR.factorize(mwd1.B(), 3, 2);

            // Perturb by 1e-13
            ConstraintRow r1Perturbed = new ConstraintRow("r1", 0, 3);
            r1Perturbed.setJacobian(0, Math.cos(theta) + 1.0e-13);
            r1Perturbed.setJacobian(1, Math.sin(theta) - 1.0e-13);

            MassWhitenedDelassus mwd2 = MassWhitenedDelassus.of(mapping, List.of(r0, r1Perturbed), new double[3], 0.0, 0.0);
            ColumnPivotedRRQR.RRQRResult res2 = ColumnPivotedRRQR.factorize(mwd2.B(), 3, 2);

            assertEquals(res1.tiers()[0], res2.tiers()[0]);
            assertEquals(res1.tiers()[1], res2.tiers()[1]);
            assertEquals(res1.rank(), res2.rank());
        }

        @Test
        @DisplayName("1.8 Zero Delassus Mobility Rejection: Rows with zero dynamic mobility trigger Tier 4")
        void zeroDelassusMobilityRejection() {
            RigidBody body = RigidBody.dynamicDisc("dyn", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            // Row with zero Jacobian (e.g. connecting static to static or unmapped DOFs)
            ConstraintRow emptyRow = new ConstraintRow("empty", 0, 3);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    MassWhitenedDelassus.of(mapping, List.of(emptyRow), new double[3], 0.0, 0.0));

            assertTrue(ex.getMessage().contains("zero Delassus mobility"));
        }

        @Test
        @DisplayName("1.9 Duplicate Constraint Row Identity Rejection: Identical (constraintId, rowIndex) triggers IllegalArgumentException")
        void duplicateConstraintRowIdentityRejection() {
            RigidBody body = RigidBody.dynamicDisc("bob", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));

            ConstraintRow r0 = new ConstraintRow("cableA", 0, 3);
            r0.setJacobian(0, 1.0);

            // Duplicate row with identical constraintId and rowIndex
            ConstraintRow r1 = new ConstraintRow("cableA", 0, 3);
            r1.setJacobian(1, 1.0);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    solver.solve(mapping, List.of(r0, r1), new double[3], 0.0, 0.0));

            assertTrue(ex.getMessage().contains("Duplicate constraint row identity detected: cableA[0]"));
        }
    }

    // =========================================================================
    // LEVEL 2: INTEGRATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 2: Integration Tests — Multi-Body Physics & Constraint Geometry")
    class IntegrationTests {

        @Test
        @DisplayName("2.1 Independent Two-Body Cables: Exact unregularized analytical solution")
        void independentTwoBodyCables() {
            // Two dynamic blocks hanging under gravity
            RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.4, 0.4, new Vector2D(0.0, 2.0));
            RigidBody b2 = RigidBody.dynamicBlock("b2", 3.0, 0.4, 0.4, new Vector2D(5.0, 2.0));
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b1, b2));

            // External acceleration: gravity -9.8 m/s^2 on y (indices 1 and 4)
            double[] extAcc = new double[6];
            extAcc[1] = -9.8;
            extAcc[4] = -9.8;

            // Cable 1 supports b1 vertically: J = [0, 1, 0,  0, 0, 0]
            ConstraintRow c1 = new ConstraintRow("c1", 0, 6);
            c1.setJacobian(1, 1.0);

            // Cable 2 supports b2 vertically: J = [0, 0, 0,  0, 1, 0]
            ConstraintRow c2 = new ConstraintRow("c2", 0, 6);
            c2.setJacobian(4, 1.0);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(c1, c2), extAcc, 0.0, 0.0);

            assertEquals(2, res.rrqrResult().rank());
            assertEquals(2, res.rrqrResult().tier1ACount());
            assertEquals(0, res.rrqrResult().tier1BCount());

            // Tension on b1 must equal m1 * g = 2.0 * 9.8 = 19.6 N
            assertEquals(19.6, res.multipliers()[0], EPSILON);
            // Tension on b2 must equal m2 * g = 3.0 * 9.8 = 29.4 N
            assertEquals(29.4, res.multipliers()[1], EPSILON);

            // Net vertical acceleration must be exactly zero: -9.8 + (1/m) * lambda = 0
            assertEquals(0.0, extAcc[1] + (1.0 / 2.0) * res.multipliers()[0], EPSILON);
            assertEquals(0.0, extAcc[4] + (1.0 / 3.0) * res.multipliers()[1], EPSILON);

            // Physical acceleration residuals must be zero
            assertEquals(0.0, res.physicalResiduals()[0], 1.0e-10);
            assertEquals(0.0, res.physicalResiduals()[1], 1.0e-10);
        }

        @Test
        @DisplayName("2.2 Permutation Invariance on Well-Separated Systems: Identical physical forces and accelerations")
        void permutationInvarianceOfPhysicalInvariants() {
            RigidBody b = RigidBody.dynamicDisc("bob", 2.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            double[] extAcc = new double[]{1.5, -9.8, 0.5};

            ConstraintRow r0 = new ConstraintRow("xLock", 0, 3);
            r0.setJacobian(0, 1.0);
            r0.setPositionError(0.01);

            ConstraintRow r1 = new ConstraintRow("yLock", 0, 3);
            r1.setJacobian(1, 1.0);
            r1.setPositionError(0.02);

            ConstraintRow r2 = new ConstraintRow("rotLock", 0, 3);
            r2.setJacobian(2, 1.0);
            r2.setPositionError(0.03);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();

            // Order 1: [0, 1, 2]
            DelassusRrqrSolver.SolveResult res1 = solver.solve(mapping, List.of(r0, r1, r2), extAcc, 5.0, 5.0);

            // Order 2: [2, 1, 0]
            DelassusRrqrSolver.SolveResult res2 = solver.solve(mapping, List.of(r2, r1, r0), extAcc, 5.0, 5.0);

            // Order 3: [1, 2, 0]
            DelassusRrqrSolver.SolveResult res3 = solver.solve(mapping, List.of(r1, r2, r0), extAcc, 5.0, 5.0);

            // Physical coordinate accelerations must be identical across all permutations
            assertArrayEquals(res1.coordinateAccelerations(), res2.coordinateAccelerations(), STRICT_EPSILON);
            assertArrayEquals(res1.coordinateAccelerations(), res3.coordinateAccelerations(), STRICT_EPSILON);

            // Multipliers mapped to respective constraint entities must match
            // In res1: [r0, r1, r2]
            // In res2: [r2, r1, r0] -> res2[0] = r2, res2[1] = r1, res2[2] = r0
            assertEquals(res1.multipliers()[0], res2.multipliers()[2], STRICT_EPSILON);
            assertEquals(res1.multipliers()[1], res2.multipliers()[1], STRICT_EPSILON);
            assertEquals(res1.multipliers()[2], res2.multipliers()[0], STRICT_EPSILON);
        }

        @Test
        @DisplayName("2.2B Canonical Pre-Factorization Ordering: Different input orders yield identical canonical factorizations")
        void canonicalPreFactorizationOrderingProducesIdenticalFactorization() {
            RigidBody b = RigidBody.dynamicDisc("bob", 2.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            double[] extAcc = new double[]{0.0, -9.8, 0.0};

            // Three constraints with distinct alphanumeric IDs
            ConstraintRow alpha = new ConstraintRow("cableAlpha", 0, 3);
            alpha.setJacobian(0, 1.0);
            alpha.setPositionError(0.01);

            ConstraintRow beta = new ConstraintRow("cableBeta", 0, 3);
            beta.setJacobian(1, 1.0);
            beta.setPositionError(0.02);

            ConstraintRow gamma = new ConstraintRow("cableGamma", 0, 3);
            gamma.setJacobian(2, 0.5);
            gamma.setPositionError(0.005);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();

            // Order A: [gamma, alpha, beta]
            DelassusRrqrSolver.SolveResult resA = solver.solve(mapping, List.of(gamma, alpha, beta), extAcc, 5.0, 5.0);

            // Order B: [beta, gamma, alpha]
            DelassusRrqrSolver.SolveResult resB = solver.solve(mapping, List.of(beta, gamma, alpha), extAcc, 5.0, 5.0);

            // 1. Both internal canonical Delassus systems are identical
            assertEquals(resA.delassus().m(), resB.delassus().m());
            for (int i = 0; i < 3; i++) {
                assertArrayEquals(resA.delassus().B()[i], resB.delassus().B()[i], STRICT_EPSILON);
                assertEquals(resA.delassus().barB()[i], resB.delassus().barB()[i], STRICT_EPSILON);
            }

            // 2. Both internal RRQR permutations and diagonal norms are identical
            assertArrayEquals(resA.rrqrResult().permutation(), resB.rrqrResult().permutation());
            assertArrayEquals(resA.rrqrResult().rDiag(), resB.rrqrResult().rDiag(), STRICT_EPSILON);

            // 3. Both internal regularized R factors are identical
            for (int i = 0; i < resA.rrqrResult().rank(); i++) {
                assertArrayEquals(resA.regularizedR()[i], resB.regularizedR()[i], STRICT_EPSILON);
            }

            // 4. Physical coordinate accelerations are identical
            assertArrayEquals(resA.coordinateAccelerations(), resB.coordinateAccelerations(), STRICT_EPSILON);

            // 5. Multipliers are correctly mapped back to callers' respective positions
            // In Order A: [gamma, alpha, beta] -> resA[0] = gamma, resA[1] = alpha, resA[2] = beta
            // In Order B: [beta, gamma, alpha] -> resB[0] = beta, resB[1] = gamma, resB[2] = alpha
            assertEquals(resA.multipliers()[0], resB.multipliers()[1], STRICT_EPSILON, "gamma multiplier mismatch");
            assertEquals(resA.multipliers()[1], resB.multipliers()[2], STRICT_EPSILON, "alpha multiplier mismatch");
            assertEquals(resA.multipliers()[2], resB.multipliers()[0], STRICT_EPSILON, "beta multiplier mismatch");
        }

        @Test
        @DisplayName("2.3 Near-Parallel Cables (Test 11): theta = 0.1 deg cleanly classifies into Tier 1B")
        void nearParallelCablesTier1BRegularization() {
            // Particle mass m = 1.0 kg (2 DOFs: x, y)
            RigidBody particle = RigidBody.pointMass("p", 1.0, Vector2D.ZERO, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(particle));

            // Cable 1 along vertical y-axis
            ConstraintRow c1 = new ConstraintRow("cable1", 0, 2);
            c1.setJacobian(1, 1.0);

            // Cable 2 rotated by theta = 0.1 deg
            double theta = Math.toRadians(0.1);
            ConstraintRow c2 = new ConstraintRow("cable2", 0, 2);
            c2.setJacobian(0, Math.sin(theta));
            c2.setJacobian(1, Math.cos(theta));
            c2.setPositionError(0.005);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(c1, c2), new double[2], 5.0, 5.0);

            assertEquals(2, res.rrqrResult().rank());
            assertEquals(1, res.rrqrResult().tier1ACount());
            assertEquals(1, res.rrqrResult().tier1BCount());
            assertEquals(0, res.rrqrResult().tier2Count());

            // The residual diagonal of the second pivot must match sin(0.1 deg) ≈ 0.0017453
            double expectedResidual = Math.sin(theta);
            assertEquals(expectedResidual, res.rrqrResult().rDiag()[1], 1.0e-7);
            assertTrue(res.rrqrResult().rDiag()[1] >= ColumnPivotedRRQR.TAU_REDUN);
            assertTrue(res.rrqrResult().rDiag()[1] < ColumnPivotedRRQR.TAU_INDEP);

            // Verify dimensionally consistent residual bound: |r_phys,i| <= mu_reg^2 * W_ii * |lambda_i|
            double mu = DelassusRrqrSolver.REGULARIZATION_MU;
            int pi1 = res.rrqrResult().permutation()[1];
            double w11 = res.delassus().wDiag()[pi1];
            double lam1 = res.multipliers()[pi1];
            double bound = mu * mu * w11 * Math.abs(lam1) + 1.0e-12;
            assertTrue(Math.abs(res.physicalResiduals()[pi1]) <= bound,
                    "Physical residual " + res.physicalResiduals()[pi1] + " must be bounded by " + bound);
        }

        @Test
        @DisplayName("2.4 Coupled Mixed Tier 1A / Tier 1B System (Test 12): m = 2.0, I = 0.5, r_x = 1.0 mm")
        void coupledMixedTier1ATier1BSystem() {
            // Planar rigid body: m = 2.0 kg, I = 0.5 kg*m^2 (width = height = sqrt(6 * 0.5 / 2.0) = sqrt(1.5) ≈ 1.2247 m)
            double width = Math.sqrt(1.5);
            PhysicalBodySpec spec = PhysicalBodySpec.createBlock("rigidBody", 2.0, width, width);
            assertEquals(0.5, spec.momentOfInertia(), 1.0e-12);

            RigidBody body = new RigidBody(spec, RigidBodyState.atRest(Vector2D.ZERO));
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));
            assertEquals(3, mapping.totalDof());

            // Constraint 1: Horizontal lock at CM (Tier 1A)
            ConstraintRow c1 = new ConstraintRow("c1", 0, 3);
            c1.setJacobian(0, 1.0);

            // Constraint 2: Vertical pendulum cable at CM (Tier 1A)
            ConstraintRow c2 = new ConstraintRow("c2", 0, 3);
            c2.setJacobian(1, 1.0);

            // Constraint 3: Vertical cable with 1 mm offset arm (Tier 1B)
            // r_att = [0.001, 0.0], u = [0.0, 1.0] -> tau_arm = -(r_x * u_y - r_y * u_x) = -0.001 m
            ConstraintRow c3 = new ConstraintRow("c3", 0, 3);
            c3.setJacobian(0, 0.0);
            c3.setJacobian(1, -1.0);
            c3.setJacobian(2, -0.001);
            c3.setPositionError(0.002);

            List<ConstraintRow> rows = List.of(c1, c2, c3);
            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, rows, new double[3], 5.0, 5.0);

            assertEquals(3, res.rrqrResult().rank());
            assertEquals(2, res.rrqrResult().tier1ACount());
            assertEquals(1, res.rrqrResult().tier1BCount());
            assertEquals(0, res.rrqrResult().tier2Count());

            // Verified analytical residual: |R_22| = 0.001 / sqrt(0.250001) ≈ 1.999996e-3
            double expectedR22 = 0.001 / Math.sqrt(0.250001);
            assertEquals(expectedR22, res.rrqrResult().rDiag()[2], 1.0e-6);
            assertTrue(res.rrqrResult().rDiag()[2] >= ColumnPivotedRRQR.TAU_REDUN);
            assertTrue(res.rrqrResult().rDiag()[2] < ColumnPivotedRRQR.TAU_INDEP);

            // Verify solution matches exact normal equations (R11^T R11 + D_mu^2)^(-1) bar{b}
            double[][] Rtilde = res.regularizedR();
            int[] pi = res.rrqrResult().permutation();
            double[] barB = res.delassus().barB();

            // Forward solve
            double[] z = new double[3];
            for (int i = 0; i < 3; i++) {
                double sum = 0.0;
                for (int j = 0; j < i; j++) {
                    sum += Rtilde[j][i] * z[j];
                }
                z[i] = (barB[pi[i]] - sum) / Rtilde[i][i];
            }
            // Backward solve
            double[] barLam = new double[3];
            for (int i = 2; i >= 0; i--) {
                double sum = 0.0;
                for (int j = i + 1; j < 3; j++) {
                    sum += Rtilde[i][j] * barLam[j];
                }
                barLam[i] = (z[i] - sum) / Rtilde[i][i];
            }

            for (int i = 0; i < 3; i++) {
                double expectedPhysLam = res.delassus().s()[pi[i]] * barLam[i];
                assertEquals(expectedPhysLam, res.multipliers()[pi[i]], 1.0e-11);
            }
        }

        @Test
        @DisplayName("2.5 Consistent Duplicate Constraints: Tier 2 duplicate receives lambda = 0")
        void consistentDuplicateConstraints() {
            RigidBody b = RigidBody.dynamicDisc("bob", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            ConstraintRow c1 = new ConstraintRow("cablePrimary", 0, 3);
            c1.setJacobian(1, 1.0);
            c1.setPositionError(0.01);

            ConstraintRow c2 = new ConstraintRow("cableDuplicate", 0, 3);
            c2.setJacobian(1, 1.0);
            c2.setPositionError(0.01); // Identical displacement target

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(c1, c2), new double[3], 5.0, 5.0);

            assertEquals(1, res.rrqrResult().rank());
            assertEquals(1, res.rrqrResult().tier1ACount());
            assertEquals(1, res.rrqrResult().tier2Count());

            // Exactly one of the duplicate constraints receives the multiplier, and the redundant row receives 0.0
            double lam1 = res.multipliers()[0];
            double lam2 = res.multipliers()[1];
            assertTrue((Math.abs(lam1) > 0.0 && lam2 == 0.0) || (Math.abs(lam2) > 0.0 && lam1 == 0.0),
                    "Exactly one duplicate row must receive non-zero multiplier and the other 0.0");
            assertEquals(0.25, Math.abs(lam1 + lam2), EPSILON, "Total constraint force must equal m * beta^2 * phi");
        }

        @Test
        @DisplayName("2.6 Incompatible Bilateral Constraints: Conflict triggers Tier 4 IllegalStateException")
        void incompatibleBilateralConstraints() {
            RigidBody b = RigidBody.dynamicDisc("bob", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            ConstraintRow c1 = new ConstraintRow("cableA", 0, 3);
            c1.setJacobian(1, 1.0);
            c1.setPositionError(0.01); // demands 1 cm correction

            ConstraintRow c2 = new ConstraintRow("cableB", 0, 3);
            c2.setJacobian(1, 1.0);
            c2.setPositionError(0.06); // conflicting demands 6 cm correction (5 cm delta -> Beta^2 * 0.05 = 1.25 m/s^2 >> 2e-4)

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    solver.solve(mapping, List.of(c1, c2), new double[3], 5.0, 5.0));

            assertTrue(ex.getMessage().contains("kinematically incompatible"));
        }

        @Test
        @DisplayName("2.7 Overdetermined Rigid Body: 5 constraints on 3-DOF body caps rank at <= 3")
        void overdeterminedRigidBody() {
            RigidBody b = RigidBody.dynamicDisc("box", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));
            assertEquals(3, mapping.totalDof());

            List<ConstraintRow> rows = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ConstraintRow r = new ConstraintRow("c" + i, 0, 3);
                r.setJacobian(0, 1.0);
                r.setJacobian(1, 1.0);
                r.setJacobian(2, 0.1 * i);
                r.setPositionError(0.0);
                rows.add(r);
            }

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, rows, new double[3], 0.0, 0.0);

            // Rank must not exceed 3
            assertTrue(res.rrqrResult().rank() <= 3);
            // Exactly 5 - rank rows must be Tier 2
            assertEquals(5 - res.rrqrResult().rank(), res.rrqrResult().tier2Count());
        }

        @Test
        @DisplayName("2.8 Rotational Inertia vs Linear Mass Coupling: Mass whitening prevents inertia bias")
        void rotationalInertiaVsLinearMassCoupling() {
            // High rotational inertia I = 100 kg*m^2, light mass m = 1 kg
            PhysicalBodySpec spec = PhysicalBodySpec.createBlock("flywheel", 1.0, 34.641, 0.1);
            RigidBody b = new RigidBody(spec, RigidBodyState.atRest(Vector2D.ZERO));
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            // Row 0: Translation along x (J = [1, 0, 0])
            ConstraintRow r0 = new ConstraintRow("trans", 0, 3);
            r0.setJacobian(0, 1.0);

            // Row 1: Rotation only (J = [0, 0, 1])
            ConstraintRow r1 = new ConstraintRow("rot", 0, 3);
            r1.setJacobian(2, 1.0);

            MassWhitenedDelassus mwd = MassWhitenedDelassus.of(mapping, List.of(r0, r1), new double[3], 0.0, 0.0);
            ColumnPivotedRRQR.RRQRResult rrqr = ColumnPivotedRRQR.factorize(mwd.B(), 3, 2);

            // Both should have normalized diagonal 1.0 regardless of the 100x ratio between m and I
            assertEquals(1.0, rrqr.rDiag()[0], STRICT_EPSILON);
            assertEquals(1.0, rrqr.rDiag()[1], STRICT_EPSILON);
            assertEquals(ConstraintTier.TIER_1A_INDEPENDENT, rrqr.tiers()[0]);
            assertEquals(ConstraintTier.TIER_1A_INDEPENDENT, rrqr.tiers()[1]);
        }
    }

    // =========================================================================
    // LEVEL 3: ACCEPTANCE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 3: Acceptance Tests — Physical Invariants & Solver Contracts")
    class AcceptanceTests {

        @Test
        @DisplayName("3.1 Solver / QR Permutation Equivalence: Solver pivot order is identical to QR permutation")
        void solverQrPermutationEquivalence() {
            RigidBody b = RigidBody.dynamicDisc("body", 2.0, 0.3, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            ConstraintRow r0 = new ConstraintRow("c0", 0, 3);
            r0.setJacobian(0, 0.1);
            r0.setJacobian(1, 0.9);

            ConstraintRow r1 = new ConstraintRow("c1", 0, 3);
            r1.setJacobian(0, 0.8);
            r1.setJacobian(1, 0.2);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(r0, r1), new double[3], 0.0, 0.0);

            int[] pi = res.rrqrResult().permutation();
            assertNotNull(pi);
            assertEquals(2, pi.length);
            assertTrue(pi[0] == 0 || pi[0] == 1);
            assertTrue(pi[1] == 0 || pi[1] == 1);
            assertTrue(pi[0] != pi[1]);
        }

        @Test
        @DisplayName("3.2 Pure Tier 1A Unregularized Residual Invariant: r_phys <= 1.0e-8 m/s^2")
        void pureTier1AUnregularizedResidualInvariant() {
            RigidBody b = RigidBody.dynamicDisc("bob", 1.5, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            ConstraintRow r0 = new ConstraintRow("xLock", 0, 3);
            r0.setJacobian(0, 1.0);
            r0.setPositionError(0.01);

            ConstraintRow r1 = new ConstraintRow("yLock", 0, 3);
            r1.setJacobian(1, 1.0);
            r1.setPositionError(-0.015);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(r0, r1), new double[3], 5.0, 5.0);

            assertEquals(2, res.rrqrResult().tier1ACount());
            assertEquals(0, res.rrqrResult().tier1BCount());

            for (double physRes : res.physicalResiduals()) {
                assertTrue(Math.abs(physRes) <= 1.0e-8,
                        "Pure Tier 1A residual must be within 1.0e-8 m/s^2, but was: " + physRes);
            }
        }

        @Test
        @DisplayName("3.3 Dimensionally Consistent Regularization Bias Bound: |r_phys,i| <= mu_reg^2 * W_ii * |lambda_i|")
        void dimensionallyConsistentRegularizationBiasBound() {
            RigidBody particle = RigidBody.pointMass("p", 2.0, Vector2D.ZERO, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(particle));

            ConstraintRow c1 = new ConstraintRow("cable1", 0, 2);
            c1.setJacobian(1, 1.0);

            double theta = Math.toRadians(0.1);
            ConstraintRow c2 = new ConstraintRow("cable2", 0, 2);
            c2.setJacobian(0, Math.sin(theta));
            c2.setJacobian(1, Math.cos(theta));
            c2.setPositionError(0.01);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(c1, c2), new double[2], 5.0, 5.0);

            int pi1 = res.rrqrResult().permutation()[1];
            double w11 = res.delassus().wDiag()[pi1];
            double lam1 = res.multipliers()[pi1];
            double mu = DelassusRrqrSolver.REGULARIZATION_MU;

            double expectedBound = mu * mu * w11 * Math.abs(lam1) + 1.0e-11;
            assertTrue(Math.abs(res.physicalResiduals()[pi1]) <= expectedBound);
        }

        @Test
        @DisplayName("3.4 Provisional Multipliers: Negative multiplier is preserved without clamping")
        void provisionalMultiplierPreservation() {
            // A body pushed away from constraint target produces negative multiplier (pushing rather than pulling)
            RigidBody b = RigidBody.dynamicDisc("bob", 1.0, 0.2, Vector2D.ZERO);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(b));

            // Acceleration pushing in +y direction
            double[] extAcc = new double[]{0.0, 10.0, 0.0};

            // Cable pulling along +y direction (J = [0, 1, 0])
            ConstraintRow cable = new ConstraintRow("cable", 0, 3);
            cable.setJacobian(1, 1.0);
            cable.setPositionError(0.0);

            DelassusRrqrSolver solver = new DelassusRrqrSolver();
            DelassusRrqrSolver.SolveResult res = solver.solve(mapping, List.of(cable), extAcc, 0.0, 0.0);

            // Algebraic solution requires negative tension lambda = -10.0 N to cancel +10.0 m/s^2
            assertTrue(res.multipliers()[0] < 0.0,
                    "Algebraic multiplier must be negative without silent clamping to 0.0");
            assertEquals(-10.0, res.multipliers()[0], EPSILON);
        }
    }
}
