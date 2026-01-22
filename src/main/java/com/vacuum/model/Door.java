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
        if (orientation == null) {
            throw new IllegalArgumentException("Door orientation must not be null");
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
        if (room1 == null || room2 == null || orientation == null) {
            return false;
        }

        final double EPS = 0.01;

        // Room 1 edges
        double r1Left = room1.getX();
        double r1Right = room1.getX() + room1.getWidth();
        double r1Top = room1.getY();
        double r1Bottom = room1.getY() + room1.getHeight();

        // Room 2 edges
        double r2Left = room2.getX();
        double r2Right = room2.getX() + room2.getWidth();
        double r2Top = room2.getY();
        double r2Bottom = room2.getY() + room2.getHeight();

        boolean validVerticalDoor = false;

        // Check shared vertical boundary (door spans along Y, wall is vertical at some X)
        double boundaryX;
        if (Math.abs(r1Right - r2Left) < EPS) {
            boundaryX = (r1Right + r2Left) / 2.0;
        } else if (Math.abs(r2Right - r1Left) < EPS) {
            boundaryX = (r2Right + r1Left) / 2.0;
        } else {
            boundaryX = Double.NaN;
        }

        if (!Double.isNaN(boundaryX) && orientation == Orientation.VERTICAL) {
            // Overlapping vertical span between rooms
            double overlapTop = Math.max(r1Top, r2Top);
            double overlapBottom = Math.min(r1Bottom, r2Bottom);
            if (overlapBottom - overlapTop > EPS) {
                boolean onBoundary = Math.abs(this.x - boundaryX) < EPS;
                boolean withinOverlap =
                        (this.y + this.width) <= overlapBottom + EPS && this.y >= overlapTop - EPS;
                validVerticalDoor = onBoundary && withinOverlap;
            }
        }

        boolean validHorizontalDoor = false;

        // Check shared horizontal boundary (door spans along X, wall is horizontal at some Y)
        double boundaryY;
        if (Math.abs(r1Bottom - r2Top) < EPS) {
            boundaryY = (r1Bottom + r2Top) / 2.0;
        } else if (Math.abs(r2Bottom - r1Top) < EPS) {
            boundaryY = (r2Bottom + r1Top) / 2.0;
        } else {
            boundaryY = Double.NaN;
        }

        if (!Double.isNaN(boundaryY) && orientation == Orientation.HORIZONTAL) {
            // Overlapping horizontal span between rooms
            double overlapLeft = Math.max(r1Left, r2Left);
            double overlapRight = Math.min(r1Right, r2Right);
            if (overlapRight - overlapLeft > EPS) {
                boolean onBoundary = Math.abs(this.y - boundaryY) < EPS;
                boolean withinOverlap =
                        (this.x + this.width) <= overlapRight + EPS && this.x >= overlapLeft - EPS;
                validHorizontalDoor = onBoundary && withinOverlap;
            }
        }

        return validVerticalDoor || validHorizontalDoor;
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
