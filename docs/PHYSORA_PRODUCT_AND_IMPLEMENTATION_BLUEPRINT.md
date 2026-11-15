# PHYSORA — Product & Implementation Blueprint

**Document Version:** 1.1.0  
**Status:** Approved & Frozen Product & Technical Specification  
**Location in Repository:** `docs/PHYSORA_PRODUCT_AND_IMPLEMENTATION_BLUEPRINT.md`  
**Baseline Git Checkpoint:** `34e44e2` (`feature/physora-demo-development`, 2026-11-15)  
**Verified Test Baseline:** 367 / 367 automated tests passing  

---

## 1. Frozen Product Vision

> **PHYSORA is an interactive physics learning laboratory for Class 11 and 12 students preparing for JEE Main and Advanced. It allows students to predict physical outcomes, conduct virtual experiments, visualize mathematical relationships, solve problems, and understand the underlying physics through guided explanations.**

PHYSORA is not a generic game-physics sandbox, nor is it a passive question bank or an abstract equation dump. It is an active educational laboratory designed to turn abstract mathematical physics into tangible, experimentally verifiable cause-and-effect understanding.

### Non-Negotiable Core Decisions
1. **Eight Mechanics Chapters Only:** Scope is strictly bounded to the core JEE mechanics sequence.
2. **Solids and Fluids Excluded:** Mechanical Properties of Solids (08) and Fluids (09) are explicitly outside this project scope.
3. **Student Experience Takes Priority:** A student evaluates software by clarity, responsiveness, and pedagogical guidance—not by backend matrix complexity.
4. **Kinematics is the First Complete Experience:** Perfect one chapter end-to-end before expanding breadth.
5. **Deterministic Java Physics as the Sole Numerical Authority:** Every accepted number, coordinate, and state vector is calculated by verified Java algorithms.
6. **AI as an Experiment-Aware Teaching Companion:** AI interprets text, guides conceptual reasoning, and explains results, but never computes answers or silently modifies experiments.
7. **Pedagogical Consistency Across Diverse Models:** Every chapter follows the **Predict $\to$ Experiment $\to$ Understand $\to$ Apply** learning cycle, while utilizing domain-appropriate physics formulations.
8. **Scope Discipline:** No feature or chapter is added without an explicit educational rationale, mathematical specification, and test plan.

---

## 2. Frozen Eight-Chapter Curriculum Scope & Support Levels

To avoid premature claims of completeness, PHYSORA enforces a transparent **Capability Support Level Taxonomy**:

- **`VERIFIED`:** Fully implemented mathematical model, deterministic solver, interactive UI workbench, and automated test suite.
- **`SIMULATED`:** Working physics engine/time-stepping model verified in backend tests; UI visualization in progress.
- **`AI-GUIDED`:** Canonical question interpretation and grounded solver verified; interactive visual simulation pending.
- **`PREVIEW`:** Foundational mathematical classes and structural representations implemented; awaiting solver and workbench integration.
- **`PLANNED`:** Pedagogical specification defined; implementation scheduled for later phases.

```mermaid
flowchart TD
    subgraph ActiveCurriculum["PHYSORA JEE Mechanics Curriculum (8 Target Chapters)"]
        direction TB
        c1["01 Kinematics (VERIFIED - Full Interactive Bench)"]
        c2["02 Relative Motion (PLANNED)"]
        c3["03 Laws of Motion (PREVIEW - Headless Delassus & Active-Set Solver)"]
        c4["04 Work & Energy (PLANNED)"]
        c5["05 Momentum & Collisions (PLANNED)"]
        c6["06 Circular Motion (PLANNED)"]
        c7["07 Rotation (PLANNED)"]
        c10["10 Oscillations (PLANNED)"]
    end

    subgraph ExcludedScope["Explicitly Excluded from Project Scope"]
        e8["08 Mechanical Properties of Solids (EXCLUDED)"]
        e9["09 Mechanical Properties of Fluids (EXCLUDED)"]
        eX["Waves, Electromagnetism, Optics, Modern Physics (EXCLUDED)"]
    end

    c1 --> c2 --> c3 --> c4 --> c5 --> c6 --> c7 --> c10
```

### Curriculum Status Matrix

| Chapter ID | Chapter Name | Support Level | Implemented Capabilities & Next Milestone |
|---|---|---|---|
| **01** | **Kinematics** | **`VERIFIED`** | Analytical SI projectile motion, flat/inclined surfaces, structured AST solver, interactive JavaFX bench, AI interpretation/explanation. Milestone 1–3 focus. |
| **02** | **Relative Motion** | **`IN_DESIGN`** | **Flagship Experience:** Two-projectile pursuit & straight-line relative trajectory under uniform gravity ($\vec{a}_{\text{rel}} = \vec{0}$). Live 3-frame switching ($\mathcal{S}, \mathcal{S}_A, \mathcal{S}_B$), vector triangle, analytical closest approach ($d_{\min}, t_{\min}$), river-boat, rain-man. Milestone 2.1 in design. |
| **03** | **Laws of Motion** | **`PREVIEW`** | Backend mass-whitened Delassus RRQR solver (`DelassusRrqrSolver`) and unilateral active-set cable solver (`UnilateralActiveSetSolver`) fully verified (56 targeted tests). UI workbench pending Phase C. |
| **04** | **Work & Energy** | **`PLANNED`** | Work-energy theorem, conservative fields, real-time $K, U_g, U_s, W_{nc}$ energy bars, potential energy wells $U(x)$, stability analysis. |
| **05** | **Momentum & Collisions** | **`PLANNED`** | Center-of-mass kinematics, 1D/2D elastic & inelastic collisions ($0 \le e \le 1$), impulse curves, zero-momentum frame transformation. |
| **06** | **Circular Motion** | **`PLANNED`** | Kinematics ($\omega, \alpha$), centripetal/tangential acceleration vectors, banked tracks, vertical circular motion loop-the-loop tension dynamics. |
| **07** | **Rotation** | **`PLANNED`** | Rigid-body rotation $\tau = I\alpha$, angular momentum, pure rolling vs. slipping on inclines, moment of inertia visual races. |
| **10** | **Oscillations** | **`PLANNED`** | Simple Harmonic Motion (SHM), spring-mass series/parallel configs, simple pendulum, phase-space orbits ($v$ vs. $x$), energy exchange. |
| *08* | *Solids* | **`EXCLUDED`** | Stress-strain curves, elastic moduli, Hooke's continuum law. Out of scope. |
| *09* | *Fluids* | **`EXCLUDED`** | Hydrostatics, Pascal's law, Bernoulli equation, viscosity. Out of scope. |

---

## 3. Student Personas & Pedagogical Learning Workflow

PHYSORA addresses three distinct student personas within the Class 11–12 and JEE preparation spectrum:

### Student Personas
1. **The Intuition Builder (Class 11 Beginner):**
   - *Needs:* Direct visual connection between parameters and physical behavior.
   - *Pain Point:* Confused by vector decomposition and sign conventions; memorizes formulas without understanding parameter sensitivity.
2. **The JEE Main Aspirant (Speed & Variation Recognition):**
   - *Needs:* Rapid exploration of parameter variations.
   - *Pain Point:* Over-focuses on standard symmetric formulas ($R = \frac{v^2\sin 2\theta}{g}$) and stumbles when launch and landing elevations differ ($y_0 \ne y_{\text{land}}$).
3. **The JEE Advanced Solver (Rigorous Multi-Condition Analysis):**
   - *Needs:* Step-by-step verification of boundary conditions, non-standard constraints, and multi-step derivations.
   - *Pain Point:* Textbooks provide final answers with skipped intermediate steps, making it difficult to locate where conceptual reasoning broke down.

### The Universal Four-Stage Learning Loop
Every chapter and interactive bench in PHYSORA structures its user experience around this four-stage cognitive cycle:

```mermaid
flowchart LR
    A["1. PREDICT\nFormulate hypothesis\n& estimated outcome"] --> 
    B["2. EXPERIMENT\nTune conditions,\nsimulate & observe"] --> 
    C["3. UNDERSTAND\nInspect equations, synced\ngraphs & causal steps"] --> 
    D["4. APPLY\nSolve target challenges\n& new variations"]
    D --> A
```

1. **PREDICT:** Before running a simulation, the student is prompted with a conceptual or quantitative prediction (e.g., *"If launch height is doubled, will time of flight double, more than double, or less than double?"*).
2. **EXPERIMENT:** The student executes the experiment, scrubs the timeline, modulates physical parameters, and directly observes the empirical physical trajectory and event markers.
3. **UNDERSTAND:** The student opens the step-by-step logic view and synchronized graphs ($y-t$, $v_y-t$). They see the analytical derivation unfold: **Given $\to$ Governing Principle $\to$ Formula $\to$ Substitution $\to$ Evaluated Result**.
4. **APPLY:** The student takes on a goal-oriented challenge (e.g., *Hit the Target*, *Achieve Optimum Landing Speed*, or solve a JEE problem using the AI Teaching Companion).

---

## 4. Reusable Platform Architecture (Domain-Appropriate Modeling)

Rather than forcing every chapter into an identical monolithic physics engine, PHYSORA separates **shared educational infrastructure** from **chapter-specific physics formulations**:

```mermaid
classDiagram
    class ChapterWorkbench {
        <<interface>>
        +getChapterId() String
        +getTitle() String
        +getSupportLevel() SupportLevel
        +buildView(SessionContext ctx) Node
    }

    class SimulationHarness {
        +play() void
        +pause() void
        +restartAnimation() void
        +restoreDefaults() void
        +scrubTimeline(double t) void
        +setPlaybackSpeed(double rate) void
    }

    class SynchronizedGraphView {
        +bindTimeline(DoubleProperty time) void
        +plotSeries(String name, Function~Double,Double~ fn) void
        +updateNeedle(double t) void
    }

    class PedagogicalDerivation {
        +setGivens(Map givens) void
        +setPrinciple(String name, String equationLatex) void
        +setSubstitutions(Map subs) void
        +setResult(String key, PhysicalQuantity val) void
        +renderCard() Node
    }

    class ExperimentManager {
        +saveReferenceTrajectory() void
        +clearReferenceTrajectory() void
        +loadPreset(ExperimentPreset p) void
        +getDeltaMetrics() DeltaSummary
    }

    class AITeachingBridge {
        +injectActiveExperiment(ExperimentContext ctx) void
        +proposeParameterChange(ParameterDelta delta) ConfirmationRequest
        +explainSolverResult(SolutionStep step) void
    }

    ChapterWorkbench --> SimulationHarness
    ChapterWorkbench --> SynchronizedGraphView
    ChapterWorkbench --> PedagogicalDerivation
    ChapterWorkbench --> ExperimentManager
    ChapterWorkbench --> AITeachingBridge
```

### Shared Platform Infrastructure
1. **Simulation Harness:** Lifecycle management, animation timer pacing, rate scaling ($0.25\times$ to $2.0\times$), timeline scrubbing, non-destructive restart, and clean state restoration.
2. **Synchronized Graphing System:** 2D plotting engine generating synchronized curves ($x-t, y-t, v_y-t, E-t$) with a scrub-linked needle indicating the active simulation timestamp.
3. **Pedagogical Logic Engine:** Four-stage instructional derivation cards:
   $$\text{Stage 1: Given Quantities} \longrightarrow \text{Stage 2: Governing Principle} \longrightarrow \text{Stage 3: Symbolic Substitution} \longrightarrow \text{Stage 4: Evaluated Answer}$$
4. **Experiment Manager (Presets & Comparison):**
   - **Preset Library:** Standard pedagogical configurations (*Maximum Range 45°*, *Cliff Launch*, *Different Angles Same Speed*, *Earth vs Moon Gravity*).
   - **Trajectory Comparison:** Caches Trajectory A in memory, allows parameter adjustment to create Trajectory B, and renders Trajectory A as a translucent reference overlay with delta metrics ($\Delta R, \Delta H, \Delta T$).
5. **Challenge Engine:** Manages prediction hypotheses and target-hit scoring.
6. **AI Teaching Bridge:** Standardized interface passing active parameters to the AI conversation service and handling validated student-confirmed parameter updates.

### Domain-Appropriate Physics Modeling
Each chapter implements the mathematical representation best suited to its physical domain:
- **Kinematics:** Analytical closed-form parametric functions ($x(t), y(t)$) for perfect numerical precision and instantaneous root-finding.
- **Relative Motion:** Analytical vector frame transformations ($\vec{r}_{A/B} = \vec{r}_A - \vec{r}_B$) and parametric pursuit curves.
- **Laws of Motion:** Semi-implicit symplectic numerical integration coupled with the mass-whitened Delassus RRQR solver (`DelassusRrqrSolver`) for constrained multi-body systems.
- **Work & Energy:** State-function energy accounting ($E = K + U$) and numerical work integrals.
- **Momentum & Collisions:** Momentum conservation equations, center-of-mass kinematics, and restitution jump conditions.
- **Circular Motion & Rotation:** Angular state variables ($\theta, \omega, \alpha$) and coupled translational-rotational Newton-Euler equations.
- **Oscillations:** Harmonic state oscillators, phase-space state vectors, and numerical ODE integration.

---

## 5. Mathematically Safe AI Teaching Companion

The AI integration must strictly serve pedagogical exploration while maintaining zero risk of silent model corruption or hallucinated numerical answers.

```mermaid
sequenceDiagram
    participant Student as Student
    participant UI as AIProblemView
    participant Bridge as AITeachingBridge
    participant Ctrl as ProjectileSimulationController
    participant Val as ProblemValidator
    participant Solver as DeterministicSolver

    Note over Student,Ctrl: 1. Experiment Context Injection
    Student->>UI: Clicks "⚡ Use Current Experiment"
    UI->>Bridge: Query active parameters
    Bridge->>Ctrl: Read v0=25, theta=30, g=9.81, y0=0
    Bridge->>UI: Prefixes prompt with active experiment context

    Note over Student,Solver: 2. Question Answering & Verification
    Student->>UI: "What angle gives the maximum range from this height?"
    UI->>Val: Classify & validate requested problem
    Val->>Solver: Solve analytically for optimum angle
    Solver-->>UI: Verified result: theta_opt = 35.26 deg, Range = 68.4 m
    UI-->>Student: Displays grounded explanation with verified numbers

    Note over Student,Ctrl: 3. Safe Parameter Application (No Silent Overwrite)
    Student->>UI: Clicks "▶ Apply to Simulator"
    UI->>Bridge: Request parameter update (theta -> 35.26 deg)
    Bridge->>Val: Validate parameter boundaries (0 <= theta <= 90)
    Bridge->>Student: Shows Confirmation Dialog: "Update launch angle from 30° to 35.26°?"
    Student->>Bridge: Confirms Update
    Bridge->>Ctrl: Apply launchAngle.set(35.26) & restartAnimation()
```

### AI Safety Invariants
1. **Deterministic Numerical Authority:** The AI LLM generates *explanations and interpretations only*. All numerical values displayed in solution cards originate exclusively from `ProjectileProblemSolver` and `ProjectileMotion`.
2. **Explicit Student Confirmation for Parameter Changes:** When the AI suggests an experimental configuration (e.g., *"Try setting angle to 35.26°"*), clicking **Apply to Simulator** presents an explicit confirmation card showing current vs. proposed values. **The AI is strictly prohibited from silently modifying simulator state.**
3. **Rigorous Input Validation:** Any parameter suggested by AI and confirmed by the student must pass `ProblemValidator` range and finiteness checks before updating controller properties.
4. **Attribution Transparency Without Clutter:** Developer controls (`ProviderMode`, Ollama settings) are tucked into an expandable `[⚙ Advanced Settings]` drawer, leaving the visible interface clean, focused, and distraction-free.

---

## 6. Visual Readability & Adaptive Viewport Specification

The renderer's primary requirement is **visual readability**, ensuring that trajectories across 2 orders of magnitude (from $v_0 = 1\text{ m/s}$ to $v_0 = 100\text{ m/s}$) are instantly comprehensible and aesthetically polished.

### 1. Removing the Hardcoded 2.0-Metre Floor
In `ProjectileCanvasRenderer.java`, replace lines 63–64 with an adaptive bounding algorithm:
```java
double xSpan = Math.max(0.1, maxX - minX);
double ySpan = Math.max(0.1, maxY - minY);
```

### 2. Viewport Geometry & Aspect Ratio Management
- **Margins:** Top = 32px, Bottom = 52px, Left = 68px, Right = 32px.
- **Padding Factor:** Add 12% padding above maximum height and 10% padding beyond maximum range to prevent markers from touching borders.
- **Dynamic Grid Sizing:** Grid step lines must not use fixed counts (e.g. 8x6). Use standard 1-2-5 decade steps ($\dots, 0.2, 0.5, 1, 2, 5, 10, 20, 50, \dots$ meters) so grid intervals always represent clean, readable metric units regardless of zoom.

### 3. Event Marker Decluttering
- **Marker Collision Detection:** Calculate screen coordinates for the `LAUNCH`, `APEX`, and `LANDING` badges.
- **Vertical / Horizontal Offsetting:** If the distance between `APEX` and `LANDING` badges is $< 48$ screen pixels (common in low-angle or low-speed launches):
  - Stagger `APEX` badge 16px higher.
  - Stagger `LANDING` badge 12px lower.
  - Suppress duplicate overlapping coordinate labels while retaining badge points.

### 4. Interactive "Fit Trajectory" Action
- Add a prominent **`[🔍 Fit Trajectory]`** button on the canvas toolbar.
- Clicking automatically computes the tightest bounding box containing $(x_0, y_0)$, $(x_{\text{apex}}, y_{\text{apex}})$, $(x_{\text{land}}, y_{\text{land}})$, and the entire sampled trajectory, resetting pan/zoom to optimal focus.

---

## 7. Decoupled Playback: Restart vs. Defaults

To prevent accidental loss of student experiments, animation playback is strictly decoupled from parameter state:

```mermaid
flowchart TD
    subgraph PlaybackBar["Transport Controls (Bottom Bar)"]
        Play["PLAY / PAUSE"]
        Restart["REPLAY / RESTART\n(restartAnimation)"]
        Scrubber["Time Scrubber (0 -> t_flight)"]
        Rate["Speed (0.5x, 1x, 2x)"]
    end

    subgraph Inspector["Parameter Inspector (Right Panel)"]
        Inputs["Speed, Angle, Gravity, Coordinates"]
        RestoreBtn["RESTORE FACTORY DEFAULTS\n(restoreDefaults)"]
    end

    Restart -->|Sets t = 0.0| SimTime["Simulation Time = 0"]
    Restart -.->|PRESERVES| Inputs
    RestoreBtn -->|Wipes & resets| Defaults["v0=20, theta=45, g=9.81, y0=0"]
```

- **`restartAnimation()` (Primary Transport Action):** Sets `simulationTime.set(0.0)`, pauses animation, and **strictly preserves** all tuned user parameters ($v_0, \theta, g, x_0, y_0, y_{\text{land}}$).
- **`restoreDefaults()` (Secondary Inspector Action):** Explicitly styled button labeled "Restore Defaults" with confirmation prompt if parameters were modified from standard factory settings.

---

## 8. Non-Destructive Number Field Input Handling

To eliminate typing-induced animation resets and false error flashes:
1. **Commit on Action / Focus Lost:** `TextField` updates the underlying `DoubleProperty` only when the student presses **Enter** or clicks outside the field (**focus lost**).
2. **Debounced Validation Feedback:** While the student is actively typing, perform non-blocking syntax validation:
   - If text is intermediate (e.g. empty, `-`, `.`), do not show validation error badges or reset model.
   - If invalid text remains after focus is lost, highlight the field with `.input-invalid` and restore the last valid value on **Escape**.
3. **Sliders Remain Live:** Sliders continue to update the model in real time during drag gestures.

---

## 9. 48-Hour Demonstration Plan with Built-In Contingency

The 48-hour schedule is organized into discrete, independently testable milestones with explicit contingency buffers:

```mermaid
gantt
    title PHYSORA 48-Hour Demo Sprint Timeline (With Buffer)
    dateFormat HH:mm
    axisFormat %H:%M

    section Milestone 1 (P0 Fixes)
    Adaptive Scaling & Fit Trajectory :m1a, 00:00, 4h
    Decouple Restart vs Defaults :m1b, after m1a, 3h
    AI Experiment Context Bridge :m1c, after m1b, 4h

    section Milestone 2 (P1 Polish)
    Input Typing Enter/Blur Handling :m2a, after m1c, 3h
    Instructional Logic Card (Given->Answer) :m2b, after m2a, 5h
    AI Advanced Settings Collapse :m2c, after m2b, 2h
    Sidebar 8-Chapter Alignment & CSS Overhaul :m2d, after m2c, 4h

    section Milestone 3 (Signature Features)
    Predict Before You Simulate :m3a, after m2d, 5h
    Compare Two Experiments Overlay :m3b, after m3a, 6h

    section Milestone 4 (Verification & Buffer)
    Automated Regression (367 Tests + New) :m4a, after m3b, 4h
    UI Walkthrough & Contingency Buffer :m4b, after m4a, 8h
```

### Prioritized Milestone Breakdown

#### Milestone 1: Kinematics Stability (P0 — Immediate)
- **1.1 Adaptive Scaling & Fit Trajectory:** Remove 2.0m clamp, implement dynamic padding, grid decades, and Fit button.
- **1.2 Decouple Restart vs. Restore:** Implement `restartAnimation()` preserving parameters; move `restoreDefaults()` to inspector.
- **1.3 AI Experiment Context Bridge:** Wire `controller` into `AIProblemView`, add `[⚡ Use Current Experiment]` button.
- *Verification:* Low/high-velocity manual checks + existing test suite passes.

#### Milestone 2: Student Experience (P1 — Core Usability)
- **2.1 Non-Destructive Number Fields:** Commit on Enter/unfocus; live sliders preserved.
- **2.2 Guided 4-Stage Logic Card:** Redesign `ProjectileLogicView` into **Given $\to$ Principle $\to$ Formula $\to$ Substitution $\to$ Answer** card with target selector.
- **2.3 De-Clutter AI Interface:** Move provider settings into `Advanced Settings` drawer; clean status pills for attribution.
- **2.4 8-Chapter Navigation & CSS:** Update `ChapterRail` to display the frozen 8 chapters; upgrade font sizes to 12px–14px.
- *Verification:* Automated UI tests via `FxTestRuntime`.

#### Milestone 3: Signature Educational Features (P2 — Distinctive Pedagogy)
- **3.1 Predict Before You Simulate:** Interactive hypothesis prompt before launch with post-flight explanation.
- **3.2 Compare Two Experiments (Contingency Feature):** Reference Trajectory A overlay with delta summary card ($\Delta R, \Delta H, \Delta T$). If time or stability is constrained, deliver Predict first.

#### Milestone 4: Verification, Regression & Demonstration Readiness
- Run full 367-test suite plus new targeted tests via `.\mvnw.cmd test`.
- Visual walkthrough on Windows desktop.
- 8-hour contingency buffer reserved for edge-case polishing and demo rehearsals.

---

## 10. Test and Verification Strategy

1. **Test Preservation Invariant:** The existing **367 automated tests** across Phase 1, Phase 2, and Phase 3 must remain 100% passing at all times.
2. **Analytical vs. Numerical Invariants:**
   - Analytical models (`ProjectileMotion`) must match closed-form formulas within $10^{-9}$ tolerance.
   - Symplectic integrators and constraint solvers (`UnilateralActiveSetSolver`) must satisfy complementarity within defined tolerances ($w \ge -\epsilon_w, \lambda \ge 0$).
3. **Headless UI Testing:** All JavaFX controller and view enhancements must be verified using `FxTestRuntime` on the FX application thread to guarantee headless build compatibility in Surefire.
4. **No Unverified AI Answers:** Test suites will confirm that AI suggestions continue to be verified against `SolutionVerifier` and `ExplanationVerifier` before any output is shown.

---

## 11. Approval & Governance

This blueprint authoritatively governs PHYSORA development on branch `feature/physora-demo-development`. All implementation pull requests and milestones must trace their changes directly to the requirements in this document.


---

## 12. Chapter 02: Relative Motion Architecture & Milestone Plan

### 12.1 Pedagogical Goal & Flagship Demonstration
Relative Motion is the second core chapter in PHYSORA's Class 11–12 JEE physics sequence. Its primary educational objective is to make reference frame transformations intuitive through live, interactive experimentation.

The flagship experience is **Two-Projectile Pursuit and Straight-Line Relative Motion**:
* In the Ground Frame ($\mathcal{S}$), students observe two distinct parabolic trajectories:
  $$\vec{r}_A(t) = \vec{r}_{A0} + \vec{v}_{A0}t - \frac{1}{2}g t^2\hat{j}, \quad \vec{r}_B(t) = \vec{r}_{B0} + \vec{v}_{B0}t - \frac{1}{2}g t^2\hat{j}$$
* When switching to the Observer Frame ($\mathcal{S}_A$), the gravitational terms identically cancel:
  $$\vec{a}_{B/A} = (-g\hat{j}) - (-g\hat{j}) = \vec{0} \implies \vec{r}_{B/A}(t) = \vec{r}_{B/A}(0) + (\vec{v}_{B0} - \vec{v}_{A0})t$$
  The student directly observes body $B$ moving along an exact straight line at constant velocity, transforming a seemingly complex curve-crossing problem into elementary linear vector geometry.

---

### 12.2 Mathematical Specifications & Physics Contracts

#### 1. Closest Approach & Flight Boundaries Contract
For two projectiles $A$ and $B$ launched with initial positions $\vec{r}_{A0}, \vec{r}_{B0}$ and velocities $\vec{v}_{A0}, \vec{v}_{B0}$:
- Initial relative position: $\vec{r}_0 = \vec{r}_{B0} - \vec{r}_{A0}$
- Relative velocity: $\vec{v}_{\text{rel}} = \vec{v}_{B0} - \vec{v}_{A0}$
- Simultaneous active flight window:
  $$t \in [0, T], \quad \text{where } T = \min(t_{\text{land}, A}, t_{\text{land}, B})$$
- Unconstrained stationary point on $(-\infty, +\infty)$:
  $$t_{\min} = -\frac{\vec{r}_0 \cdot \vec{v}_{\text{rel}}}{|\vec{v}_{\text{rel}}|^2} \quad (\text{for } |\vec{v}_{\text{rel}}| > 0)$$
- Geometric perpendicular line distance:
  $$d_{\min, \text{line}} = \frac{|\vec{r}_0 \times \vec{v}_{\text{rel}}|}{|\vec{v}_{\text{rel}}|}$$
- Interval-clamped closest approach time:
  $$t^* = \operatorname{clamp}(t_{\min}, 0, T) = \max(0, \min(t_{\min}, T))$$
- Minimum separation achieved during flight:
  $$d_{\min, \text{interval}} = |\vec{r}_0 + \vec{v}_{\text{rel}} t^*|$$

##### Case Breakdown
1. $|\vec{v}_{\text{rel}}| \le 10^{-9}\text{ m/s}$: Separation is constant ($d(t) \equiv |\vec{r}_0|$). Relative kinematics state is `STATIONARY_SEPARATION`.
2. $t_{\min} < 0$: Trajectories strictly diverge on $[0, T]$. Closest separation occurs at launch ($t^* = 0$, $d_{\min, \text{interval}} = |\vec{r}_0|$).
3. $0 \le t_{\min} \le T$: Closest approach occurs during active flight ($t^* = t_{\min}$, $d_{\min, \text{interval}} = d_{\min, \text{line}}$).
4. $t_{\min} > T$: Bodies are approaching, but flight terminates at surface impact $T$ before reaching $t_{\min}$. The physical closest approach during flight occurs at termination ($t^* = T$, $d_{\min, \text{interval}} = |\vec{r}_0 + \vec{v}_{\text{rel}} T| > d_{\min, \text{line}}$). The solver and UI MUST evaluate separation at $t^* = T$ and never report an unphysical underground collision.

#### 2. Landing-Time Semantics Contract
For a projectile with $(y_0, v_{y0})$ under gravity $g > 0$ and horizontal landing surface $y = y_{\text{surface}}$ (default ground $y_{\text{surface}} = 0$):
$$\frac{1}{2} g t^2 - v_{y0} t + (y_{\text{surface}} - y_0) = 0, \quad \Delta_{\text{surface}} = v_{y0}^2 - 2g(y_{\text{surface}} - y_0)$$

The solver strictly distinguishes three geometric outcomes at $y(t) = y_{\text{surface}}$:
1. **Upward Crossing (`UPWARD_CROSSING`):** $y(t) = y_{\text{surface}}$ with $v_y(t) > 0$.
2. **Tangential Surface Contact (`TANGENTIAL_CONTACT`):** $y(t) = y_{\text{surface}}$ with $v_y(t) = 0$. Apex touches surface tangentially; **a tangential contact is NOT a downward crossing.**
3. **Downward Crossing (`DOWNWARD_CROSSING`):** $y(t) = y_{\text{surface}}$ with $v_y(t) < 0$.

*Flight Termination Semantics:* For open terrain, both tangential contact and downward crossing terminate flight, but their distinct classifications are preserved in `LandingCalculationResult`.

##### Case Breakdown
1. **Elevated Launch ($y_0 > y_{\text{surface}}$):**
   $$t_{\text{land}} = \frac{v_{y0} + \sqrt{v_{y0}^2 + 2g(y_0 - y_{\text{surface}})}}{g} > 0$$
   Vertical velocity at impact is downward: $v_y(t_{\text{land}}) = -\sqrt{\Delta_{\text{surface}}} < 0$ (`DOWNWARD_CROSSING`).
2. **Ground Launch ($y_0 = y_{\text{surface}}$):**
   - Upward ($v_{y0} > 0$): First downward crossing is $t_{\text{land}} = \frac{2 v_{y0}}{g} > 0$ (`DOWNWARD_CROSSING`). Ground launch must NEVER be evaluated as immediate landing ($t = 0$).
   - Downward / resting ($v_{y0} \le 0$): $t_{\text{land}} = 0$.
3. **Sub-Surface Launch ($y_0 < y_{\text{surface}}$, e.g. trench/pit $y_0 = -5\text{ m}, y_{\text{surface}} = 0$):**
   - If $v_{y0} \le 0$ or $\Delta_{\text{surface}} < 0$: Projectile never reaches surface (`NO_LANDING_RECEDING` / `NO_LANDING_APEX_BELOW`).
   - If $\Delta_{\text{surface}} = 0$: Apex touches surface at $t_{\text{apex}} = \frac{v_{y0}}{g} > 0$ with $v_y = 0$ (`TANGENTIAL_CONTACT`). Flight terminates tangentially without crossing plane.
   - If $\Delta_{\text{surface}} > 0$: Projectile has sufficient upward velocity to cross plane.
     - Upward emergence: $t_{\text{up}} = \frac{v_{y0} - \sqrt{\Delta_{\text{surface}}}}{g} > 0$ with $v_y > 0$ (`UPWARD_CROSSING`).
     - Downward crossing / physical landing: $t_{\text{down}} = \frac{v_{y0} + \sqrt{\Delta_{\text{surface}}}}{g} > t_{\text{up}}$ with $v_y < 0$ (`DOWNWARD_CROSSING`).
4. **Zero Gravity ($g = 0$):** Linear motion $y(t) = y_0 + v_{y0}t$. Downward crossing at $t = \frac{y_0 - y_{\text{surface}}}{|v_{y0}|}$ if $v_{y0} < 0$ and $y_0 > y_{\text{surface}}$. Otherwise `NO_LANDING_PARALLEL`.
5. **Invalid Gravity ($g < 0$):** Precondition rejection (`INVALID_GRAVITY`).

#### 3. Orthogonal Collision & Kinematics Classification
Relative motion behavior and contact geometry are separated into two orthogonal, mutually exclusive enums:

1. **`RelativeKinematicsState`:**
   - `STATIONARY_SEPARATION`: $|\vec{v}_{\text{rel}}| \le 10^{-9}\text{ m/s}$ (separation remains constant: $d(t) \equiv |\vec{r}_0|$).
   - `UNIFORM_RELATIVE_VELOCITY`: $|\vec{v}_{\text{rel}}| > 10^{-9}\text{ m/s}$ (separation varies along line of motion).
2. **`ContactClassification`:**
   Evaluated at $t^* = \operatorname{clamp}(t_{\min}, 0, T)$ during active simultaneous flight $[0, T]$:
   - `EXACT_CONTACT`: $d_{\min, \text{interval}} \le 10^{-9}\text{ m}$.
   - `TOLERANCE_CONTACT`: $10^{-9}\text{ m} < d_{\min, \text{interval}} \le \epsilon_{\text{contact}}$.
   - `NEAR_MISS`: $\epsilon_{\text{contact}} < d_{\min, \text{interval}} \le \epsilon_{\text{proximity}}$.
   - `NO_CONTACT`: $d_{\min, \text{interval}} > \epsilon_{\text{proximity}}$.
3. **Configurable Parameters:** Contact radius $\epsilon_{\text{contact}}$ (default $0.2\text{ m}$) and proximity threshold $\epsilon_{\text{proximity}}$ (default $2.0\text{ m}$) are scenario-specific configurable parameters, NOT universal constants.
4. **Precedence for Display:** For single-badge UI displays, `ContactClassification` takes primary precedence, qualified by `RelativeKinematicsState`. Both distinct enums are preserved in result records.

#### 4. River-Boat Problem Archetype & Equal-Speed Contract
River width $d$, current $\vec{v}_r = v_r\hat{i}$, boat engine speed relative to water $v_b$.  
Steering angle $\theta$ measured **upstream from normal to bank**:
$$\vec{v}_{b/r} = -v_b \sin\theta\hat{i} + v_b \cos\theta\hat{j}, \quad \vec{v}_b = (v_r - v_b \sin\theta)\hat{i} + (v_b \cos\theta)\hat{j}$$
Crossing time: $t = \frac{d}{v_b \cos\theta}$. Downstream drift: $x = d \frac{v_r - v_b \sin\theta}{v_b \cos\theta}$.

- **Shortest Time (`SHORTEST_TIME`):** $\theta = 0^\circ \implies t_{\min} = \frac{d}{v_b}$, drift $x = v_r \frac{d}{v_b}$.
- **Shortest Path / Zero Drift Outcomes (`RiverBoatCrossingType`):**
  - `ZERO_DRIFT_ACHIEVABLE` ($v_b > v_r$): $\sin\theta = \frac{v_r}{v_b}$, finite crossing time $t = \frac{d}{\sqrt{v_b^2 - v_r^2}}$, drift $x = 0$.
  - `ZERO_DRIFT_ONLY_ASYMPTOTIC` ($v_b = v_r$): Setting $\sin\theta = 1 \implies \theta = 90^\circ$. Net forward velocity $v_y = v_b \cos(90^\circ) = 0$. Boat holds stationary against stream ($\vec{v}_b = \vec{0}$) and never reaches opposite bank in finite time ($t \to \infty$). Zero drift is an asymptotic limit, not a realizable finite crossing. The solver returns `OptionalDouble.empty()` for crossing time and drift.
  - `ZERO_DRIFT_UNACHIEVABLE` ($v_b < v_r$): Zero drift impossible. Optimal heading for minimum drift is $\sin\theta = \frac{v_b}{v_r}$, crossing time $t = \frac{d}{v_b \sqrt{1 - (v_b/v_r)^2}}$, minimum drift $x_{\min} = d \sqrt{\frac{v_r^2}{v_b^2} - 1}$.

#### 5. Rain-Man / Rain-Umbrella Problem Archetype
- Rain velocity relative to Ground: $\vec{v}_r = v_{rx}\hat{i} - v_{ry}\hat{j}$ ($v_{ry} > 0$).
- Observer velocity: $\vec{v}_m = v_{mx}\hat{i}$.
- Apparent rain velocity: $\vec{v}_{r/m} = (v_{rx} - v_{mx})\hat{i} - v_{ry}\hat{j}$.
- Umbrella orientation vector: $\vec{u} = -\vec{v}_{r/m} = (v_{mx} - v_{rx})\hat{i} + v_{ry}\hat{j}$.
- Umbrella tilt angle from upward vertical:
  $$\phi = \arctan\left(\frac{v_{mx} - v_{rx}}{v_{ry}}\right)$$
- Multi-observation inverse deduction: $v_{rx} = v_{m1}$ (if $\phi_1 = 0$) and $v_{ry} = \frac{v_{m2} - v_{m1}}{\tan\phi_2}$.

#### 6. 1D Pursuit & Overtaking Mathematical Contract
Relative motion along $x$-axis: $x_{\text{rel}}(t) = x_0 + v_{\text{rel}, 0}t + \frac{1}{2}a_{\text{rel}} t^2$ on active interval $t \in [0, T_{\text{active}}]$.

##### 1. Initial Collision Precedence ($x_0 = 0$)
Initial co-location ($|x_0| \le 10^{-9}\text{ m}$) is recognized as collision at $t = 0.0$.
Precedence hierarchy:
1. `INITIAL_COLLISION_CO_LOCATED` (or `PERMANENT_CO_LOCATION`): Initial co-location at $t = 0$.
2. Subsequent valid collision: Earliest strictly positive root ($t > 0$) within $[0, T_{\text{active}}]$.
3. No collision during active interval.

When $x_0 = 0, v_{\text{rel}, 0} \ne 0, a_{\text{rel}} \ne 0$, any subsequent root $t_{\text{subsequent}} = -\frac{2 v_{\text{rel}, 0}}{a_{\text{rel}}} > 0$ within $[0, T_{\text{active}}]$ is stored in `OptionalDouble subsequentCollisionTime` for educational explanation, but never replaces $t = 0.0$ as the primary collision result.

##### 2. Numerically Stable Quadratic Root Formula
For $A t^2 + B t + C = 0$, where $A = \frac{1}{2} a_{\text{rel}}, B = v_{\text{rel}, 0}, C = x_0$:
- Zero-acceleration linear fallback ($|A| \le 10^{-12}\text{ m/s}^2$):
  - If $|B| \le 10^{-12}\text{ m/s}$: `PERMANENT_CO_LOCATION` if $|C| \le 10^{-9}\text{ m}$, else `CONSTANT_NONZERO_SEPARATION`.
  - Else: Linear root $t_{\text{lin}} = -\frac{C}{B}$. Valid collision if $t_{\text{lin}} \in [0, T_{\text{active}}]$.
- Repeated roots & near-zero discriminant ($|\Delta| \le 10^{-12}$ where $\Delta = B^2 - 4AC$):
  - Repeated root $t^* = -\frac{B}{2A} = -\frac{v_{\text{rel}, 0}}{a_{\text{rel}}}$. `GLANCING_CONTACT` if $t^* \in [0, T_{\text{active}}]$.
- Distinct roots ($\Delta > 10^{-12}$):
  Stable Citardauq intermediate term to eliminate subtractive cancellation:
  $$q = -\frac{1}{2} \left[ B + \operatorname{sgn}(B) \sqrt{\Delta} \right], \quad t_a = \frac{q}{A}, \quad t_b = \frac{C}{q}$$
  Roots sorted as $t_{\text{early}} = \min(t_a, t_b)$, $t_{\text{late}} = \max(t_a, t_b)$.
  Earliest positive root in $[0, T_{\text{active}}]$ determines collision time (`COLLISION_FIRST_PASS` or `COLLISION_OVERTAKE`).
- Negative discriminant ($\Delta < -10^{-12}$): No real roots. Parabola never crosses zero ($a_{\text{rel}} x_0 > 0$).
  `NO_COLLISION_MINIMUM_SEPARATION` if turning point $t_{\text{ext}} = -\frac{v_{\text{rel}, 0}}{a_{\text{rel}}} \in [0, T_{\text{active}}]$, else `NO_COLLISION_DIVERGING`.

##### 3. Mathematically Complete Minimum Separation ($d_{\min, \text{interval}}$)
The property `minimumSeparationInterval` is defined for every solver outcome over $t \in [0, T_{\text{active}}]$:
- If a collision occurs on $[0, T_{\text{active}}]$, $d_{\min, \text{interval}} = 0.0$.
- Otherwise:
  - Finite interval $[0, T_{\text{active}}]$:
    $$d_{\min, \text{interval}} = \min\left(|x_{\text{rel}}(0)|, \, |x_{\text{rel}}(T_{\text{active}})|, \, D_{\text{ext}}\right)$$
    where $D_{\text{ext}} = |x_{\text{rel}}(t_{\text{ext}})| = \left|x_0 - \frac{v_{\text{rel}, 0}^2}{2 a_{\text{rel}}}\right|$ if $a_{\text{rel}} \ne 0$ and $0 < t_{\text{ext}} < T_{\text{active}}$; otherwise $+\infty$.
  - Unbounded interval $[0, \infty)$:
    $$d_{\min, \text{interval}} = \begin{cases} \min(|x_{\text{rel}}(0)|, |x_{\text{rel}}(t_{\text{ext}})|) & \text{if } a_{\text{rel}} \ne 0 \text{ and } t_{\text{ext}} > 0 \\ |x_{\text{rel}}(0)| & \text{otherwise} \end{cases}$$

##### 4. Explicit Deferral of Braking Dynamics
Braking avoidance conditions and driver reaction delay are explicitly deferred from Milestone 2.1 to a future dedicated stopping-distance module.

---

### 12.3 Domain Class & Record Specifications (Preview)

The domain model will be placed in `edu.physics.relative`:

```java
// Immutable 2D relative motion body specification
public record RelativeBody2D(
    String id,
    Vector2D initialPosition,
    Vector2D initialVelocity,
    double radius,
    double landingSurfaceY
) {}

// Closest approach & collision outcome
public record ClosestApproachResult(
    double tMinUnconstrained,
    double dMinLine,
    double tMinInterval,
    double dMinInterval,
    ContactClassification contactClassification,
    RelativeKinematicsState kinematicsState,
    Vector2D positionAAtClosest,
    Vector2D positionBAtClosest,
    double simultaneousFlightDuration,
    double contactRadius,
    double proximityRadius
) {}

public enum RelativeKinematicsState {
    STATIONARY_SEPARATION,
    UNIFORM_RELATIVE_VELOCITY
}

public enum ContactClassification {
    EXACT_CONTACT,
    TOLERANCE_CONTACT,
    NEAR_MISS,
    NO_CONTACT
}

// Landing calculation outcome
public record LandingCalculationResult(
    OptionalDouble firstFutureIntersectionTime,
    OptionalDouble firstDownwardCrossingTime,
    OptionalDouble tangentialContactTime,
    OptionalDouble physicalLandingTime,
    LandingClassification classification
) {}

public enum LandingClassification {
    DOWNWARD_CROSSING,
    TANGENTIAL_CONTACT,
    UPWARD_CROSSING,
    NO_LANDING_APEX_BELOW,
    NO_LANDING_RECEDING,
    NO_LANDING_PARALLEL,
    INVALID_GRAVITY
}

// River-boat analytical solution
public record RiverBoatResult(
    double riverWidth,
    double riverSpeed,
    double boatSpeed,
    double headingAngleRad,
    OptionalDouble crossingTime,
    OptionalDouble downstreamDrift,
    RiverBoatCrossingType crossingType
) {}

public enum RiverBoatCrossingType {
    SHORTEST_TIME,
    ZERO_DRIFT_ACHIEVABLE,
    ZERO_DRIFT_ONLY_ASYMPTOTIC,
    ZERO_DRIFT_UNACHIEVABLE,
    GENERAL_CROSSING
}

// Rain-man apparent velocity and umbrella orientation
public record RainManResult(
    Vector2D rainVelocityGround,
    Vector2D personVelocityGround,
    Vector2D apparentRainVelocity,
    double umbrellaAngleRad,
    double apparentRainSpeed
) {}

// 1D pursuit quadratic solution
public record Pursuit1DResult(
    double initialSeparation,
    double initialRelativeVelocity,
    double relativeAcceleration,
    double discriminant,
    OptionalDouble primaryCollisionTime,
    OptionalDouble subsequentCollisionTime,
    double minimumSeparationInterval,
    double activeIntervalDuration,
    Pursuit1DStatus status
) {}

public enum Pursuit1DStatus {
    INITIAL_COLLISION_CO_LOCATED,
    PERMANENT_CO_LOCATION,
    CONSTANT_NONZERO_SEPARATION,
    LINEAR_COLLISION,
    COLLISION_FIRST_PASS,
    COLLISION_OVERTAKE,
    GLANCING_CONTACT,
    NO_COLLISION_MINIMUM_SEPARATION,
    NO_COLLISION_DIVERGING,
    NO_COLLISION_INTERVAL_EXPIRED
}
```

---

### 12.4 Architectural Boundaries & Decoupling
1. **Domain Isolation**:
   - `edu.physics.relative`: Standalone physics records, root-finders, and analytical solvers.
   - `edu.physics.ui.relative`: JavaFX views, multi-frame renderer, vector addition overlay, and timeline controls.
2. **Zero Coupling to Chapter 01 Kinematics**:
   - Relative motion does NOT extend or subclass `ProjectileMotion.java` or `SimulationController.java`.
   - Reuses `edu.physics.simulation.Vector2D` directly for all 2D vector algebra.
3. **Multi-Frame Workbench Architecture**:
   - Three reference frames synchronized by a single master simulation clock:
     - $\mathcal{S}$: Ground Frame (both bodies follow parabolic trajectories).
     - $\mathcal{S}_A$: Observer A Frame (body $A$ pinned to $(0,0)$; body $B$ follows straight line).
     - $\mathcal{S}_B$: Observer B Frame (body $B$ pinned to $(0,0)$; body $A$ follows straight line in opposite direction).
   - Dynamic vector addition triangle overlay: $\vec{v}_B = \vec{v}_A + \vec{v}_{B/A}$ updating in real time.

---

### 12.5 Incremental Milestone Roadmap
*Curriculum Status:* **`IN_DESIGN`** (No production Java code authorized until contract freeze is verified).

1. **Milestone 2.1 — Physics Foundation & Domain Solvers:**
   - Implement `RelativeBody2D`, `ClosestApproachSolver`, `LandingSolver`, `RiverBoatSolver`, `RainManSolver`, and `Pursuit1DSolver` in `edu.physics.relative`.
   - Implement closed-form analytical solvers handling all edge cases ($v_{\text{rel}} = 0$, $t_{\min} < 0$, interval boundary clamping, $v_b = v_r$, sub-surface trenches, 1D quadratic root signs, tangential contacts, stable Citardauq roots).
   - Comprehensive unit tests covering all physics contracts and boundary conditions.
2. **Milestone 2.2 — Interactive Workbench & Dual-Frame Visualization:**
   - Implement `RelativeBenchView` registered in ChapterRail under Chapter 02.
   - Interactive reference frame switcher ($\mathcal{S}, \mathcal{S}_A, \mathcal{S}_B$) with master synchronized timeline and dynamic vector addition triangle overlay.
3. **Milestone 2.3 — Signature Learning Workflows:**
   - Predict Mode (predict collision vs miss; predict shortest crossing path).
   - Compare Mode (side-by-side ground vs relative frame analysis).
   - Understand Mode (6-step guided derivations of straight-line relative motion).
4. **Milestone 2.4 — AI Problem Interpretation & Grounded Explanations:**
   - Constrained relative motion problem solver with natural language interpretation and verified attribution.
