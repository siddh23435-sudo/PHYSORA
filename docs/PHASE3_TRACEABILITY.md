# Phase 3 Traceability — Multi-Body Constraint Engine

This document traces the completed stages of Phase 3: the headless multi-body dynamics and constraint engine (`edu.physics.simulation`). Phase 3 implements foundational physics infrastructure for constrained rigid-body systems. It is **not yet connected to the student-facing JavaFX application**; the UI continues to use the Phase 1 analytical projectile model.

Verification baseline: **367 tests**, `.\mvnw.cmd test`, all passing. Recorded on `feature/physora-48hr-demo` at commit `f112909` (2026-11-15).

---

## Stage 3C-2A — Unilateral Inextensible Cable System

Stage 3C-2A builds a direct constraint solver for systems of rigid bodies connected by inextensible cables (unilateral, tension-only constraints) coupled with bilateral equality constraints (distance links, revolute joints). The solver operates on acceleration-level kinematics using mass-whitened Delassus formulations and rank-revealing QR decomposition.

### Stage 3C-2A.1 — Cable Geometry and Kinematics

**Objective:** Establish the geometric and kinematic foundation for direct point-to-point inextensible cables attached between rigid bodies and/or world anchors.

**Production classes:**

| Class | Package | Responsibility |
|---|---|---|
| `CableKinematics` | `constraint` | Immutable record capturing a cable's kinematic snapshot: world attachment coordinates, velocities, accelerations, relative displacement, current length, unit direction, rest length, gap $g = L_0 - L$, gap rate $\dot{g} = -\mathbf{u} \cdot \mathbf{v}_{\text{rel}}$, and convective acceleration $\gamma_{\text{conv}} = \dot{J} \cdot v$ |
| `DirectCableGeometry` | `constraint` | Geometric and kinematic evaluator for direct cables. Resolves `CableTopology` from `LaboratoryScene`, normalizes endpoints (dynamic body to Slot A, world anchor to Slot B), validates minimum separation, computes `CableKinematics`, and assembles `ConstraintRow` Jacobians. Enforces the pulling tension invariant: $f_A = -\mathbf{u}\lambda$ (pulls A toward B) |

**Endpoint normalization policy:**
- Dynamic ↔ Dynamic: preserved in canonical topology order
- Dynamic → World Anchor: assigned to Slot A (dynamic) and Slot B (anchor)
- World Anchor → Dynamic: automatically swapped
- Dynamic ↔ Static Body: static attachment resolved to world coordinates, treated as fixed anchor
- Anchor ↔ Anchor or Static ↔ Static: rejected (zero dynamic DOF)

**Test class:** `DirectCableGeometryTest` — **17 tests** organized in three nested levels:

| Level | Tests | Coverage |
|---|---|---|
| Level 1: Unit Tests — Kinematics, Jacobians, and Derivatives | 8 | Gap function, gap rate, convective acceleration, Jacobian rows for body–anchor and body–body configurations, analytical derivative verification |
| Level 2: Integration Tests — Scene Topology Adapter and Coordinate Mapping | 4 | `LaboratoryScene` round-trip (topology → geometry → kinematics → constraint row), coordinate mapping integration, static body attachment resolution |
| Level 3: Acceptance Tests — Normalization, Invariance, and Workflows | 5 | Endpoint swap invariance, rest-length boundary behavior, minimum separation rejection, degenerate and multi-cable scene workflows |

**Known limitations:**
- Supports only direct point-to-point cables (no pulleys, no routed cables)
- Rotational attachment offsets are supported but not yet exercised in an interactive simulation loop

---

### Stage 3C-2A.2 — Mass-Whitened RRQR Delassus Solver

**Objective:** Solve the acceleration-level equality constraint subsystem $W\lambda = b$ using mass-whitened Delassus factorization with rank-revealing QR decomposition, supporting redundant and near-dependent constraint configurations.

**Production classes:**

| Class | Package | Responsibility |
|---|---|---|
| `MassWhitenedDelassus` | `constraint` | Constructs the mass-whitened, row-normalized Jacobian $B = S J_{\text{dyn}} C$ where $C = M_{\text{dyn}}^{-1/2}$ and $S_{ii} = 1/\sqrt{W_{ii}}$, yielding unit-diagonal normalized Delassus $\bar{W} = BB^T$. Validates minimum Delassus mobility ($W_{ii} \geq 10^{-14}$ kg⁻¹) and rejects zero-mobility rows |
| `ColumnPivotedRRQR` | `constraint` | Column-pivoted Householder RRQR factorization of $B^T P = QR$ with deterministic tie-breaking. Classifies constraint rows into tiers by diagonal magnitude. Provides the Cholesky identity $P^T \bar{W} P = R^T R$ |
| `ConstraintTier` | `constraint` | Four-tier classification enum: `TIER_1A_INDEPENDENT` ($r_{\text{diag}} \geq \tau_{\text{indep}}$), `TIER_1B_REGULARIZED` ($\tau_{\text{redun}} \leq r_{\text{diag}} < \tau_{\text{indep}}$), `TIER_2_REDUNDANT` ($r_{\text{diag}} < \tau_{\text{redun}}$), `TIER_4_INCONSISTENT` |
| `DelassusRrqrSolver` | `constraint` | Direct symmetric solver: canonical pre-factorization ordering by `(constraintId, rowIndex)`, Tier 1B augmented Householder QR regularization ($\mu = 10^{-4}$), Option A redundant-row compatibility verification (tolerance $2 \times 10^{-4}$ m/s²), and caller-order provisional multiplier mapping. Rejects duplicate constraint identities |

**Mathematical foundation:**
- Threshold: $\tau_{\text{indep}} = \sqrt{10^{-5}} \approx 3.162 \times 10^{-3}$, $\tau_{\text{redun}} = 10^{-4}$
- Regularization: augmented system $[\,R_{11};\; D_\mu\,]$ factored to obtain $\tilde{R}^T \tilde{R} = R_{11}^T R_{11} + D_\mu^2$
- Redundant-row compatibility: forward substitution $R_{11}^T y = \bar{b}_{\text{indep}}$, then verify $\|R_{12}^T y - \bar{b}_{\text{dep}}\| < \epsilon_a$

**Test class:** `DelassusRrqrSolverTest` — **22 tests** organized in three nested levels:

| Level | Tests | Coverage |
|---|---|---|
| Level 1: Unit Tests — Matrix Decompositions and Linear Algebra | 9 | Mass whitening, row normalization, Householder reflections, column pivoting, deterministic tie-breaking, Cholesky identity verification, tier classification |
| Level 2: Integration Tests — Multi-Body Physics and Constraint Geometry | 9 | Two-body distance links, three-body chains, mixed bilateral/cable configurations, redundant constraint detection, near-dependent regularization, solver–geometry round-trip |
| Level 3: Acceptance Tests — Physical Invariants and Solver Contracts | 4 | Canonical ordering determinism, caller-index multiplier mapping, duplicate identity rejection, acceleration consistency across redundant configurations |

**Known limitations:**
- Outputs provisional algebraic multipliers without unilateral clamping (equality solver only)
- Does not perform active-set iteration; that responsibility belongs to Stage 3C-2A.3

---

### Stage 3C-2A.3 — Unilateral Active-Set Solver

**Objective:** Solve coupled bilateral + unilateral (cable) constraint systems using an active-set method that enforces complementarity: taut cables carry non-negative tension; slack cables exert zero force.

**Production classes:**

| Class | Package | Responsibility |
|---|---|---|
| `CableState` | `constraint` | Physical regime enum: `SLACK_INTERIOR` (geometric slack), `SLACK_SEPARATING` (at boundary but separating), `SLACK_FREE` (at boundary, acceleration maintains gap), `TAUT` (active, enforcing inextensibility) |
| `UnilateralSolveResult` | `constraint` | Deeply immutable result record: public accelerations ($a_{\text{public}} = a_{\text{alg}} + \delta a$), algebraic accelerations, cable states, public tensions (clamped $\geq 0$), algebraic multipliers, slack accelerations, bilateral multipliers, active-set bitset, and complementarity slack |
| `ActiveSetConvergenceException` | `constraint` | Diagnostic exception preserving iteration count, visited active-set history (`BitSet` list), algebraic multipliers, slack accelerations, offending constraint IDs, and structured failure reason |
| `UnilateralActiveSetSolver` | `constraint` | Active-set solver wrapping `DelassusRrqrSolver`. Features: canonical cable ordering, velocity-gated initialization ($g \leq \epsilon_{\text{pos}}$, $\dot{g} \leq \epsilon_{\text{vel}}$), three-condition activation ($g \leq \epsilon_{\text{pos}}$, $\dot{g} \leq \epsilon_{\text{vel}}$, $w < -\epsilon_w$), near-zero multiplier policy (retain $-\epsilon_\lambda \leq \lambda_{\text{alg}} < 0$, clamp public tension to 0), public acceleration validation with dimensionally separated tolerances (translational $\epsilon_{a,\text{trans}}$, rotational $\epsilon_{a,\text{rot}}$), `BitSet`-based cycle detection with single-pivot fallback, hard iteration cap $K_{\max} = 2(m_{\text{cables}} + 1)$ |

**Complementarity conditions:**
- Deactivation: $\lambda_{\text{alg}} < -\epsilon_\lambda$ (cable pulls rather than pushes)
- Activation: $g_j \leq \epsilon_{\text{pos}}$ AND $\dot{g}_j \leq \epsilon_{\text{vel}}$ AND $w_j < -\epsilon_w$
- Near-zero policy: $-\epsilon_\lambda \leq \lambda_{\text{alg}} < 0$ retained as active, public tension clamped to 0

**Residual validation guardrail:** Residual validation failures (bilateral constraint residual exceeds $\epsilon_{\text{bilateral}}$, or active cable constraint acceleration exceeds tolerances) immediately abort with `ActiveSetConvergenceException`. They **never** independently trigger active-set pivots.

**Default tolerances:**

| Parameter | Default | Unit |
|---|---|---|
| `epsLambda` | $10^{-7}$ | N |
| `epsW` | $10^{-7}$ | m/s² |
| `epsPos` | $10^{-5}$ | m |
| `epsVel` | $10^{-5}$ | m/s |
| `epsATrans` | $10^{-6}$ | m/s² |
| `epsARot` | $10^{-5}$ | rad/s² |
| `epsBilateral` | $2 \times 10^{-4}$ | m/s² |

**Test class:** `UnilateralActiveSetSolverTest` — **17 tests** organized in three nested levels:

| Level | Tests | Coverage |
|---|---|---|
| Level 1: Unit Tests — State Transitions, Safeguards, and Immutability | 5 | `CableState` enum completeness, result immutability (defensive copies), duplicate cable ID rejection, exception diagnostic preservation, empty cable list passthrough |
| Level 2: Integration Tests — Bilateral Constraints, Pivoting, and Determinism | 7 | Bilateral-only solve, single cable activation/deactivation, multi-cable pivoting, velocity-gated deactivation, near-zero multiplier clamping, residual failure guardrail (abort, not pivot), cycle detection |
| Level 3: Acceptance Tests — Physical Benchmarks and Complementarity | 5 | Two-mass Atwood machine equilibrium, three-cable selective activation, mixed bilateral + cable coupling, slack acceleration sign verification, deterministic ordering across permuted inputs |

**Known limitations:**
- Solver operates on a single acceleration-level snapshot; it does not advance simulation time
- Impact/snap impulses and continuous-time event detection (root-finding) are deferred to Stage 3C-2A.5/6
- Pulley kinematics are deferred to Stage 3C-2B

---

## Stage 3C-2A Summary

| Stage | Classes | Tests | Status |
|---|---|---|---|
| 3C-2A.1 — Cable Geometry | `CableKinematics`, `DirectCableGeometry` | 17 | Implemented; verified |
| 3C-2A.2 — Delassus RRQR Solver | `MassWhitenedDelassus`, `ColumnPivotedRRQR`, `ConstraintTier`, `DelassusRrqrSolver` | 22 | Implemented; verified |
| 3C-2A.3 — Unilateral Active-Set | `CableState`, `UnilateralSolveResult`, `ActiveSetConvergenceException`, `UnilateralActiveSetSolver` | 17 | Implemented; verified |
| **Total Stage 3C-2A** | **10 classes** | **56 tests** | **All passing** |

## Deferred Stages

| Stage | Scope | Status |
|---|---|---|
| 3C-2A.4 | Dynamic simulation integration and state evolution (time-stepping with unilateral constraints) | Not started |
| 3C-2A.5 | Impact snap impulses (velocity-level discontinuities at cable taut events) | Not started |
| 3C-2A.6 | Continuous-time root-finding for cable state transitions | Not started |
| 3C-2B | Pulley kinematics and routing | Not started |

## Pre-existing Phase 3 Infrastructure

The following classes were implemented before Stage 3C-2A and provide the underlying rigid-body and scene infrastructure:

| Package | Classes | Tests |
|---|---|---|
| `edu.physics.simulation` | `RigidBody`, `RigidBodyState`, `PhysicalBodySpec`, `BodyType`, `SimulationWorld`, `SymplecticIntegrator`, `ForceGenerator`, `UniformGravity`, `Vector2D` | `SimulationWorldTest` (12), `Vector2DTest` (15) |
| `edu.physics.simulation.constraint` | `ConstraintRow`, `CoordinateMapping`, `MechanicalConstraint`, `DistanceLinkConstraint`, `RevoluteJointConstraint`, `DirectLinearConstraintSolver` | `BilateralConstraintsTest` (16) |
| `edu.physics.simulation.geometry` | `GeometricShape`, `CircleShape`, `RectangleShape`, `PolygonShape`, `PointMassShape`, `BoundingBox`, `ShapeType`, `Transform2D` | `GeometricShapesTest` (8), `Transform2DTest` (5) |
| `edu.physics.simulation.scene` | `LaboratoryScene`, `SceneBody`, `SceneAnchor`, `CableTopology`, `DistanceLinkTopology`, `RevoluteJointTopology`, `LinearSpringTopology`, `SurfaceContactTopology`, `StaticSurface`, `AttachmentPoint`, `AnchorRef`, `InitialPose`, `RelationshipType`, `StructuralRelationship`, `SceneValidationResult`, `SceneDiagnostic` | `LaboratorySceneTest` (15) |

**Total pre-existing Phase 3 tests:** 71

**Total Phase 3 tests (pre-existing + Stage 3C-2A):** 127

