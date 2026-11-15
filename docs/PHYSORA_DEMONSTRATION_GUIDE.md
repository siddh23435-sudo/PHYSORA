# PHYSORA Demonstration Guide & Academic Handbook

**PHYSORA — Interactive Physics Learning, Modeling, and Simulation Platform**  
Designed for Class 11–12 JEE Main and JEE Advanced Physics Students  
**Active Milestone Reference Chapter**: 01 · Kinematics (Ideal 2D Projectile Motion)

---

## 1. Product Overview

PHYSORA bridges the gap between passive computer simulations and rigorous mathematical problem solving. Instead of simply manipulating sliders to watch a ball fly, students engage with physics through a structured four-stage learning loop:

$$\text{PREDICT} \longrightarrow \text{EXPERIMENT} \longrightarrow \text{UNDERSTAND} \longrightarrow \text{APPLY}$$

PHYSORA enforces strict mathematical and architectural boundaries:
- **Deterministic Physics Authority**: All calculations, trajectories, apexes, flight times, and landing points are derived from closed-form analytical kinematics ($ax = 0$, $ay = -g$).
- **No Hallucinated Numbers**: AI models (Google Gemini or local fallbacks) are never permitted to generate accepted numerical values; they operate strictly as explanatory companions grounded in immutable snapshots from the physics solver.
- **Scientific Integrity**: Hypotheses in the Prediction Challenge are locked before simulation execution and evaluated strictly against analytical truth.

---

## 2. Curriculum Scope & Status

| Chapter | Title | Status | Capability in Product |
|---|---|---|---|
| **01** | **Kinematics** | **LIVE** | Full interactive 2D simulation, prediction challenges, dual-experiment overlay comparison, 6-stage guided derivations, grounded AI assistance. |
| **02** | **Relative Motion** | **PLANNED** | Informative curriculum roadmap (Inertial frames, river-boat, rain-umbrella drift) with 1-click return to Kinematics. |
| **03** | **Laws of Motion** | **PLANNED** | Curriculum roadmap (Newton's laws, FBDs, friction, coupled pulleys). |
| **04** | **Work & Energy** | **PLANNED** | Curriculum roadmap (Work-Energy theorem, potential energy curves, power). |
| **05** | **Momentum & Collisions** | **PLANNED** | Curriculum roadmap (Impulse, conservation of momentum, 1D/2D collisions). |
| **06** | **Circular Motion** | **PLANNED** | Curriculum roadmap (Uniform circular kinematics, banking, vertical loops). |
| **07** | **Rotation** | **PLANNED** | Curriculum roadmap (Angular kinematics, torque, moment of inertia, rolling). |
| **08** | **Oscillations** | **PLANNED** | Curriculum roadmap (SHM, spring-mass systems, energy oscillations). |
| — | *Solids & Fluids* | *EXCLUDED* | Explicitly out of scope per curriculum boundaries. |

---

## 3. How to Run PHYSORA

### Prerequisites
- **Java Development Kit (JDK)**: Java 17 LTS (verified with `java -version`)
- **Maven**: Checked-in Maven Wrapper (`.\mvnw.cmd` on Windows, `./mvnw` on Linux/macOS)

### Launching the Desktop Application
From the repository root:
```powershell
.\mvnw.cmd javafx:run
```

### Running the Full Verification Suite
```powershell
.\mvnw.cmd test
```
*Current Suite Baseline: 412 automated tests, 0 failures, 0 errors, 0 skipped.*

---

## 4. AI Configuration & Offline Fallback

PHYSORA includes a multi-tier AI teaching architecture configured in the **ASK AI** tab:

1. **Online Google Gemini (Recommended)**:
   - Provide an API key via environment variable:
     ```powershell
     $env:GOOGLE_API_KEY="your-gemini-api-key"
     ```
   - Model defaults to `gemini-3.1-flash-lite` (or user-selected Gemini models).
2. **Local Ollama (Optional)**:
   - Run a local model (e.g., `ollama run llama3.2` or `mistral`) listening on `http://localhost:11434`.
   - Check the **"Enable optional Ollama (local LLM)"** checkbox in the provider settings.
3. **Deterministic Heuristic Offline Parser (Always Available)**:
   - If no API key is set and no network is available, PHYSORA automatically engages its built-in rule-based kinematics parser.
   - It extracts parameters ($v_0, \theta, g, y_0, y_{\text{land}}$), solves through `ProjectileProblemSolver`, and formats mathematical explanations without making any network calls.

---

## 5. Five-to-Seven Minute Demonstration Script

This reproducible script walks an evaluator or teacher through PHYSORA's complete educational journey.

### Step 1: Launch & Architectural Orientation (1 Minute)
1. Launch PHYSORA with `.\mvnw.cmd javafx:run`.
2. Point out the left **Curriculum Rail**:
   - Highlight **01 Kinematics** as the active LIVE bench.
   - Point out that chapters 02 through 08 are explicitly designated as PLANNED roadmaps rather than misleading incomplete simulations.
3. Observe the Kinematics workspace:
   - **Visual Center**: 2D Trajectory Viewport with 1-2-5 decade grid ticks, launch/apex/landing event badges, and animated velocity vectors ($v_x$ blue, $v_y$ orange).
   - **Right Inspector**: Model Inspector with dual input modes (responsive sliders and direct text input with validation).
   - **Bottom Transport Bar**: Playback controls (Play, Pause, Restart, Scrubber) and live physical telemetry.

### Step 2: Formulate a Hypothesis in PREDICT (1.5 Minutes)
1. In the top navigation bar, click **🎯 PREDICT**.
2. Note the experiment configuration card ($v_0 = 20\text{ m/s}, \theta = 45^\circ, g = 9.81\text{ m/s}^2$).
3. Ask the student/evaluator:
   > *"Before we press play, can you calculate or estimate the maximum height and the horizontal range?"*
4. Enter predictions:
   - **Maximum Height ($H_{\text{max}}$)**: `11.5` m
   - **Flight Time ($T$)**: `2.8` s
   - **Horizontal Range ($R$)**: `42.0` m
5. Click **🔒 LOCK PREDICTIONS**.
6. Point out key scientific integrity features:
   - The fields are immediately frozen and cannot be edited.
   - The **"REVEAL & COMPARE"** button remains disabled. Predictions cannot be revealed prematurely while the simulation is in `READY`, `PLAYING`, or `PAUSED` states.
   - If you nudge the launch speed slider now, an amber invalidation alert appears: *"Parameters modified after locking."* (Click **↺ NEW ATTEMPT** and re-lock with $v_0 = 20$, $\theta = 45$).

### Step 3: Run the Experiment & Reveal Results (1 Minute)
1. Click **⚗ EXPERIMENT** (or press Play in the transport bar).
2. Watch the projectile ascend, reach the apex badge where vertical velocity $v_y = 0$, and touch down at the landing surface.
3. The status badge transitions to **LANDED**.
4. Return to **🎯 PREDICT**:
   - The evaluation card automatically reveals the physical comparison:
     - **Maximum Height**: Predicted $11.50\text{ m}$ vs Actual $10.19\text{ m}$ ($\Delta = 1.31\text{ m}$, $12.8\%$ difference).
     - **Flight Time**: Predicted $2.80\text{ s}$ vs Actual $2.88\text{ s}$ ($\Delta = 0.08\text{ s}$, $2.9\%$ difference).
     - **Horizontal Range**: Predicted $42.00\text{ m}$ vs Actual $40.77\text{ m}$ ($\Delta = 1.23\text{ m}$, $3.0\%$ difference).
   - Highlight the constructive explanations referencing Torricelli's theorem and uniform horizontal displacement ($R = v_{0x} \cdot T$).

### Step 4: Explore Trade-offs in COMPARE (1.5 Minutes)
1. In the top bar, click **⚖ COMPARE**.
2. Click **⚡ LOAD 30° vs 60° COMPLEMENTARY DEMO**.
3. Point out the trajectory overlay:
   - Experiment A ($30^\circ$, Solid Cobalt) and Experiment B ($60^\circ$, Dashed Crimson) are rendered on the **exact same physical coordinate system**.
   - Because scale is isotropic ($px/m$ identical on both axes), there is zero visual distortion.
   - Observe that both trajectories land at the **exact same point** ($55.24\text{ m}$), but $60^\circ$ achieves much higher altitude ($23.89\text{ m}$ vs $7.96\text{ m}$).
4. Review the **Numerical Comparison Table**:
   - Check the delta column: Horizontal Range delta is **"Identical"**.
5. Read the **Physics Insight Card**:
   - Point out the JEE core distinction: Complementary angles ($\theta_A + \theta_B = 90^\circ$) yield identical range **strictly on level ground** ($y_0 = y_{\text{land}}$). If launched from an elevated platform ($y_0 \ne y_{\text{land}}$), $\sin(2\theta)$ symmetry breaks!

### Step 5: Master the Mathematics in UNDERSTAND (1 Minute)
1. In the top bar, click **📐 UNDERSTAND**.
2. Select **Maximum Height ($H_{\text{max}}$)**.
3. Step through the 6 stages using the **NEXT ▶** button:
   - **Step 1: Given Parameters** — Identifies $y_0 = 0\text{ m}$, $v_{0y} = 14.14\text{ m/s}$, $g = 9.81\text{ m/s}^2$.
   - **Step 2: Governing Law** — Establishes Torricelli's equation $v_y^2 = v_{0y}^2 - 2g(y - y_0)$.
   - **Step 3: Physical Boundary Condition** — Explains why vertical velocity $v_y = 0$ at apex.
   - **Step 4: Algebraic Derivation** — Isolates $H_{\text{max}} = y_0 + \frac{v_{0y}^2}{2g}$.
   - **Step 5: Active Value Substitution** — Demonstrates exact numerical calculation yielding $10.19\text{ m}$.
   - **Step 6: Physics Insight & Limiting Cases** — Discusses quadratic sensitivity to vertical velocity.

### Step 6: Grounded AI Teaching Companion in ASK AI (1 Minute)
1. Click **✦ ASK AI**.
2. Click **📸 USE CURRENT EXPERIMENT** (or note the active attached context card).
3. Point out that the prompt context is strictly populated with deterministic values:
   $$v_0 = 20.00\text{ m/s}, \quad \theta = 45.0^\circ, \quad H_{\text{max}} = 10.19\text{ m}, \quad R = 40.77\text{ m}$$
4. Click one of the quick inquiry pills: *"Why does horizontal velocity remain constant?"* or type a question.
5. In the right Inspector, adjust the launch speed slider from $20$ to $30\text{ m/s}$.
6. Show that the attached context card immediately displays:
   **⚠ EXPERIMENT CONTEXT STALE — Simulation parameters modified since attachment.**
7. Click **↺ REFRESH** to re-synchronize the context card with the live simulation.

---

## 6. Known Constraints & Non-Goals

1. **Curriculum Scope**: Chapters 02 through 08 are planned curriculum roadmaps; interactive solvers for non-Kinematics chapters are intentionally deferred to future milestones.
2. **Ideal 2D Mechanics**: Air resistance, Coriolis forces, and variable gravity are deliberately excluded to adhere to Class 11–12 JEE syllabus foundations.
3. **AI Boundaries**: AI is strictly constrained to natural-language problem interpretation and qualitative explanation; the Java physics engine is the sole source of truth for numerical answers.

