package edu.physics.simulation.scene;

import edu.physics.simulation.BodyType;
import edu.physics.simulation.PhysicalBodySpec;
import edu.physics.simulation.RigidBody;
import edu.physics.simulation.RigidBodyState;
import edu.physics.simulation.SimulationWorld;
import edu.physics.simulation.Vector2D;
import edu.physics.simulation.geometry.CircleShape;
import edu.physics.simulation.geometry.GeometricShape;
import edu.physics.simulation.geometry.PointMassShape;
import edu.physics.simulation.geometry.PolygonShape;
import edu.physics.simulation.geometry.RectangleShape;
import edu.physics.simulation.geometry.ShapeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LaboratoryScene - Geometry, Shapes, and Scene Definitions")
class LaboratorySceneTest {

    private static final double TOL = 1e-12;

    @Test
    @DisplayName("Builds valid Atwood Machine scene with 1 anchor, 1 disc, 2 blocks, and cable topology")
    void buildAtwoodMachineScene() {
        UUID discId = UUID.randomUUID();
        UUID block1Id = UUID.randomUUID();
        UUID block2Id = UUID.randomUUID();

        SceneAnchor pulleyAxle = SceneAnchor.of("axle_1", "Pulley Axle", new Vector2D(0.0, 3.0));

        SceneBody disc = SceneBody.of(
                discId,
                "Pulley Disc",
                PhysicalBodySpec.createWithShape(discId, "Pulley Disc", BodyType.DYNAMIC, 1.0, new CircleShape(0.15)),
                new CircleShape(0.15),
                InitialPose.atRest(new Vector2D(0.0, 3.0)),
                List.of(AttachmentPoint.of("center", Vector2D.ZERO)));

        SceneBody block1 = SceneBody.of(
                block1Id,
                "Block 1",
                PhysicalBodySpec.createWithShape(block1Id, "Block 1", BodyType.DYNAMIC, 3.0, new RectangleShape(0.2, 0.25)),
                new RectangleShape(0.2, 0.25),
                InitialPose.atRest(new Vector2D(-0.15, 1.5)),
                List.of(AttachmentPoint.of("top", new Vector2D(0.0, 0.125))));

        SceneBody block2 = SceneBody.of(
                block2Id,
                "Block 2",
                PhysicalBodySpec.createWithShape(block2Id, "Block 2", BodyType.DYNAMIC, 5.0, new RectangleShape(0.2, 0.3)),
                new RectangleShape(0.2, 0.3),
                InitialPose.atRest(new Vector2D(0.15, 0.8)),
                List.of(AttachmentPoint.of("top", new Vector2D(0.0, 0.15))));

        RevoluteJointTopology pin = RevoluteJointTopology.of("pin_1", discId, AnchorRef.anchor("axle_1"));
        CableTopology cable = CableTopology.of(
                "cable_1",
                AnchorRef.body(block1Id, "top"),
                AnchorRef.body(block2Id, "top"),
                List.of(AnchorRef.anchor("axle_1")),
                3.8);

        LaboratoryScene scene = LaboratoryScene.builder("atwood_lab", "Atwood Machine Demonstration")
                .gravity(new Vector2D(0.0, -9.81))
                .addAnchor(pulleyAxle)
                .addBody(disc)
                .addBody(block1)
                .addBody(block2)
                .addRelationship(pin)
                .addRelationship(cable)
                .build();

        SceneValidationResult validation = scene.validate();
        assertTrue(validation.isValid(), "Scene must be valid: " + validation.errorMessages());
        assertEquals(0, validation.errors().size());

        // Instantiate live simulation world
        SimulationWorld world = scene.buildSimulationWorld();
        assertNotNull(world);
        assertEquals(3, world.bodyCount());
        assertEquals(new Vector2D(0.0, -9.81), world.gravity().acceleration());

        // Verify bodies were instantiated with exact scene poses
        assertTrue(world.getBody(discId).isPresent());
        assertTrue(world.getBody(block1Id).isPresent());
        assertTrue(world.getBody(block2Id).isPresent());

        assertEquals(new Vector2D(0.0, 3.0), world.getBody(discId).get().position());
        assertEquals(new Vector2D(-0.15, 1.5), world.getBody(block1Id).get().position());
        assertEquals(new Vector2D(0.15, 0.8), world.getBody(block2Id).get().position());
    }

    @Test
    @DisplayName("Fail-fast duplicate identity detection on bodies, anchors, surfaces, and relationships")
    void failFastDuplicateIdentityDetection() {
        LaboratoryScene.Builder builder = LaboratoryScene.builder("dup_test", "Duplicate Test");
        UUID id = UUID.randomUUID();
        SceneBody body = SceneBody.of(
                id, "b1",
                PhysicalBodySpec.createBlock(id, "b1", 1.0, 1.0, 1.0),
                new RectangleShape(1.0, 1.0),
                InitialPose.atRest(Vector2D.ZERO));

        builder.addBody(body);
        assertThrows(IllegalArgumentException.class, () -> builder.addBody(body),
                "Builder must immediately reject duplicate body UUID");

        SceneAnchor anchor = SceneAnchor.of("a1", Vector2D.ZERO);
        builder.addAnchor(anchor);
        assertThrows(IllegalArgumentException.class, () -> builder.addAnchor(anchor),
                "Builder must immediately reject duplicate anchor ID");

        StaticSurface surface = StaticSurface.horizontalFloor("s1", "Floor", 0.0, 10.0, 0.0, 0.3, 0.0);
        builder.addSurface(surface);
        assertThrows(IllegalArgumentException.class, () -> builder.addSurface(surface),
                "Builder must immediately reject duplicate surface ID");

        SurfaceContactTopology rel = SurfaceContactTopology.of("r1", id, "s1");
        builder.addRelationship(rel);
        assertThrows(IllegalArgumentException.class, () -> builder.addRelationship(rel),
                "Builder must immediately reject duplicate relationship ID");
    }

    @Test
    @DisplayName("Structured diagnostics catch missing anchors, attachments, surfaces, and invalid cable lengths")
    void structuredDiagnosticsOnInvalidTopology() {
        UUID validBodyId = UUID.randomUUID();
        UUID missingBodyId = UUID.randomUUID();

        SceneBody body = SceneBody.of(
                validBodyId, "b1",
                PhysicalBodySpec.createBlock(validBodyId, "b1", 2.0, 1.0, 1.0),
                new RectangleShape(1.0, 1.0),
                InitialPose.atRest(Vector2D.ZERO),
                List.of(AttachmentPoint.of("p1", Vector2D.ZERO)));

        // 1. Missing attachment on existing body
        CableTopology badCableAttach = CableTopology.of(
                "c_bad1",
                AnchorRef.body(validBodyId, "non_existent_point"),
                AnchorRef.body(validBodyId, "p1"),
                5.0);

        // 2. Missing body
        CableTopology badCableBody = CableTopology.of(
                "c_bad2",
                AnchorRef.body(missingBodyId, "p1"),
                AnchorRef.body(validBodyId, "p1"),
                5.0);

        // 3. Cable with non-positive length (strictly rejected in Stage 3B)
        CableTopology zeroLenCable = CableTopology.of(
                "c_bad3",
                AnchorRef.body(validBodyId, "p1"),
                AnchorRef.body(validBodyId, "p1"),
                0.0);

        // 4. Missing static surface
        SurfaceContactTopology badSurfaceRel = SurfaceContactTopology.of(
                "sc_bad", validBodyId, "non_existent_surface");

        LaboratoryScene scene = LaboratoryScene.builder("broken_scene", "Broken Scene")
                .addBody(body)
                .addRelationship(badCableAttach)
                .addRelationship(badCableBody)
                .addRelationship(zeroLenCable)
                .addRelationship(badSurfaceRel)
                .build();

        SceneValidationResult validation = scene.validate();
        assertFalse(validation.isValid());
        assertTrue(validation.errors().size() >= 4);

        // Verify diagnostic codes
        List<SceneDiagnostic.DiagnosticCode> codes = validation.errors().stream()
                .map(SceneDiagnostic::code)
                .toList();
        assertTrue(codes.contains(SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR));
        assertTrue(codes.contains(SceneDiagnostic.DiagnosticCode.INVALID_CABLE_LENGTH));
        assertTrue(codes.contains(SceneDiagnostic.DiagnosticCode.MISSING_SURFACE));
    }

    @Test
    @DisplayName("Invalid scene build throws IllegalStateException and never returns a partially constructed world")
    void invalidSceneBuildProducesNoWorld() {
        LaboratoryScene scene = LaboratoryScene.builder("invalid", "Invalid")
                .addRelationship(SurfaceContactTopology.of("sc", UUID.randomUUID(), "surf"))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, scene::buildSimulationWorld);
        assertTrue(ex.getMessage().contains("Cannot build SimulationWorld from invalid LaboratoryScene"));
    }

    @Test
    @DisplayName("StaticSurface inclined ramp contact side convention handles both endpoint directions correctly")
    void staticSurfaceRampOrientationBothDirections() {
        Vector2D bottom = new Vector2D(0.0, 0.0);
        Vector2D top = new Vector2D(4.0, 3.0);

        // Bottom to top: direction (4, 3)
        StaticSurface rampUp = StaticSurface.inclinedRamp("ramp_up", "Ramp Up", bottom, top,
                StaticSurface.RampContactSide.UPPER, 0.3, 0.0);

        // Top to bottom: direction (-4, -3)
        StaticSurface rampDown = StaticSurface.inclinedRamp("ramp_down", "Ramp Down", top, bottom,
                StaticSurface.RampContactSide.UPPER, 0.3, 0.0);

        // Both must have normal pointing upward (ny > 0): normal = (-0.6, 0.8)
        assertEquals(-0.6, rampUp.outwardNormal().x(), TOL);
        assertEquals(0.8, rampUp.outwardNormal().y(), TOL);
        assertTrue(rampUp.outwardNormal().y() > 0.0);

        assertEquals(-0.6, rampDown.outwardNormal().x(), TOL);
        assertEquals(0.8, rampDown.outwardNormal().y(), TOL);
        assertTrue(rampDown.outwardNormal().y() > 0.0);

        // Lower contact side test (normal pointing downward into ramp bottom)
        StaticSurface rampLower = StaticSurface.inclinedRamp("ramp_lower", "Ramp Lower", bottom, top,
                StaticSurface.RampContactSide.LOWER, 0.3, 0.0);
        assertEquals(0.6, rampLower.outwardNormal().x(), TOL);
        assertEquals(-0.8, rampLower.outwardNormal().y(), TOL);
        assertTrue(rampLower.outwardNormal().y() < 0.0);
    }

    @Test
    @DisplayName("StaticSurface validates distinct endpoints, perpendicularity, friction, and restitution")
    void staticSurfaceValidation() {
        // 1. Zero-length endpoints
        assertThrows(IllegalArgumentException.class, () -> new StaticSurface(
                "s", "l", new Vector2D(1.0, 1.0), new Vector2D(1.0, 1.0), Vector2D.UNIT_Y, 0.3, 0.0));

        // 2. Non-perpendicular normal: segment (0,0) to (4,0) has direction (4,0). Normal (0.5, 0.866) has dot = 2.0 != 0
        assertThrows(IllegalArgumentException.class, () -> new StaticSurface(
                "s", "l", new Vector2D(0.0, 0.0), new Vector2D(4.0, 0.0), new Vector2D(0.5, Math.sqrt(0.75)), 0.3, 0.0));

        // 3. Negative friction
        assertThrows(IllegalArgumentException.class, () -> StaticSurface.horizontalFloor(
                "s", "l", 0.0, 5.0, 0.0, -0.1, 0.0));

        // 4. Restitution > 1.0
        assertThrows(IllegalArgumentException.class, () -> StaticSurface.horizontalFloor(
                "s", "l", 0.0, 5.0, 0.0, 0.3, 1.2));
    }

    @Test
    @DisplayName("PhysicalBodySpec.createWithShape preserves explicit inertia override for non-uniform mass")
    void explicitInertiaOverridePreserved() {
        UUID id = UUID.randomUUID();
        double mass = 2.0;
        double radius = 0.5;
        CircleShape discShape = new CircleShape(radius);

        // Uniform solid disc inertia would be 1/2 * m * r² = 0.5 * 2.0 * 0.25 = 0.25 kg·m²
        assertEquals(0.25, discShape.computeMomentOfInertia(mass), TOL);

        // Thin cylindrical shell / ring: I = m * r² = 2.0 * 0.25 = 0.50 kg·m²
        double explicitInertia = 0.50;
        PhysicalBodySpec spec = PhysicalBodySpec.createWithShape(
                id, "Hollow Cylinder", BodyType.DYNAMIC, mass, discShape, explicitInertia);

        assertEquals(explicitInertia, spec.momentOfInertia(), TOL,
                "Explicit inertia must take strict precedence over uniform-density calculation");
        assertEquals(1.0 / explicitInertia, spec.inverseInertia(), TOL);
        assertEquals(mass, spec.mass(), TOL);
    }

    @Test
    @DisplayName("Deterministic scene reconstruction produces bitwise identical initial conditions")
    void deterministicSceneReconstruction() {
        UUID b1Id = UUID.randomUUID();
        SceneBody body = SceneBody.of(
                b1Id, "b1",
                PhysicalBodySpec.createBlock(b1Id, "b1", 3.0, 1.0, 1.0),
                new RectangleShape(1.0, 1.0),
                InitialPose.of(new Vector2D(2.0, 5.0), new Vector2D(10.0, -4.0)));

        LaboratoryScene scene = LaboratoryScene.builder("det_scene", "Deterministic Reconstruction")
                .addBody(body)
                .build();

        SimulationWorld world1 = scene.buildSimulationWorld();
        SimulationWorld world2 = scene.buildSimulationWorld();

        assertEquals(world1.getBody(b1Id).get().position(), world2.getBody(b1Id).get().position());
        assertEquals(world1.getBody(b1Id).get().velocity(), world2.getBody(b1Id).get().velocity());
        assertEquals(world1.getBody(b1Id).get().mass(), world2.getBody(b1Id).get().mass(), TOL);
    }

    @Test
    @DisplayName("Option B: Runtime RigidBody retains exact GeometricShape instance from LaboratoryScene")
    void runtimeBodyRetainsAuthoritativeGeometricShape() {
        UUID discId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        CircleShape discShape = new CircleShape(0.25);
        RectangleShape blockShape = new RectangleShape(0.6, 0.4);

        SceneBody disc = SceneBody.of(
                discId, "Disc",
                PhysicalBodySpec.createWithShape(discId, "Disc", BodyType.DYNAMIC, 2.0, discShape),
                discShape,
                InitialPose.atRest(new Vector2D(0.0, 5.0)));

        SceneBody block = SceneBody.of(
                blockId, "Block",
                PhysicalBodySpec.createWithShape(blockId, "Block", BodyType.DYNAMIC, 4.0, blockShape),
                blockShape,
                InitialPose.atRest(new Vector2D(2.0, 0.0)));

        LaboratoryScene scene = LaboratoryScene.builder("geom_test", "Geometry Test")
                .addBody(disc)
                .addBody(block)
                .build();

        SimulationWorld world = scene.buildSimulationWorld();

        RigidBody runtimeDisc = world.getBody(discId).orElseThrow();
        RigidBody runtimeBlock = world.getBody(blockId).orElseThrow();

        // Must be the EXACT instance from the scene, not a reconstructed or default shape
        assertSame(discShape, runtimeDisc.shape(), "Runtime disc shape must be identical to SceneBody shape");
        assertSame(blockShape, runtimeBlock.shape(), "Runtime block shape must be identical to SceneBody shape");
        assertEquals(ShapeType.CIRCLE, runtimeDisc.shape().shapeType());
        assertEquals(ShapeType.RECTANGLE, runtimeBlock.shape().shapeType());
    }

    @Test
    @DisplayName("Option B: Polygon vertices and outward edge normals are directly accessible at runtime")
    void polygonVerticesAndNormalsAccessibleAtRuntime() {
        UUID wedgeId = UUID.randomUUID();
        PolygonShape triangle = PolygonShape.createRightTriangle(3.0, 4.0);

        SceneBody wedge = SceneBody.of(
                wedgeId, "Wedge",
                PhysicalBodySpec.createWithShape(wedgeId, "Wedge", BodyType.DYNAMIC, 6.0, triangle),
                triangle,
                InitialPose.atRest(Vector2D.ZERO));

        LaboratoryScene scene = LaboratoryScene.builder("poly_scene", "Polygon Scene")
                .addBody(wedge)
                .build();

        SimulationWorld world = scene.buildSimulationWorld();
        RigidBody runtimeWedge = world.getBody(wedgeId).orElseThrow();

        assertInstanceOf(PolygonShape.class, runtimeWedge.shape(), "Shape must be an instance of PolygonShape");
        PolygonShape runtimePoly = (PolygonShape) runtimeWedge.shape();
        assertSame(triangle, runtimePoly);

        // Verify polygon vertices and normals can be inspected directly at runtime
        List<Vector2D> vertices = runtimePoly.centeredVertices();
        List<Vector2D> normals = runtimePoly.localNormals();
        assertEquals(3, vertices.size());
        assertEquals(3, normals.size());
        assertEquals(6.0, runtimePoly.area(), TOL);

        // Verify edge normals are perpendicular to centered edges and point outward
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D v1 = vertices.get(i);
            Vector2D v2 = vertices.get((i + 1) % vertices.size());
            Vector2D edge = v2.subtract(v1);
            Vector2D n = normals.get(i);
            assertEquals(0.0, edge.dot(n), 1e-10, "Normal must be perpendicular to edge");
            assertEquals(1.0, n.magnitude(), 1e-10, "Normal must be unit length");
        }
    }

    @Test
    @DisplayName("All RigidBody static factories assign expected GeometricShape with input validation")
    void allRigidBodyFactoriesPreserveExpectedShape() {
        RigidBody block = RigidBody.dynamicBlock("b", 5.0, 1.2, 0.8, Vector2D.ZERO);
        assertInstanceOf(RectangleShape.class, block.shape());
        RectangleShape rect = (RectangleShape) block.shape();
        assertEquals(1.2, rect.width(), TOL);
        assertEquals(0.8, rect.height(), TOL);

        RigidBody disc = RigidBody.dynamicDisc("d", 2.0, 0.35, Vector2D.ZERO);
        assertInstanceOf(CircleShape.class, disc.shape());
        CircleShape circle = (CircleShape) disc.shape();
        assertEquals(0.35, circle.radius(), TOL);

        RigidBody pt = RigidBody.pointMass("p", 1.5, Vector2D.ZERO, Vector2D.ZERO);
        assertSame(PointMassShape.INSTANCE, pt.shape());

        RigidBody staticBox = RigidBody.staticBox("sb", 4.0, 2.0, Vector2D.ZERO);
        assertInstanceOf(RectangleShape.class, staticBox.shape());

        RigidBody anchorDisc = RigidBody.staticAnchor("sa_circle", 0.5, Vector2D.ZERO);
        assertInstanceOf(CircleShape.class, anchorDisc.shape());
        assertEquals(0.5, ((CircleShape) anchorDisc.shape()).radius(), TOL);

        // radius == 0.0 yields PointMassShape
        RigidBody anchorPt = RigidBody.staticAnchor("sa_point", 0.0, Vector2D.ZERO);
        assertSame(PointMassShape.INSTANCE, anchorPt.shape());

        // Negative or non-finite radius must be rejected per safeguard 2
        assertThrows(IllegalArgumentException.class, () -> RigidBody.staticAnchor("bad1", -0.1, Vector2D.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RigidBody.staticAnchor("bad2", Double.NaN, Vector2D.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RigidBody.staticAnchor("bad3", Double.NEGATIVE_INFINITY, Vector2D.ZERO));
    }

    @Test
    @DisplayName("Legacy two-argument constructor derives expected shapes with positive-radius precedence")
    void legacyTwoArgumentConstructorRemainsCompatible() {
        RigidBodyState state = RigidBodyState.atRest(Vector2D.ZERO);

        // Disc spec: radius = 0.5, width = 1.0, height = 1.0 -> CircleShape precedence
        PhysicalBodySpec discSpec = PhysicalBodySpec.createDisc("disc", 2.0, 0.5);
        RigidBody discBody = new RigidBody(discSpec, state);
        assertInstanceOf(CircleShape.class, discBody.shape());
        assertEquals(0.5, ((CircleShape) discBody.shape()).radius(), TOL);

        // Block spec: radius = 0.0, width = 2.0, height = 1.5 -> RectangleShape
        PhysicalBodySpec blockSpec = PhysicalBodySpec.createBlock("block", 3.0, 2.0, 1.5);
        RigidBody blockBody = new RigidBody(blockSpec, state);
        assertInstanceOf(RectangleShape.class, blockBody.shape());
        RectangleShape rect = (RectangleShape) blockBody.shape();
        assertEquals(2.0, rect.width(), TOL);
        assertEquals(1.5, rect.height(), TOL);

        // Point mass spec: dimensions = 0 -> PointMassShape.INSTANCE
        PhysicalBodySpec ptSpec = PhysicalBodySpec.createPointMass("pt", 1.0);
        RigidBody ptBody = new RigidBody(ptSpec, state);
        assertSame(PointMassShape.INSTANCE, ptBody.shape());

        // Static box spec -> RectangleShape
        PhysicalBodySpec boxSpec = PhysicalBodySpec.createStaticBox("box", 3.0, 1.0);
        RigidBody boxBody = new RigidBody(boxSpec, state);
        assertInstanceOf(RectangleShape.class, boxBody.shape());

        // Static anchor spec with radius > 0 -> CircleShape precedence
        PhysicalBodySpec anchorSpec = PhysicalBodySpec.createStaticAnchor("anchor", 0.4);
        RigidBody anchorBody = new RigidBody(anchorSpec, state);
        assertInstanceOf(CircleShape.class, anchorBody.shape());
        assertEquals(0.4, ((CircleShape) anchorBody.shape()).radius(), TOL);

        // Static anchor spec with radius == 0 -> PointMassShape
        PhysicalBodySpec zeroAnchorSpec = PhysicalBodySpec.createStaticAnchor("anchor_zero", 0.0);
        RigidBody zeroAnchorBody = new RigidBody(zeroAnchorSpec, state);
        assertSame(PointMassShape.INSTANCE, zeroAnchorBody.shape());
    }

    @Test
    @DisplayName("Runtime simulation motion updates kinematic state without mutating shape geometry")
    void runtimeMotionDoesNotMutateSharedShape() {
        UUID id = UUID.randomUUID();
        RectangleShape rectShape = new RectangleShape(1.0, 2.0);
        SceneBody body = SceneBody.of(
                id, "Moving Block",
                PhysicalBodySpec.createWithShape(id, "Moving Block", BodyType.DYNAMIC, 5.0, rectShape),
                rectShape,
                InitialPose.of(new Vector2D(0.0, 10.0), new Vector2D(5.0, 0.0)));

        LaboratoryScene scene = LaboratoryScene.builder("motion_scene", "Motion")
                .gravity(new Vector2D(0.0, -9.81))
                .addBody(body)
                .build();

        SimulationWorld world = scene.buildSimulationWorld();
        RigidBody runtimeBody = world.getBody(id).orElseThrow();

        Vector2D initialPos = runtimeBody.position();
        GeometricShape initialShapeRef = runtimeBody.shape();

        // Run 200 simulation steps
        for (int i = 0; i < 200; i++) {
            world.stepSingle();
        }

        // Kinematic position must have moved
        assertNotEquals(initialPos, runtimeBody.position());
        assertTrue(runtimeBody.position().y() < 10.0);

        // Shape reference and local intrinsic properties must remain completely untouched
        assertSame(initialShapeRef, runtimeBody.shape());
        assertSame(rectShape, runtimeBody.shape());
        assertEquals(2.0, runtimeBody.shape().area(), TOL);
        assertEquals(Vector2D.ZERO, runtimeBody.shape().centroid());
        assertEquals(1.0, ((RectangleShape) runtimeBody.shape()).width(), TOL);
        assertEquals(2.0, ((RectangleShape) runtimeBody.shape()).height(), TOL);
    }

    @Test
    @DisplayName("SimulationWorld.reset restores kinematic state while preserving shape reference")
    void resetPreservesShapeWithoutReplacement() {
        UUID id = UUID.randomUUID();
        CircleShape discShape = new CircleShape(0.3);
        SceneBody body = SceneBody.of(
                id, "Spinning Disc",
                PhysicalBodySpec.createWithShape(id, "Spinning Disc", BodyType.DYNAMIC, 2.0, discShape),
                discShape,
                new InitialPose(new Vector2D(1.0, 2.0), 0.5, new Vector2D(3.0, 4.0), 2.0));

        LaboratoryScene scene = LaboratoryScene.builder("reset_scene", "Reset")
                .addBody(body)
                .build();

        SimulationWorld world = scene.buildSimulationWorld();
        RigidBody runtimeBody = world.getBody(id).orElseThrow();

        // Step simulation
        for (int i = 0; i < 50; i++) {
            world.stepSingle();
        }

        // Reset world
        world.reset();

        assertEquals(new Vector2D(1.0, 2.0), runtimeBody.position());
        assertEquals(new Vector2D(3.0, 4.0), runtimeBody.velocity());
        assertEquals(0.5, runtimeBody.angle(), TOL);
        assertEquals(2.0, runtimeBody.angularVelocity(), TOL);

        // Exact shape reference is preserved
        assertSame(discShape, runtimeBody.shape());
    }

    @Test
    @DisplayName("Multiple SimulationWorld instances instantiated from one scene share the same immutable shape reference")
    void reconstructingTwoWorldsPreservesSceneBlueprintIdentity() {
        UUID id = UUID.randomUUID();
        RectangleShape shape = new RectangleShape(0.8, 0.4);
        SceneBody body = SceneBody.of(
                id, "Block",
                PhysicalBodySpec.createWithShape(id, "Block", BodyType.DYNAMIC, 3.0, shape),
                shape,
                InitialPose.atRest(Vector2D.ZERO));

        LaboratoryScene scene = LaboratoryScene.builder("multi_world", "Multi World")
                .addBody(body)
                .build();

        SimulationWorld world1 = scene.buildSimulationWorld();
        SimulationWorld world2 = scene.buildSimulationWorld();

        GeometricShape shape1 = world1.getBody(id).orElseThrow().shape();
        GeometricShape shape2 = world2.getBody(id).orElseThrow().shape();

        assertSame(shape, shape1, "World 1 must share the scene's exact shape instance");
        assertSame(shape, shape2, "World 2 must share the scene's exact shape instance");
        assertSame(shape1, shape2, "Both runtime worlds must reference the exact same immutable geometry blueprint");
    }
}

