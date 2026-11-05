# PROJECT CONTEXT

## Interactive Physics Learning, Modeling, and Simulation Platform

## 1. Overview

We are developing an interactive physics learning, modeling, and simulation platform for Class 11–12 students, particularly those preparing for JEE Main and JEE Advanced.

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

AI integration is deferred from the first milestone.

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

Choose the physically relevant nonnegative root.

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





