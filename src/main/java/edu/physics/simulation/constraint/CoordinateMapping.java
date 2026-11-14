package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps dynamic rigid bodies and point masses to a reduced generalized coordinate space.
 *
 * <p>Coordinate Reduction Rules:
 * <ul>
 *   <li>Dynamic Rigid Body (I > 0): 3 DOFs (x, y, θ).</li>
 *   <li>Dynamic Point Mass (I = 0): 2 DOFs (x, y). Rotational coordinate is completely excluded.</li>
 *   <li>Static Body / Fixed Anchor: 0 DOFs (kinematic boundary condition, not a dynamic coordinate).</li>
 * </ul>
 *
 * <p>The resulting generalized mass matrix M_dyn and inverse mass matrix M_dyn⁻¹ are strictly positive-definite.
 */
public final class CoordinateMapping {

    public static final int PLANAR_RIGID_BODY_DOFS = 3;
    public static final int PLANAR_POINT_MASS_DOFS = 2;

    private final List<RigidBody> dynamicBodies;
    private final Map<UUID, Integer> startIndices;
    private final Map<UUID, Integer> dofMap;
    private final int totalDof;
    private final double[] invMass;
    private final double[] mass;

    private CoordinateMapping(
            List<RigidBody> dynamicBodies,
            Map<UUID, Integer> startIndices,
            Map<UUID, Integer> dofMap,
            int totalDof,
            double[] invMass,
            double[] mass) {
        this.dynamicBodies = dynamicBodies;
        this.startIndices = startIndices;
        this.dofMap = dofMap;
        this.totalDof = totalDof;
        this.invMass = invMass;
        this.mass = mass;
    }

    public static CoordinateMapping of(Collection<RigidBody> bodies) {
        Objects.requireNonNull(bodies, "bodies collection is required");
        List<RigidBody> dynList = new ArrayList<>();
        Map<UUID, Integer> startIdx = new LinkedHashMap<>();
        Map<UUID, Integer> dofs = new LinkedHashMap<>();

        int currentDof = 0;
        for (RigidBody b : bodies) {
            if (b.isStatic()) {
                continue;
            }
            dynList.add(b);
            int d = b.inverseInertia() > 0.0 ? PLANAR_RIGID_BODY_DOFS : PLANAR_POINT_MASS_DOFS;
            startIdx.put(b.id(), currentDof);
            dofs.put(b.id(), d);
            currentDof += d;
        }

        double[] invM = new double[currentDof];
        double[] m = new double[currentDof];

        for (RigidBody b : dynList) {
            int idx = startIdx.get(b.id());
            invM[idx] = b.inverseMass();
            invM[idx + 1] = b.inverseMass();
            m[idx] = b.mass();
            m[idx + 1] = b.mass();

            if (dofs.get(b.id()) == PLANAR_RIGID_BODY_DOFS) {
                invM[idx + 2] = b.inverseInertia();
                m[idx + 2] = b.momentOfInertia();
            }
        }

        return new CoordinateMapping(
                Collections.unmodifiableList(dynList),
                Collections.unmodifiableMap(startIdx),
                Collections.unmodifiableMap(dofs),
                currentDof,
                invM,
                m);
    }

    public int totalDof() {
        return totalDof;
    }

    public List<RigidBody> dynamicBodies() {
        return dynamicBodies;
    }

    public boolean isDynamic(UUID id) {
        return startIndices.containsKey(id);
    }

    public int startIndex(UUID id) {
        Integer idx = startIndices.get(id);
        if (idx == null) {
            throw new IllegalArgumentException("Body is not a dynamic coordinate: " + id);
        }
        return idx;
    }

    public int dofOf(UUID id) {
        Integer d = dofMap.get(id);
        if (d == null) {
            return 0;
        }
        return d;
    }

    public boolean hasRotation(UUID id) {
        return dofOf(id) == PLANAR_RIGID_BODY_DOFS;
    }

    public double[] invMass() {
        return invMass.clone();
    }

    public double invMassAt(int index) {
        return invMass[index];
    }

    public double massAt(int index) {
        return mass[index];
    }
}

