package edu.physics.simulation.constraint;

import java.util.Objects;

/**
 * Column-pivoted Rank-Revealing QR (RRQR) factorization of normalized constraint matrices.
 *
 * <p>Decomposition:
 * Given normalized mass-whitened Jacobian {@code B ∈ ℝ^(m × n_dyn)}, this class factorizes the
 * transpose {@code A = B^T ∈ ℝ^(n_dyn × m)} using column-pivoted Householder reflections:
 * <pre>
 *   B^T P = Q R
 * </pre>
 * where:
 * <ul>
 *   <li>{@code P ∈ ℝ^(m × m)} is a permutation matrix represented by index vector {@code pi}.</li>
 *   <li>{@code Q ∈ ℝ^(n_dyn × n_dyn)} is an orthogonal matrix ({@code Q^T Q = I}).</li>
 *   <li>{@code R ∈ ℝ^(n_dyn × m)} is an upper trapezoidal matrix.</li>
 * </ul>
 *
 * <p>Delassus Cholesky Identity:
 * <pre>
 *   P^T bar{W} P = P^T (B B^T) P = (B^T P)^T (B^T P) = R^T Q^T Q R = R^T R
 * </pre>
 * The upper triangular block {@code R_11 ∈ ℝ^(r × r)} is the exact Cholesky factor of the independent
 * normalized Delassus submatrix {@code (P^T bar{W} P)_{1:r, 1:r} = R_11^T R_11}.
 *
 * <p>Classification Thresholds:
 * <ul>
 *   <li>Tier 1A (Independent): {@code r_diag >= tau_indep = sqrt(10^-5) ≈ 3.162277e-3}</li>
 *   <li>Tier 1B (Near-dependent, regularized): {@code tau_redun = 1.0e-4 <= r_diag < tau_indep}</li>
 *   <li>Tier 2 (Redundant): {@code r_diag < tau_redun = 1.0e-4}</li>
 * </ul>
 */
public final class ColumnPivotedRRQR {

    public static final double TAU_INDEP = Math.sqrt(1.0e-5); // ≈ 3.162277660168379e-3
    public static final double TAU_REDUN = 1.0e-4;
    public static final double TIE_TOLERANCE = 1.0e-14;

    public record RRQRResult(
            int rank,
            int tier1ACount,
            int tier1BCount,
            int tier2Count,
            int[] permutation,
            double[][] R,
            double[][] Q,
            double[] rDiag,
            ConstraintTier[] tiers) {

        public double[][] R11() {
            double[][] r11 = new double[rank][rank];
            for (int i = 0; i < rank; i++) {
                System.arraycopy(R[i], 0, r11[i], 0, rank);
            }
            return r11;
        }

        public double[][] R12() {
            int m = tiers.length;
            int depCount = m - rank;
            double[][] r12 = new double[rank][depCount];
            for (int i = 0; i < rank; i++) {
                System.arraycopy(R[i], rank, r12[i], 0, depCount);
            }
            return r12;
        }
    }

    private ColumnPivotedRRQR() {
    }

    /**
     * Factorizes {@code B^T ∈ ℝ^(nDyn × m)} using column-pivoted Householder reflections.
     *
     * @param B normalized mass-whitened matrix (m × nDyn)
     * @param nDyn dynamic degrees of freedom
     * @param m number of constraint rows
     * @return decomposition result containing R, Q, permutation, and 4-tier classifications
     */
    public static RRQRResult factorize(double[][] B, int nDyn, int m) {
        Objects.requireNonNull(B, "B is required");

        if (m == 0 || nDyn == 0) {
            return new RRQRResult(
                    0, 0, 0, 0, new int[0], new double[0][0], new double[nDyn][nDyn], new double[0], new ConstraintTier[0]);
        }

        int kMax = Math.min(nDyn, m);

        // 1. Initialize transpose A = B^T (dimensions: nDyn × m)
        double[][] A = new double[nDyn][m];
        for (int i = 0; i < nDyn; i++) {
            for (int j = 0; j < m; j++) {
                A[i][j] = B[j][i];
            }
        }

        // 2. Initialize permutation vector pi and column norms squared
        int[] pi = new int[m];
        double[] c = new double[m];
        for (int j = 0; j < m; j++) {
            pi[j] = j;
            double sumSq = 0.0;
            for (int i = 0; i < nDyn; i++) {
                sumSq += A[i][j] * A[i][j];
            }
            c[j] = sumSq;
        }

        // 3. Initialize orthogonal matrix Q = I_(nDyn × nDyn)
        double[][] Q = new double[nDyn][nDyn];
        for (int i = 0; i < nDyn; i++) {
            Q[i][i] = 1.0;
        }

        double[] rDiag = new double[m];
        ConstraintTier[] tiers = new ConstraintTier[m];
        int tier1ACount = 0;
        int tier1BCount = 0;
        int tier2Count = 0;

        // Workspace for Householder vector u
        double[] u = new double[nDyn];

        // 4. Column-pivoted Householder reduction
        for (int k = 0; k < kMax; k++) {
            // Find pivot column with maximum trailing norm, applying deterministic tie-breaking
            int p = k;
            double maxNorm = c[k];
            for (int j = k + 1; j < m; j++) {
                if (c[j] > maxNorm + TIE_TOLERANCE) {
                    maxNorm = c[j];
                    p = j;
                } else if (Math.abs(c[j] - maxNorm) <= TIE_TOLERANCE) {
                    // Tie-breaking: prefer smaller original input index
                    if (pi[j] < pi[p]) {
                        maxNorm = c[j];
                        p = j;
                    }
                }
            }

            // Swap columns k and p in A, c, and pi
            if (p != k) {
                for (int i = 0; i < nDyn; i++) {
                    double tmpA = A[i][k];
                    A[i][k] = A[i][p];
                    A[i][p] = tmpA;
                }
                double tmpC = c[k];
                c[k] = c[p];
                c[p] = tmpC;

                int tmpPi = pi[k];
                pi[k] = pi[p];
                pi[p] = tmpPi;
            }

            // Compute Householder reflector for column k from row k to nDyn - 1
            double normX = 0.0;
            for (int i = k; i < nDyn; i++) {
                normX += A[i][k] * A[i][k];
            }
            normX = Math.sqrt(normX);

            if (normX > 0.0) {
                double alpha = (A[k][k] >= 0.0) ? -normX : normX;
                double u0 = A[k][k] - alpha;
                double beta = -1.0 / (alpha * u0);

                u[0] = u0;
                for (int i = k + 1; i < nDyn; i++) {
                    u[i - k] = A[i][k];
                }

                // Apply H_k = I - beta * u * u^T to trailing columns of A
                for (int j = k + 1; j < m; j++) {
                    double dot = u[0] * A[k][j];
                    for (int i = k + 1; i < nDyn; i++) {
                        dot += u[i - k] * A[i][j];
                    }
                    double tau = beta * dot;
                    A[k][j] -= tau * u[0];
                    for (int i = k + 1; i < nDyn; i++) {
                        A[i][j] -= tau * u[i - k];
                    }
                }

                // Apply H_k to Q from the right: Q <- Q * H_k
                for (int r = 0; r < nDyn; r++) {
                    double dotQ = 0.0;
                    for (int i = k; i < nDyn; i++) {
                        dotQ += Q[r][i] * u[i - k];
                    }
                    double tauQ = beta * dotQ;
                    for (int i = k; i < nDyn; i++) {
                        Q[r][i] -= tauQ * u[i - k];
                    }
                }

                A[k][k] = alpha;
                for (int i = k + 1; i < nDyn; i++) {
                    A[i][k] = 0.0;
                }
            }

            // Recompute exact trailing column norms for remaining columns
            for (int j = k + 1; j < m; j++) {
                double sumSq = 0.0;
                for (int i = k + 1; i < nDyn; i++) {
                    sumSq += A[i][j] * A[i][j];
                }
                c[j] = sumSq;
            }

            // Evaluate residual diagonal norm r_diag = |R_kk|
            double diagVal = Math.abs(A[k][k]);
            rDiag[k] = diagVal;

            if (diagVal >= TAU_INDEP) {
                tiers[k] = ConstraintTier.TIER_1A_INDEPENDENT;
                tier1ACount++;
            } else if (diagVal >= TAU_REDUN) {
                tiers[k] = ConstraintTier.TIER_1B_REGULARIZED;
                tier1BCount++;
            } else {
                tiers[k] = ConstraintTier.TIER_2_REDUNDANT;
                tier2Count++;
            }
        }

        // Columns beyond nDyn (when m > nDyn) have zero residual dimension
        for (int k = kMax; k < m; k++) {
            rDiag[k] = 0.0;
            tiers[k] = ConstraintTier.TIER_2_REDUNDANT;
            tier2Count++;
        }

        int rank = tier1ACount + tier1BCount;

        // Extract upper trapezoidal R matrix (nDyn × m)
        double[][] R = new double[nDyn][m];
        for (int i = 0; i < nDyn; i++) {
            System.arraycopy(A[i], 0, R[i], 0, m);
        }

        return new RRQRResult(
                rank,
                tier1ACount,
                tier1BCount,
                tier2Count,
                pi,
                R,
                Q,
                rDiag,
                tiers);
    }
}

