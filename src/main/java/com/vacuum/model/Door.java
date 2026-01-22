package com.vacuum.model;

/**
 * Represents a door connection between two rooms. Doors allow the vacuum to traverse between rooms.
 */

public class Door {
    private final String id;
    private Room room1;
    private Room room2;
    private double x; // Door position X
    private double y; // Door position Y
    private double width; // Door width in feet
    private Orientation orientation;

    public enum Orientation {
        HORIZONTAL, // Door on horizontal wall (top/bottom)
        VERTICAL // Door on vertical wall (left/right)
    }

    public static final double DEFAULT_WIDTH = 3.0; // feet

    public Door(Room room1, Room room2, double x, double y, Orientation orientation) {
        this(room1, room2, x, y, orientation, DEFAULT_WIDTH);
    }

    public Door(Room room1, Room room2, double x, double y, Orientation orientation, double width) {
        if (room1 == null || room2 == null) {
            throw new IllegalArgumentException("Door must connect two rooms");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("Door width must be positive");
        }

        this.id = java.util.UUID.randomUUID().toString();
        this.room1 = room1;
        this.room2 = room2;
        this.x = x;
        this.y = y;
        this.width = width;
        this.orientation = orientation;

        // Register door with both rooms
        room1.addDoor(this);
        room2.addDoor(this);
    }

    /**
     * Check if this door connects the given room
     */
    public boolean connects(Room room) {
        return room1.equals(room) || room2.equals(room);
    }

    /**
     * Get the other room connected by this door
     */
    public Room getOtherRoom(Room room) {
        if (room.equals(room1))
            return room2;
        if (room.equals(room2))
            return room1;
        return null;
    }

    /**
     * Check if door position is valid (on shared wall between rooms)
     */
    public boolean isValidPosition() {
        // Check if door is on a shared boundary between the two rooms
        boolean sharesVerticalBoundary =
                (Math.abs(room1.getX() + room1.getWidth() - room2.getX()) < 0.01)
                        || (Math.abs(room2.getX() + room2.getWidth() - room1.getX()) < 0.01);

        boolean sharesHorizontalBoundary =
                (Math.abs(room1.getY() + room1.getHeight() - room2.getY()) < 0.01)
                        || (Math.abs(room2.getY() + room2.getHeight() - room1.getY()) < 0.01);

        return sharesVerticalBoundary || sharesHorizontalBoundary;
    }

    // Getters
    public String getId() {
        return id;
    }

    public Room getRoom1() {
        return room1;
    }

    public Room getRoom2() {
        return room2;
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

    public Orientation getOrientation() {
        return orientation;
    }

    @Override
    public String toString() {
        return String.format("Door[id=%s, pos=(%.1f,%.1f), width=%.1f, %s]", id.substring(0, 8), x,
                y, width, orientation);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Door door = (Door) obj;
        return id.equals(door.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
