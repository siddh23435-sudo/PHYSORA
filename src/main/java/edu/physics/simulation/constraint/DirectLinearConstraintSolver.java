package edu.physics.simulation.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Direct symmetric linear solver for bilateral kinematic constraints.
 *
 * <p>Implementation Details:
 * <ul>
 *   <li>Constructs the normalized Delassus matrix bar{W} = S (J M_dyn⁻¹ J^T) S, where S_i = 1 / sqrt(w_ii).</li>
 *   <li>Performs unregularized rank-revealing LDL^T factorization on bar{W} to detect duplicate / redundant constraints before regularization.</li>
 *   <li>Validates kinematic consistency of redundant rows and rejects inconsistent constraints with an exception.</li>
 *   <li>Regularizes only the independent subsystem with dimensionless factor γ = 10⁻⁸ to ensure numerical conditioning.</li>
 *   <li>Avoids explicit matrix inversion; solves multipliers via forward and back substitution.</li>
 * </ul>
 */
public final class DirectLinearConstraintSolver {

    public static final double RANK_TOLERANCE = 1.0e-8;
    public static final double NEGATIVE_PIVOT_TOLERANCE = 1.0e-10;
    public static final double REGULARIZATION_GAMMA = 1.0e-8;

    /**
     * Minimum Delassus mobility threshold w_ii = J_i M_dyn⁻¹ J_i^T in inverse mass units (kg⁻¹).
     *
     * <p>A row with mobility w_ii ≤ 1.0e-14 kg⁻¹ corresponds to an effective mass m_eff ≥ 1.0e14 kg
     * (e.g. constraints connecting two static bodies/anchors with zero dynamic mobility).
     * Guards against division by zero when constructing scale factor S_i = 1 / sqrt(w_ii).
     */
    public static final double MIN_DELASSUS_MOBILITY = 1.0e-14; // kg⁻¹

    public static final double POSITION_CONSISTENCY_TOLERANCE = 1.0e-6; // metres
    public static final double VELOCITY_CONSISTENCY_TOLERANCE = 1.0e-5; // metres / second

    /**
     * Acceleration-level kinematic consistency tolerance in physical acceleration units (m/s²).
     *
     * <p>Derivation: For a consistent redundant constraint, residual acceleration arises from
     * Baumgarte stabilization noise: β² Φ + 2α J̇q. At position tolerance Φ ≤ 1.0e-6 m and velocity
     * tolerance J̇q ≤ 1.0e-5 m/s, with default α = 5.0, β = 5.0:
     * <pre>
     *   β² Φ + 2α J̇q ≤ 25 * 1.0e-6 + 10 * 1.0e-5 = 2.5e-5 + 1.0e-4 = 1.25e-4 m/s²
     * </pre>
     * Adding centripetal acceleration roundoff (~1e-6 m/s²), consistent duplicates produce residuals
     * ≤ 1.5e-4 m/s². Conversely, conflicting constraints (e.g. ΔΦ = 50 µm = 5.0e-5 m) generate
     * β² ΔΦ = 1.25e-3 m/s². Setting the tolerance to 2.0e-4 m/s² cleanly separates consistent duplicate
     * constraints from conflicting kinematic requirements.
     */
    public static final double ACCELERATION_CONSISTENCY_TOLERANCE = 2.0e-4; // metres / second²

    public record Solution(
            double[] multipliers,
            double[] constraintAccelerations,
            List<String> redundantConstraintIds) {
    }

    public DirectLinearConstraintSolver() {
    }

    /**
     * Solves the bilateral acceleration-level constraint system W λ = b.
     *
     * @param mapping coordinate mapping of dynamic degrees of freedom
     * @param rows active constraint rows
     * @param extAccelerations acceleration produced by external forces (M⁻¹ f_ext)
     * @param alpha Baumgarte velocity damping coefficient (s⁻¹)
     * @param beta Baumgarte position restoration coefficient (s⁻¹)
     * @return solved multipliers, resulting constraint accelerations, and detected redundant IDs
     */
    public Solution solve(
            CoordinateMapping mapping,
            List<ConstraintRow> rows,
            double[] extAccelerations,
            double alpha,
            double beta) {

        Objects.requireNonNull(mapping, "mapping is required");
        Objects.requireNonNull(rows, "rows is required");
        Objects.requireNonNull(extAccelerations, "extAccelerations is required");

        int m = rows.size();
        int d = mapping.totalDof();
        if (m == 0 || d == 0) {
            return new Solution(new double[0], new double[d], List.of());
        }

        // 1. Evaluate row Delassus mobility w_ii = J_i M_dyn⁻¹ J_i^T and scale factor S_i
        double[] s = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow r = rows.get(i);
            r.computeEffectiveMass(mapping);
            double w_ii = r.effectiveMass();
            if (w_ii <= MIN_DELASSUS_MOBILITY) {
                throw new IllegalStateException("Constraint row " + r.constraintId() + "[" + r.rowIndex()
                        + "] has zero Delassus mobility (effective mass >= 1e14 kg); connected entities cannot accelerate");
            }
            s[i] = 1.0 / Math.sqrt(w_ii);
        }

        // 2. Assemble normalized right-hand side bar{b} = S * b
        double[] barB = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow r = rows.get(i);
            double jAExt = 0.0;
            for (int j = 0; j < d; j++) {
                double jVal = r.getJacobian(j);
                if (jVal != 0.0) {
                    jAExt += jVal * extAccelerations[j];
                }
            }
            double bi = -r.centripetalAccel() - jAExt - 2.0 * alpha * r.velocityError() - (beta * beta) * r.positionError();
            barB[i] = s[i] * bi;
        }

        // 3. Assemble normalized Delassus matrix bar{W} = S (J M⁻¹ J^T) S
        double[][] barW = buildNormalizedDelassus(mapping, rows, s, m, d);

        // 4. Unregularized rank-revealing LDL^T factorization and redundancy check
        FactorizationResult fact = factorizeUnregularized(barW, barB, s, rows, m, ACCELERATION_CONSISTENCY_TOLERANCE);

        // 5. Solve on independent subsystem with regularization γ = 1e-8
        double[] barLambda = solveSystem(fact, m);

        // 6. Recover physical multipliers λ_i = S_i * bar{λ}_i
        double[] lambda = new double[m];
        for (int i = 0; i < m; i++) {
            lambda[i] = s[i] * barLambda[i];
        }

        // 7. Compute coordinate accelerations a_c = M⁻¹ (Σ J_i^T λ_i)
        double[] aConstraint = computeConstraintAccelerations(mapping, rows, lambda, m, d);

        return new Solution(lambda, aConstraint, fact.redundantConstraintIds);
    }

    /**
     * Solves coordinate displacement Δq = - M⁻¹ J^T (J M⁻¹ J^T)⁻¹ Φ for Newton-Raphson position projection.
     */
    public double[] solvePositionCorrection(
            CoordinateMapping mapping,
            List<ConstraintRow> rows,
            double[] positionErrors) {

        int m = rows.size();
        int d = mapping.totalDof();
        if (m == 0 || d == 0) {
            return new double[d];
        }

        double[] s = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow r = rows.get(i);
            r.computeEffectiveMass(mapping);
            double w_ii = r.effectiveMass();
            if (w_ii <= MIN_DELASSUS_MOBILITY) {
                throw new IllegalStateException("Constraint row " + r.constraintId() + "[" + r.rowIndex()
                        + "] has zero Delassus mobility (effective mass >= 1e14 kg); connected entities cannot move");
            }
            s[i] = 1.0 / Math.sqrt(w_ii);
        }

        double[] barB = new double[m];
        for (int i = 0; i < m; i++) {
            barB[i] = s[i] * positionErrors[i];
        }

        double[][] barW = buildNormalizedDelassus(mapping, rows, s, m, d);
        FactorizationResult fact = factorizeUnregularized(barW, barB, s, rows, m, POSITION_CONSISTENCY_TOLERANCE);
        double[] barMu = solveSystem(fact, m);

        double[] mu = new double[m];
        for (int i = 0; i < m; i++) {
            mu[i] = s[i] * barMu[i];
        }

        // Δq = - M⁻¹ J^T mu
        double[] deltaQ = new double[d];
        for (int i = 0; i < m; i++) {
            double mu_i = mu[i];
            if (mu_i == 0.0) continue;
            ConstraintRow r = rows.get(i);
            for (int j = 0; j < d; j++) {
                double jVal = r.getJacobian(j);
                if (jVal != 0.0) {
                    deltaQ[j] -= mapping.invMassAt(j) * jVal * mu_i;
                }
            }
        }
        return deltaQ;
    }

    /**
     * Solves velocity correction Δv = - M⁻¹ J^T (J M⁻¹ J^T)⁻¹ (J v) for linear velocity projection.
     */
    public double[] solveVelocityCorrection(
            CoordinateMapping mapping,
            List<ConstraintRow> rows,
            double[] velocityErrors) {

        int m = rows.size();
        int d = mapping.totalDof();
        if (m == 0 || d == 0) {
            return new double[d];
        }

        double[] s = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow r = rows.get(i);
            r.computeEffectiveMass(mapping);
            double w_ii = r.effectiveMass();
            if (w_ii <= MIN_DELASSUS_MOBILITY) {
                throw new IllegalStateException("Constraint row " + r.constraintId() + "[" + r.rowIndex()
                        + "] has zero Delassus mobility (effective mass >= 1e14 kg); connected entities cannot move");
            }
            s[i] = 1.0 / Math.sqrt(w_ii);
        }

        double[] barB = new double[m];
        for (int i = 0; i < m; i++) {
            barB[i] = s[i] * velocityErrors[i];
        }

        double[][] barW = buildNormalizedDelassus(mapping, rows, s, m, d);
        FactorizationResult fact = factorizeUnregularized(barW, barB, s, rows, m, VELOCITY_CONSISTENCY_TOLERANCE);
        double[] barSigma = solveSystem(fact, m);

        double[] sigma = new double[m];
        for (int i = 0; i < m; i++) {
            sigma[i] = s[i] * barSigma[i];
        }

        // Δv = - M⁻¹ J^T sigma
        double[] deltaV = new double[d];
        for (int i = 0; i < m; i++) {
            double sig_i = sigma[i];
            if (sig_i == 0.0) continue;
            ConstraintRow r = rows.get(i);
            for (int j = 0; j < d; j++) {
                double jVal = r.getJacobian(j);
                if (jVal != 0.0) {
                    deltaV[j] -= mapping.invMassAt(j) * jVal * sig_i;
                }
            }
        }
        return deltaV;
    }

    private static double[][] buildNormalizedDelassus(
            CoordinateMapping mapping, List<ConstraintRow> rows, double[] s, int m, int d) {
        double[][] barW = new double[m][m];
        for (int i = 0; i < m; i++) {
            ConstraintRow rowI = rows.get(i);
            for (int k = 0; k <= i; k++) {
                if (i == k) {
                    barW[i][i] = 1.0;
                } else {
                    ConstraintRow rowK = rows.get(k);
                    double sum = 0.0;
                    for (int j = 0; j < d; j++) {
                        double jI = rowI.getJacobian(j);
                        double jK = rowK.getJacobian(j);
                        if (jI != 0.0 && jK != 0.0) {
                            sum += jI * mapping.invMassAt(j) * jK;
                        }
                    }
                    double normW = s[i] * sum * s[k];
                    barW[i][k] = normW;
                    barW[k][i] = normW;
                }
            }
        }
        return barW;
    }

    static record FactorizationResult(
            double[][] L,
            double[] D,
            boolean[] isRedundant,
            List<String> redundantConstraintIds,
            double[] y) {
    }

    static FactorizationResult factorizeUnregularized(
            double[][] A,
            double[] b,
            double[] s,
            List<ConstraintRow> rows,
            int m,
            double inconsistencyTolerance) {

        double[][] L = new double[m][m];
        double[] D = new double[m];
        boolean[] isRedundant = new boolean[m];
        double[] y = new double[m];
        List<String> redundantIds = new ArrayList<>();

        for (int i = 0; i < m; i++) {
            L[i][i] = 1.0;
        }

        for (int k = 0; k < m; k++) {
            double sumD = 0.0;
            for (int j = 0; j < k; j++) {
                if (!isRedundant[j]) {
                    sumD += L[k][j] * L[k][j] * D[j];
                }
            }
            double d_k = A[k][k] - sumD;

            double sumY = 0.0;
            for (int j = 0; j < k; j++) {
                if (!isRedundant[j]) {
                    sumY += L[k][j] * y[j];
                }
            }
            double residual = b[k] - sumY;

            if (d_k < -NEGATIVE_PIVOT_TOLERANCE) {
                // Case 3: Materially negative pivot: indicates an indefinite Delassus matrix (mathematical or numerical failure)
                throw new IllegalStateException("Materially negative pivot encountered during LDLT factorization at row "
                        + rows.get(k).constraintId() + "[" + rows.get(k).rowIndex()
                        + "]: d_k = " + d_k + " < -" + NEGATIVE_PIVOT_TOLERANCE
                        + "; Delassus matrix is indefinite or numerical corruption occurred");
            } else if (d_k < RANK_TOLERANCE) {
                // Case 2: Near-zero pivot (including small negative roundoff within [-NEGATIVE_PIVOT_TOLERANCE, RANK_TOLERANCE)):
                // Classify as rank-deficient / redundant constraint
                isRedundant[k] = true;
                D[k] = 0.0;
                y[k] = 0.0;
                redundantIds.add(rows.get(k).constraintId());

                double scale = (s != null && k < s.length && s[k] > 0.0) ? s[k] : 1.0;
                double physResidual = Math.abs(residual) / scale;
                if (physResidual > inconsistencyTolerance) {
                    throw new IllegalStateException("Inconsistent conflicting constraint detected at row "
                            + rows.get(k).constraintId() + "[" + rows.get(k).rowIndex()
                            + "]: physical residual " + physResidual + " exceeds consistency tolerance "
                            + inconsistencyTolerance);
                }
            } else {
                // Case 1: Sufficiently positive pivot (>= RANK_TOLERANCE): independent constraint row
                D[k] = d_k;
                y[k] = residual;
                for (int i = k + 1; i < m; i++) {
                    double sumL = 0.0;
                    for (int j = 0; j < k; j++) {
                        if (!isRedundant[j]) {
                            sumL += L[i][j] * L[k][j] * D[j];
                        }
                    }
                    L[i][k] = (A[i][k] - sumL) / d_k;
                }
            }
        }

        return new FactorizationResult(L, D, isRedundant, Collections.unmodifiableList(redundantIds), y);
    }

    private static double[] solveSystem(FactorizationResult fact, int m) {
        double[][] L = fact.L;
        double[] D = fact.D;
        boolean[] isRedundant = fact.isRedundant;
        double[] y = fact.y;

        // Diagonal solve: z = y / (D + gamma)
        double[] z = new double[m];
        for (int i = 0; i < m; i++) {
            if (isRedundant[i]) {
                z[i] = 0.0;
            } else {
                z[i] = y[i] / (D[i] + REGULARIZATION_GAMMA);
            }
        }

        // Backward solve: L^T x = z
        double[] x = new double[m];
        for (int i = m - 1; i >= 0; i--) {
            if (isRedundant[i]) {
                x[i] = 0.0;
                continue;
            }
            double sum = 0.0;
            for (int j = i + 1; j < m; j++) {
                if (!isRedundant[j]) {
                    sum += L[j][i] * x[j];
                }
            }
            x[i] = z[i] - sum;
        }

        return x;
    }

    private static double[] computeConstraintAccelerations(
            CoordinateMapping mapping, List<ConstraintRow> rows, double[] lambda, int m, int d) {
        double[] aConstraint = new double[d];
        for (int i = 0; i < m; i++) {
            double lam_i = lambda[i];
            if (lam_i == 0.0) continue;
            ConstraintRow r = rows.get(i);
            for (int j = 0; j < d; j++) {
                double jVal = r.getJacobian(j);
                if (jVal != 0.0) {
                    aConstraint[j] += mapping.invMassAt(j) * jVal * lam_i;
                }
            }
        }
        return aConstraint;
    }
}
