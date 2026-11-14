package edu.physics.simulation.constraint;

import java.util.List;
import java.util.Objects;

/**
 * Mass-whitened and row-normalized representation of a constraint system.
 *
 * <p>Mathematical Formulation:
 * <ul>
 *   <li>Inverse mass whitening operator: {@code C = M_dyn^(-1/2)}, diagonal with entries {@code 1 / sqrt(m_i)}
 *       for translation and {@code 1 / sqrt(I_i)} for rotation.</li>
 *   <li>Unscaled mass-whitened Jacobian: {@code J_white = J_dyn C}, where row norm satisfies
 *       {@code ||J_white,i||_2^2 = W_ii = J_i M_dyn^(-1) J_i^T} (Delassus diagonal mobility in kg^-1).</li>
 *   <li>Delassus row scaling: {@code S_ii = 1 / sqrt(W_ii) = 1 / ||J_white,i||_2} (in kg^(1/2)).</li>
 *   <li>Normalized mass-whitened Jacobian: {@code B = S J_dyn C}, with unit row Euclidean norm {@code ||B_i||_2 = 1.0}.</li>
 *   <li>Normalized Delassus matrix: {@code bar{W} = B B^T = S W S}, with unit diagonal {@code bar{W}_ii = 1.0}.</li>
 *   <li>Normalized right-hand side: {@code bar{b} = S b}, where {@code b_i = -(gamma_conv,i + J_i a_free + 2 alpha dot{g}_i + beta^2 g_i)}.</li>
 * </ul>
 */
public record MassWhitenedDelassus(
        double[][] B,
        double[] barB,
        double[] s,
        double[] rawB,
        double[] wDiag,
        int m,
        int nDyn) {

    public static final double MIN_DELASSUS_MOBILITY = 1.0e-14; // kg^-1

    public static MassWhitenedDelassus of(
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
            return new MassWhitenedDelassus(
                    new double[0][0], new double[0], new double[0], new double[0], new double[0], 0, nDyn);
        }

        // 1. Compute diagonal inverse mass whitening factor C_j = sqrt(M_jj^(-1))
        double[] invMass = mapping.invMass();
        double[] c = new double[nDyn];
        for (int j = 0; j < nDyn; j++) {
            c[j] = Math.sqrt(invMass[j]);
        }

        // 2. Evaluate Delassus mobility W_ii and row scale factor s_i = 1 / sqrt(W_ii)
        double[] wDiag = new double[m];
        double[] s = new double[m];
        for (int i = 0; i < m; i++) {
            ConstraintRow row = rows.get(i);
            row.computeEffectiveMass(mapping);
            double w_ii = row.effectiveMass();

            if (!Double.isFinite(w_ii) || w_ii <= MIN_DELASSUS_MOBILITY) {
                throw new IllegalStateException("Constraint row " + row.constraintId() + "[" + row.rowIndex()
                        + "] has zero Delassus mobility (W_ii = " + w_ii + " <= " + MIN_DELASSUS_MOBILITY
                        + " kg^-1); connected entities have zero dynamic mobility (Tier 4 rejection)");
            }
            wDiag[i] = w_ii;
            s[i] = 1.0 / Math.sqrt(w_ii);
        }

        // 3. Assemble normalized mass-whitened Jacobian B_ij = s_i * J_ij * C_j
        double[][] B = new double[m][nDyn];
        for (int i = 0; i < m; i++) {
            ConstraintRow row = rows.get(i);
            double si = s[i];
            for (int j = 0; j < nDyn; j++) {
                double jVal = row.getJacobian(j);
                if (jVal != 0.0) {
                    double bVal = si * jVal * c[j];
                    if (bVal == 0.0) bVal = 0.0;
                    B[i][j] = bVal;
                }
            }
        }

        // 4. Assemble physical and normalized right-hand side b and bar{b}
        double[] rawB = new double[m];
        double[] barB = new double[m];
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
            rawB[i] = bi;
            barB[i] = s[i] * bi;
        }

        return new MassWhitenedDelassus(B, barB, s, rawB, wDiag, m, nDyn);
    }
}

