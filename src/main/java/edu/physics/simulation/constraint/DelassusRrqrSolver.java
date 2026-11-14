package edu.physics.simulation.constraint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Direct symmetric solver for constraint subsystems based on mass-whitened RRQR factorization.
 *
 * <p>Implementation Details:
 * <ul>
 *   <li>Pre-factorization Canonical Ordering: Sorts candidate constraint rows by persistent
 *       {@code (constraintId, rowIndex)} without mutating caller's collection, guaranteeing deterministic
 *       initialization across arbitrary caller collection traversal orders.</li>
 *   <li>Constructs mass-whitened and row-normalized Jacobian {@code B = S J_dyn C}.</li>
 *   <li>Performs column-pivoted Householder RRQR on {@code B^T P = Q R} with deterministic tie-breaking.</li>
 *   <li>Unregularized Delassus Cholesky Identity: {@code P^T bar{W} P = R^T R}.</li>
 *   <li>Option A Redundant-Row Compatibility: Solves unregularized {@code R_11^T y = bar{b}_indep} by forward
 *       substitution and verifies that implied RHS {@code R_12^T y} matches dependent rows within
 *       acceleration tolerance {@code 2.0e-4 m/s^2}.</li>
 *   <li>Augmented Householder QR Regularization: For Tier 1B directions, factors {@code [R_11; D_mu]} to obtain
 *       exact factor {@code tilde{R}} satisfying {@code tilde{R}^T tilde{R} = R_11^T R_11 + D_mu^2}.</li>
 *   <li>Provisional Multipliers: Outputs algebraic multipliers {@code lambda_i} mapped back to caller's original
 *       input indices without silent clamping or unilateral active-set iteration.</li>
 * </ul>
 */
public final class DelassusRrqrSolver {

    public static final double REGULARIZATION_MU = 1.0e-4;
    public static final double ACCELERATION_CONSISTENCY_TOLERANCE = 2.0e-4; // m/s^2

    public record SolveResult(
            double[] multipliers,
            double[] coordinateAccelerations,
            double[] physicalResiduals,
            List<String> redundantConstraintIds,
            MassWhitenedDelassus delassus,
            ColumnPivotedRRQR.RRQRResult rrqrResult,
            double[][] regularizedR) {
    }

    public DelassusRrqrSolver() {
    }

    /**
     * Solves the linear equality constraint subsystem {@code W lambda = b}.
     *
     * @param mapping coordinate mapping of dynamic degrees of freedom
     * @param rows active constraint rows
     * @param extAccelerations acceleration produced by external forces (M^-1 f_ext)
     * @param alpha Baumgarte velocity damping coefficient (s^-1)
     * @param beta Baumgarte position restoration coefficient (s^-1)
     * @return solved provisional multipliers, resulting coordinate accelerations, residuals, and diagnostics
     */
    public SolveResult solve(
            CoordinateMapping mapping,
            List<ConstraintRow> rows,
            double[] extAccelerations,
            double alpha,
            double beta) {

        Objects.requireNonNull(mapping, "mapping is required");
        Objects.requireNonNull(rows, "rows is required");
        Objects.requireNonNull(extAccelerations, "extAccelerations is required");

        int m = rows.size();
        int nDyn = mapping.totalDof();

        if (m == 0 || nDyn == 0) {
            MassWhitenedDelassus emptyDelassus = MassWhitenedDelassus.of(mapping, rows, extAccelerations, alpha, beta);
            ColumnPivotedRRQR.RRQRResult emptyRrqr = ColumnPivotedRRQR.factorize(emptyDelassus.B(), nDyn, m);
            return new SolveResult(
                    new double[0], new double[nDyn], new double[0], List.of(), emptyDelassus, emptyRrqr, new double[0][0]);
        }

        // 1. Establish canonical pre-factorization ordering
        // Sorts rows deterministically by (constraintId, rowIndex) without mutating the caller's list
        Integer[] canonicalOrder = new Integer[m];
        for (int i = 0; i < m; i++) {
            canonicalOrder[i] = i;
        }
        Arrays.sort(canonicalOrder, Comparator
                .comparing((Integer idx) -> rows.get(idx).constraintId())
                .thenComparingInt(idx -> rows.get(idx).rowIndex())
                .thenComparingInt(idx -> idx));

        List<ConstraintRow> canonicalRows = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            canonicalRows.add(rows.get(canonicalOrder[i]));
        }

        // Defensive validation: reject duplicate (constraintId, rowIndex) keys before matrix assembly
        for (int i = 1; i < m; i++) {
            ConstraintRow prev = canonicalRows.get(i - 1);
            ConstraintRow curr = canonicalRows.get(i);
            if (prev.constraintId().equals(curr.constraintId()) && prev.rowIndex() == curr.rowIndex()) {
                throw new IllegalArgumentException("Duplicate constraint row identity detected: "
                        + curr.constraintId() + "[" + curr.rowIndex() + "]");
            }
        }

        // 2. Prepare mass-whitened and row-normalized Delassus system on canonically ordered rows
        MassWhitenedDelassus delassus = MassWhitenedDelassus.of(mapping, canonicalRows, extAccelerations, alpha, beta);

        // 3. Column-pivoted Householder RRQR on B^T
        ColumnPivotedRRQR.RRQRResult rrqr = ColumnPivotedRRQR.factorize(delassus.B(), nDyn, m);
        int rank = rrqr.rank();
        int[] pi = rrqr.permutation();
        double[][] R = rrqr.R();
        double[] barB = delassus.barB();
        double[] s = delassus.s();

        List<String> redundantIds = new ArrayList<>();

        // 4. Option A Redundant-Row Compatibility Check using unregularized R_11
        if (rank < m) {
            double[] y = new double[rank];
            for (int i = 0; i < rank; i++) {
                double sum = 0.0;
                for (int j = 0; j < i; j++) {
                    sum += R[j][i] * y[j];
                }
                double diag = R[i][i];
                if (Math.abs(diag) < 1.0e-12) {
                    throw new IllegalStateException("Singular pivot encountered in unregularized R_11 during compatibility check: " + diag);
                }
                y[i] = (barB[pi[i]] - sum) / diag;
            }

            for (int k = rank; k < m; k++) {
                int rowIdx = pi[k];
                double impliedBarB = 0.0;
                for (int i = 0; i < rank; i++) {
                    impliedBarB += R[i][k] * y[i];
                }
                double normResidual = Math.abs(barB[rowIdx] - impliedBarB);
                double rAccel = normResidual / s[rowIdx];

                if (rAccel > ACCELERATION_CONSISTENCY_TOLERANCE) {
                    ConstraintRow failedRow = canonicalRows.get(rowIdx);
                    throw new IllegalStateException("Constraint " + failedRow.constraintId() + "[" + failedRow.rowIndex()
                            + "] is kinematically incompatible with the independent subsystem: acceleration residual = "
                            + rAccel + " m/s^2 > " + ACCELERATION_CONSISTENCY_TOLERANCE + " m/s^2 (Tier 4 rejection)");
                }
                redundantIds.add(canonicalRows.get(rowIdx).constraintId());
            }
        }

        if (rank == 0) {
            return new SolveResult(
                    new double[m], new double[nDyn], delassus.rawB().clone(), redundantIds, delassus, rrqr, new double[0][0]);
        }

        // 5. Regularization via Augmented Householder QR
        double[][] Rtilde = new double[rank][rank];
        if (rrqr.tier1BCount() > 0) {
            int twoR = 2 * rank;
            double[][] K = new double[twoR][rank];
            for (int i = 0; i < rank; i++) {
                System.arraycopy(R[i], 0, K[i], 0, rank);
            }
            for (int i = 0; i < rank; i++) {
                if (rrqr.tiers()[i] == ConstraintTier.TIER_1B_REGULARIZED) {
                    K[rank + i][i] = REGULARIZATION_MU;
                }
            }

            // Householder QR reduction on K
            double[] uK = new double[twoR];
            for (int k = 0; k < rank; k++) {
                double normX = 0.0;
                for (int i = k; i < twoR; i++) {
                    normX += K[i][k] * K[i][k];
                }
                normX = Math.sqrt(normX);
                if (normX > 0.0) {
                    double alphaK = (K[k][k] >= 0.0) ? -normX : normX;
                    double u0 = K[k][k] - alphaK;
                    double betaK = -1.0 / (alphaK * u0);
                    uK[0] = u0;
                    for (int i = k + 1; i < twoR; i++) {
                        uK[i - k] = K[i][k];
                    }
                    for (int j = k + 1; j < rank; j++) {
                        double dot = u0 * K[k][j];
                        for (int i = k + 1; i < twoR; i++) {
                            dot += uK[i - k] * K[i][j];
                        }
                        double tau = betaK * dot;
                        K[k][j] -= tau * u0;
                        for (int i = k + 1; i < twoR; i++) {
                            K[i][j] -= tau * uK[i - k];
                        }
                    }
                    K[k][k] = alphaK;
                    for (int i = k + 1; i < twoR; i++) {
                        K[i][k] = 0.0;
                    }
                }
            }

            for (int i = 0; i < rank; i++) {
                System.arraycopy(K[i], 0, Rtilde[i], 0, rank);
                if (Rtilde[i][i] < 0.0) {
                    for (int j = i; j < rank; j++) {
                        Rtilde[i][j] = -Rtilde[i][j];
                    }
                }
            }
        } else {
            for (int i = 0; i < rank; i++) {
                System.arraycopy(R[i], 0, Rtilde[i], 0, rank);
                if (Rtilde[i][i] < 0.0) {
                    for (int j = i; j < rank; j++) {
                        Rtilde[i][j] = -Rtilde[i][j];
                    }
                }
            }
        }

        // 6. Solve linear system via forward and back substitution
        // Forward solve: Rtilde^T z = barB_pi
        double[] z = new double[rank];
        for (int i = 0; i < rank; i++) {
            double sum = 0.0;
            for (int j = 0; j < i; j++) {
                sum += Rtilde[j][i] * z[j];
            }
            z[i] = (barB[pi[i]] - sum) / Rtilde[i][i];
        }

        // Backward solve: Rtilde barLambda_pi = z
        double[] barLambdaPi = new double[rank];
        for (int i = rank - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < rank; j++) {
                sum += Rtilde[i][j] * barLambdaPi[j];
            }
            barLambdaPi[i] = (z[i] - sum) / Rtilde[i][i];
        }

        // 7. Recover provisional physical multipliers on canonically ordered rows
        double[] canonicalLambda = new double[m];
        for (int i = 0; i < rank; i++) {
            canonicalLambda[pi[i]] = s[pi[i]] * barLambdaPi[i];
        }

        // 8. Map multipliers back to caller's original input ordering
        double[] callerLambda = new double[m];
        for (int c = 0; c < m; c++) {
            int origIdx = canonicalOrder[c];
            callerLambda[origIdx] = canonicalLambda[c];
        }

        // 9. Compute resulting coordinate accelerations a_constraint = M_dyn^-1 J^T lambda
        double[] aConstraint = new double[nDyn];
        for (int i = 0; i < m; i++) {
            double lam = callerLambda[i];
            if (lam != 0.0) {
                ConstraintRow row = rows.get(i);
                for (int j = 0; j < nDyn; j++) {
                    double jVal = row.getJacobian(j);
                    if (jVal != 0.0) {
                        aConstraint[j] += mapping.invMassAt(j) * jVal * lam;
                    }
                }
            }
        }

        // 10. Compute physical acceleration residuals r_phys,i = b_i - J_i a_constraint for caller's rows
        double[] callerPhysResiduals = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow row = rows.get(i);
            double jAExt = 0.0;
            for (int j = 0; j < nDyn; j++) {
                double jVal = row.getJacobian(j);
                if (jVal != 0.0) {
                    jAExt += jVal * extAccelerations[j];
                }
            }
            double bi = -row.centripetalAccel() - jAExt - 2.0 * alpha * row.velocityError() - (beta * beta) * row.positionError();
            double jAc = 0.0;
            for (int j = 0; j < nDyn; j++) {
                double jVal = row.getJacobian(j);
                if (jVal != 0.0) {
                    jAc += jVal * aConstraint[j];
                }
            }
            callerPhysResiduals[i] = bi - jAc;
        }

        return new SolveResult(
                callerLambda,
                aConstraint,
                callerPhysResiduals,
                redundantIds,
                delassus,
                rrqr,
                Rtilde);
    }
}


