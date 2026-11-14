package edu.physics.simulation.constraint;

import java.util.Objects;

/**
 * Encapsulates a single scalar row of a linearized kinematic constraint equation.
 *
 * <p>Holds the Jacobian entries J_k ∈ ℝ^D mapped to the reduced generalized coordinate space,
 * the position error Φ_k, the centripetal acceleration (J̇ v)_k, the velocity constraint error (J v)_k,
 * and the diagonal Delassus mobility w_kk = J_k M_dyn⁻¹ J_k^T in units of kg⁻¹ (where effective mass is m_eff = 1 / w_kk).
 */
public final class ConstraintRow {

    private final String constraintId;
    private final int rowIndex;
    private final double[] jacobian;
    private double positionError;
    private double centripetalAccel;
    private double velocityError;
    private double effectiveMass;

    public ConstraintRow(String constraintId, int rowIndex, int totalDof) {
        this.constraintId = Objects.requireNonNull(constraintId, "constraintId is required");
        this.rowIndex = rowIndex;
        this.jacobian = new double[totalDof];
    }

    public String constraintId() {
        return constraintId;
    }

    public int rowIndex() {
        return rowIndex;
    }

    public double[] jacobian() {
        return jacobian;
    }

    public double getJacobian(int index) {
        return jacobian[index];
    }

    public void setJacobian(int index, double value) {
        jacobian[index] = value;
    }

    public void addJacobian(int index, double value) {
        jacobian[index] += value;
    }

    public double positionError() {
        return positionError;
    }

    public void setPositionError(double positionError) {
        this.positionError = positionError;
    }

    public double centripetalAccel() {
        return centripetalAccel;
    }

    public void setCentripetalAccel(double centripetalAccel) {
        this.centripetalAccel = centripetalAccel;
    }

    public double velocityError() {
        return velocityError;
    }

    public void setVelocityError(double velocityError) {
        this.velocityError = velocityError;
    }

    public double effectiveMass() {
        return effectiveMass;
    }

    public void setEffectiveMass(double effectiveMass) {
        this.effectiveMass = effectiveMass;
    }

    /**
     * Evaluates the diagonal Delassus mobility w_kk = J_k M_dyn⁻¹ J_k^T in units of kg⁻¹.
     *
     * <p>Note: Physical effective mass is m_eff = 1 / w_kk. The getter/setter retain effectiveMass
     * for API compatibility while representing Delassus diagonal compliance w_kk.
     */
    public void computeEffectiveMass(CoordinateMapping mapping) {
        double sum = 0.0;
        int d = mapping.totalDof();
        for (int j = 0; j < d; j++) {
            double jVal = jacobian[j];
            if (jVal != 0.0) {
                sum += jVal * mapping.invMassAt(j) * jVal;
            }
        }
        this.effectiveMass = sum;
    }
}

