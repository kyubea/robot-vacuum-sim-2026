package com.vacuum.util;

import com.vacuum.model.BlockingObstruction;
import com.vacuum.model.Door;
import com.vacuum.model.House;
import com.vacuum.model.Obstruction;
import com.vacuum.model.PassUnderObstruction;
import com.vacuum.model.Room;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless batch simulation runner for algorithm efficiency comparisons.
 */
public final class BatchTrialRunner {

    private static final double STEP_SECONDS = 1.0 / 30.0;
    private static final double MAX_SIM_SECONDS = 4.0 * 60.0 * 60.0;
    private static final double CLEANING_RADIUS_RATIO = 0.36;

    private BatchTrialRunner() {}

    public enum LayoutMode {
        SAME_LAYOUT, DIFFERENT_LAYOUT
    }

    public interface ProgressListener {
        void onProgress(int completedTrials, int totalTrials, String message);

        boolean isCancelled();
    }

    public static final class BatchConfig {
        private final House templateHouse;
        private final int trialsPerAlgorithm;
        private final List<Vacuum.MoveMode> algorithms;
        private final LayoutMode layoutMode;
        private final int startBatteryPercent;
        private final double batteryDrainRatePercent;
        private final double moveSpeedFeetPerSec;
        private final double startX;
        private final double startY;
        private final long seedBase;

        public BatchConfig(House templateHouse, int trialsPerAlgorithm,
                List<Vacuum.MoveMode> algorithms, LayoutMode layoutMode, int startBatteryPercent,
                double batteryDrainRatePercent, double moveSpeedFeetPerSec, double startX,
                double startY, long seedBase) {
            this.templateHouse = templateHouse;
            this.trialsPerAlgorithm = Math.max(1, trialsPerAlgorithm);
            this.algorithms = List.copyOf(algorithms);
            this.layoutMode = layoutMode;
            this.startBatteryPercent = Math.max(0, Math.min(100, startBatteryPercent));
            this.batteryDrainRatePercent = Math.max(0.0, batteryDrainRatePercent);
            this.moveSpeedFeetPerSec = Math.max(0.25, Math.min(3.0, moveSpeedFeetPerSec));
            this.startX = startX;
            this.startY = startY;
            this.seedBase = seedBase;
        }
    }

    public static final class BatchRunResult {
        private final Map<Vacuum.MoveMode, AlgorithmStats> statsByAlgorithm;
        private final int completedTrials;
        private final int totalTrials;
        private final boolean cancelled;

        public BatchRunResult(Map<Vacuum.MoveMode, AlgorithmStats> statsByAlgorithm,
                int completedTrials, int totalTrials, boolean cancelled) {
            this.statsByAlgorithm = statsByAlgorithm;
            this.completedTrials = completedTrials;
            this.totalTrials = totalTrials;
            this.cancelled = cancelled;
        }

        public Map<Vacuum.MoveMode, AlgorithmStats> getStatsByAlgorithm() {
            return statsByAlgorithm;
        }

        public int getCompletedTrials() {
            return completedTrials;
        }

        public int getTotalTrials() {
            return totalTrials;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class AlgorithmStats {
        private final int trials;
        private final double averageEfficiency;
        private final double minEfficiency;
        private final double maxEfficiency;
        private final double stdDevEfficiency;
        private final double averageDurationSeconds;

        public AlgorithmStats(int trials, double averageEfficiency, double minEfficiency,
                double maxEfficiency, double stdDevEfficiency, double averageDurationSeconds) {
            this.trials = trials;
            this.averageEfficiency = averageEfficiency;
            this.minEfficiency = minEfficiency;
            this.maxEfficiency = maxEfficiency;
            this.stdDevEfficiency = stdDevEfficiency;
            this.averageDurationSeconds = averageDurationSeconds;
        }

        public int getTrials() {
            return trials;
        }

        public double getAverageEfficiency() {
            return averageEfficiency;
        }

        public double getMinEfficiency() {
            return minEfficiency;
        }

        public double getMaxEfficiency() {
            return maxEfficiency;
        }

        public double getStdDevEfficiency() {
            return stdDevEfficiency;
        }

        public double getAverageDurationSeconds() {
            return averageDurationSeconds;
        }
    }

    public static BatchRunResult run(BatchConfig config, ProgressListener listener) {
        List<Vacuum.MoveMode> algorithms =
                config.algorithms.isEmpty() ? List.of(Vacuum.MoveMode.STRAIGHT) : config.algorithms;
        int totalTrials = config.trialsPerAlgorithm * algorithms.size();

        Map<Vacuum.MoveMode, List<TrialMeasurement>> measurements =
                new EnumMap<>(Vacuum.MoveMode.class);
        for (Vacuum.MoveMode mode : algorithms) {
            measurements.put(mode, new ArrayList<>());
        }

        int completedTrials = 0;
        for (Vacuum.MoveMode mode : algorithms) {
            List<TrialMeasurement> modeMeasurements = measurements.get(mode);
            for (int i = 0; i < config.trialsPerAlgorithm; i++) {
                if (listener != null && listener.isCancelled()) {
                    return new BatchRunResult(aggregateStats(measurements), completedTrials,
                            totalTrials, true);
                }

                long seed = config.seedBase + completedTrials * 31L + i;
                House trialHouse = config.layoutMode == LayoutMode.SAME_LAYOUT
                        ? cloneHouse(config.templateHouse)
                        : generateDifferentLayout(config.templateHouse, seed);

                TrialMeasurement measurement = runSingleTrial(trialHouse, config, mode);
                modeMeasurements.add(measurement);

                completedTrials++;
                if (listener != null) {
                    String message = String.format("%s trial %d/%d", mode.toString(), i + 1,
                            config.trialsPerAlgorithm);
                    listener.onProgress(completedTrials, totalTrials, message);
                }
            }
        }

        return new BatchRunResult(aggregateStats(measurements), completedTrials, totalTrials,
                false);
    }

    private static TrialMeasurement runSingleTrial(House house, BatchConfig config,
            Vacuum.MoveMode mode) {
        double startX = config.startX;
        double startY = config.startY;
        if (!isPointInsideHouse(house, startX, startY)) {
            Room fallback = house.getRooms().isEmpty() ? null : house.getRooms().get(0);
            if (fallback != null) {
                startX = fallback.getX() + fallback.getWidth() * 0.5;
                startY = fallback.getY() + fallback.getHeight() * 0.5;
            }
        }

        Vacuum vacuum = new Vacuum(startX, startY);
        vacuum.setStartPosition(startX, startY);
        vacuum.setMoveMode(mode.getCode());
        vacuum.setBatteryDrainRate(config.batteryDrainRatePercent);
        vacuum.setMoveSpeedFeetPerSec(config.moveSpeedFeetPerSec);
        vacuum.createWallColliders(house.getRooms());
        vacuum.addObstructions(house.getObstructions());
        vacuum.reset(config.startBatteryPercent, mode.getCode());

        CleaningCoverageTracker coverageTracker = new CleaningCoverageTracker(house);

        double elapsedSeconds = 0.0;
        while (elapsedSeconds < MAX_SIM_SECONDS && vacuum.getBattery() > 0.0
                && !coverageTracker.isFullyCleaned()) {
            vacuum.update(STEP_SECONDS, 0.0, 0.0, 1.0);
            coverageTracker.updateFromVacuum(vacuum);
            elapsedSeconds += STEP_SECONDS;
        }

        return new TrialMeasurement(coverageTracker.getCleanedCoverageRatio(), elapsedSeconds);
    }

    private static boolean isPointInsideHouse(House house, double x, double y) {
        if (house == null) {
            return false;
        }
        for (Room room : house.getRooms()) {
            if (room.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static Map<Vacuum.MoveMode, AlgorithmStats> aggregateStats(
            Map<Vacuum.MoveMode, List<TrialMeasurement>> measurements) {
        Map<Vacuum.MoveMode, AlgorithmStats> results = new EnumMap<>(Vacuum.MoveMode.class);

        for (Map.Entry<Vacuum.MoveMode, List<TrialMeasurement>> entry : measurements.entrySet()) {
            List<TrialMeasurement> samples = entry.getValue();
            if (samples.isEmpty()) {
                continue;
            }

            double sumEff = 0.0;
            double sumDur = 0.0;
            double minEff = Double.POSITIVE_INFINITY;
            double maxEff = Double.NEGATIVE_INFINITY;

            for (TrialMeasurement measurement : samples) {
                sumEff += measurement.cleanedCoverageRatio;
                sumDur += measurement.elapsedSeconds;
                minEff = Math.min(minEff, measurement.cleanedCoverageRatio);
                maxEff = Math.max(maxEff, measurement.cleanedCoverageRatio);
            }

            double avgEff = sumEff / samples.size();
            double avgDur = sumDur / samples.size();

            double variance = 0.0;
            for (TrialMeasurement measurement : samples) {
                double delta = measurement.cleanedCoverageRatio - avgEff;
                variance += delta * delta;
            }
            variance /= samples.size();

            results.put(entry.getKey(), new AlgorithmStats(samples.size(), avgEff, minEff, maxEff,
                    Math.sqrt(variance), avgDur));
        }

        return results;
    }

    private static House generateDifferentLayout(House templateHouse, long seed) {
        House generated = new House(seed);
        generated.setFloorCovering(templateHouse.getFloorCovering());
        generated.generateDefaultFloorPlan();
        return generated;
    }

    private static House cloneHouse(House source) {
        House copy = new House(source.getSeed());
        copy.setFloorCovering(source.getFloorCovering());

        List<Room> sourceRooms = source.getRooms();
        Map<Room, Room> roomMap = new HashMap<>();

        for (Room sourceRoom : sourceRooms) {
            Room clonedRoom = new Room(sourceRoom.getX(), sourceRoom.getY(), sourceRoom.getWidth(),
                    sourceRoom.getHeight(), sourceRoom.getName());
            copy.addRoom(clonedRoom);
            roomMap.put(sourceRoom, clonedRoom);
        }

        for (Door sourceDoor : source.getDoors()) {
            Room room1 = roomMap.get(sourceDoor.getRoom1());
            Room room2 = roomMap.get(sourceDoor.getRoom2());
            if (room1 == null || room2 == null) {
                continue;
            }
            Door clonedDoor = new Door(room1, room2, sourceDoor.getX(), sourceDoor.getY(),
                    sourceDoor.getOrientation(), sourceDoor.getWidth());
            copy.addDoor(clonedDoor);
        }

        for (Obstruction sourceObstruction : source.getObstructions()) {
            Room room = roomMap.get(sourceObstruction.getRoom());
            if (room == null) {
                continue;
            }

            Obstruction cloned;
            if (sourceObstruction instanceof PassUnderObstruction passUnder) {
                cloned = new PassUnderObstruction(room, passUnder.getX(), passUnder.getY(),
                        passUnder.getWidth(), passUnder.getHeight(),
                        passUnder.getLegDiameter() * 12.0, passUnder.getHSpaceBetweenLegs(),
                        passUnder.getVSpaceBetweenLegs());
            } else {
                cloned = new BlockingObstruction(room, sourceObstruction.getX(),
                        sourceObstruction.getY(), sourceObstruction.getWidth(),
                        sourceObstruction.getHeight());
            }
            copy.addObstruction(cloned);
        }

        return copy;
    }

    private static final class TrialMeasurement {
        private final double cleanedCoverageRatio;
        private final double elapsedSeconds;

        private TrialMeasurement(double cleanedCoverageRatio, double elapsedSeconds) {
            this.cleanedCoverageRatio = cleanedCoverageRatio;
            this.elapsedSeconds = elapsedSeconds;
        }
    }

    private static final class CleaningCoverageTracker {
        private final Set<Long> cleanableTiles = new HashSet<>();
        private final Map<Long, Integer> tilePassRequirements = new HashMap<>();
        private final Map<Long, Integer> tilePassCounts = new HashMap<>();
        private final double cleaningEfficiencyPerPass;
        private double accumulatedCoverageProgress = 0.0;

        private CleaningCoverageTracker(House house) {
            this.cleaningEfficiencyPerPass =
                    Math.max(0.01, Math.min(1.0, house.getFloorCovering().getDefaultEfficiency()));
            initializeCleanableTiles(house);
        }

        private void initializeCleanableTiles(House house) {
            if (house == null || house.getRooms().isEmpty()) {
                return;
            }

            int requiredPasses = Math.max(1, (int) Math.ceil(1.0 / cleaningEfficiencyPerPass));

            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (Room room : house.getRooms()) {
                minX = Math.min(minX, room.getX());
                maxX = Math.max(maxX, room.getMaxX());
                minY = Math.min(minY, room.getY());
                maxY = Math.max(maxY, room.getMaxY());
            }

            int startGx = (int) Math.floor(minX);
            int endGx = (int) Math.ceil(maxX) - 1;
            int startGy = (int) Math.floor(minY);
            int endGy = (int) Math.ceil(maxY) - 1;

            List<Obstruction> obstructions = house.getObstructions();
            for (int gx = startGx; gx <= endGx; gx++) {
                for (int gy = startGy; gy <= endGy; gy++) {
                    double centerX = gx + 0.5;
                    double centerY = gy + 0.5;
                    if (house.getRoomAt(centerX, centerY) == null) {
                        continue;
                    }

                    boolean blocked = false;
                    for (Obstruction obstruction : obstructions) {
                        if (obstruction.blocksCleanableArea()
                                && obstruction.contains(centerX, centerY)) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        continue;
                    }

                    long tileKey = encodeTileKey(gx, gy);
                    cleanableTiles.add(tileKey);
                    tilePassRequirements.put(tileKey, requiredPasses);
                    tilePassCounts.put(tileKey, 0);
                }
            }
        }

        private void updateFromVacuum(Vacuum vacuum) {
            if (vacuum == null || cleanableTiles.isEmpty()) {
                return;
            }

            double cleanRadius = vacuum.getSize() * CLEANING_RADIUS_RATIO;
            double centerX = vacuum.getX() + vacuum.getSize() * 0.5;
            double centerY = vacuum.getY() + vacuum.getSize() * 0.5;

            int startGx = (int) Math.floor(centerX - cleanRadius);
            int endGx = (int) Math.floor(centerX + cleanRadius);
            int startGy = (int) Math.floor(centerY - cleanRadius);
            int endGy = (int) Math.floor(centerY + cleanRadius);

            for (int gx = startGx; gx <= endGx; gx++) {
                for (int gy = startGy; gy <= endGy; gy++) {
                    if (!circleIntersectsTile(centerX, centerY, cleanRadius, gx, gy)) {
                        continue;
                    }

                    long tileKey = encodeTileKey(gx, gy);
                    if (!cleanableTiles.contains(tileKey)) {
                        continue;
                    }

                    int required = tilePassRequirements.getOrDefault(tileKey, 1);
                    int currentPasses = tilePassCounts.getOrDefault(tileKey, 0);
                    if (currentPasses < required) {
                        double oldProgress =
                                Math.min(1.0, currentPasses * cleaningEfficiencyPerPass);
                        int newPasses = currentPasses + 1;
                        double newProgress = Math.min(1.0, newPasses * cleaningEfficiencyPerPass);
                        tilePassCounts.put(tileKey, newPasses);
                        accumulatedCoverageProgress += (newProgress - oldProgress);
                    }
                }
            }
        }

        private boolean circleIntersectsTile(double cx, double cy, double radius, int tileX,
                int tileY) {
            double nearestX = Math.max(tileX, Math.min(cx, tileX + 1.0));
            double nearestY = Math.max(tileY, Math.min(cy, tileY + 1.0));
            double dx = cx - nearestX;
            double dy = cy - nearestY;
            return (dx * dx + dy * dy) <= (radius * radius);
        }

        private double getCleanedCoverageRatio() {
            if (cleanableTiles.isEmpty()) {
                return 0.0;
            }
            return accumulatedCoverageProgress / cleanableTiles.size();
        }

        private boolean isFullyCleaned() {
            return getCleanedCoverageRatio() >= 0.999;
        }

        private long encodeTileKey(int gx, int gy) {
            return (((long) gx) << 32) ^ (gy & 0xffffffffL);
        }
    }
}
