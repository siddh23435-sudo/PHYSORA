package edu.physics.simulation;

/**
 * Dynamic behavior classification of a physical body.
 */
public enum BodyType {
    /**
     * Dynamic bodies participate in motion integration, responding to forces and torques.
     * They have finite mass and moment of inertia.
     */
    DYNAMIC,

    /**
     * Static bodies represent immovable environment boundaries (floors, walls, fixed axles).
     * They have zero inverse mass and zero inverse inertia, remaining permanently stationary.
     */
    STATIC
}

