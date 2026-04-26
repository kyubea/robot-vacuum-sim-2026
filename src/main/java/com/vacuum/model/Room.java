package com.vacuum.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.vacuum.model.AdjoinResult.Side;

/**
 * Represents a rectangular room in the house. Rooms must be at least 4 square feet and have at
 * least one door.
 */
public class Room {
    private final String id;
    private String name;
    private double x; // Bottom-left corner X
    private double y; // Bottom-left corner Y
    private double width; // In feet
    private double height; // In feet
    private List<Door> doors;

    public static final double MIN_AREA = 4.0; // square feet

    public Room(double x, double y, double width, double height, String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.doors = new ArrayList<>();
        setDimensions(x, y, width, height);
    }

    public Room(double x, double y, double width, double height) {
        this(x, y, width, height, "Room");
    }

    /**
     * Set room dimensions with validation
     */
    public void setDimensions(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Room dimensions must be positive");
        }
        if (width * height < MIN_AREA) {
            throw new IllegalArgumentException(String.format(
                    "Room area (%.2f m²) is below minimum (%.2f m²)", width * height, MIN_AREA));
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Add a door to this room
     */
    public void addDoor(Door door) {
        if (!doors.contains(door)) {
            doors.add(door);
        }
    }

    /**
     * Remove a door from this room
     */
    public void removeDoor(Door door) {
        doors.remove(door);
    }

    /**
     * Check if room satisfies connectivity requirement (Req 2.4)
     */
    public boolean hasValidConnectivity() {
        return !doors.isEmpty();
    }

    /**
     * Get the area of this room in square feet
     */
    public double getArea() {
        return width * height;
    }

    /**
     * Check if a point is inside this room
     */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Check if this room intersects with another room (improved edition) This version allows the
     * adjacency of walls to not count as part of the overlapping interior. This difference is
     * important to room placement.
     */
    boolean intersects(Room other) {
        // Returns true only if interiors overlap (edge touching ≠ intersection)
        if (getMaxX() <= other.x || other.getMaxX() <= x)
            return false;
        if (getMaxY() <= other.y || other.getMaxY() <= y)
            return false;

        boolean x_overlap = Math.max(x, other.x) < Math.min(getMaxX(), other.getMaxX());
        boolean y_overlap = Math.max(y, other.y) < Math.min(getMaxY(), other.getMaxY());
        return x_overlap && y_overlap;
    }

    AdjoinResult adjoins(Room other) {
        // Quick reject
        if (getMaxX() < other.x || other.getMaxX() < x || getMaxY() < other.y
                || other.getMaxY() < y) {
            return null;
        }

        final double EPS = 1e-6;

        // Right of this → Left of other
        if (Math.abs((x + width) - other.x) < EPS) {
            double o_min = Math.max(y, other.y);
            double o_max = Math.min(getMaxY(), other.getMaxY());
            if (o_max - o_min > EPS)
                return new AdjoinResult(Side.RIGHT, o_min, o_max);
        }

        // Left of this → Right of other
        if (Math.abs(x - other.getMaxX()) < EPS) {
            double o_min = Math.max(y, other.y);
            double o_max = Math.min(getMaxY(), other.getMaxY());
            if (o_max - o_min > EPS)
                return new AdjoinResult(Side.LEFT, o_min, o_max);
        }

        // Top of this → Bottom of other
        if (Math.abs(getMaxY() - other.y) < EPS) {
            double o_min = Math.max(x, other.x);
            double o_max = Math.min(getMaxX(), other.getMaxX());
            if (o_max - o_min > EPS)
                return new AdjoinResult(Side.TOP, o_min, o_max);
        }

        // Bottom of this → Top of other
        if (Math.abs(y - getMaxY()) < EPS) {
            double o_min = Math.max(x, other.x);
            double o_max = Math.min(getMaxX(), other.getMaxX());
            if (o_max - o_min > EPS)
                return new AdjoinResult(Side.BOTTOM, o_min, o_max);
        }

        return null;
    }


    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public double getMaxX() {
        return x + width;
    }

    public double getMaxY() {
        return y + height;
    }

    public List<Door> getDoors() {
        return new ArrayList<>(doors);
    }

    @Override
    public String toString() {
        return String.format(
                "Room[id=%s, name=%s, pos=(%.1f,%.1f), size=%.1fx%.1f, area=%.1f m², doors=%d]",
                id.substring(0, 8), name, x, y, width, height, getArea(), doors.size());
    }
}
