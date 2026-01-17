package com.vacuum.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a rectangular room in the house. Rooms must be at least 4 square feet and have at
 * least one door.
 */
public class Room {
    private final String id;
    private double x; // Bottom-left corner X
    private double y; // Bottom-left corner Y
    private double width; // In feet
    private double height; // In feet
    private List<Door> doors;

    public static final double MIN_AREA = 4.0; // square feet

    public Room(double x, double y, double width, double height) {
        this.id = UUID.randomUUID().toString();
        this.doors = new ArrayList<>();
        setDimensions(x, y, width, height);
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
                    "Room area (%.2f ft²) is below minimum (%.2f ft²)", width * height, MIN_AREA));
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
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Check if this room intersects with another room
     */
    public boolean intersects(Room other) {
        return !(this.x + this.width <= other.x || other.x + other.width <= this.x
                || this.y + this.height <= other.y || other.y + other.height <= this.y);
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

    public List<Door> getDoors() {
        return new ArrayList<>(doors);
    }

    @Override
    public String toString() {
        return String.format(
                "Room[id=%s, pos=(%.1f,%.1f), size=%.1fx%.1f, area=%.1f ft², doors=%d]",
                id.substring(0, 8), x, y, width, height, getArea(), doors.size());
    }
}
