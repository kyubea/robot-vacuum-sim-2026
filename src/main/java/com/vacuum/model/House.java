package com.vacuum.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the house containing rooms and doors. Houses must be between 200-8000 square feet
 * total.
 */
public class House {
    private List<Room> rooms;
    private List<Door> doors;
    private List<Obstruction> obstructions;
    private FloorCovering floorCovering;

    public static final double MIN_TOTAL_AREA = 200.0; // square feet
    public static final double MAX_TOTAL_AREA = 8000.0; // square feet

    public enum FloorCovering {
        HARD("Hard (wood, laminate, tile)", 0.90), LOOP_PILE("Loop Pile (Berber)",
                0.75), CUT_PILE("Cut Pile", 0.70), FRIEZE("Frieze-cut (California shag)", 0.65);

        private final String displayName;
        private final double defaultEfficiency;

        FloorCovering(String displayName, double defaultEfficiency) {
            this.displayName = displayName;
            this.defaultEfficiency = defaultEfficiency;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getDefaultEfficiency() {
            return defaultEfficiency;
        }
    }

    public House() {
        this.rooms = new ArrayList<>();
        this.doors = new ArrayList<>();
        this.obstructions = new ArrayList<>();
        this.floorCovering = FloorCovering.HARD; // Default
    }

    /**
     * Add a room to the house
     */
    public void addRoom(Room room) {
        if (room == null) {
            throw new IllegalArgumentException("Room must not be null");
        }
        // Check for overlap with existing rooms
        for (Room existing : rooms) {
            if (existing.intersects(room)) {
                throw new IllegalArgumentException(
                        "Room overlaps with existing room: " + existing.getId());
            }
        }

        rooms.add(room);
    }

    /**
     * Remove a room from the house
     */
    public void removeRoom(Room room) {
        if (room == null) {
            throw new IllegalArgumentException("room cannot be null");
        }
        // Remove all doors connected to this room
        List<Door> doorsToRemove = new ArrayList<>();
        for (Door door : doors) {
            if (door.connects(room)) {
                doorsToRemove.add(door);
            }
        }
        doorsToRemove.forEach(this::removeDoor);

        rooms.remove(room);
    }

    /**
     * Add a door connecting two rooms
     */
    public void addDoor(Door door) {
        if (door == null) {
            throw new IllegalArgumentException("Door cannot be null");
        }
        if (!rooms.contains(door.getRoom1()) || !rooms.contains(door.getRoom2())) {
            throw new IllegalArgumentException("Door must connect rooms in this house");
        }
        if (!door.isValidPosition()) {
            throw new IllegalArgumentException("Door must be on shared wall between rooms");
        }
        doors.add(door);
    }

    /**
     * Remove a door from the house
     */
    public void removeDoor(Door door) {
        if (door == null) {
            throw new IllegalArgumentException("Door must not be null");
        }
        door.getRoom1().removeDoor(door);
        door.getRoom2().removeDoor(door);
        doors.remove(door);
    }

    /**
     * Add an obstruction to the house (Req 3.1)
     */
    public void addObstruction(Obstruction obstruction) {
        if (obstruction == null) {
            throw new IllegalArgumentException("Obstruction cannot be null");
        }
        obstructions.add(obstruction);
    }

    /**
     * Remove an obstruction from the house
     */
    public void removeObstruction(Obstruction obstruction) {
        if (obstruction == null) {
            throw new IllegalArgumentException("Obstruction cannot be null");
        }
        obstructions.remove(obstruction);
    }

    /**
     * Get total non-cleanable area from blocking obstructions (Req 3.5)
     */
    public double getNonCleanableArea() {
        double total = 0.0;
        for (Obstruction obstruction : obstructions) {
            if (obstruction.blocksCleanableArea()) {
                total += obstruction.getArea();
            }
        }
        return total;
    }

    /**
     * Get cleanable area (total area minus non-cleanable) (Req 3.6)
     */
    public double getCleanableArea() {
        return getTotalArea() - getNonCleanableArea();
    }

    /**
     * Get total area of all rooms in the house
     */
    public double getTotalArea() {
        return rooms.stream().mapToDouble(Room::getArea).sum();
    }

    /**
     * Validate house configuration (Req 2.5)
     *
     * @return a list of validation errors, empty if valid
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Check total area bounds
        double totalArea = getTotalArea();
        if (totalArea < MIN_TOTAL_AREA) {
            errors.add(String.format("House area (%.2f ft²) is below minimum (%.2f ft²)", totalArea,
                    MIN_TOTAL_AREA));
        }
        if (totalArea > MAX_TOTAL_AREA) {
            errors.add(String.format("House area (%.2f ft²) exceeds maximum (%.2f ft²)", totalArea,
                    MAX_TOTAL_AREA));
        }

        // Check each room has at least one door (Req 2.4) and minimum area in a single pass
        List<String> connectivityErrors = new ArrayList<>();
        List<String> areaErrors = new ArrayList<>();

        for (Room room : rooms) {
            if (!room.hasValidConnectivity()) {
                connectivityErrors.add(String.format("Room %s has no doors/connections",
                        room.getId().substring(0, 8)));
            }
            if (room.getArea() < Room.MIN_AREA) {
                areaErrors.add(String.format(
                        "Room %s area (%.2f ft²) is below minimum (%.2f ft²)",
                        room.getId().substring(0, 8), room.getArea(), Room.MIN_AREA));
            }
        }

        errors.addAll(connectivityErrors);
        errors.addAll(areaErrors);
        return errors;
    }

    /**
     * Check if house is valid for simulation
     */
    public boolean isValid() {
        return validate().isEmpty();
    }

    /**
     * Set the floor covering for the entire house (Req 2.6)
     */
    public void setFloorCovering(FloorCovering covering) {
        if (covering == null) {
            throw new IllegalArgumentException("Floor covering cannot be null");
        }
        this.floorCovering = covering;
    }

    // Getters
    public List<Room> getRooms() {
        return new ArrayList<>(rooms);
    }

    public List<Door> getDoors() {
        return new ArrayList<>(doors);
    }

    public List<Obstruction> getObstructions() {
        return new ArrayList<>(obstructions);
    }

    public FloorCovering getFloorCovering() {
        return floorCovering;
    }

    @Override
    public String toString() {
        return String.format("House[rooms=%d, doors=%d, obstructions=%d, area=%.1f ft², cleanable=%.1f ft², covering=%s, valid=%s]",
            rooms.size(), doors.size(), obstructions.size(), getTotalArea(), 
            getCleanableArea(), floorCovering.name(), isValid());
    }
}