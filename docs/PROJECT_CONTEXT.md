# PROJECT CONTEXT

## PHYSORA — Interactive Physics Learning, Modeling, and Simulation Platform

## 1. Overview

PHYSORA is an interactive physics learning, modeling, and simulation platform for Class 11–12 students, particularly those preparing for JEE Main and JEE Advanced.

The platform should help students:

- Construct physical systems.
- Understand relationships between objects.
- Define initial conditions.
- Identify forces, constraints, and governing principles.
- Solve physics problems.
- Observe physical systems evolve.
- Modify parameters and investigate outcomes.
- Follow mathematical reasoning.
- Inspect equations and calculated results.
- Ask questions about the physical system.
- Receive contextual guidance from AI.

This is not simply a collection of physics animations.

It is not merely an AI chatbot that solves physics questions.

The central idea is:

A student should be able to construct, understand, solve, execute, inspect, and modify a physical model inside a unified learning environment.

The same underlying physical model should eventually support mathematical reasoning, computation, visualization, simulation, explanations, AI assistance, and experimentation.

## 2. Mandatory Technology Requirement

Java 17 is mandatory for this project. JavaFX is the approved desktop UI framework. Maven is the approved build system and the Maven Wrapper is checked in for portable builds. JUnit is used for automated tests.

Do not replace the primary implementation with TypeScript, JavaScript, Python, or another language.

The projectile calculations remain independent of JavaFX.

The first milestone should use the minimum practical Java architecture.

## 3. Product Environments

The long-term product has two primary environments.

### Normal Bench

The Normal Bench is the student's direct physics workspace.

It should eventually allow students to:

- Open a prebuilt physical scenario.
- Modify parameters.
- Construct a physical system.
- Connect objects and define relationships.
- Set initial conditions.
- Inspect forces and equations.
- Run a simulation.
- Follow a step-by-step solution.
- Save and reuse experiments.
- Annotate their work.
- Ask questions about the current model.

Prebuilt scenarios should be editable.

A student should eventually be able to start from a blank workspace.

### AI Bench

The AI Bench will eventually allow students to provide physics questions using text, photographs, screenshots, printed questions, handwritten questions, diagrams, or combinations of these.

AI should interpret the problem and identify:

- Physical objects
- Known quantities
- Unknown quantities
- Units
- Initial conditions
- Geometry
- Directions
- Forces
- Connections
- Constraints
- Governing concepts
- Assumptions
- Possible solution approaches

AI should propose a structured physical model.

The student must be able to inspect and correct the interpretation before accepting it.

AI must not silently decide that its interpretation is correct.

The resulting model should be editable in the Normal Bench.

AI integration was deferred from Phase 1. Phase 2 adds a bounded text-only projectile-question workflow using Google Gemini for interpretation and explanatory prose, with all accepted numerical answers produced by the deterministic solver. This does not authorize AI model editing, simulation control, image interpretation, persistence, or other chapters.

## 4. AI Copilot

The future AI Copilot should support:

1. Judge My Work
2. Guide Me
3. Build My Model
4. Modify My Model

AI is a collaborator, not an unquestionable authority.

Its responses should be grounded in the actual physical model, parameters, equations, execution state, and student work.

AI should not run every simulation frame or replace deterministic physics calculations.

## 5. Learning Environment Views

The long-term learning environment has three major views.

### Logic View

Presents reasoning and mathematical processes, including:

- Identification of the physical system
- Reference frame
- Coordinate axes
- Forces
- Free-body diagrams
- Vector resolution
- Governing principles
- Equation construction
- Algebraic transformations
- Substitution
- Numerical results
- Verification
- Alternative solution branches

### Simulation View

Presents the physical system visually, including:

- Physical objects
- Motion
- Forces
- Velocity vectors
- Acceleration vectors
- Trajectories
- Constraints
- Collisions
- Graphs
- Measurements
- Playback controls

The visualization must reflect the actual physical model.

It must not merely play a prerecorded animation unrelated to the equations.

### Doubts and FAQs View

Provides contextual explanations.

Examples:

- Why is acceleration constant here?
- Why is horizontal velocity unchanged?
- Why did we choose this coordinate system?
- What happens if gravity is doubled?
- Why is this force acting in this direction?
- Could we solve the same problem using energy?

These views are long-term product direction, not requirements for the first milestone.

## 6. Two Distinct Execution Modes

### Physical Simulation Execution

Executes the physical model.

Examples:

- A projectile moves.
- A pendulum oscillates.
- A body slides down an inclined plane.
- A spring compresses.
- A collision occurs.

Physical simulation has its own time variable.

It may eventually support play, pause, reset, scrubbing, checkpoints, playback speed, state inspection, and graphing.

Playback speed changes how quickly the simulation is displayed. It must not change the underlying physical equations or numerical results.

### Learning Execution

Executes the student's mathematical reasoning.

For example:

1. Identify the system.
2. Select the reference frame.
3. Choose coordinate axes.
4. Identify forces.
5. Resolve vectors.
6. Apply Newton's laws.
7. Construct equations.
8. Solve the equations.
9. Substitute numerical values.
10. Verify the result.

Learning Execution is not physical simulation time.

Selecting an axis or resolving a force should not cause an object to move.

A student may explore different solution branches, such as Newton's laws, work-energy theorem, momentum conservation, and kinematic equations.

The Learning Execution Engine must eventually be distinct from the Physics Simulation Engine.

It is not required for the first milestone.

## 7. Long-Term Physics Scope

The initial planned chapter scope includes 19 chapters.

This is eventual educational coverage, not the first development milestone.

| No. | Chapter                                                |
| --- | ------------------------------------------------------ |
| 1   | Kinematics: One-Dimensional and Two-Dimensional Motion |
| 2   | Relative Motion                                        |
| 3   | Laws of Motion and Friction                            |
| 4   | Work, Energy, and Power                                |
| 5   | Centre of Mass, Momentum, Impulse, and Collisions      |
| 6   | Circular Motion                                        |
| 7   | Rotational Motion and Rigid Body Dynamics              |
| 8   | Mechanical Properties of Solids                        |
| 9   | Mechanical Properties of Fluids                        |
| 10  | Simple Harmonic Motion and Oscillations                |
| 11  | Waves and Sound                                        |
| 12  | Electrostatics                                         |
| 13  | Capacitance                                            |
| 14  | Current Electricity                                    |
| 15  | Magnetic Effects of Current and Magnetism              |
| 16  | Electromagnetic Induction                              |
| 17  | Alternating Current                                    |
| 18  | Ray Optics and Optical Instruments                     |
| 19  | Wave Optics                                            |

Gravitation was deferred from the initial scope.

Do not implement all chapters now.

## 8. Long-Term Architecture Direction

The project is intended to use a hybrid model architecture combining:

- Reusable physical components
- Validated physical templates
- A deterministic model composer
- Domain-specific physics solvers
- Shared mathematical infrastructure
- A learning execution system
- A visualization system
- AI-assisted interpretation and editing

The LLM should propose models and structured changes.

Deterministic software should validate and execute those models.

AI must not generate arbitrary executable equations and run them directly.

### Conceptual Model Representations

The following representations have been discussed conceptually. Their exact class names and implementations are not finalized.

#### ModelDefinition

A reusable model definition may contain component types, parameters, equations, constraints, assumptions, behaviors, inputs, and outputs.

#### ModelInstance or ScenarioInstance

A configured instance of a model.

Example:

- Initial speed = 20 m/s
- Launch angle = 45°
- Gravity = 9.81 m/s²
- Initial position = (0, 0)

The final name is not settled.

#### ProblemGraph

Represents the physical structure of a problem, including physical objects, interactions, connections, constraints, shared variables, coupling relationships, state transfers, and execution dependencies.

#### LearningExecutionGraph

Represents the educational and mathematical reasoning process, including solution steps, dependencies, branches, equations, hints, alternative approaches, and verification steps.

#### WorkspaceDocument

Represents the student's workspace and editable document, including visual layout, annotations, student edits, revision history, model references, current context, and presentation state.

Keep these concerns conceptually separate.

Do not build all these abstractions before the first simulator works.

### Physical Components

Examples discussed:

- RigidBody
- Force
- Pulley
- String
- Spring
- Plane
- Constraint
- Resistor
- Capacitor
- Lens
- Mirror

These are examples, not a finalized component catalog.

A component, interaction, connection, and constraint are not necessarily the same thing.

Their definitions should emerge from real use cases.

### Model Composer

The long-term Model Composer may:

1. Load a validated template or construct a model from components.
2. Configure parameters.
3. Connect components through typed ports.
4. Validate dimensional consistency.
5. Validate physical compatibility.
6. Build a graph representation.
7. Select an appropriate solver.
8. Execute calculations.
9. Produce numerical results and traces.
10. Update the visualization.

Do not implement a universal composer prematurely.

### Solver Strategy

There should not be one universal physics solver.

Different physics domains have different mathematical requirements.

Potential domains:

- Classical Mechanics
- Materials and Fluid Mechanics
- Oscillations and Waves
- Electric and Magnetic Field Physics
- Circuit Physics
- Optics

Potential computation categories:

- Equation evaluation
- Algebraic equation solving
- Simultaneous equation solving
- Ordinary differential equation integration
- Geometric calculations
- Field computations
- Wave calculations

Some physical problems do not need time-based simulation.

Do not force every chapter into an ODE simulator.

### Shared Mathematical Infrastructure

The long-term shared infrastructure may include:

- Units and dimensional analysis
- Vectors and matrices
- Coordinate frames
- Equations and constraints
- State representation
- Numerical integration
- Events and transitions
- Checkpoints and replay
- Dependency tracking
- Graphing
- Validation
- Communication with the renderer

These are potential reusable capabilities, not instructions to implement everything immediately.

## 9. Physics Engineering Principles

The platform must be physically correct.

Always distinguish:

### Physics Model

The assumptions and equations describing the physical system.

### Numerical Solver

The algorithm used to calculate a solution.

### Visualization

The graphical representation of calculated results.

### Learning Explanation

The educational explanation of the model and its solution.

When investigating an incorrect trajectory, inspect:

1. Physical equations
2. Initial conditions
3. Units
4. Solver calculations
5. Rendering coordinate mapping
6. Camera and visual presentation

Check units, sign conventions, coordinate systems, initial conditions, boundary conditions, assumptions, physical constraints, dimensional consistency, limiting cases, conservation laws where applicable, analytical solutions, and numerical stability where applicable.

## 10. AI Architecture Principles

AI may eventually support:

- Question interpretation
- Diagram interpretation
- Extraction of physical quantities
- Model selection
- Model composition proposals
- Explanation generation
- Hints
- Student-work evaluation
- Natural-language model modification

The LLM should not run every simulation frame.

It should not replace deterministic physics calculations.

A sensible long-term workflow:

1. Student provides a question.
2. AI interprets the question.
3. AI proposes a structured model.
4. The application validates the proposed model.
5. The student reviews the interpretation.
6. The student accepts or modifies it.
7. The deterministic physics engine executes it.
8. AI explains the result when requested.

AI should eventually expose uncertainty and ambiguity.

If a diagram is unclear or a quantity is missing, AI should ask rather than invent information.

Unresolved AI decisions include provider, model, OCR and handwriting strategy, multimodal pipeline, structured schema, confidence representation, retrieval strategy, prompt design, evaluation dataset, cost, latency, privacy, and data handling.

Do not introduce AI into the first milestone.

## 11. Student Ownership and Edit Semantics

The student is the authority over their model.

AI must not silently overwrite student work.

The long-term platform should consider:

- Revision IDs
- Structured edit operations
- Attribution
- Conflict detection
- Dependency invalidation
- Recalculation
- Undo and redo
- Version history
- Previewing changes
- Accepting or rejecting AI proposals

These are future capabilities.

The first simulator does not need collaborative editing or a complex revision system.

## 12. Validation and Testing

Testing is a core part of the project.

Distinguish:

### Verification

Did we implement the intended equations and algorithm correctly?

### Validation

Does the model represent the intended physical situation correctly?

These are different questions.

A numerically correct solver can still produce a physically inappropriate result if the model assumptions are wrong.

Depending on the feature, use analytical comparison, known reference examples, unit tests, integration tests, property-based tests, dimensional checks, limiting-case tests, conservation checks, numerical convergence tests, model-composition tests, and educational review.

For the first simulator, use straightforward automated tests against analytical results.

Do not claim that tests passed unless they were actually executed.

## 13. First Development Milestone: Projectile Motion Simulator

The first goal is a complete, functioning projectile motion simulator.

Inputs:

- Initial speed
- Launch angle
- Gravity
- Initial position, if useful
- Simulation duration or automatically calculated flight time

Physics:

- Horizontal position
- Vertical position
- Horizontal velocity
- Vertical velocity
- Time of flight
- Maximum height
- Horizontal range

Visualization:

- Projectile
- Launch point
- Trajectory
- Ground
- Velocity vectors
- Position coordinates
- Relevant numerical values

Controls:

- Play
- Pause
- Reset
- Playback speed
- Launch speed control
- Launch angle control
- Gravity control

Changing a parameter should update the model.

The trajectory must correspond to the actual equations.

The simulator must be controllable and understandable.

Do not include AI question interpretation, AI Copilot, database persistence, user accounts, collaboration, all 19 chapters, a general-purpose component composer, a complete Learning Execution Engine, or a universal physics solver in the first milestone.

## 14. Projectile Motion Physics Reference

Use the standard idealized projectile assumptions unless a newer explicit requirement changes them.

- Motion is two-dimensional.
- Gravity is constant.
- Gravity acts vertically downward.
- Air resistance is ignored.
- The launch point is the origin by default.
- The ground is at y = 0 by default.
- The projectile is treated as a point particle.
- Launch speed is measured relative to the ground.
- Launch angle is measured counterclockwise from the positive horizontal axis.

Let:

- v0 = initial speed
- θ = launch angle
- g = gravitational acceleration magnitude
- t = elapsed time
- x0, y0 = initial position

### Initial Velocity Components

vx0 = v0 cos(θ)

vy0 = v0 sin(θ)

### Acceleration

ax = 0

ay = -g

### Position

x(t) = x0 + v0 cos(θ) t

y(t) = y0 + v0 sin(θ) t - 0.5 g t²

### Velocity

vx(t) = v0 cos(θ)

vy(t) = v0 sin(θ) - g t

### Equal-Height Launch and Landing

For y0 = 0 and 0 < θ < 90°:

Time to apex:

t_apex = v0 sin(θ) / g

Maximum height:

H_max = v0² sin²(θ) / (2g)

Time of flight:

T = 2 v0 sin(θ) / g

Horizontal range:

R = v0² sin(2θ) / g

These equations assume equal launch and landing heights.

### Unequal Launch and Landing Heights

If launch and landing heights differ, solve:

y0 + v0 sin(θ) t - 0.5 g t² = y_landing

For the simulator's landing time, use the first future downward crossing of the landing height; an upward crossing is not a landing event. Generic position/height queries should continue to return every relevant nonnegative root rather than discarding the alternate crossing.

Do not blindly use the equal-height range equation for an unequal-height problem.

### Edge Cases

Consider:

- Zero launch speed
- Zero launch angle
- Vertical launch
- Gravity equal to zero
- Negative or invalid gravity input
- Launch from an elevated position
- Landing height different from launch height
- A projectile that does not intersect the selected landing surface in the expected interval

Some edge cases may be restricted through validation in the first version.

Do not silently generate invalid physics.

### Numerical and UI Notes

- Convert degrees to radians before using Java trigonometric functions.
- Java's Math.sin() and Math.cos() use radians.
- Keep physics calculations in SI units.
- Use consistent units in the UI.
- Use a consistent screen-coordinate convention.
- If screen y increases downward, correctly map physical positive y upward.
- Do not change the physics model merely to accommodate the renderer.
- Playback speed must not alter the calculated physical trajectory.

### Testing Expectations

Test analytical results, including meaningful cases such as:

- A known launch speed and angle
- A 45° equal-height trajectory
- A horizontal launch
- A vertical launch
- Zero speed
- Invalid gravity
- Unequal launch and landing heights
- Correct velocity components
- Correct apex behavior
- Correct trajectory endpoints

## 14.1 Chapter 02: Relative Motion Physics & Architecture Specification

### Pedagogical Objective & JEE Focus
Relative Motion is PHYSORA's second core curriculum chapter for Class 11–12 JEE Main and JEE Advanced students. Its primary pedagogical goal is to develop intuitive and mathematical mastery over reference frame transformations. By shifting from the Ground Frame ($\mathcal{S}$) to an accelerated or moving Observer Frame ($\mathcal{S}_A$), students discover that seemingly complex multi-body curved interactions collapse into elementary linear or constant-acceleration geometries.

### Core Mathematical Framework: Galilean Transformations
All calculations adhere to standard Galilean relativity in two dimensions using Cartesian coordinates and SI units:
$$\vec{r}_{B/A}(t) = \vec{r}_B(t) - \vec{r}_A(t)$$
$$\vec{v}_{B/A}(t) = \vec{v}_B(t) - \vec{v}_A(t)$$
$$\vec{a}_{B/A}(t) = \vec{a}_B(t) - \vec{a}_A(t)$$
Where:
- $\vec{r}_{B/A}(t)$ is the position of body $B$ as observed in the frame of body $A$ ($\mathcal{S}_A$, where $A$ remains fixed at the origin $(0, 0)$).
- $\vec{v}_{B/A}(t)$ is the velocity of $B$ relative to $A$.
- $\vec{a}_{B/A}(t)$ is the acceleration of $B$ relative to $A$.

### Flagship Experience: Two-Projectile Pursuit & Straight-Line Relative Motion
When two projectiles $A$ and $B$ are launched under identical uniform downward gravity $\vec{g} = -g\hat{j}$ ($g > 0$) with no air drag:
$$\vec{a}_A = -g\hat{j}, \quad \vec{a}_B = -g\hat{j} \implies \vec{a}_{B/A} = (-g\hat{j}) - (-g\hat{j}) = \vec{0}$$
Because the relative acceleration vanishes identically throughout flight, the relative trajectory in frame $\mathcal{S}_A$ is a uniform rectilinear motion:
$$\vec{r}_{B/A}(t) = \vec{r}_0 + \vec{v}_{\text{rel}} t$$
where:
- $\vec{r}_0 = \vec{r}_B(0) - \vec{r}_A(0) = (x_{B0} - x_{A0})\hat{i} + (y_{B0} - y_{A0})\hat{j}$
- $\vec{v}_{\text{rel}} = \vec{v}_B(0) - \vec{v}_A(0) = (v_{Bx0} - v_{Ax0})\hat{i} + (v_{By0} - v_{Ay0})\hat{j}$

This fundamental cancellation means that while an observer on the ground sees two intersecting or diverging parabolas:
$$\vec{r}_A(t) = \vec{r}_{A0} + \vec{v}_{A0} t - \frac{1}{2}g t^2\hat{j}, \quad \vec{r}_B(t) = \vec{r}_{B0} + \vec{v}_{B0} t - \frac{1}{2}g t^2\hat{j}$$
an observer riding projectile $A$ observes projectile $B$ moving along an exact straight line with constant speed $|\vec{v}_{\text{rel}}|$.

---

### Contract 1: Interval-Aware Closest Approach ($d_{\min, \text{interval}}$) & Flight Boundaries

In idealized geometry, the separation between two straight lines extends over $t \in (-\infty, +\infty)$. In physical projectile motion, however, the bodies exist in simultaneous active flight only over a bounded time window:
$$t \in [0, T], \quad \text{where } T = \min(t_{\text{land}, A}, t_{\text{land}, B})$$
Here $T$ represents the duration of simultaneous active flight. Once either projectile strikes the landing surface $y = y_{\text{surface}}$, free parabolic flight terminates, and the governing equations cease to hold.

#### 1. Unconstrained Stationary Point
The squared separation distance between the two bodies is:
$$d^2(t) = |\vec{r}_{B/A}(t)|^2 = |\vec{r}_0 + \vec{v}_{\text{rel}} t|^2 = |\vec{r}_0|^2 + 2(\vec{r}_0 \cdot \vec{v}_{\text{rel}}) t + |\vec{v}_{\text{rel}}|^2 t^2$$
For $|\vec{v}_{\text{rel}}| > 0$, setting $\frac{d(d^2)}{dt} = 0$ yields the unique unconstrained stationary point on $(-\infty, +\infty)$:
$$t_{\min} = -\frac{\vec{r}_0 \cdot \vec{v}_{\text{rel}}}{|\vec{v}_{\text{rel}}|^2}$$
The perpendicular distance from the origin of $\mathcal{S}_A$ to the infinite line of relative motion is:
$$d_{\min, \text{line}} = |\vec{r}_{B/A}(t_{\min})| = \frac{|\vec{r}_0 \times \vec{v}_{\text{rel}}|}{|\vec{v}_{\text{rel}}|}$$
where in 2D, $|\vec{r}_0 \times \vec{v}_{\text{rel}}| = |r_{0x} v_{\text{rel}, y} - r_{0y} v_{\text{rel}, x}|$.

#### 2. Interval-Clamped Physical Closest Approach
The true physical closest approach must be constrained to the simultaneous active flight domain $[0, T]$:
$$t^* = \operatorname{clamp}(t_{\min}, 0, T) = \max(0, \min(t_{\min}, T))$$
$$d_{\min, \text{interval}} = |\vec{r}_{B/A}(t^*)| = |\vec{r}_0 + \vec{v}_{\text{rel}} t^*|$$

#### 3. Rigorous Analytical Case Analysis
1. **Zero Relative Velocity ($|\vec{v}_{\text{rel}}| \le 10^{-9}\text{ m/s}$)**:
   - The bodies move along parallel parabolic paths in the ground frame with identical velocities.
   - Separation is constant: $d(t) \equiv |\vec{r}_0|$ for all $t \in [0, T]$.
   - The closest approach time is conventionally reported as $t^* = 0$, with $d_{\min, \text{interval}} = |\vec{r}_0|$.
   - Relative kinematics state is `STATIONARY_SEPARATION`.
2. **Divergent Trajectories ($t_{\min} < 0$)**:
   - The unconstrained geometric line minimum occurred in the unphysical past ($t < 0$).
   - Since $d^2(t)$ is a quadratic opening upwards with vertex at $t_{\min} < 0$, $d(t)$ is strictly monotonically increasing on the physical forward interval $t \in [0, T]$.
   - The bodies are diverging throughout flight. The minimum separation achieved during flight occurs at launch:
     $$t^* = 0, \quad d_{\min, \text{interval}} = |\vec{r}_0|$$
3. **Internal Closest Approach ($0 \le t_{\min} \le T$)**:
   - The unconstrained geometric minimum occurs during active simultaneous flight.
   - The bodies approach each other and reach their minimum distance before either touches the surface:
     $$t^* = t_{\min}, \quad d_{\min, \text{interval}} = d_{\min, \text{line}} = \frac{|\vec{r}_0 \times \vec{v}_{\text{rel}}|}{|\vec{v}_{\text{rel}}|}$$
4. **Post-Landing Line Minimum ($t_{\min} > T$)**:
   - The bodies are approaching each other throughout simultaneous flight ($d(t)$ is strictly decreasing on $[0, T]$).
   - However, the flight window terminates at $t = T$ because at least one body impacts the surface before the unconstrained stationary point is reached.
   - The closest separation achieved while both bodies are in flight occurs at termination:
     $$t^* = T, \quad d_{\min, \text{interval}} = |\vec{r}_0 + \vec{v}_{\text{rel}} T|$$
   - Crucially, $d_{\min, \text{interval}} > d_{\min, \text{line}}$. The solver and UI MUST report $d_{\min, \text{interval}}$ and $t^* = T$ as the true physical minimum, and MUST NOT report a contact/collision or $d_{\min, \text{line}}$ if the contact point lies beyond surface impact.

---

### Contract 2: Landing-Time Semantics ($t_{\text{land}}$) & Multi-Surface Boundaries

For a projectile launched at $(x_0, y_0)$ with velocity $(v_{x0}, v_{y0})$ under uniform downward gravity $g > 0$, the landing surface is defined as a horizontal plane $y = y_{\text{surface}}$ (default ground $y_{\text{surface}} = 0$).

The vertical equation of motion is:
$$y(t) = y_0 + v_{y0} t - \frac{1}{2} g t^2, \quad v_y(t) = v_{y0} - g t$$
The intersection condition $y(t) = y_{\text{surface}}$ yields:
$$\frac{1}{2} g t^2 - v_{y0} t + (y_{\text{surface}} - y_0) = 0$$
Let $\Delta y = y_{\text{surface}} - y_0$. The surface discriminant is:
$$\Delta_{\text{surface}} = v_{y0}^2 - 2g(y_{\text{surface}} - y_0) = v_{y0}^2 + 2g(y_0 - y_{\text{surface}})$$

#### Rigorous Semantic Distinctions
The physics contract explicitly separates three distinct geometrical outcomes whenever $y(t) = y_{\text{surface}}$:
1. **Upward Crossing (`UPWARD_CROSSING`):**
   $y(t) = y_{\text{surface}}$ with $v_y(t) > 0$. The projectile crosses from below the plane to above the plane.
2. **Tangential Surface Contact (`TANGENTIAL_CONTACT`):**
   $y(t) = y_{\text{surface}}$ with $v_y(t) = 0$. The parabolic trajectory reaches its apex exactly on the surface plane ($\Delta_{\text{surface}} = 0$, $t_{\text{apex}} = \frac{v_{y0}}{g}$). The trajectory touches the plane tangentially without penetrating or crossing it. **A tangential contact is NOT a downward crossing.**
3. **Downward Crossing (`DOWNWARD_CROSSING`):**
   $y(t) = y_{\text{surface}}$ with $v_y(t) < 0$. The projectile crosses from above the plane to below the plane.

*Physical Flight Termination Rule:* For ordinary open-terrain projectile motion, both `TANGENTIAL_CONTACT` and `DOWNWARD_CROSSING` terminate physical flight (landing), but they remain distinctly identified and classified in the result record.

#### Comprehensive Initial Condition Cases
1. **Elevated Launch ($y_0 > y_{\text{surface}} \implies y_0 - y_{\text{surface}} > 0$)**:
   - Discriminant $\Delta_{\text{surface}} = v_{y0}^2 + 2g(y_0 - y_{\text{surface}}) > v_{y0}^2 \ge 0$.
   - Exactly one positive root exists:
     $$t_{\text{land}} = \frac{v_{y0} + \sqrt{v_{y0}^2 + 2g(y_0 - y_{\text{surface}})}}{g} > 0$$
   - At this root, vertical velocity is downward: $v_y(t_{\text{land}}) = -\sqrt{\Delta_{\text{surface}}} < 0$.
   - Classification: `DOWNWARD_CROSSING` (`GROUND_IMPACT_DOWNWARD`). Pure horizontal launch ($v_{y0} = 0$) simplifies to $t_{\text{land}} = \sqrt{\frac{2(y_0 - y_{\text{surface}})}{g}}$.
2. **Ground-Level Launch ($y_0 = y_{\text{surface}} \implies y_0 - y_{\text{surface}} = 0$)**:
   - Roots are $t = 0$ and $t = \frac{2 v_{y0}}{g}$.
   - **Upward Launch ($v_{y0} > 0$):** At $t = 0$, $v_y(0) = v_{y0} > 0$ (ascending into the air, NOT a landing). The projectile reaches apex and descends, crossing downward at:
     $$t_{\text{land}} = \frac{2 v_{y0}}{g} > 0 \quad (v_y(t_{\text{land}}) = -v_{y0} < 0)$$
     Classification: `DOWNWARD_CROSSING` (`GROUND_IMPACT_DOWNWARD`). Launching at ground level ($y_0 = y_{\text{surface}}$) with $v_{y0} > 0$ MUST NEVER be evaluated as immediate landing ($t = 0$).
   - **Downward / Resting Launch ($v_{y0} \le 0$):** The projectile is immediately on or directed into the surface: $t_{\text{land}} = 0$.
3. **Sub-Surface Launch ($y_0 < y_{\text{surface}} \implies y_{\text{surface}} - y_0 > 0$)**:
   - Maximum height reached: $y_{\max} = y_0 + \frac{v_{y0}^2}{2g}$.
   - **Downward or Stationary Initial Motion ($v_{y0} \le 0$):** Moves away downwards. Classification: `NO_LANDING_RECEDING` (`OptionalDouble.empty()`).
   - **Upward Initial Motion ($v_{y0} > 0$):**
     - If $\Delta_{\text{surface}} < 0 \iff v_{y0} < \sqrt{2g(y_{\text{surface}} - y_0)}$: Apex lies strictly below the surface ($y_{\max} < y_{\text{surface}}$). Trajectory turns around without reaching surface. Classification: `NO_LANDING_APEX_BELOW` (`OptionalDouble.empty()`).
     - If $\Delta_{\text{surface}} = 0 \iff v_{y0} = \sqrt{2g(y_{\text{surface}} - y_0)}$: Apex reaches surface exactly ($y_{\max} = y_{\text{surface}}$) at $t_{\text{apex}} = \frac{v_{y0}}{g} > 0$ with $v_y = 0$.
       **Classification:** `TANGENTIAL_CONTACT` (`APEX_TOUCH_SURFACE`). (Example: $y_0 = -5\text{ m}, v_{y0} = 10\text{ m/s}, g = 10\text{ m/s}^2 \implies t_{\text{apex}} = 1\text{ s}, v_y = 0$). Flight terminates at $t = 1\text{ s}$ as tangential contact, not downward crossing.
     - If $\Delta_{\text{surface}} > 0 \iff v_{y0} > \sqrt{2g(y_{\text{surface}} - y_0)}$: Trajectory emerges above surface.
       Two distinct positive roots exist:
       $$t_{\text{up}} = \frac{v_{y0} - \sqrt{\Delta_{\text{surface}}}}{g} > 0 \quad (v_y(t_{\text{up}}) = +\sqrt{\Delta_{\text{surface}}} > 0 \implies \text{`UPWARD_CROSSING`})$$
       $$t_{\text{down}} = \frac{v_{y0} + \sqrt{\Delta_{\text{surface}}}}{g} > t_{\text{up}} \quad (v_y(t_{\text{down}}) = -\sqrt{\Delta_{\text{surface}}} < 0 \implies \text{`DOWNWARD_CROSSING`})$$
       For standard open-terrain physics, the projectile emerges through $y_{\text{surface}}$ at $t_{\text{up}}$, reaches apex in the air, and lands via downward crossing at $t_{\text{land}} = t_{\text{down}}$.
4. **Zero Gravity ($g = 0$)**:
   - Linear motion: $y(t) = y_0 + v_{y0} t$.
   - Downward crossing occurs at $t = \frac{y_0 - y_{\text{surface}}}{|v_{y0}|} > 0$ if $v_{y0} < 0$ and $y_0 > y_{\text{surface}}$.
   - Upward crossing occurs at $t = \frac{y_{\text{surface}} - y_0}{v_{y0}} > 0$ if $v_{y0} > 0$ and $y_0 < y_{\text{surface}}$.
   - Otherwise `NO_LANDING_PARALLEL`.
5. **Invalid Gravity ($g < 0$)**:
   - Triggers explicit precondition rejection `INVALID_GRAVITY`.

---

### Contract 3: Collision Classification & Kinematics Orthogonality

To eliminate ambiguity, relative motion behavior and contact geometry are separated into two independent, orthogonal classifications evaluated over the simultaneous active flight interval $[0, T]$:

#### 1. Relative Kinematics State (`RelativeKinematicsState`)
Characterizes whether the relative distance changes over time:
- **`STATIONARY_SEPARATION`**: $|\vec{v}_{\text{rel}}| \le 10^{-9}\text{ m/s}$. The relative velocity is zero; the distance between the bodies remains constant throughout flight: $d(t) \equiv |\vec{r}_0|$.
- **`UNIFORM_RELATIVE_VELOCITY`**: $|\vec{v}_{\text{rel}}| > 10^{-9}\text{ m/s}$. The bodies have non-zero relative velocity; separation varies linearly along the relative line of motion.

#### 2. Contact Classification (`ContactClassification`)
Evaluates the physical proximity of the bodies at the interval-clamped closest approach point $t^* = \operatorname{clamp}(t_{\min}, 0, T)$ during simultaneous active flight $[0, T]$:
- **`EXACT_CONTACT`**: $d_{\min, \text{interval}} \le 10^{-9}\text{ m}$ (analytical center-to-center intersection).
- **`TOLERANCE_CONTACT`**: $10^{-9}\text{ m} < d_{\min, \text{interval}} \le \epsilon_{\text{contact}}$ (bodies touch or overlap based on configurable contact radius $\epsilon_{\text{contact}}$).
- **`NEAR_MISS`**: $\epsilon_{\text{contact}} < d_{\min, \text{interval}} \le \epsilon_{\text{proximity}}$ (bodies pass closely without touching, based on configurable proximity threshold $\epsilon_{\text{proximity}}$).
- **`NO_CONTACT`**: $d_{\min, \text{interval}} > \epsilon_{\text{proximity}}$ throughout active flight.

#### 3. Parameter Configurability
- $\epsilon_{\text{contact}}$ (default $R_A + R_B = 0.2\text{ m}$) and $\epsilon_{\text{proximity}}$ (default $2.0\text{ m}$) are **configurable scenario parameters** passed into the solver, NOT universal physical constants.
- If $\vec{v}_{\text{rel}} = \vec{0}$ and $|\vec{r}_0| = 0$, the state is unambiguously `STATIONARY_SEPARATION` + `EXACT_CONTACT`.
- If $\vec{v}_{\text{rel}} = \vec{0}$ and $|\vec{r}_0| = 5\text{ m}$, the state is unambiguously `STATIONARY_SEPARATION` + `NO_CONTACT`.

#### 4. UI Display Precedence Rule
When an aggregated label is required for UI display badges, contact geometry takes primary precedence, qualified by kinematics:
1. Primary classification follows `ContactClassification` (`EXACT_CONTACT` > `TOLERANCE_CONTACT` > `NEAR_MISS` > `NO_CONTACT`).
2. If `STATIONARY_SEPARATION`, the badge displays "Stationary Co-location" (if exact contact) or "Constant Separation" (if no contact). Both distinct enum properties are preserved in the result record.

---

### Contract 4: River-Boat Problem Archetype & Equal-Speed Contract

A boat crosses a river of width $d > 0$ with current flow velocity $\vec{v}_r = v_r \hat{i}$ ($v_r \ge 0$). The boat engine provides speed $v_b > 0$ relative to the water.

#### Heading Angle Convention
The steering angle $\theta$ is defined as the angle of the boat's heading vector $\vec{v}_{b/r}$ measured **upstream from the normal (perpendicular) to the river bank**:
$$\vec{v}_{b/r} = -v_b \sin\theta \hat{i} + v_b \cos\theta \hat{j} \quad (\theta \in [-90^\circ, +90^\circ])$$
The absolute velocity of the boat relative to the ground bank is:
$$\vec{v}_b = \vec{v}_{b/r} + \vec{v}_r = (v_r - v_b \sin\theta)\hat{i} + (v_b \cos\theta)\hat{j}$$
Across-bank velocity: $v_y = v_b \cos\theta > 0$ (for $|\theta| < 90^\circ$).  
Crossing time: $t = \frac{d}{v_b \cos\theta}$.  
Downstream drift on the opposite bank:
$$x = (v_r - v_b \sin\theta) t = d \left(\frac{v_r - v_b \sin\theta}{v_b \cos\theta}\right)$$

#### River-Boat Scenarios & Outcome Classification
1. **Shortest Time Crossing (`SHORTEST_TIME`)**:
   - To minimize $t(\theta) = \frac{d}{v_b \cos\theta}$, maximize $\cos\theta \implies \theta = 0^\circ$ (heading directly perpendicular across the river).
   - Minimum crossing time: $t_{\min} = \frac{d}{v_b}$.
   - Downstream drift: $x = v_r \frac{d}{v_b}$.
2. **Shortest Path / Zero Drift Analysis**:
   Zero downstream drift requires $x = 0 \iff v_r - v_b \sin\theta = 0 \implies \sin\theta = \frac{v_r}{v_b}$.
   The physical outcome depends strictly on the speed ratio $v_b / v_r$:
   - **`ZERO_DRIFT_ACHIEVABLE` ($v_b > v_r$)**:
     - Upstream angle: $\theta = \arcsin\left(\frac{v_r}{v_b}\right)$.
     - Net velocity across bank: $v_{\text{net}} = \sqrt{v_b^2 - v_r^2}\hat{j}$.
     - Finite crossing time: $t = \frac{d}{\sqrt{v_b^2 - v_r^2}}$.
     - Downstream drift: $x = 0$.
   - **`ZERO_DRIFT_ONLY_ASYMPTOTIC` ($v_b = v_r$)**:
     - Setting $\sin\theta = 1 \implies \theta = 90^\circ$ (heading directly upstream against the flow).
     - At $\theta = 90^\circ$, across-river velocity is $v_y = v_b \cos(90^\circ) = 0$. The boat holds stationary against the stream ($\vec{v}_b = \vec{0}$) and never reaches the opposite bank in finite time ($t \to \infty$).
     - As $\theta \to 90^\circ$, drift $x \to 0$ while crossing time $t \to \infty$. Zero drift is an asymptotic limit, not a physically realizable finite crossing.
     - The solver explicitly classifies this outcome and returns `OptionalDouble.empty()` for crossing time and drift, preventing division-by-zero or infinite loops.
   - **`ZERO_DRIFT_UNACHIEVABLE` ($v_b < v_r$)**:
     - Boat speed is insufficient to counter the current ($\frac{v_r}{v_b} > 1 \implies \sin\theta = \frac{v_r}{v_b}$ has no real solution). Zero drift is physically impossible.
     - To minimize downstream drift, minimize $x(\theta) = d \frac{v_r - v_b \sin\theta}{v_b \cos\theta}$.
     - Setting $\frac{dx}{d\theta} = 0 \implies \sin\theta = \frac{v_b}{v_r}$.
     - Optimal steering angle: $\theta^* = \arcsin\left(\frac{v_b}{v_r}\right)$.
     - Minimum achievable drift:
       $$x_{\min} = d \sqrt{\frac{v_r^2}{v_b^2} - 1}$$
     - Crossing time at minimum drift:
       $$t = \frac{d}{v_b \cos\theta^*} = \frac{d}{v_b \sqrt{1 - \frac{v_b^2}{v_r^2}}}$$

---

### Contract 5: Rain-Man / Rain-Umbrella Problem Archetype

A person moves horizontally while rain falls at a constant terminal velocity. The apparent velocity of rain dictates the orientation of an umbrella required to stay dry.

#### Coordinate Frame & Velocity Vectors
- Rain velocity relative to Ground ($\mathcal{S}$): $\vec{v}_r = v_{rx}\hat{i} - v_{ry}\hat{j}$ ($v_{ry} > 0$, rain falling downwards).
- Observer/person velocity relative to Ground: $\vec{v}_m = v_{mx}\hat{i}$.
- Apparent rain velocity relative to person ($\mathcal{S}_m$):
  $$\vec{v}_{r/m} = \vec{v}_r - \vec{v}_m = (v_{rx} - v_{mx})\hat{i} - v_{ry}\hat{j}$$
- Apparent speed of raindrops:
  $$|\vec{v}_{r/m}| = \sqrt{(v_{rx} - v_{mx})^2 + v_{ry}^2}$$

#### Umbrella Orientation Contract
To shield from rain, the umbrella handle must point directly along the direction from which rain appears to arrive, i.e., opposite to $\vec{v}_{r/m}$:
$$\vec{u} = -\vec{v}_{r/m} = (v_{mx} - v_{rx})\hat{i} + v_{ry}\hat{j}$$
The angle $\phi$ of the umbrella measured **from the upward vertical** ($\hat{j}$):
$$\tan\phi = \frac{v_{mx} - v_{rx}}{v_{ry}} \implies \phi = \arctan\left(\frac{v_{mx} - v_{rx}}{v_{ry}}\right)$$
- If $v_{mx} > v_{rx}$: $\phi > 0$, umbrella must be tilted **forward** in the direction of motion.
- If $v_{mx} = v_{rx}$: $\phi = 0$, rain appears purely vertical; umbrella held **upright**.
- If $v_{mx} < v_{rx}$: $\phi < 0$, umbrella must be tilted **backward**.

#### Multi-Observation Inverse Problem (Classic JEE Pattern)
If a person observes vertical rain ($\phi_1 = 0$) at velocity $v_{m1}$, then $v_{rx} = v_{m1}$. If at velocity $v_{m2}$ the umbrella angle is $\phi_2$, the vertical rain speed is determined analytically:
$$v_{ry} = \frac{v_{m2} - v_{rx}}{\tan\phi_2} = \frac{v_{m2} - v_{m1}}{\tan\phi_2}$$

---

### Contract 6: 1D Pursuit & Overtaking Mathematical Contract

Two bodies $A$ and $B$ move along the 1D $x$-axis with constant accelerations $a_A$ and $a_B$:
$$x_A(t) = x_{A0} + v_{A0} t + \frac{1}{2} a_A t^2$$
$$x_B(t) = x_{B0} + v_{B0} t + \frac{1}{2} a_B t^2$$
Relative position, velocity, and acceleration of $B$ relative to $A$:
$$x_0 = x_{B0} - x_{A0}, \quad v_{\text{rel}, 0} = v_{B0} - v_{A0}, \quad a_{\text{rel}} = a_B - a_A$$
$$x_{\text{rel}}(t) = x_0 + v_{\text{rel}, 0} t + \frac{1}{2} a_{\text{rel}} t^2$$
$$v_{\text{rel}}(t) = v_{\text{rel}, 0} + a_{\text{rel}} t$$
The active time interval is $t \in [0, T_{\text{active}}]$ (where $T_{\text{active}}$ is finite or $+\infty$ if unbounded).

Collision occurs when $x_{\text{rel}}(t) = 0 \iff \frac{1}{2} a_{\text{rel}} t^2 + v_{\text{rel}, 0} t + x_0 = 0$.

#### 1. Initial Collision Precedence ($x_0 = 0$)
Initial co-location ($|x_0| \le 10^{-9}\text{ m}$) is recognized as collision at $t = 0.0$.
Strict 3-level result precedence hierarchy:
1. **Initial Collision (`INITIAL_COLLISION_CO_LOCATED` or `PERMANENT_CO_LOCATION`):** Initial co-location at $t = 0$.
2. **Subsequent Valid Collision (`LINEAR_COLLISION`, `COLLISION_FIRST_PASS`, `COLLISION_OVERTAKE`, or `GLANCING_CONTACT`):** Earliest subsequent strictly positive root ($t > 0$) within $[0, T_{\text{active}}]$.
3. **No Collision During Active Interval (`NO_COLLISION_MINIMUM_SEPARATION`, `NO_COLLISION_DIVERGING`, or `NO_COLLISION_INTERVAL_EXPIRED`).**

*Educational Traceability:* When $x_0 = 0, v_{\text{rel}, 0} \ne 0, a_{\text{rel}} \ne 0$, any subsequent future root $t_{\text{subsequent}} = -\frac{2 v_{\text{rel}, 0}}{a_{\text{rel}}} > 0$ within $[0, T_{\text{active}}]$ is preserved in `OptionalDouble subsequentCollisionTime` for explanation, but NEVER replaces $t = 0.0$ as the primary collision result.

#### 2. Numerically Stable Quadratic Root Formula
For $A t^2 + B t + C = 0$, where $A = \frac{1}{2} a_{\text{rel}}, B = v_{\text{rel}, 0}, C = x_0$:
1. **Zero-Acceleration Degeneracy ($|A| \le 10^{-12}\text{ m/s}^2$):**
   Degenerates to linear equation $B t + C = 0$:
   - If $|B| \le 10^{-12}\text{ m/s}$:
     - If $|C| \le 10^{-9}\text{ m} \implies$ `PERMANENT_CO_LOCATION` ($t_{\text{collision}} = 0.0$).
     - If $|C| > 10^{-9}\text{ m} \implies$ `CONSTANT_NONZERO_SEPARATION` (no collision).
   - If $|B| > 10^{-12}\text{ m/s}$:
     - Root: $t_{\text{lin}} = -\frac{C}{B} = -\frac{x_0}{v_{\text{rel}, 0}}$.
     - If $t_{\text{lin}} \in [0, T_{\text{active}}] \implies$ `LINEAR_COLLISION`.
     - If $t_{\text{lin}} > T_{\text{active}} \implies$ `NO_COLLISION_INTERVAL_EXPIRED`.
     - If $t_{\text{lin}} < 0 \implies$ `NO_COLLISION_DIVERGING`.
2. **Discriminant Near-Zero Tolerance ($\epsilon_\Delta = 10^{-12}$):**
   Calculate $\Delta = B^2 - 4AC = v_{\text{rel}, 0}^2 - 2 a_{\text{rel}} x_0$.
   - If $|\Delta| \le \epsilon_\Delta$: Treated as repeated root ($\Delta = 0$):
     $$t^* = -\frac{B}{2A} = -\frac{v_{\text{rel}, 0}}{a_{\text{rel}}}$$
     - If $t^* \in [0, T_{\text{active}}] \implies$ `GLANCING_CONTACT` (relative velocity vanishes at contact).
     - If $t^* > T_{\text{active}} \implies$ `NO_COLLISION_INTERVAL_EXPIRED`.
     - If $t^* < 0 \implies$ `NO_COLLISION_DIVERGING`.
3. **Numerically Stable Roots ($\Delta > \epsilon_\Delta$):**
   To avoid catastrophic subtractive cancellation when $|B| \approx \sqrt{\Delta}$, compute the intermediate term:
   $$q = -\frac{1}{2} \left[ B + \operatorname{sgn}(B) \sqrt{\Delta} \right], \quad \text{where } \operatorname{sgn}(B) = \begin{cases} +1 & B \ge 0 \\ -1 & B < 0 \end{cases}$$
   The two exact roots are:
   $$t_a = \frac{q}{A}, \quad t_b = \frac{C}{q}$$
   Roots are ordered as $t_{\text{early}} = \min(t_a, t_b)$ and $t_{\text{late}} = \max(t_a, t_b)$.
   - If initial collision ($x_0 = 0$): $t_{\text{collision}} = 0.0$ (`INITIAL_COLLISION_CO_LOCATED`).
   - Else if $t_{\text{early}} > 0$:
     - If $t_{\text{early}} \le T_{\text{active}} \implies$ `COLLISION_FIRST_PASS` at $t_{\text{collision}} = t_{\text{early}}$.
     - If $t_{\text{early}} > T_{\text{active}} \implies$ `NO_COLLISION_INTERVAL_EXPIRED`.
   - Else if $t_{\text{early}} \le 0$ and $t_{\text{late}} > 0$:
     - If $t_{\text{late}} \le T_{\text{active}} \implies$ `COLLISION_OVERTAKE` at $t_{\text{collision}} = t_{\text{late}}$.
     - If $t_{\text{late}} > T_{\text{active}} \implies$ `NO_COLLISION_INTERVAL_EXPIRED`.
   - Else: `NO_COLLISION_DIVERGING`.
4. **Negative Discriminant ($\Delta < -\epsilon_\Delta$):**
   $a_{\text{rel}}$ and $x_0$ have the same sign. The parabola never crosses zero for any real $t \in (-\infty, \infty)$.
   - If turning point $t_{\text{ext}} = -\frac{v_{\text{rel}, 0}}{a_{\text{rel}}} \in [0, T_{\text{active}}] \implies$ `NO_COLLISION_MINIMUM_SEPARATION`.
   - Else $\implies$ `NO_COLLISION_DIVERGING` (or expired).

#### 3. Mathematical Contract for Minimum Separation ($d_{\min, \text{interval}}$)
The result property `minimumSeparationInterval` is defined mathematically for EVERY solver outcome over $t \in [0, T_{\text{active}}]$:
- If a valid physical collision occurs in $[0, T_{\text{active}}]$, then $d_{\min, \text{interval}} = 0.0$.
- When NO collision occurs in $[0, T_{\text{active}}]$:
  1. **Finite Interval $[0, T_{\text{active}}]$:**
     The minimum of the continuous function $D(t) = |x_{\text{rel}}(t)|$ on a closed interval occurs at an endpoint or an interior local stationary point:
     $$d_{\min, \text{interval}} = \min\left( |x_{\text{rel}}(0)|, \, |x_{\text{rel}}(T_{\text{active}})|, \, D_{\text{ext}} \right)$$
     where $D_{\text{ext}} = |x_{\text{rel}}(t_{\text{ext}})| = \left|x_0 - \frac{v_{\text{rel}, 0}^2}{2 a_{\text{rel}}}\right|$ if $a_{\text{rel}} \ne 0$ and $0 < t_{\text{ext}} < T_{\text{active}}$; otherwise $D_{\text{ext}} = +\infty$.
  2. **Unbounded Interval $[0, \infty)$:**
     - If $a_{\text{rel}} \ne 0$ and $t_{\text{ext}} > 0$:
       $$d_{\min, \text{interval}} = \min\left(|x_{\text{rel}}(0)|, \, |x_{\text{rel}}(t_{\text{ext}})|\right)$$
     - If $a_{\text{rel}} = 0$ or $t_{\text{ext}} \le 0$:
       The function $|x_{\text{rel}}(t)|$ is strictly monotonic on $[0, \infty)$, so:
       $$d_{\min, \text{interval}} = |x_{\text{rel}}(0)| = |x_0|$$

#### 4. Explicit Deferral of Braking Dynamics
Braking avoidance conditions (e.g. required braking deceleration $a_{\text{brake}} \ge \frac{v_{\text{rel}, 0}^2}{2 x_0}$) and driver reaction-delay dynamics are explicitly **deferred from Milestone 2.1**. They will be addressed in a future dedicated stopping-distance and safety-margins module once the core 1D relative pursuit kinematics engine is verified.

---

### Architectural Scope, Package Boundaries & Reuse
1. **Package Ownership**:
   - `edu.physics.relative`: Domain records, analytical solvers, state models, and physics verifiers.
   - `edu.physics.ui.relative`: JavaFX views, multi-frame renderer, vector addition overlay, and timeline controls.
2. **Decoupling from Chapter 01**:
   - Relative motion does NOT subclass or depend on `ProjectileMotion.java` or `SimulationController.java`.
   - Reuses the production-grade immutable 2D vector `edu.physics.simulation.Vector2D`.
3. **Synchronized 3-Frame Architecture**:
   - Ground Frame ($\mathcal{S}$): Standard laboratory Cartesian coordinates.
   - Observer A Frame ($\mathcal{S}_A$): Origin $(0, 0)$ pinned to body $A$; body $B$ displays straight-line motion.
   - Observer B Frame ($\mathcal{S}_B$): Origin $(0, 0)$ pinned to body $B$; body $A$ displays straight-line motion in opposite direction.
   - Master simulation timeline synchronizes all frames to the exact same physical time step $t$.
   - Live vector addition triangle overlay: $\vec{v}_B = \vec{v}_A + \vec{v}_{B/A}$.

## 15. Development Workflow

1. Inspect the current project.
2. Confirm the minimum suitable Java setup.
3. Implement the physics model.
4. Implement the calculations.
5. Add and run tests.
6. Build the interactive workspace.
7. Test the complete feature.
8. Review the architecture.

Do not overwrite existing work without understanding it.

Do not introduce a complex backend if a local application is sufficient for the first milestone.

Do not proceed to a visually polished interface while the basic physics is unverified.

Only extract reusable architecture when there is a real reason.

## 16. Decisions That Remain Open

The following decisions remain open unless a newer explicit decision or the current repository establishes them:

- Final product name
- Exact package structure
- Backend framework
- Frontend/backend communication strategy
- Persistence and database
- AI provider and model
- Multimodal interpretation architecture
- Solver framework
- Physics component catalog
- Model Composer implementation
- Collaboration architecture
- Revision and synchronization protocol
- Whether to use an external physics library
- Whether to introduce a dedicated rendering engine

Java 17, JavaFX, Maven, and JUnit are confirmed project choices. The first projectile simulator does not require a general-purpose rigid-body engine.
## 17. Development Priorities

Prioritize:

1. Correct physics
2. A functioning implementation
3. Clear and understandable user experience
4. Maintainable code
5. Automated testing
6. A reasonable path toward future features
7. Performance appropriate to the current scope
8. Visual polish

## 18. Project Continuity

This document is the master project context.

The current repository and the student's latest explicit decisions are authoritative for implementation details.

Distinguish between:

- Product vision
- Mandatory requirement
- Confirmed decision
- Proposed architecture
- Current implementation
- Future possibility
- Unresolved question

If the student explicitly changes a decision, follow the updated decision.

Do not silently resurrect an old decision that has been rejected.

Java is mandatory. The earlier TypeScript-first recommendation is superseded.

## 19. Final Principle

Build a serious product without losing sight of the student who is learning to build it.

Be technically rigorous.

Be physically accurate.

Be architecturally thoughtful.

Be skeptical of generated code.

Be patient when teaching.

Avoid premature complexity.

Keep the student involved in important decisions.

Do not let planning replace development.





