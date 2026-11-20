package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Direct active-set solver for unilateral inextensible cable systems coupled with bilateral constraints.
 *
 * <p>Implementation Details:
 * <ul>
 *   <li>Canonical Indexing: Sorts cables deterministically by {@code cableId} and rejects duplicate identities.</li>
 *   <li>Velocity Gating: Cables at boundary with separating velocity ({@code gapRate > epsVel}) are strictly inactive.</li>
 *   <li>Complementarity: Solves equality subsystem via {@link DelassusRrqrSolver} and evaluates slack acceleration
 *       {@code w_j} across all cables.</li>
 *   <li>Robust Anti-Cycling: Uses {@link BitSet} set representations, cycle history detection, deterministic
 *       single-pivot fallback, and a hard iteration cap {@code K_max = 2(m_cables + 1)}.</li>
 *   <li>Near-Zero Multiplier Policy: Retains {@code -epsLambda <= lambda_alg < 0} in active set, clamps public tension
 *       to {@code 0.0}, and explicitly validates the resulting public acceleration and physical residuals.</li>
 *   <li>Guardrail: Residual validation failures immediately abort with {@link ActiveSetConvergenceException};
 *       they never independently trigger active-set pivots.</li>
 * </ul>
 */
public final class UnilateralActiveSetSolver {

    public static final double DEFAULT_EPS_LAMBDA = 1.0e-7;      // N
    public static final double DEFAULT_EPS_W = 1.0e-7;           // m/s^2
    public static final double DEFAULT_EPS_POS = 1.0e-5;         // m
    public static final double DEFAULT_EPS_VEL = 1.0e-5;         // m/s
    public static final double DEFAULT_EPS_A_TRANS = 1.0e-6;     // m/s^2
    public static final double DEFAULT_EPS_A_ROT = 1.0e-5;       // rad/s^2
    public static final double DEFAULT_EPS_BILATERAL = 2.0e-4;   // m/s^2

    private final double epsLambda;
    private final double epsW;
    private final double epsPos;
    private final double epsVel;
    private final double epsATrans;
    private final double epsARot;
    private final double epsBilateral;
    private final DelassusRrqrSolver innerSolver;

    public UnilateralActiveSetSolver() {
        this(DEFAULT_EPS_LAMBDA, DEFAULT_EPS_W, DEFAULT_EPS_POS, DEFAULT_EPS_VEL,
                DEFAULT_EPS_A_TRANS, DEFAULT_EPS_A_ROT, DEFAULT_EPS_BILATERAL);
    }

    public UnilateralActiveSetSolver(
            double epsLambda,
            double epsW,
            double epsPos,
            double epsVel,
            double epsATrans,
            double epsARot,
            double epsBilateral) {

        this.epsLambda = epsLambda;
        this.epsW = epsW;
        this.epsPos = epsPos;
        this.epsVel = epsVel;
        this.epsATrans = epsATrans;
        this.epsARot = epsARot;
        this.epsBilateral = epsBilateral;
        this.innerSolver = new DelassusRrqrSolver();
    }

    /**
     * Entry descriptor bundling a cable's unique ID, constraint Jacobian row, and kinematic state.
     */
    public record UnilateralCableEntry(
            String cableId,
            ConstraintRow row,
            CableKinematics kinematics) {

        public UnilateralCableEntry {
            Objects.requireNonNull(cableId, "cableId is required");
            if (cableId.isBlank()) {
                throw new IllegalArgumentException("cableId cannot be blank");
            }
            Objects.requireNonNull(row, "row is required");
            Objects.requireNonNull(kinematics, "kinematics is required");
        }
    }

    /**
     * Solves the combined bilateral and unilateral cable system from runtime geometries.
     */
    public UnilateralSolveResult solve(
            CoordinateMapping mapping,
            Map<UUID, RigidBody> bodies,
            List<ConstraintRow> bilateralConstraints,
            List<DirectCableGeometry> cables,
            double[] extAccelerations,
            double alpha,
            double beta,
            Set<String> initialTautCables) {

        Objects.requireNonNull(mapping, "mapping is required");
        Objects.requireNonNull(bodies, "bodies is required");
        Objects.requireNonNull(bilateralConstraints, "bilateralConstraints is required");
        Objects.requireNonNull(cables, "cables is required");
        Objects.requireNonNull(extAccelerations, "extAccelerations is required");

        List<UnilateralCableEntry> entries = new ArrayList<>(cables.size());
        for (DirectCableGeometry cable : cables) {
            CableKinematics kin = cable.evaluateKinematics(bodies);
            ConstraintRow row = new ConstraintRow(cable.cableId(), 0, mapping.totalDof());
            cable.populateJacobian(mapping, bodies, row);
            entries.add(new UnilateralCableEntry(cable.cableId(), row, kin));
        }

        return solveDirect(mapping, bilateralConstraints, entries, extAccelerations, alpha, beta, initialTautCables);
    }

    /**
     * Solves the combined bilateral and unilateral system directly from prepared cable entries.
     */
    public UnilateralSolveResult solveDirect(
            CoordinateMapping mapping,
            List<ConstraintRow> bilateralConstraints,
            List<UnilateralCableEntry> cableEntries,
            double[] extAccelerations,
            double alpha,
            double beta,
            Set<String> initialTautCables) {

        Objects.requireNonNull(mapping, "mapping is required");
        Objects.requireNonNull(bilateralConstraints, "bilateralConstraints is required");
        Objects.requireNonNull(cableEntries, "cableEntries is required");
        Objects.requireNonNull(extAccelerations, "extAccelerations is required");

        int nDyn = mapping.totalDof();
        if (extAccelerations.length != nDyn) {
            throw new IllegalArgumentException("extAccelerations length (" + extAccelerations.length
                    + ") must match dynamic DOFs (" + nDyn + ")");
        }

        int mCables = cableEntries.size();

        // 1. Establish canonical ordering of cables by cableId
        Integer[] canonicalOrder = new Integer[mCables];
        for (int i = 0; i < mCables; i++) {
            canonicalOrder[i] = i;
        }
        Arrays.sort(canonicalOrder, Comparator.comparing(i -> cableEntries.get(i).cableId()));

        List<UnilateralCableEntry> sortedCables = new ArrayList<>(mCables);
        for (int i = 0; i < mCables; i++) {
            sortedCables.add(cableEntries.get(canonicalOrder[i]));
        }

        // Defensive validation: reject duplicate cable IDs
        for (int i = 1; i < mCables; i++) {
            if (sortedCables.get(i - 1).cableId().equals(sortedCables.get(i).cableId())) {
                throw new IllegalArgumentException("Duplicate cable identity detected: " + sortedCables.get(i).cableId());
            }
        }

        // 2. Initialize active set A^(0) with boundary position and velocity conditions
        BitSet activeBits = new BitSet(mCables);
        for (int c = 0; c < mCables; c++) {
            UnilateralCableEntry entry = sortedCables.get(c);
            boolean requested = initialTautCables != null && initialTautCables.contains(entry.cableId());
            boolean defaultTaut = (initialTautCables == null) && (entry.kinematics().gap() <= 0.0);
            boolean boundaryAllowed = (entry.kinematics().gap() <= epsPos) && (entry.kinematics().gapRate() <= epsVel);

            if ((requested || defaultTaut) && boundaryAllowed) {
                activeBits.set(c);
            }
        }

        // 3. Iteration state and cycle safeguards
        int maxIterations = 2 * (mCables + 1);
        List<BitSet> visitedHistory = new ArrayList<>();
        Set<BitSet> visitedSet = new HashSet<>();

        visitedHistory.add((BitSet) activeBits.clone());
        visitedSet.add((BitSet) activeBits.clone());

        // 4. Active set iteration loop
        for (int iteration = 1; iteration <= maxIterations; iteration++) {

            // 4a. Assemble candidate rows = Bilateral + Active Cables
            int activeCableCount = activeBits.cardinality();
            List<ConstraintRow> candidateRows = new ArrayList<>(bilateralConstraints.size() + activeCableCount);
            candidateRows.addAll(bilateralConstraints);
            for (int c = activeBits.nextSetBit(0); c >= 0; c = activeBits.nextSetBit(c + 1)) {
                candidateRows.add(sortedCables.get(c).row());
            }

            // 4b. Solve equality subsystem via DelassusRrqrSolver
            DelassusRrqrSolver.SolveResult innerResult = innerSolver.solve(
                    mapping, candidateRows, extAccelerations, alpha, beta);

            // Extract bilateral multipliers
            Map<String, List<Double>> bilateralMultList = new LinkedHashMap<>();
            for (int b = 0; b < bilateralConstraints.size(); b++) {
                ConstraintRow bRow = bilateralConstraints.get(b);
                bilateralMultList.computeIfAbsent(bRow.constraintId(), k -> new ArrayList<>())
                        .add(innerResult.multipliers()[b]);
            }
            Map<String, double[]> bilateralMultipliers = new LinkedHashMap<>();
            for (Map.Entry<String, List<Double>> entry : bilateralMultList.entrySet()) {
                double[] arr = new double[entry.getValue().size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = entry.getValue().get(i);
                }
                bilateralMultipliers.put(entry.getKey(), arr);
            }

            // Extract algebraic cable multipliers
            double[] lambdaAlg = new double[mCables];
            int activeRowIdx = bilateralConstraints.size();
            for (int c = activeBits.nextSetBit(0); c >= 0; c = activeBits.nextSetBit(c + 1)) {
                lambdaAlg[c] = innerResult.multipliers()[activeRowIdx++];
            }

            // Compute algebraic coordinate accelerations: a_alg = a_ext + a_constraint_alg
            double[] aConstraintAlg = innerResult.coordinateAccelerations();
            double[] aAlg = new double[nDyn];
            for (int j = 0; j < nDyn; j++) {
                aAlg[j] = extAccelerations[j] + aConstraintAlg[j];
            }

            // 4c. Compute slack accelerations w_j for ALL cables
            double[] w = new double[mCables];
            for (int c = 0; c < mCables; c++) {
                UnilateralCableEntry entry = sortedCables.get(c);
                ConstraintRow row = entry.row();
                double jExt = 0.0;
                double jConstraint = 0.0;
                for (int j = 0; j < nDyn; j++) {
                    double jVal = row.getJacobian(j);
                    if (jVal != 0.0) {
                        jExt += jVal * extAccelerations[j];
                        jConstraint += jVal * aConstraintAlg[j];
                    }
                }
                double bi = -entry.kinematics().convectiveAcceleration()
                        - jExt
                        - 2.0 * alpha * entry.kinematics().gapRate()
                        - (beta * beta) * entry.kinematics().gap();
                w[c] = jConstraint - bi;
            }

            // 4d. Identify releasing active cables and violating inactive cables
            List<Integer> releasing = new ArrayList<>();
            for (int c = activeBits.nextSetBit(0); c >= 0; c = activeBits.nextSetBit(c + 1)) {
                if (lambdaAlg[c] < -epsLambda) {
                    releasing.add(c);
                }
            }

            List<Integer> violating = new ArrayList<>();
            for (int c = 0; c < mCables; c++) {
                if (!activeBits.get(c)) {
                    UnilateralCableEntry entry = sortedCables.get(c);
                    if (entry.kinematics().gap() <= epsPos
                            && entry.kinematics().gapRate() <= epsVel
                            && w[c] < -epsW) {
                        violating.add(c);
                    }
                }
            }

            // 4e. Check active-set complementarity
            if (releasing.isEmpty() && violating.isEmpty()) {
                // Complementarity is satisfied. Now execute rigorous Physical Residual Validation.

                // 1. Form public tensions
                double[] lambdaPublic = new double[mCables];
                double[] deltaLambda = new double[mCables];
                for (int c = 0; c < mCables; c++) {
                    lambdaPublic[c] = Math.max(0.0, lambdaAlg[c]);
                    deltaLambda[c] = lambdaPublic[c] - lambdaAlg[c];
                }

                // 2. Compute generalized acceleration perturbation delta_a = M_dyn^-1 J_A^T delta_lambda
                double[] deltaA = new double[nDyn];
                for (int c = activeBits.nextSetBit(0); c >= 0; c = activeBits.nextSetBit(c + 1)) {
                    double dLam = deltaLambda[c];
                    if (dLam != 0.0) {
                        ConstraintRow row = sortedCables.get(c).row();
                        for (int j = 0; j < nDyn; j++) {
                            double jVal = row.getJacobian(j);
                            if (jVal != 0.0) {
                                deltaA[j] += mapping.invMassAt(j) * jVal * dLam;
                            }
                        }
                    }
                }

                double[] aPublic = new double[nDyn];
                double[] aConstraintPublic = new double[nDyn];
                for (int j = 0; j < nDyn; j++) {
                    aPublic[j] = aAlg[j] + deltaA[j];
                    aConstraintPublic[j] = aConstraintAlg[j] + deltaA[j];
                }

                // 3. Validate dimensionally distinct acceleration perturbations
                for (RigidBody b : mapping.dynamicBodies()) {
                    int startIdx = mapping.startIndex(b.id());
                    double dx = Math.abs(deltaA[startIdx]);
                    double dy = Math.abs(deltaA[startIdx + 1]);
                    if (dx > epsATrans || dy > epsATrans) {
                        throw new ActiveSetConvergenceException(
                                "Translational acceleration perturbation (" + Math.max(dx, dy)
                                        + " m/s^2) exceeded tolerance (" + epsATrans + " m/s^2) on body " + b.id(),
                                iteration, visitedHistory, buildMultMap(sortedCables, lambdaAlg),
                                buildSlackMap(sortedCables, w), List.of(b.id().toString()),
                                "Translational acceleration perturbation tolerance exceeded");
                    }

                    if (mapping.hasRotation(b.id())) {
                        double dTheta = Math.abs(deltaA[startIdx + 2]);
                        if (dTheta > epsARot) {
                            throw new ActiveSetConvergenceException(
                                    "Rotational acceleration perturbation (" + dTheta
                                            + " rad/s^2) exceeded tolerance (" + epsARot + " rad/s^2) on body " + b.id(),
                                    iteration, visitedHistory, buildMultMap(sortedCables, lambdaAlg),
                                    buildSlackMap(sortedCables, w), List.of(b.id().toString()),
                                    "Rotational acceleration perturbation tolerance exceeded");
                        }
                    }
                }

                // 4. Validate bilateral residuals under public constraint acceleration
                for (ConstraintRow bRow : bilateralConstraints) {
                    double jAExt = 0.0;
                    double jAc = 0.0;
                    for (int j = 0; j < nDyn; j++) {
                        double jVal = bRow.getJacobian(j);
                        if (jVal != 0.0) {
                            jAExt += jVal * extAccelerations[j];
                            jAc += jVal * aConstraintPublic[j];
                        }
                    }
                    double bk = -bRow.centripetalAccel() - jAExt - 2.0 * alpha * bRow.velocityError() - (beta * beta) * bRow.positionError();
                    double rk = bk - jAc;
                    if (Math.abs(rk) > epsBilateral) {
                        throw new ActiveSetConvergenceException(
                                "Bilateral constraint residual |r_k| = " + Math.abs(rk)
                                        + " m/s^2 exceeded tolerance " + epsBilateral + " m/s^2 on "
                                        + bRow.constraintId() + "[" + bRow.rowIndex() + "]",
                                iteration, visitedHistory, buildMultMap(sortedCables, lambdaAlg),
                                buildSlackMap(sortedCables, w), List.of(bRow.constraintId()),
                                "Bilateral residual tolerance exceeded under public acceleration");
                    }
                }

                // 5. Validate active cable residuals under public acceleration
                double[] wPublic = new double[mCables];
                for (int c = 0; c < mCables; c++) {
                    UnilateralCableEntry entry = sortedCables.get(c);
                    ConstraintRow row = entry.row();
                    double jPublic = 0.0;
                    for (int j = 0; j < nDyn; j++) {
                        double jVal = row.getJacobian(j);
                        if (jVal != 0.0) {
                            jPublic += jVal * aPublic[j];
                        }
                    }
                    double gTarget = -2.0 * alpha * entry.kinematics().gapRate() - (beta * beta) * entry.kinematics().gap();
                    wPublic[c] = jPublic + entry.kinematics().convectiveAcceleration() - gTarget;

                    if (activeBits.get(c) && Math.abs(wPublic[c]) > epsW) {
                        throw new ActiveSetConvergenceException(
                                "Active cable residual |w_public| = " + Math.abs(wPublic[c])
                                        + " m/s^2 exceeded tolerance " + epsW + " m/s^2 on " + entry.cableId(),
                                iteration, visitedHistory, buildMultMap(sortedCables, lambdaAlg),
                                buildSlackMap(sortedCables, wPublic), List.of(entry.cableId()),
                                "Active cable residual tolerance exceeded under public acceleration");
                    }
                }

                // 6. Build final CableState classifications
                Map<String, CableState> cableStates = new LinkedHashMap<>();
                for (int c = 0; c < mCables; c++) {
                    UnilateralCableEntry entry = sortedCables.get(c);
                    if (entry.kinematics().gap() > epsPos) {
                        cableStates.put(entry.cableId(), CableState.SLACK_INTERIOR);
                    } else if (entry.kinematics().gapRate() > epsVel) {
                        cableStates.put(entry.cableId(), CableState.SLACK_SEPARATING);
                    } else if (!activeBits.get(c)) {
                        cableStates.put(entry.cableId(), CableState.SLACK_FREE);
                    } else {
                        cableStates.put(entry.cableId(), CableState.TAUT);
                    }
                }

                // 7. Calculate complementarity slack
                double maxCompSlack = 0.0;
                for (int c = 0; c < mCables; c++) {
                    double comp = lambdaPublic[c] * Math.abs(wPublic[c]);
                    if (comp > maxCompSlack) {
                        maxCompSlack = comp;
                    }
                }

                return new UnilateralSolveResult(
                        true,
                        iteration,
                        aPublic,
                        aAlg,
                        cableStates,
                        buildMultMap(sortedCables, lambdaPublic),
                        buildMultMap(sortedCables, lambdaAlg),
                        buildSlackMap(sortedCables, wPublic),
                        bilateralMultipliers,
                        activeBits,
                        maxCompSlack);
            }

            // 4f. Active-set pivot required (driven STRICTLY by unilateral multiplier/slack conditions)
            BitSet candidate = (BitSet) activeBits.clone();
            for (int r : releasing) {
                candidate.clear(r);
            }
            for (int v : violating) {
                candidate.set(v);
            }

            if (!visitedSet.contains(candidate)) {
                activeBits = candidate;
            } else {
                // CYCLE DETECTED! Fall back to deterministic single-pivot
                BitSet singlePivot = (BitSet) activeBits.clone();
                if (!releasing.isEmpty()) {
                    // Priority 1: release constraint with most negative multiplier
                    int bestR = releasing.get(0);
                    double minLam = lambdaAlg[bestR];
                    for (int r : releasing) {
                        if (lambdaAlg[r] < minLam) {
                            minLam = lambdaAlg[r];
                            bestR = r;
                        }
                    }
                    singlePivot.clear(bestR);
                } else if (!violating.isEmpty()) {
                    // Priority 2: activate constraint with most negative slack acceleration
                    int bestV = violating.get(0);
                    double minW = w[bestV];
                    for (int v : violating) {
                        if (w[v] < minW) {
                            minW = w[v];
                            bestV = v;
                        }
                    }
                    singlePivot.set(bestV);
                }

                if (visitedSet.contains(singlePivot)) {
                    List<String> offending = new ArrayList<>();
                    for (int r : releasing) offending.add(sortedCables.get(r).cableId());
                    for (int v : violating) offending.add(sortedCables.get(v).cableId());

                    throw new ActiveSetConvergenceException(
                            "Cycle trap detected: single-pivot fallback set already visited at iteration " + iteration,
                            iteration, visitedHistory, buildMultMap(sortedCables, lambdaAlg),
                            buildSlackMap(sortedCables, w), offending,
                            "Cycle trap detected in active set");
                }
                activeBits = singlePivot;
            }

            visitedHistory.add((BitSet) activeBits.clone());
            visitedSet.add((BitSet) activeBits.clone());
        }

        // Iteration cap exceeded
        List<String> offending = new ArrayList<>();
        for (UnilateralCableEntry entry : sortedCables) {
            offending.add(entry.cableId());
        }
        throw new ActiveSetConvergenceException(
                "Maximum active-set iterations (" + maxIterations + ") exceeded",
                maxIterations, visitedHistory, Map.of(), Map.of(), offending,
                "Iteration cap exceeded");
    }

    private static Map<String, Double> buildMultMap(List<UnilateralCableEntry> cables, double[] values) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < cables.size(); i++) {
            map.put(cables.get(i).cableId(), values[i]);
        }
        return map;
    }

    private static Map<String, Double> buildSlackMap(List<UnilateralCableEntry> cables, double[] values) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < cables.size(); i++) {
            map.put(cables.get(i).cableId(), values[i]);
        }
        return map;
    }

    public double epsLambda() {
        return epsLambda;
    }

    public double epsW() {
        return epsW;
    }

    public double epsPos() {
        return epsPos;
    }

    public double epsVel() {
        return epsVel;
    }

    public double epsATrans() {
        return epsATrans;
    }

    public double epsARot() {
        return epsARot;
    }

    public double epsBilateral() {
        return epsBilateral;
    }
}

