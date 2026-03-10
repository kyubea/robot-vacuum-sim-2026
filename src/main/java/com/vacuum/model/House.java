package com.vacuum.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Represents the house containing rooms and doors. Houses must be between 200-8000 square feet
 * total.
 */
public class House {

    // Constants (tweak as desired)
    private static final double GOLDEN_RATIO = (1 + Math.sqrt(5)) / 2; // ≈ 1.618
    private static final double RECIPROCAL_GOLDEN = 1 / GOLDEN_RATIO; // ≈ 0.618

    private static final double MIN_ASPECT_GREAT = 1.1;
    private static final double MAX_ASPECT_GREAT = GOLDEN_RATIO;

    private static final double MIN_ASPECT_OTHER = RECIPROCAL_GOLDEN;
    private static final double MAX_ASPECT_OTHER = GOLDEN_RATIO;

    private static final double DOOR_MARGIN_FACTOR = 0.5; // Min spacing to corner of room (factor
                                                          // of door width)
    private static final double EXTRA_DOOR_PROB = 0.10; // 10% prob of extra door to adjacent room

    private static final boolean DEBUG = false; // Turn on for generation diagnostic output

    private List<Room> rooms;
    private List<Door> doors;
    private List<Obstruction> obstructions;
    private FloorCovering floorCovering;
    private long seed;
    private Random random;
    private double doorWidth = Door.DEFAULT_WIDTH;

    public static final double MIN_TOTAL_AREA = 200.0; // square feet
    public static final double MAX_TOTAL_AREA = 8000.0; // square feet

    public enum FloorCovering {
        HARDWOOD("Hardwood", 0.90), LAMINATE("Laminate", 0.90), TILE("Tile", 0.90), BERBERPILE(
                "Berber Pile",
                0.75), CUTPILE("Cut Pile", 0.70), CALIFORNIASHAG("California Shag)", 0.65);

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

    public House(long seed) {
        this.rooms = new ArrayList<>();
        this.doors = new ArrayList<>();
        this.obstructions = new ArrayList<>();
        this.floorCovering = FloorCovering.HARDWOOD; // Default
        this.seed = seed;
        this.random = new Random(seed);
    }

    public House() {
        this(42); // constant seed deterministic
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
                        "Room overlaps with existing room: " + existing.getName());
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
                areaErrors.add(String.format("Room %s area (%.2f ft²) is below minimum (%.2f ft²)",
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
        return String.format(
                "House[rooms=%d, doors=%d, obstructions=%d, area=%.1f ft², cleanable=%.1f ft², covering=%s, valid=%s]",
                rooms.size(), doors.size(), obstructions.size(), getTotalArea(), getCleanableArea(),
                floorCovering.name(), isValid());
    }

    public void generateFloorPlan(int targetNumRooms, double minTotalArea, double maxTotalArea) {
        obstructions.clear();
        while (true) {
            rooms.clear();
            doors.clear();

            _generate(targetNumRooms, minTotalArea, maxTotalArea);

            List<String> errors = validate();
            if (errors.isEmpty() && rooms.size() == targetNumRooms)
                break;

            System.err.println("House generation produced an invalid result.");
            System.err.println("Please capture the following information and report!");
            System.err.printf(
                    "Seed: %d  targetNumRooms: %d  minTotalArea: %.2f  maxTotalArea: %2f%n",
                    this.seed, targetNumRooms, minTotalArea, maxTotalArea);
            for (String e : errors)
                System.err.println(e);
            for (Room r : rooms)
                System.err.println(r);
            for (Door d : doors)
                System.err.println(d);
            System.err.println("Generation will now retry...");
        }

        // Success! Normalize the floor plan.
        double xmin = 0.0, ymin = 0.0;
        for (Room r : rooms) {
            double x = r.getX();
            double y = r.getY();
            if (x < xmin)
                xmin = x;
            if (y < ymin)
                ymin = y;
        }
        for (Room r : rooms) {
            r.setDimensions(r.getX() - xmin, r.getY() - ymin, r.getWidth(), r.getHeight());
        }
        for (Door d : doors) {
            d.setPosition(d.getX() - xmin, d.getY() - ymin);
        }
    }

    private void _generate(int targetNumRooms, double minTotalArea, double maxTotalArea) {
        /* Start with a random total area in range */
        double totalArea = minTotalArea + random.nextDouble() * (maxTotalArea - minTotalArea);

        /* Establish area of Great Room, 20-40% of total area */
        double greatAreaTarget = (0.20 + random.nextDouble() * 0.20) * totalArea;

        double aspectGreat =
                MIN_ASPECT_GREAT + random.nextDouble() * (MAX_ASPECT_GREAT - MIN_ASPECT_GREAT);
        double greatHeight = Math.sqrt(greatAreaTarget / aspectGreat);
        double greatWidth = greatAreaTarget / greatHeight;

        // Snap to nearest 0.5 ft
        greatWidth = Math.round(greatWidth * 2) / 2.0;
        greatHeight = Math.round(greatHeight * 2) / 2.0;

        Room great = new Room(0, 0, greatWidth, greatHeight, "GR");
        rooms.add(great);

        /*
         * From here, we track remaining area for the rest of the rooms we create. We may exceed the
         * remainingArea while creating enough rooms, but we will never exceed the maxTotalArea
         * (that's an abort/fail).
         */
        double remainingArea = totalArea - great.getArea();
        double minNewArea = Room.MIN_AREA;

        /*
         * We work counter-clockwise around the Great Room, trying to add new rooms. When a room
         * cannot be placed on a side with sufficient overlap to place a door, we move on to the
         * next side. Each new room created adds a set of sides onto which new rooms may also be
         * placed later (that's what the placementQueue is for).
         */
        Deque<SidePlacement> placementQueue = new ArrayDeque<>();
        placementQueue.add(new SidePlacement(great, AdjoinResult.Side.LEFT));
        placementQueue.add(new SidePlacement(great, AdjoinResult.Side.TOP));
        placementQueue.add(new SidePlacement(great, AdjoinResult.Side.RIGHT));

        int placedCount = 1;

        // Keep going until we've placed all the rooms or screwed ourselves somehow.
        SidePlacement sp = placementQueue.pollFirst();
        while (placedCount < targetNumRooms && getTotalArea() < maxTotalArea) {
            Room parent = sp.room;

            double area =
                    minNewArea + random.nextDouble() * Math.max(0, remainingArea - minNewArea);

            double aspect =
                    MIN_ASPECT_OTHER + random.nextDouble() * (MAX_ASPECT_OTHER - MIN_ASPECT_OTHER);
            double h = Math.ceil(Math.sqrt(area / aspect) * 100.0) / 100.0;
            double w = Math.ceil(area / h * 100.0) / 100.0;

            // No wall can be shorter than the space required to place a door.
            w = Math.max(w, doorWidth * (1.0 + DOOR_MARGIN_FACTOR));
            h = Math.max(h, doorWidth * (1.0 + DOOR_MARGIN_FACTOR));

            // Try to place a room on the current side.
            Room newRoom = tryPlaceOnWall(parent, sp.side, w, h, area);
            if (newRoom == null) {
                // Can't place it on this side. Move on to next side.
                if (placementQueue.isEmpty()) {
                    break; // No more sides to try... give up!
                }
                sp = placementQueue.pollFirst();
                continue;
            }

            rooms.add(newRoom);
            placedCount++;
            remainingArea -= newRoom.getArea();

            // Create the required door between the new room and its parent.
            createDoorBetween(parent, newRoom, sp.side);

            // Randomly create additional door between the new door and a room it's adjacent to (if
            // any)
            maybeAddExtraDoors(newRoom);

            // The sides of this new room become eligible to have rooms placed on them, too.
            enqueueNewWalls(newRoom, placementQueue);
        }

        if (DEBUG) {
            System.out.printf("Generated house with %d rooms (target was %d)%n", rooms.size(),
                    targetNumRooms);
            System.out.printf("Total area: %.0f sq ft%n", getTotalArea());
        }
    }

    private Room tryPlaceOnWall(Room parent, AdjoinResult.Side side, double w, double h,
            double targetArea) {
        double minShared = doorWidth * (1.0 + DOOR_MARGIN_FACTOR * 2);
        if (DEBUG)
            System.out.printf("Try/place on %s of %s%n", side, parent);

        double newX, newY;

        if (side.equals(AdjoinResult.Side.LEFT) || side.equals(AdjoinResult.Side.RIGHT)) {
            double sharedLenMax = Math.min(h, parent.getHeight());
            if (sharedLenMax < minShared)
                return null;

            newX = side.equals(AdjoinResult.Side.RIGHT) ? parent.getMaxX() : parent.getX() - w;
            newY = parent.getY();

            // Very naive sliding — in real code you'd do better collision resolution
            for (Room r : rooms) {
                if (r == parent)
                    continue;
                AdjoinResult adj = parent.adjoins(r);
                if (adj == null || !adj.side.equals(side))
                    continue;

                double extentY = r.getMaxY();
                if (newY < extentY) {
                    newY = extentY;
                    if (DEBUG)
                        System.out.printf("  %s collides on %s%n", r, side);
                }
            }

            if (newY > (parent.getMaxY() - minShared)) {
                return null;
            }

            Room candidate = new Room(newX, newY, w, h, "R" + rooms.size());
            if (DEBUG)
                System.out.printf("  New room on %s: %s%n", side, candidate);

            boolean collision =
                    rooms.stream().filter(r -> r != parent).anyMatch(candidate::intersects);

            return collision ? null : candidate;

        } else {
            double sharedLenMax = Math.min(w, parent.getWidth());
            if (sharedLenMax < minShared)
                return null;

            newX = parent.getX();
            newY = side.equals(AdjoinResult.Side.TOP) ? parent.getMaxY() : parent.getY() - h;

            for (Room r : rooms) {
                if (r == parent)
                    continue;
                AdjoinResult adj = parent.adjoins(r);
                if (adj == null || !adj.side.equals(side))
                    continue;

                double extentX = r.getMaxX();
                if (newX < extentX) {
                    newX = extentX;
                    if (DEBUG)
                        System.out.printf("  %s collides on %s%n", r, side);
                }
            }

            if (newX > (parent.getMaxX() - minShared)) {
                return null;
            }

            Room candidate = new Room(newX, newY, w, h, "R" + rooms.size());
            if (DEBUG)
                System.out.printf("  New room on %s: %s%n", side, candidate);

            boolean collision =
                    rooms.stream().filter(r -> r != parent).anyMatch(candidate::intersects);

            return collision ? null : candidate;
        }
    }

    private void createDoorBetween(Room a, Room b, AdjoinResult.Side side) {
        double margin = doorWidth * DOOR_MARGIN_FACTOR;

        if (side.equals(AdjoinResult.Side.LEFT) || side.equals(AdjoinResult.Side.RIGHT)) {
            double x = Math.max(a.getX(), b.getX()); // rough
            double ymin = Math.max(a.getY(), b.getY()) + margin;
            double ymax = Math.min(a.getMaxY(), b.getMaxY()) - doorWidth - margin;
            if (ymax <= ymin) {
                if (DEBUG)
                    System.out.printf("**Door unable %.2f <= %.2f%n", ymax, ymin);
                return;
            }

            double y = ymin + random.nextDouble() * (ymax - ymin);
            Door door = new Door(a, b, x, y, Door.Orientation.VERTICAL);
            if (DEBUG)
                System.out.println("  New door" + door + " between " + a + " and " + b);
            doors.add(door);
            a.addDoor(door);
            b.addDoor(door);
        } else { // top or bottom
            double y = Math.max(a.getY(), b.getY());
            double xmin = Math.max(a.getX(), b.getX()) + margin;
            double xmax = Math.min(a.getMaxX(), b.getMaxX()) - doorWidth - margin;
            if (xmax < xmin) {
                if (DEBUG)
                    System.out.printf("**Door unable %.2f <= %.2f%n", xmax, xmin);
                return;
            }

            double x = xmin + random.nextDouble() * (xmax - xmin);
            Door door = new Door(a, b, x, y, Door.Orientation.HORIZONTAL);
            if (DEBUG)
                System.out.println("  New door " + door + " between " + a + " and " + b);

            doors.add(door);
            a.addDoor(door);
            b.addDoor(door);
        }
    }

    private void maybeAddExtraDoors(Room newRoom) {
        for (Room other : rooms) {
            if (other == newRoom)
                continue;
            AdjoinResult adj = newRoom.adjoins(other);
            if (adj == null)
                continue;

            boolean has_door = false;
            for (Door door : other.getDoors()) {
                if (door.connects(newRoom)) {
                    has_door = true;
                }
            }
            if (has_door)
                continue;

            if (!newRoom.hasValidConnectivity() || random.nextDouble() <= EXTRA_DOOR_PROB) {
                if (DEBUG)
                    System.out.printf("**Creating extra door between %s and %s on %s%n", newRoom,
                            other, adj.side);
                createDoorBetween(newRoom, other, adj.side);
            }
        }
    }

    private void enqueueNewWalls(Room room, Deque<SidePlacement> queue) {
        queue.add(new SidePlacement(room, AdjoinResult.Side.LEFT));
        queue.add(new SidePlacement(room, AdjoinResult.Side.TOP));
        queue.add(new SidePlacement(room, AdjoinResult.Side.RIGHT));
        // skipping bottom intentionally
    }

    private record SidePlacement(Room room, AdjoinResult.Side side) {
    }
}
