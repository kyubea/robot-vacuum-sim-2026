package com.vacuum.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vacuum.model.BlockingObstruction;
import com.vacuum.model.Door;
import com.vacuum.model.House;
import com.vacuum.model.Obstruction;
import com.vacuum.model.PassUnderObstruction;
import com.vacuum.model.Room;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Save and load complete simulation layouts from JSON files.
 */
public final class LayoutPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FORMAT_VERSION = "1.0";

    private LayoutPersistence() {}

    public static void save(Path file, LayoutSnapshot snapshot) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
        }
    }

    public static LayoutSnapshot load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LayoutSnapshot snapshot = GSON.fromJson(reader, LayoutSnapshot.class);
            validateLoadedSnapshot(snapshot);
            return snapshot;
        }
    }

    private static void validateLoadedSnapshot(LayoutSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Layout file is empty or invalid JSON");
        }
        if (snapshot.rooms == null || snapshot.rooms.isEmpty()) {
            throw new IllegalArgumentException("Layout must contain at least one room");
        }
        if (snapshot.doors == null) {
            snapshot.doors = new ArrayList<>();
        }
        if (snapshot.obstructions == null) {
            snapshot.obstructions = new ArrayList<>();
        }
    }

    public static LayoutSnapshot capture(House house, Vacuum vacuum, int startBattery,
            int selectedMoveMode, double batteryDrainRatePercent, double moveSpeedFeetPerSec,
            double speedMultiplier) {
        LayoutSnapshot snapshot = new LayoutSnapshot();
        snapshot.formatVersion = FORMAT_VERSION;
        snapshot.seed = house.getSeed();
        snapshot.floorCovering = house.getFloorCovering().name();

        List<Room> rooms = house.getRooms();
        snapshot.rooms = new ArrayList<>(rooms.size());

        Map<Room, Integer> roomIndex = new HashMap<>();
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            roomIndex.put(room, i);

            RoomState state = new RoomState();
            state.name = room.getName();
            state.x = room.getX();
            state.y = room.getY();
            state.width = room.getWidth();
            state.height = room.getHeight();
            snapshot.rooms.add(state);
        }

        snapshot.doors = new ArrayList<>();
        for (Door door : house.getDoors()) {
            Integer room1 = roomIndex.get(door.getRoom1());
            Integer room2 = roomIndex.get(door.getRoom2());
            if (room1 == null || room2 == null) {
                continue;
            }

            DoorState state = new DoorState();
            state.room1Index = room1;
            state.room2Index = room2;
            state.x = door.getX();
            state.y = door.getY();
            state.width = door.getWidth();
            state.orientation = door.getOrientation().name();
            snapshot.doors.add(state);
        }

        snapshot.obstructions = new ArrayList<>();
        for (Obstruction obstruction : house.getObstructions()) {
            Integer room = roomIndex.get(obstruction.getRoom());
            if (room == null) {
                continue;
            }

            ObstructionState state = new ObstructionState();
            state.roomIndex = room;
            state.x = obstruction.getX();
            state.y = obstruction.getY();
            state.width = obstruction.getWidth();
            state.height = obstruction.getHeight();

            if (obstruction instanceof BlockingObstruction) {
                state.type = "blocking";
            } else if (obstruction instanceof PassUnderObstruction passUnder) {
                state.type = "pass-under";
                state.legDiameterInches = passUnder.getLegDiameter() * 12.0;
                state.hSpaceBetweenLegs = passUnder.getHSpaceBetweenLegs();
                state.vSpaceBetweenLegs = passUnder.getVSpaceBetweenLegs();
            } else {
                continue;
            }
            snapshot.obstructions.add(state);
        }

        snapshot.robot = new RobotState();
        snapshot.robot.startX = vacuum.getStartX();
        snapshot.robot.startY = vacuum.getStartY();
        snapshot.robot.currentX = vacuum.getX();
        snapshot.robot.currentY = vacuum.getY();
        snapshot.robot.orientation = vacuum.getOrientation();
        snapshot.robot.battery = vacuum.getBattery();
        snapshot.robot.moveMode = selectedMoveMode;
        snapshot.robot.batteryDrainRatePercent = batteryDrainRatePercent;
        snapshot.robot.startBatteryPercent = startBattery;
        snapshot.robot.moveSpeedFeetPerSec = moveSpeedFeetPerSec;

        snapshot.simulation = new SimulationState();
        snapshot.simulation.speedMultiplier = speedMultiplier;

        return snapshot;
    }

    public static House buildHouse(LayoutSnapshot snapshot) {
        validateLoadedSnapshot(snapshot);

        House house = new House(snapshot.seed);

        House.FloorCovering covering = House.FloorCovering.HARDWOOD;
        if (snapshot.floorCovering != null && !snapshot.floorCovering.isBlank()) {
            covering = House.FloorCovering.valueOf(snapshot.floorCovering);
        }
        house.setFloorCovering(covering);

        List<Room> rooms = new ArrayList<>();
        for (RoomState roomState : snapshot.rooms) {
            String roomName =
                    (roomState.name == null || roomState.name.isBlank()) ? "Room" : roomState.name;
            Room room =
                    new Room(roomState.x, roomState.y, roomState.width, roomState.height, roomName);
            house.addRoom(room);
            rooms.add(room);
        }

        for (DoorState doorState : snapshot.doors) {
            validateRoomIndex(doorState.room1Index, rooms.size(), "door.room1Index");
            validateRoomIndex(doorState.room2Index, rooms.size(), "door.room2Index");

            Room room1 = rooms.get(doorState.room1Index);
            Room room2 = rooms.get(doorState.room2Index);
            Door.Orientation orientation = Door.Orientation.valueOf(doorState.orientation);
            Door door =
                    new Door(room1, room2, doorState.x, doorState.y, orientation, doorState.width);
            house.addDoor(door);
        }

        for (ObstructionState obstructionState : snapshot.obstructions) {
            validateRoomIndex(obstructionState.roomIndex, rooms.size(), "obstruction.roomIndex");
            Room room = rooms.get(obstructionState.roomIndex);

            Obstruction obstruction;
            if ("pass-under".equals(obstructionState.type)) {
                double legDiameterInches = obstructionState.legDiameterInches != null
                        ? obstructionState.legDiameterInches
                        : PassUnderObstruction.DEFAULT_LEG_DIAMETER;
                double hSpace = obstructionState.hSpaceBetweenLegs != null
                        ? obstructionState.hSpaceBetweenLegs
                        : obstructionState.width;
                double vSpace = obstructionState.vSpaceBetweenLegs != null
                        ? obstructionState.vSpaceBetweenLegs
                        : obstructionState.height;
                obstruction = new PassUnderObstruction(room, obstructionState.x, obstructionState.y,
                        obstructionState.width, obstructionState.height, legDiameterInches, hSpace,
                        vSpace);
            } else {
                obstruction = new BlockingObstruction(room, obstructionState.x, obstructionState.y,
                        obstructionState.width, obstructionState.height);
            }
            house.addObstruction(obstruction);
        }

        return house;
    }

    private static void validateRoomIndex(int index, int roomCount, String fieldName) {
        if (index < 0 || index >= roomCount) {
            throw new IllegalArgumentException(fieldName + " is out of range");
        }
    }

    public static class LayoutSnapshot {
        public String formatVersion;
        public long seed;
        public String floorCovering;
        public List<RoomState> rooms;
        public List<DoorState> doors;
        public List<ObstructionState> obstructions;
        public RobotState robot;
        public SimulationState simulation;
    }

    public static class RoomState {
        public String name;
        public double x;
        public double y;
        public double width;
        public double height;
    }

    public static class DoorState {
        public int room1Index;
        public int room2Index;
        public double x;
        public double y;
        public double width;
        public String orientation;
    }

    public static class ObstructionState {
        public String type;
        public int roomIndex;
        public double x;
        public double y;
        public double width;
        public double height;
        public Double legDiameterInches;
        public Double hSpaceBetweenLegs;
        public Double vSpaceBetweenLegs;
    }

    public static class RobotState {
        public Double startX;
        public Double startY;
        public Double currentX;
        public Double currentY;
        public Double orientation;
        public Double battery;
        public Integer moveMode;
        public Double batteryDrainRatePercent;
        public Integer startBatteryPercent;
        public Double moveSpeedFeetPerSec;
    }

    public static class SimulationState {
        public Double speedMultiplier;
    }
}
