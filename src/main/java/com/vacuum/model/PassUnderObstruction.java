package com.vacuum.model;

/**
 * Pass-under obstruction that vacuum can traverse beneath. Examples: chairs, tables with legs, bed
 * frames Req 3.2, 3.4: Vacuum shall be able to traverse beneath pass-under obstructions
 */
public class PassUnderObstruction extends Obstruction {
    private double legDiameter; // Diameter of legs in inches
    private double spaceBetweenLegs; // Space between legs in inches

    public static final double DEFAULT_LEG_DIAMETER = 2.0; // inches
    public static final double DEFAULT_SPACE_BETWEEN = 24.0; // inches

    public PassUnderObstruction(double x, double y, double width, double height) {
        this(x, y, width, height, DEFAULT_LEG_DIAMETER, DEFAULT_SPACE_BETWEEN);
    }

    public PassUnderObstruction(double x, double y, double width, double height, double legDiameter,
            double spaceBetweenLegs) {
        super(x, y, width, height);
        if (legDiameter <= 0 || spaceBetweenLegs <= 0) {
            throw new IllegalArgumentException("Leg dimensions must be positive");
        }
        this.legDiameter = legDiameter;
        this.spaceBetweenLegs = spaceBetweenLegs;
    }

    @Override
    public boolean isTraversable() {
        return true; // Vacuum can pass underneath
    }

    @Override
    public boolean blocksCleanableArea() {
        return false; // Does not reduce cleanable area
    }

    /**
     * Get area blocked by legs (for detailed collision if needed)
     */
    public double getLegArea() {
        // Simplified: 4 legs, circular footprint
        double legRadiusFeet = (legDiameter / 2.0) / 12.0; // Convert to feet
        return 4 * Math.PI * legRadiusFeet * legRadiusFeet;
    }

    // Getters
    public double getLegDiameter() {
        return legDiameter;
    }

    public double getSpaceBetweenLegs() {
        return spaceBetweenLegs;
    }

    @Override
    public String toString() {
        return String.format(
                "PassUnderObstruction[id=%s, pos=(%.1f,%.1f), size=%.1fx%.1f, legDiam=%.1fin, spacing=%.1fin]",
                getId().substring(0, 8), getX(), getY(), getWidth(), getHeight(), legDiameter,
                spaceBetweenLegs);
    }
}
