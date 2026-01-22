package com.vacuum.model;

/**
 * Base class for obstructions in the house. Obstructions affect vacuum movement and cleanable area.
 */
public abstract class Obstruction {
    private final String id;
    private double x; // Position X (feet)
    private double y; // Position Y (feet)
    private double width; // Width in feet
    private double height; // Height in feet

    protected Obstruction(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Obstruction dimensions must be positive");
        }
        this.id = java.util.UUID.randomUUID().toString();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Check if vacuum can traverse this obstruction
     */
    public abstract boolean isTraversable();

    /**
     * Check if this obstruction blocks cleaning (reduces cleanable area)
     */
    public abstract boolean blocksCleanableArea();

    /**
     * Get the area footprint of this obstruction
     */
    public double getArea() {
        return width * height;
    }

    /**
     * Check if a point is within this obstruction's bounds
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Check if this obstruction intersects with a rectangular area
     */
    public boolean intersects(double rx, double ry, double rwidth, double rheight) {
        return !(this.x + this.width <= rx || rx + rwidth <= this.x || this.y + this.height <= ry
                || ry + rheight <= this.y);
    }

    // Getters
    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    // Setters for repositioning
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, pos=(%.1f,%.1f), size=%.1fx%.1f, area=%.1f ft²]",
                getClass().getSimpleName(), id.substring(0, 8), x, y, width, height, getArea());
    }
}
