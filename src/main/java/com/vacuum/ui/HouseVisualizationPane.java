package com.vacuum.ui;

import com.vacuum.model.*;
import com.vacuum.util.Vacuum;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.shape.Line;
import javafx.scene.shape.Circle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.shape.StrokeLineCap;
import java.util.function.Consumer;

/**
 * Visualization pane that renders a House with rooms, doors, and obstructions
 */
public class HouseVisualizationPane extends Pane {

    private static final Color WALL_COLOR = Color.web("#2b353d");
    private static final Color HOVER_COLOR = Color.web("#6a8ea3");
    private static final Color SELECTION_COLOR = Color.web("#0f7b82");
    private static final Color DOOR_COLOR = Color.web("#d96b1d");
    private static final double GEOMETRY_EPSILON = 0.01;
    private static final double GRID_SNAP = 1.0;
    private static final double CANVAS_PADDING = 32.0;
    private static final double FLOOR_TEXTURE_TILE_FEET = 2.0;

    // Dark-mode colour alternates
    private static final Color DARK_WALL_COLOR = Color.web("#8faabc");
    private static final Color DARK_HOVER_COLOR = Color.web("#6ea8c8");
    private static final Color DARK_SELECTION_COLOR = Color.web("#1cc9d4");
    private static final Color DARK_DOOR_COLOR = Color.web("#ffb347");

    private House house;
    private double scale = 10.0;
    private double offsetX = CANVAS_PADDING;
    private double offsetY = CANVAS_PADDING;
    private boolean darkMode = false;

    // Add-room draw mode
    private boolean editMode = false;
    private boolean addRoomMode = false;
    private boolean addObstructionMode = false;
    private boolean addBlockingObstruction = true; // Only relevant if addObstructionMode is true
    private boolean isDraggingRoom = false;
    private boolean isDraggingObstruction = false;
    private double drawStartModelX;
    private double drawStartModelY;
    private Rectangle ghostRect;

    private VBox hoverTooltip;
    private Room currentHoverRoom;

    private Rectangle selectedRoomRect;
    private Room selectedRoomModel;
    private Rectangle selectedObstructionRect;
    private Obstruction selectedObstructionModel;
    private Circle leftHandle, rightHandle, topHandle, bottomHandle;

    private String resizeEdge;
    private double dragStartX;
    private double dragStartY;
    private double originalX, originalY, originalWidth, originalHeight;
    public Vacuum vacuum;
    private String lastResizeWarning;
    private boolean resizeAppliedDuringDrag;

    private final Map<Long, Integer> tilePassCounts = new HashMap<>();
    private final Map<Long, Integer> tilePassRequirements = new HashMap<>();
    private final Set<Long> cleanableTiles = new HashSet<>();
    private final Set<Long> tilesTouchedLastUpdate = new HashSet<>();
    private boolean cleaningMapInitialized = false;
    private boolean cleaningHeatMapVisible = true;
    private double cleaningEfficiencyPerPass = 1.0;

    private static final double TILE_SIZE = 1.0;
    private static final double CLEANING_RADIUS_RATIO = 0.36;
    private static final double REACHABILITY_STEP = 0.25;

    private Consumer<String> statusMessageHandler;
    private Runnable houseChangedHandler;
    private Consumer<Point2D> robotPlacementHandler;

    private static class DoorPositionSnapshot {
        private final Door door;
        private final double x;
        private final double y;

        private DoorPositionSnapshot(Door door, double x, double y) {
            this.door = door;
            this.x = x;
            this.y = y;
        }
    }

    private static class DoorAnchor {
        private final Door door;
        private final double progress;

        private DoorAnchor(Door door, double progress) {
            this.door = door;
            this.progress = progress;
        }
    }

    public HouseVisualizationPane() {
        this.getStyleClass().add("house-visualization-pane");

        hoverTooltip = new VBox(5);
        hoverTooltip.setPadding(new Insets(8));
        hoverTooltip.setBackground(new Background(new BackgroundFill(Color.rgb(243, 246, 248, 0.95),
                new CornerRadii(8), Insets.EMPTY)));
        hoverTooltip.getStyleClass().add("room-tooltip");
        hoverTooltip.setVisible(false);
        hoverTooltip.setMouseTransparent(true);
        this.getChildren().add(hoverTooltip);

        leftHandle = createResizeHandle();
        rightHandle = createResizeHandle();
        topHandle = createResizeHandle();
        bottomHandle = createResizeHandle();

        setupResizeHandle(leftHandle, "left", Cursor.W_RESIZE);
        setupResizeHandle(rightHandle, "right", Cursor.E_RESIZE);
        setupResizeHandle(topHandle, "top", Cursor.N_RESIZE);
        setupResizeHandle(bottomHandle, "bottom", Cursor.S_RESIZE);

        setOnMouseClicked(e -> {
            if (e.getTarget() == this && !(addRoomMode || addObstructionMode)) {
                if (editMode) {
                    deselectAll();
                    hoverTooltip.setVisible(false);
                } else if (robotPlacementHandler != null) {
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    robotPlacementHandler.accept(model);
                }
            }
        });

        ghostRect = new Rectangle();
        ghostRect.setStrokeWidth(2.0);
        ghostRect.getStrokeDashArray().addAll(6.0, 3.0);
        ghostRect.setOpacity(0.65);
        ghostRect.setVisible(false);
        ghostRect.setMouseTransparent(true);
        this.getChildren().add(ghostRect);

        setOnMousePressed(e -> {
            if (!editMode || !addRoomMode || e.getTarget() != this)
                return;
            Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
            drawStartModelX = model.getX();
            drawStartModelY = model.getY();
            isDraggingRoom = true;
            updateGhostRect(drawStartModelX, drawStartModelY, 1.0, 1.0);
            e.consume();
        });

        setOnMouseDragged(e -> {
            if (!editMode || !addRoomMode || !isDraggingRoom)
                return;
            Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
            double x = Math.min(drawStartModelX, model.getX());
            double y = Math.min(drawStartModelY, model.getY());
            double w = Math.max(2.0, Math.abs(model.getX() - drawStartModelX));
            double h = Math.max(2.0, Math.abs(model.getY() - drawStartModelY));
            updateGhostRect(x, y, w, h);
            e.consume();
        });

        setOnMouseReleased(e -> {
            if (!editMode || !addRoomMode || !isDraggingRoom)
                return;
            isDraggingRoom = false;
            Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
            double x = Math.min(drawStartModelX, model.getX());
            double y = Math.min(drawStartModelY, model.getY());
            double w = Math.max(2.0, Math.abs(model.getX() - drawStartModelX));
            double h = Math.max(2.0, Math.abs(model.getY() - drawStartModelY));
            placeNewRoom(x, y, w, h);
            ghostRect.setVisible(false);
            e.consume();
        });
    }

    private Circle createResizeHandle() {
        Circle handle = new Circle(6);
        handle.setFill(Color.WHITE);
        handle.setStroke(SELECTION_COLOR);
        handle.setStrokeWidth(2);
        handle.setVisible(false);
        this.getChildren().add(handle);
        return handle;
    }

    private void setupResizeHandle(Circle handle, String edge, Cursor cursor) {
        handle.setCursor(cursor);

        handle.setOnMouseEntered(e -> {
            handle.setFill(Color.web("#d5eef0"));
            handle.setRadius(7);
        });

        handle.setOnMouseExited(e -> {
            handle.setFill(Color.WHITE);
            handle.setRadius(6);
        });

        handle.setOnMousePressed(e -> {
            if (!editMode) {
                e.consume();
                return;
            }
            resizeEdge = edge;
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            lastResizeWarning = null;
            resizeAppliedDuringDrag = false;
            if (selectedRoomModel != null) {
                originalX = selectedRoomModel.getX();
                originalY = selectedRoomModel.getY();
                originalWidth = selectedRoomModel.getWidth();
                originalHeight = selectedRoomModel.getHeight();
            }
            e.consume();
        });

        handle.setOnMouseDragged(e -> {
            if (resizeEdge != null) {
                resizeRoom(e.getSceneX(), e.getSceneY());
            }
            e.consume();
        });

        handle.setOnMouseReleased(e -> {
            if (resizeAppliedDuringDrag) {
                notifyStatus("Room resize applied");
                notifyHouseChanged();
            }
            resizeEdge = null;
            e.consume();
        });
    }

    public void setHouse(House house) {
        this.house = house;
        resetCleaningMap();
        render();
    }

    public void setVacuum(Vacuum vacuum) {
        this.vacuum = vacuum;
    }

    public void setStatusMessageHandler(Consumer<String> statusMessageHandler) {
        this.statusMessageHandler = statusMessageHandler;
    }

    public void setHouseChangedHandler(Runnable houseChangedHandler) {
        this.houseChangedHandler = houseChangedHandler;
    }

    public void setRobotPlacementHandler(Consumer<Point2D> robotPlacementHandler) {
        this.robotPlacementHandler = robotPlacementHandler;
    }

    public void render() {
        this.getChildren().retainAll(hoverTooltip, leftHandle, rightHandle, topHandle, bottomHandle,
                ghostRect);

        if (house == null) {
            return;
        }

        double minRoomX = 0, minRoomY = 0, maxRoomX = 0, maxRoomY = 0;
        for (Room room : house.getRooms()) {
            minRoomX = Math.min(minRoomX, room.getX());
            minRoomY = Math.min(minRoomY, room.getY());
            maxRoomX = Math.max(maxRoomX, room.getX() + room.getWidth());
            maxRoomY = Math.max(maxRoomY, room.getY() + room.getHeight());
        }

        double planWidth = (maxRoomX - minRoomX) * scale;
        double planHeight = (maxRoomY - minRoomY) * scale;
        double canvasWidth = planWidth + CANVAS_PADDING * 2;
        double canvasHeight = planHeight + CANVAS_PADDING * 2;

        this.setPrefSize(canvasWidth, canvasHeight);

        // Keep the current plan centered in the virtual canvas space.
        double planX = (canvasWidth - planWidth) * 0.5;
        double planY = (canvasHeight - planHeight) * 0.5;
        offsetX = planX - minRoomX * scale;
        offsetY = planY - minRoomY * scale;

        renderCanvasBackground(canvasWidth, canvasHeight);
        renderGrid();

        selectedRoomRect = null;
        selectedObstructionRect = null;
        renderRooms();
        renderCleaningMap();
        renderObstructions();
        renderVacuum();
        renderDoors();
        // renderColliders();

        if (selectedRoomModel != null && editMode) {
            if (selectedRoomRect != null) {
                updateSelectedRoom();
            } else {
                deselectRoom();
            }
            renderSharedWallHints(selectedRoomModel);
        } else if (!editMode && selectedRoomModel != null) {
            deselectRoom();
        }

        if (!editMode && selectedObstructionModel != null) {
            deselectObstruction();
        }

        updateTooltipStyle();
        ghostRect.toFront();
        hoverTooltip.toFront();
        leftHandle.toFront();
        rightHandle.toFront();
        topHandle.toFront();
        bottomHandle.toFront();
    }

    public void resetCleaningMap() {
        tilePassCounts.clear();
        tilePassRequirements.clear();
        cleanableTiles.clear();
        tilesTouchedLastUpdate.clear();
        cleaningMapInitialized = false;
        cleaningEfficiencyPerPass = 1.0;
    }

    public void setCleaningHeatMapVisible(boolean visible) {
        this.cleaningHeatMapVisible = visible;
        render();
    }

    public boolean isCleaningHeatMapVisible() {
        return cleaningHeatMapVisible;
    }

    public void updateCleaningFromVacuumFrontHitbox() {
        if (house == null || vacuum == null) {
            return;
        }

        initializeCleaningMapIfNeeded();
        if (cleanableTiles.isEmpty()) {
            return;
        }

        double cleanRadius = vacuum.getSize() * CLEANING_RADIUS_RATIO;
        double centerX = vacuum.getX() + vacuum.getSize() * 0.5;
        double centerY = vacuum.getY() + vacuum.getSize() * 0.5;

        int startGx = (int) Math.floor(centerX - cleanRadius);
        int endGx = (int) Math.floor(centerX + cleanRadius);
        int startGy = (int) Math.floor(centerY - cleanRadius);
        int endGy = (int) Math.floor(centerY + cleanRadius);
        Set<Long> tilesTouchedThisUpdate = new HashSet<>();

        for (int gx = startGx; gx <= endGx; gx++) {
            for (int gy = startGy; gy <= endGy; gy++) {
                if (!circleIntersectsTile(centerX, centerY, cleanRadius, gx, gy)) {
                    continue;
                }

                long tileKey = encodeTileKey(gx, gy);
                if (!cleanableTiles.contains(tileKey)) {
                    continue;
                }
                tilesTouchedThisUpdate.add(tileKey);

                int required = tilePassRequirements.getOrDefault(tileKey, 1);
                int currentPasses = tilePassCounts.getOrDefault(tileKey, 0);
                if (!tilesTouchedLastUpdate.contains(tileKey) && currentPasses < required) {
                    tilePassCounts.put(tileKey, currentPasses + 1);
                }
            }
        }

        tilesTouchedLastUpdate.clear();
        tilesTouchedLastUpdate.addAll(tilesTouchedThisUpdate);
    }

    private boolean circleIntersectsTile(double cx, double cy, double radius, int tileX,
            int tileY) {
        double nearestX = Math.max(tileX, Math.min(cx, tileX + 1.0));
        double nearestY = Math.max(tileY, Math.min(cy, tileY + 1.0));
        double dx = cx - nearestX;
        double dy = cy - nearestY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    private void initializeCleaningMapIfNeeded() {
        if (cleaningMapInitialized) {
            return;
        }
        cleaningMapInitialized = true;

        if (house == null || house.getRooms().isEmpty()) {
            return;
        }

        cleaningEfficiencyPerPass =
                Math.max(0.01, Math.min(1.0, house.getFloorCovering().getDefaultEfficiency()));
        int requiredPasses = toRequiredPasses(cleaningEfficiencyPerPass);

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

        Set<Long> reachableRobotSamples = computeReachableRobotSamples(minX, maxX, minY, maxY);
        if (reachableRobotSamples.isEmpty()) {
            return;
        }

        for (long sampleKey : reachableRobotSamples) {
            double sampleX = decodeSampleX(sampleKey);
            double sampleY = decodeSampleY(sampleKey);
            markReachableCleaningTiles(sampleX, sampleY, requiredPasses, startGx, endGx, startGy,
                    endGy);
        }
    }

    private Set<Long> computeReachableRobotSamples(double minX, double maxX, double minY,
            double maxY) {
        Set<Long> visited = new HashSet<>();
        if (vacuum == null) {
            return visited;
        }

        Deque<Long> frontier = new ArrayDeque<>();
        long startSampleKey = findReachableStartSample(minX, maxX, minY, maxY);
        if (startSampleKey == Long.MIN_VALUE) {
            return visited;
        }

        frontier.add(startSampleKey);
        visited.add(startSampleKey);

        int minSx = toSampleIndex(minX - vacuum.getSize());
        int maxSx = toSampleIndex(maxX);
        int minSy = toSampleIndex(minY - vacuum.getSize());
        int maxSy = toSampleIndex(maxY);

        while (!frontier.isEmpty()) {
            long current = frontier.removeFirst();
            int sampleX = decodeSampleIndexX(current);
            int sampleY = decodeSampleIndexY(current);

            enqueueReachableSample(sampleX + 1, sampleY, minSx, maxSx, minSy, maxSy, visited,
                    frontier);
            enqueueReachableSample(sampleX - 1, sampleY, minSx, maxSx, minSy, maxSy, visited,
                    frontier);
            enqueueReachableSample(sampleX, sampleY + 1, minSx, maxSx, minSy, maxSy, visited,
                    frontier);
            enqueueReachableSample(sampleX, sampleY - 1, minSx, maxSx, minSy, maxSy, visited,
                    frontier);
        }

        return visited;
    }

    private void enqueueReachableSample(int sampleX, int sampleY, int minSx, int maxSx, int minSy,
            int maxSy, Set<Long> visited, Deque<Long> frontier) {
        if (sampleX < minSx || sampleX > maxSx || sampleY < minSy || sampleY > maxSy) {
            return;
        }

        long sampleKey = encodeSampleKey(sampleX, sampleY);
        if (visited.contains(sampleKey)) {
            return;
        }

        double worldX = fromSampleIndex(sampleX);
        double worldY = fromSampleIndex(sampleY);
        if (!canRobotOccupy(worldX, worldY)) {
            return;
        }

        visited.add(sampleKey);
        frontier.addLast(sampleKey);
    }

    private long findReachableStartSample(double minX, double maxX, double minY, double maxY) {
        long directStart = encodeWorldSample(vacuum.getStartX(), vacuum.getStartY());
        if (canRobotOccupy(decodeSampleX(directStart), decodeSampleY(directStart))) {
            return directStart;
        }

        long currentStart = encodeWorldSample(vacuum.getX(), vacuum.getY());
        if (canRobotOccupy(decodeSampleX(currentStart), decodeSampleY(currentStart))) {
            return currentStart;
        }

        double searchStartX = Math.max(minX - vacuum.getSize(), vacuum.getStartX() - 1.0);
        double searchEndX = Math.min(maxX, vacuum.getStartX() + 1.0);
        double searchStartY = Math.max(minY - vacuum.getSize(), vacuum.getStartY() - 1.0);
        double searchEndY = Math.min(maxY, vacuum.getStartY() + 1.0);

        for (int sx = toSampleIndex(searchStartX); sx <= toSampleIndex(searchEndX); sx++) {
            for (int sy = toSampleIndex(searchStartY); sy <= toSampleIndex(searchEndY); sy++) {
                double worldX = fromSampleIndex(sx);
                double worldY = fromSampleIndex(sy);
                if (canRobotOccupy(worldX, worldY)) {
                    return encodeSampleKey(sx, sy);
                }
            }
        }

        return Long.MIN_VALUE;
    }

    private void markReachableCleaningTiles(double robotX, double robotY, int requiredPasses,
            int startGx, int endGx, int startGy, int endGy) {
        double cleanRadius = vacuum.getSize() * CLEANING_RADIUS_RATIO;
        double centerX = robotX + vacuum.getSize() * 0.5;
        double centerY = robotY + vacuum.getSize() * 0.5;

        int minTileX = Math.max(startGx, (int) Math.floor(centerX - cleanRadius));
        int maxTileX = Math.min(endGx, (int) Math.floor(centerX + cleanRadius));
        int minTileY = Math.max(startGy, (int) Math.floor(centerY - cleanRadius));
        int maxTileY = Math.min(endGy, (int) Math.floor(centerY + cleanRadius));

        for (int gx = minTileX; gx <= maxTileX; gx++) {
            for (int gy = minTileY; gy <= maxTileY; gy++) {
                if (!circleIntersectsTile(centerX, centerY, cleanRadius, gx, gy)) {
                    continue;
                }
                if (!isTileSurfaceCleanable(gx, gy)) {
                    continue;
                }

                long tileKey = encodeTileKey(gx, gy);
                cleanableTiles.add(tileKey);
                tilePassRequirements.put(tileKey, requiredPasses);
                tilePassCounts.putIfAbsent(tileKey, 0);
            }
        }
    }

    private boolean isTileSurfaceCleanable(int gx, int gy) {
        double centerX = gx + 0.5;
        double centerY = gy + 0.5;
        if (house.getRoomAt(centerX, centerY) == null) {
            return false;
        }

        for (Obstruction obstruction : house.getObstructions()) {
            if (obstruction.blocksCleanableArea() && obstruction.contains(centerX, centerY)) {
                return false;
            }
        }

        return true;
    }

    private boolean canRobotOccupy(double robotX, double robotY) {
        if (vacuum == null || house == null) {
            return false;
        }

        double centerX = robotX + vacuum.getSize() * 0.5;
        double centerY = robotY + vacuum.getSize() * 0.5;
        if (house.getRoomAt(centerX, centerY) == null) {
            return false;
        }

        double radius = vacuum.getSize() * 0.46;
        for (Rectangle collider : vacuum.getWallColliders()) {
            if (circleIntersectsRectangle(centerX, centerY, radius, collider)) {
                return false;
            }
        }

        return true;
    }

    private boolean circleIntersectsRectangle(double centerX, double centerY, double radius,
            Rectangle rect) {
        double nearestX = Math.max(rect.getX(), Math.min(centerX, rect.getX() + rect.getWidth()));
        double nearestY = Math.max(rect.getY(), Math.min(centerY, rect.getY() + rect.getHeight()));
        double dx = centerX - nearestX;
        double dy = centerY - nearestY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    private long encodeSampleKey(int sampleX, int sampleY) {
        return (((long) sampleX) << 32) ^ (sampleY & 0xffffffffL);
    }

    private long encodeWorldSample(double worldX, double worldY) {
        return encodeSampleKey(toSampleIndex(worldX), toSampleIndex(worldY));
    }

    private int toSampleIndex(double value) {
        return (int) Math.round(value / REACHABILITY_STEP);
    }

    private double fromSampleIndex(int sampleIndex) {
        return sampleIndex * REACHABILITY_STEP;
    }

    private int decodeSampleIndexX(long sampleKey) {
        return (int) (sampleKey >> 32);
    }

    private int decodeSampleIndexY(long sampleKey) {
        return (int) sampleKey;
    }

    private double decodeSampleX(long sampleKey) {
        return fromSampleIndex(decodeSampleIndexX(sampleKey));
    }

    private double decodeSampleY(long sampleKey) {
        return fromSampleIndex(decodeSampleIndexY(sampleKey));
    }

    public double getCleanedArea() {
        double cleanedArea = 0.0;
        for (long tileKey : cleanableTiles) {
            int required = tilePassRequirements.getOrDefault(tileKey, 1);
            int passes = tilePassCounts.getOrDefault(tileKey, 0);
            double progress = Math.min(1.0, (double) passes / Math.max(1, required));
            cleanedArea += progress * TILE_SIZE * TILE_SIZE;
        }
        return cleanedArea;

    }

    public double getComputedCleanableArea() {
        initializeCleaningMapIfNeeded();
        return cleanableTiles.size() * TILE_SIZE * TILE_SIZE;
    }

    public double getNonCleanableArea() {
        if (house == null) {
            return 0.0;
        }
        return Math.max(0.0, house.getTotalArea() - getComputedCleanableArea());
    }

    public boolean isHouseFullyCleaned() {
        initializeCleaningMapIfNeeded();
        if (cleanableTiles.isEmpty()) {
            return false;
        }
        return getCleanedArea() >= getComputedCleanableArea();
    }

    private void renderCleaningMap() {
        if (!cleaningHeatMapVisible) {
            return;
        }

        initializeCleaningMapIfNeeded();
        if (cleanableTiles.isEmpty()) {
            return;
        }

        for (long tileKey : cleanableTiles) {
            int required = tilePassRequirements.getOrDefault(tileKey, 1);
            int passes = tilePassCounts.getOrDefault(tileKey, 0);
            double progress = Math.min(1.0, (double) passes / Math.max(1, required));

            int gx = decodeTileX(tileKey);
            int gy = decodeTileY(tileKey);

            Rectangle tile = new Rectangle();
            tile.setX(offsetX + gx * scale);
            tile.setY(offsetY + gy * scale);
            tile.setWidth(TILE_SIZE * scale);
            tile.setHeight(TILE_SIZE * scale);

            tile.setFill(getCleaningHeatMapColor(progress));
            tile.setMouseTransparent(true);
            this.getChildren().add(tile);
        }
    }

    private Color getCleaningHeatMapColor(double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));

        Color dirtyBlue = Color.web("#2f1eff");
        Color midTone = Color.web("#7a5a7e");
        Color cleanWarm = Color.web("#e6a03a");

        Color base;
        if (t <= 0.5) {
            base = dirtyBlue.interpolate(midTone, t / 0.5);
        } else {
            base = midTone.interpolate(cleanWarm, (t - 0.5) / 0.5);
        }

        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.48);
    }

    private int toRequiredPasses(double efficiency) {
        double clamped = Math.max(0.01, Math.min(1.0, efficiency));
        // Each pass contributes floor efficiency worth of cleaning.
        return Math.max(1, (int) Math.ceil(1.0 / clamped));
    }

    private long encodeTileKey(int gx, int gy) {
        return (((long) gx) << 32) ^ (gy & 0xffffffffL);
    }

    private int decodeTileX(long key) {
        return (int) (key >> 32);
    }

    private int decodeTileY(long key) {
        return (int) key;
    }

    private void renderCanvasBackground(double canvasWidth, double canvasHeight) {
        Rectangle background = new Rectangle(0, 0, canvasWidth, canvasHeight);
        background.setFill(darkMode ? Color.web("#1e2c38") : Color.web("#fdfdfd"));
        background.setStroke(Color.TRANSPARENT);
        background.setMouseTransparent(true);
        this.getChildren().add(background);
    }

    private void renderGrid() {
        if (house == null || house.getRooms().isEmpty()) {
            return;
        }

        Color gridColor = editMode ? (darkMode ? Color.web("#2e7cae") : Color.web("#9dcfd3"))
                : (darkMode ? Color.web("#2a3d4c") : Color.web("#d4dee4"));

        for (Room room : house.getRooms()) {
            long firstGx = (long) Math.ceil(room.getX());
            long lastGx = (long) Math.floor(room.getMaxX());
            double top = offsetY + room.getY() * scale;
            double bottom = offsetY + room.getMaxY() * scale;
            for (long gx = firstGx; gx <= lastGx; gx++) {
                double px = offsetX + gx * scale;
                Line line = new Line(px, top, px, bottom);
                line.setStroke(gridColor);
                line.setStrokeWidth(0.5);
                line.setMouseTransparent(true);
                this.getChildren().add(line);
            }

            long firstGy = (long) Math.ceil(room.getY());
            long lastGy = (long) Math.floor(room.getMaxY());
            double left = offsetX + room.getX() * scale;
            double right = offsetX + room.getMaxX() * scale;
            for (long gy = firstGy; gy <= lastGy; gy++) {
                double py = offsetY + gy * scale;
                Line line = new Line(left, py, right, py);
                line.setStroke(gridColor);
                line.setStrokeWidth(0.5);
                line.setMouseTransparent(true);
                this.getChildren().add(line);
            }
        }
    }

    private void updateTooltipStyle() {
        Color bg = darkMode ? Color.rgb(30, 44, 56, 0.95) : Color.rgb(243, 246, 248, 0.95);
        hoverTooltip.setBackground(
                new Background(new BackgroundFill(bg, new CornerRadii(8), Insets.EMPTY)));
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        render();
    }

    private ImagePattern getCurrentFloorPattern() {
        House.FloorCovering covering = house.getFloorCovering();
        FlooringTypes floorType;
        try {
            floorType = FlooringTypes.valueOf(covering.name());
        } catch (IllegalArgumentException ex) {
            floorType = FlooringTypes.HARDWOOD;
        }
        double tilePixels = Math.max(4.0, FLOOR_TEXTURE_TILE_FEET * scale);
        return new ImagePattern(floorType.getFloor(), 0, 0, tilePixels, tilePixels, false);
    }

    private void renderRooms() {
        ImagePattern floorPattern = getCurrentFloorPattern();
        Color wallColor = darkMode ? DARK_WALL_COLOR : WALL_COLOR;
        Color hoverColor = darkMode ? DARK_HOVER_COLOR : HOVER_COLOR;
        Color selectionColor = darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR;

        for (Room room : house.getRooms()) {
            Rectangle rect = new Rectangle();

            rect.setX(offsetX + room.getX() * scale);
            rect.setY(offsetY + room.getY() * scale);
            rect.setWidth(room.getWidth() * scale);
            rect.setHeight(room.getHeight() * scale);

            rect.setFill(floorPattern);

            rect.setStroke(wallColor);
            rect.setStrokeWidth(2.0);

            rect.setUserData(room);

            boolean isSelected = (selectedRoomModel == room);
            if (isSelected) {
                rect.setStroke(selectionColor);
                rect.setStrokeWidth(4.0);
                selectedRoomRect = rect;
            }

            rect.setOnMouseEntered(e -> {
                if (addObstructionMode) {
                    return; // Don't show hover effects when in obstruction placement mode
                }
                if (selectedRoomModel != room) {
                    rect.setStroke(darkMode ? DARK_HOVER_COLOR : HOVER_COLOR);
                    rect.setStrokeWidth(3.0);
                }
                currentHoverRoom = room;
                showRoomTooltip(room, e.getSceneX(), e.getSceneY());
            });

            rect.setOnMouseExited(e -> {
                if (addObstructionMode) {
                    return; // Don't show hover effects when in obstruction placement mode
                }
                if (selectedRoomModel != room) {
                    rect.setStroke(darkMode ? DARK_WALL_COLOR : WALL_COLOR);
                    rect.setStrokeWidth(2.0);
                }
                currentHoverRoom = null;
                hoverTooltip.setVisible(false);
            });

            rect.setOnMouseMoved(e -> {
                if (currentHoverRoom == room) {
                    positionTooltip(e.getSceneX(), e.getSceneY());
                }
            });

            rect.setOnMouseClicked(e -> {
                if (editMode && !addObstructionMode) {
                    selectRoom(room, rect);
                } else if (robotPlacementHandler != null) {
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    robotPlacementHandler.accept(model);
                }
                e.consume();
            });

            rect.setOnMousePressed(e -> {
                if (editMode && addObstructionMode) {
                    isDraggingObstruction = true;
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    drawStartModelX = model.getX();
                    drawStartModelY = model.getY();
                    updateGhostRect(drawStartModelX, drawStartModelY, 1.0, 1.0);
                    e.consume();
                }
            });

            rect.setOnMouseDragged(e -> {
                if (isDraggingObstruction) {
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    double x = Math.min(drawStartModelX, model.getX());
                    double y = Math.min(drawStartModelY, model.getY());
                    double w = Math.max(2.0, Math.abs(model.getX() - drawStartModelX));
                    double h = Math.max(2.0, Math.abs(model.getY() - drawStartModelY));

                    // Constrain the ghost rect to the room bounds
                    x = Math.max(x, room.getX());
                    y = Math.max(y, room.getY());
                    w = Math.min(w, room.getX() + room.getWidth() - x);
                    h = Math.min(h, room.getY() + room.getHeight() - y);
                    updateGhostRect(x, y, w, h);
                }
                e.consume();
            });

            rect.setOnMouseReleased(e -> {
                if (isDraggingObstruction) {
                    isDraggingObstruction = false;
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    double x = Math.min(drawStartModelX, model.getX());
                    double y = Math.min(drawStartModelY, model.getY());
                    double w = Math.max(2.0, Math.abs(model.getX() - drawStartModelX));
                    double h = Math.max(2.0, Math.abs(model.getY() - drawStartModelY));

                    // Constrain the ghost rect to the room bounds
                    x = Math.max(x, room.getX());
                    y = Math.max(y, room.getY());
                    w = Math.min(w, room.getX() + room.getWidth() - x);
                    h = Math.min(h, room.getY() + room.getHeight() - y);

                    placeNewObstruction(room, x, y, w, h);
                    ghostRect.setVisible(false);
                }
                e.consume();
            });

            this.getChildren().add(rect);
        }
    }

    private void selectRoom(Room room, Rectangle rect) {
        deselectObstruction();

        if (selectedRoomRect != null && selectedRoomRect != rect) {
            selectedRoomRect.setStroke(darkMode ? DARK_WALL_COLOR : WALL_COLOR);
            selectedRoomRect.setStrokeWidth(2.0);
        }

        selectedRoomModel = room;
        selectedRoomRect = rect;
        rect.setStroke(darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR);
        rect.setStrokeWidth(4.0);

        updateResizeHandles(rect);
    }

    private void selectObstruction(Obstruction obstruction, Rectangle rect) {
        deselectRoom();

        selectedObstructionModel = obstruction;
        selectedObstructionRect = rect;
        rect.setStroke(darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR);
        rect.setStrokeWidth(3.0);
    }

    private void updateResizeHandles(Rectangle rect) {
        double x = rect.getX();
        double y = rect.getY();
        double w = rect.getWidth();
        double h = rect.getHeight();

        leftHandle.setCenterX(x);
        leftHandle.setCenterY(y + h / 2);
        leftHandle.setVisible(true);

        rightHandle.setCenterX(x + w);
        rightHandle.setCenterY(y + h / 2);
        rightHandle.setVisible(true);

        topHandle.setCenterX(x + w / 2);
        topHandle.setCenterY(y);
        topHandle.setVisible(true);

        bottomHandle.setCenterX(x + w / 2);
        bottomHandle.setCenterY(y + h);
        bottomHandle.setVisible(true);
    }

    private void updateSelectedRoom() {
        if (selectedRoomRect != null) {
            updateResizeHandles(selectedRoomRect);
        }
    }

    private void showRoomTooltip(Room room, double x, double y) {
        hoverTooltip.getChildren().clear();

        Label idLabel = new Label("Room ID: " + room.getId().substring(0, 8));
        idLabel.getStyleClass().add("room-tooltip-title");

        Label posLabel =
                new Label(String.format("Position: (%.1f, %.1f)", room.getX(), room.getY()));
        Label sizeLabel =
                new Label(String.format("Size: %.1f × %.1f m", room.getWidth(), room.getHeight()));
        Label areaLabel = new Label(String.format("Area: %.1f m²", room.getArea()));
        Label doorsLabel = new Label("Doors: " + room.getDoors().size());

        posLabel.getStyleClass().add("room-tooltip-detail");
        sizeLabel.getStyleClass().add("room-tooltip-detail");
        areaLabel.getStyleClass().add("room-tooltip-detail");
        doorsLabel.getStyleClass().add("room-tooltip-detail");

        hoverTooltip.getChildren().addAll(idLabel, posLabel, sizeLabel, areaLabel, doorsLabel);
        positionTooltip(x, y);
        hoverTooltip.setVisible(true);
        hoverTooltip.toFront();
    }

    private void positionTooltip(double sceneX, double sceneY) {
        Point2D localPoint = this.sceneToLocal(sceneX, sceneY);
        hoverTooltip.applyCss();
        hoverTooltip.autosize();

        double margin = 12.0;
        double width = hoverTooltip.prefWidth(-1);
        double height = hoverTooltip.prefHeight(-1);

        double desiredX = localPoint.getX() + 16;
        double desiredY = localPoint.getY() + 16;

        double maxX = Math.max(margin, getWidth() - width - margin);
        double maxY = Math.max(margin, getHeight() - height - margin);

        hoverTooltip.setLayoutX(Math.min(maxX, Math.max(margin, desiredX)));
        hoverTooltip.setLayoutY(Math.min(maxY, Math.max(margin, desiredY)));
    }

    private void resizeRoom(double currentX, double currentY) {
        if (selectedRoomModel == null || resizeEdge == null) {
            return;
        }

        double deltaX = (currentX - dragStartX) / scale;
        double deltaY = (currentY - dragStartY) / scale;

        double newX = originalX;
        double newY = originalY;
        double newWidth = originalWidth;
        double newHeight = originalHeight;

        if (resizeEdge.equals("left")) {
            double snappedX = snapToGrid(originalX + deltaX);
            newX = snappedX;
            newWidth = originalX + originalWidth - snappedX;
        } else if (resizeEdge.equals("right")) {
            newWidth = snapToGrid(originalWidth + deltaX);
        } else if (resizeEdge.equals("top")) {
            double snappedY = snapToGrid(originalY + deltaY);
            newY = snappedY;
            newHeight = originalY + originalHeight - snappedY;
        } else if (resizeEdge.equals("bottom")) {
            newHeight = snapToGrid(originalHeight + deltaY);
        }

        if (newWidth <= 0 || newHeight <= 0) {
            reportResizeWarning("Resize would make room dimensions non-positive");
            return;
        }

        String resizeFailure = applyValidatedResize(newX, newY, newWidth, newHeight);
        if (resizeFailure == null) {
            lastResizeWarning = null;
            resizeAppliedDuringDrag = true;
            render();
            return;
        }

        reportResizeWarning(resizeFailure);
    }

    private String applyValidatedResize(double newX, double newY, double newWidth,
            double newHeight) {
        Room room = selectedRoomModel;

        double priorX = room.getX();
        double priorY = room.getY();
        double priorWidth = room.getWidth();
        double priorHeight = room.getHeight();

        List<DoorPositionSnapshot> priorDoorPositions = snapshotDoorPositions(room);
        List<DoorAnchor> doorAnchors =
                captureDoorAnchors(room, priorX, priorY, priorWidth, priorHeight);

        try {
            room.setDimensions(newX, newY, newWidth, newHeight);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        String collisionError = checkForCollision(room);
        if (collisionError != null) {
            rollbackResize(room, priorX, priorY, priorWidth, priorHeight, priorDoorPositions);
            return collisionError;
        }

        String doorMoveError = repositionConnectedDoors(room, doorAnchors);
        if (doorMoveError != null) {
            rollbackResize(room, priorX, priorY, priorWidth, priorHeight, priorDoorPositions);
            return doorMoveError;
        }

        String resizeValidationError = validateResizeState(room);
        if (resizeValidationError != null) {
            rollbackResize(room, priorX, priorY, priorWidth, priorHeight, priorDoorPositions);
            return resizeValidationError;
        }

        house.refreshSeedFromState();

        return null;
    }

    private String validateResizeState(Room resizedRoom) {
        double totalArea = house.getTotalArea();
        if (totalArea < House.MIN_TOTAL_AREA) {
            return String.format("Resize would put house area below minimum (%.0f m²)",
                    House.MIN_TOTAL_AREA);
        }
        if (totalArea > House.MAX_TOTAL_AREA) {
            return String.format("Resize would put house area above maximum (%.0f m²)",
                    House.MAX_TOTAL_AREA);
        }

        if (resizedRoom.getArea() < Room.MIN_AREA) {
            return String.format("Resize would make room area below minimum (%.0f m²)",
                    Room.MIN_AREA);
        }

        for (Door door : house.getDoors()) {
            if (!door.isValidPosition()) {
                return "Resize would move a door off its shared wall";
            }
        }

        return null;
    }

    private void rollbackResize(Room room, double priorX, double priorY, double priorWidth,
            double priorHeight, List<DoorPositionSnapshot> priorDoorPositions) {
        room.setDimensions(priorX, priorY, priorWidth, priorHeight);
        restoreDoorPositions(priorDoorPositions);
    }

    private List<DoorPositionSnapshot> snapshotDoorPositions(Room room) {
        List<DoorPositionSnapshot> snapshots = new ArrayList<>();
        for (Door door : room.getDoors()) {
            snapshots.add(new DoorPositionSnapshot(door, door.getX(), door.getY()));
        }
        return snapshots;
    }

    private void restoreDoorPositions(List<DoorPositionSnapshot> snapshots) {
        for (DoorPositionSnapshot snapshot : snapshots) {
            snapshot.door.setPosition(snapshot.x, snapshot.y);
        }
    }

    private String checkForCollision(Room candidateRoom) {
        for (Room other : house.getRooms()) {
            if (other == candidateRoom) {
                continue;
            }
            if (roomsIntersect(candidateRoom, other)) {
                return "Resize would overlap room " + other.getId().substring(0, 8);
            }
        }
        return null;
    }

    private boolean roomsIntersect(Room a, Room b) {
        if (a.getMaxX() <= b.getX() || b.getMaxX() <= a.getX()) {
            return false;
        }
        if (a.getMaxY() <= b.getY() || b.getMaxY() <= a.getY()) {
            return false;
        }

        boolean xOverlap = Math.max(a.getX(), b.getX()) < Math.min(a.getMaxX(), b.getMaxX());
        boolean yOverlap = Math.max(a.getY(), b.getY()) < Math.min(a.getMaxY(), b.getMaxY());
        return xOverlap && yOverlap;
    }

    private List<DoorAnchor> captureDoorAnchors(Room room, double roomX, double roomY,
            double roomWidth, double roomHeight) {
        List<DoorAnchor> anchors = new ArrayList<>();
        double roomMaxX = roomX + roomWidth;
        double roomMaxY = roomY + roomHeight;

        for (Door door : room.getDoors()) {
            Room other = door.getOtherRoom(room);
            if (other == null) {
                continue;
            }

            if (door.getOrientation() == Door.Orientation.VERTICAL) {
                double overlapStart = Math.max(roomY, other.getY());
                double overlapEnd = Math.min(roomMaxY, other.getMaxY()) - door.getWidth();
                anchors.add(new DoorAnchor(door,
                        normalizedProgress(door.getY(), overlapStart, overlapEnd)));
            } else {
                double overlapStart = Math.max(roomX, other.getX());
                double overlapEnd = Math.min(roomMaxX, other.getMaxX()) - door.getWidth();
                anchors.add(new DoorAnchor(door,
                        normalizedProgress(door.getX(), overlapStart, overlapEnd)));
            }
        }

        return anchors;
    }

    private String repositionConnectedDoors(Room room, List<DoorAnchor> doorAnchors) {
        for (DoorAnchor anchor : doorAnchors) {
            Door door = anchor.door;
            Room other = door.getOtherRoom(room);
            if (other == null) {
                return "Resize detached a door from the selected room";
            }

            if (door.getOrientation() == Door.Orientation.VERTICAL) {
                Double boundaryX = sharedVerticalBoundary(room, other);
                if (boundaryX == null) {
                    return "Resize removed a shared wall for a vertical door";
                }

                double overlapStart = Math.max(room.getY(), other.getY());
                double overlapEnd = Math.min(room.getMaxY(), other.getMaxY()) - door.getWidth();
                if (overlapEnd + GEOMETRY_EPSILON < overlapStart) {
                    return "Resize shrank a shared wall below door width";
                }

                double adjustedY = interpolate(overlapStart, overlapEnd, anchor.progress);
                door.setPosition(boundaryX, adjustedY);
            } else {
                Double boundaryY = sharedHorizontalBoundary(room, other);
                if (boundaryY == null) {
                    return "Resize removed a shared wall for a horizontal door";
                }

                double overlapStart = Math.max(room.getX(), other.getX());
                double overlapEnd = Math.min(room.getMaxX(), other.getMaxX()) - door.getWidth();
                if (overlapEnd + GEOMETRY_EPSILON < overlapStart) {
                    return "Resize shrank a shared wall below door width";
                }

                double adjustedX = interpolate(overlapStart, overlapEnd, anchor.progress);
                door.setPosition(adjustedX, boundaryY);
            }

            if (!door.isValidPosition()) {
                return "Resize places a door off its shared wall";
            }
        }

        return null;
    }

    private Double sharedVerticalBoundary(Room a, Room b) {
        if (Math.abs(a.getMaxX() - b.getX()) < GEOMETRY_EPSILON) {
            return (a.getMaxX() + b.getX()) / 2.0;
        }
        if (Math.abs(b.getMaxX() - a.getX()) < GEOMETRY_EPSILON) {
            return (b.getMaxX() + a.getX()) / 2.0;
        }
        return null;
    }

    private Double sharedHorizontalBoundary(Room a, Room b) {
        if (Math.abs(a.getMaxY() - b.getY()) < GEOMETRY_EPSILON) {
            return (a.getMaxY() + b.getY()) / 2.0;
        }
        if (Math.abs(b.getMaxY() - a.getY()) < GEOMETRY_EPSILON) {
            return (b.getMaxY() + a.getY()) / 2.0;
        }
        return null;
    }

    private double normalizedProgress(double value, double min, double max) {
        if (max <= min + GEOMETRY_EPSILON) {
            return 0.0;
        }
        double raw = (value - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, raw));
    }

    private double interpolate(double min, double max, double progress) {
        if (max <= min + GEOMETRY_EPSILON) {
            return min;
        }
        return min + (max - min) * Math.max(0.0, Math.min(1.0, progress));
    }

    private void reportResizeWarning(String warningMessage) {
        String message = warningMessage == null || warningMessage.isBlank()
                ? "Resize would make the house invalid"
                : warningMessage;

        if (!message.equals(lastResizeWarning)) {
            notifyStatus(message);
            lastResizeWarning = message;
        }
    }

    private void notifyStatus(String message) {
        if (statusMessageHandler != null) {
            statusMessageHandler.accept(message);
        }
    }

    private void notifyHouseChanged() {
        if (houseChangedHandler != null) {
            houseChangedHandler.run();
        }
    }

    public void deselectRoom() {
        if (selectedRoomRect != null) {
            selectedRoomRect.setStroke(darkMode ? DARK_WALL_COLOR : WALL_COLOR);
            selectedRoomRect.setStrokeWidth(2.0);
        }
        selectedRoomModel = null;
        selectedRoomRect = null;

        leftHandle.setVisible(false);
        rightHandle.setVisible(false);
        topHandle.setVisible(false);
        bottomHandle.setVisible(false);
    }

    public void deselectObstruction() {
        selectedObstructionModel = null;
        selectedObstructionRect = null;
    }

    public void deselectAll() {
        deselectRoom();
        deselectObstruction();
    }

    public boolean hasSelectedObstruction() {
        return selectedObstructionModel != null;
    }

    public boolean hasSelection() {
        return selectedRoomModel != null || selectedObstructionModel != null;
    }

    public double getSelectedObstructionWidth() {
        return selectedObstructionModel != null ? selectedObstructionModel.getWidth() : 0;
    }

    public double getSelectedObstructionHeight() {
        return selectedObstructionModel != null ? selectedObstructionModel.getHeight() : 0;
    }

    public boolean resizeSelectedObstruction(double newWidth, double newHeight) {
        if (selectedObstructionModel == null) {
            notifyStatus("No obstruction is selected");
            return false;
        }
        if (newWidth <= 0 || newHeight <= 0) {
            notifyStatus("Furniture dimensions must be positive");
            return false;
        }

        Obstruction original = selectedObstructionModel;
        Room room = original.getRoom();
        if (room == null) {
            notifyStatus("Selected obstruction is not bound to a room");
            return false;
        }

        double x = original.getX();
        double y = original.getY();
        if (x < room.getX() || y < room.getY() || x + newWidth > room.getMaxX()
                || y + newHeight > room.getMaxY()) {
            notifyStatus("Resized furniture must remain inside its room");
            return false;
        }

        Obstruction replacement;
        if (original instanceof PassUnderObstruction passUnder) {
            double widthScale = newWidth / Math.max(0.01, original.getWidth());
            double heightScale = newHeight / Math.max(0.01, original.getHeight());
            double legDiameterInches = passUnder.getLegDiameter() * 12.0;
            double hSpace = Math.max(0.1, passUnder.getHSpaceBetweenLegs() * widthScale);
            double vSpace = Math.max(0.1, passUnder.getVSpaceBetweenLegs() * heightScale);
            replacement = new PassUnderObstruction(room, x, y, newWidth, newHeight,
                    legDiameterInches, hSpace, vSpace);
        } else {
            replacement = new BlockingObstruction(room, x, y, newWidth, newHeight);
        }

        house.removeObstruction(original);
        try {
            house.addObstruction(replacement);
            selectedObstructionModel = replacement;
            notifyHouseChanged();
            render();
            return true;
        } catch (IllegalArgumentException ex) {
            house.addObstruction(original);
            selectedObstructionModel = original;
            notifyStatus("Cannot resize furniture: " + ex.getMessage());
            render();
            return false;
        }
    }

    public boolean deleteSelectedElement() {
        if (selectedObstructionModel != null) {
            house.removeObstruction(selectedObstructionModel);
            deselectObstruction();
            notifyHouseChanged();
            render();
            return true;
        }

        if (selectedRoomModel != null) {
            String validationError = validateRoomDeletion(selectedRoomModel);
            if (validationError != null) {
                notifyStatus(validationError);
                return false;
            }

            Room toDelete = selectedRoomModel;
            List<Obstruction> toRemove = new ArrayList<>();
            for (Obstruction obstruction : house.getObstructions()) {
                if (obstruction.getRoom() == toDelete) {
                    toRemove.add(obstruction);
                }
            }
            for (Obstruction obstruction : toRemove) {
                house.removeObstruction(obstruction);
            }

            house.removeRoom(toDelete);
            deselectRoom();
            notifyHouseChanged();
            render();
            return true;
        }

        notifyStatus("Nothing selected");
        return false;
    }

    private String validateRoomDeletion(Room roomToDelete) {
        int remainingRoomCount = house.getRooms().size() - 1;
        if (remainingRoomCount < 1) {
            return "Cannot delete the final room";
        }

        double remainingArea = house.getTotalArea() - roomToDelete.getArea();
        if (remainingArea < House.MIN_TOTAL_AREA) {
            return String.format("Deleting this room would put house area below %.0f m²",
                    House.MIN_TOTAL_AREA);
        }

        List<Room> remainingRooms = new ArrayList<>();
        for (Room room : house.getRooms()) {
            if (room != roomToDelete) {
                remainingRooms.add(room);
            }
        }

        List<Door> remainingDoors = new ArrayList<>();
        for (Door door : house.getDoors()) {
            if (!door.connects(roomToDelete)) {
                remainingDoors.add(door);
            }
        }

        for (Room room : remainingRooms) {
            boolean hasDoor = false;
            for (Door door : remainingDoors) {
                if (door.connects(room)) {
                    hasDoor = true;
                    break;
                }
            }
            if (!hasDoor) {
                return "Deleting this room would leave another room disconnected";
            }
        }

        List<Room> frontier = new ArrayList<>();
        List<Room> visited = new ArrayList<>();
        frontier.add(remainingRooms.get(0));
        visited.add(remainingRooms.get(0));

        while (!frontier.isEmpty()) {
            Room current = frontier.remove(0);
            for (Door door : remainingDoors) {
                if (!door.connects(current)) {
                    continue;
                }
                Room next = door.getOtherRoom(current);
                if (next != null && !visited.contains(next)) {
                    visited.add(next);
                    frontier.add(next);
                }
            }
        }

        if (visited.size() != remainingRooms.size()) {
            return "Deleting this room would split the house into disconnected sections";
        }

        return null;
    }

    private void renderDoors() {
        for (Door door : house.getDoors()) {
            Line doorLine = new Line();

            double x = offsetX + door.getX() * scale;
            double y = offsetY + door.getY() * scale;
            double width = door.getWidth() * scale;

            if (door.getOrientation() == Door.Orientation.HORIZONTAL) {
                doorLine.setStartX(x);
                doorLine.setStartY(y);
                doorLine.setEndX(x + width);
                doorLine.setEndY(y);
            } else {
                doorLine.setStartX(x);
                doorLine.setStartY(y);
                doorLine.setEndX(x);
                doorLine.setEndY(y + width);
            }

            doorLine.setStroke(darkMode ? DARK_DOOR_COLOR : DOOR_COLOR);
            doorLine.setStrokeWidth(5.0);
            doorLine.setStrokeLineCap(StrokeLineCap.ROUND);
            doorLine.setMouseTransparent(true);

            this.getChildren().add(doorLine);
        }
    }

    private void renderObstructions() {
        for (Obstruction obstruction : house.getObstructions()) {
            Rectangle rect = new Rectangle();

            rect.setX(offsetX + obstruction.getX() * scale);
            rect.setY(offsetY + obstruction.getY() * scale);
            rect.setWidth(obstruction.getWidth() * scale);
            rect.setHeight(obstruction.getHeight() * scale);

            if (obstruction instanceof BlockingObstruction) {
                rect.setFill(darkMode ? Color.web("#556a78") : Color.web("#5e6a72"));
                rect.setStroke(darkMode ? Color.web("#2c3d49") : Color.web("#2f3a40"));
            } else if (obstruction instanceof PassUnderObstruction) {
                rect.setFill(darkMode ? Color.web("#2f4956") : Color.web("#ccd3d8"));
                rect.setStroke(darkMode ? Color.web("#88b9cf") : Color.web("#67747d"));
                rect.getStrokeDashArray().addAll(5.0, 5.0);
            }

            boolean isSelectedObstruction = selectedObstructionModel == obstruction;
            if (isSelectedObstruction) {
                rect.setStroke(darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR);
                rect.setStrokeWidth(3.0);
                selectedObstructionRect = rect;
            } else {
                rect.setStrokeWidth(1.5);
            }

            rect.setMouseTransparent(false);

            rect.setOnMouseClicked(e -> {
                if (editMode && !addObstructionMode) {
                    selectObstruction(obstruction, rect);
                    notifyStatus("Selected obstruction");
                } else if (robotPlacementHandler != null) {
                    Point2D model = sceneToModel(e.getSceneX(), e.getSceneY());
                    robotPlacementHandler.accept(model);
                }
                e.consume();
            });

            this.getChildren().add(rect);
        }
    }

    private Color getFloorCoveringColor(House.FloorCovering covering) {
        switch (covering) {
            case HARDWOOD:
                return Color.WHEAT;
            case TILE:
                return Color.SANDYBROWN;
            case LAMINATE:
                return Color.TAN;
            case BERBERPILE:
                return Color.BURLYWOOD;
            case CUTPILE:
                return Color.LIGHTYELLOW;
            case CALIFORNIASHAG:
                return Color.LIGHTGRAY;
            default:
                return Color.LIGHTGRAY;
        }
    }

    private void renderVacuum() {
        ImageView vImageView = vacuum.getImageView();
        vImageView.setX(offsetX + vacuum.getX() * scale);
        vImageView.setY(offsetY + vacuum.getY() * scale);
        vImageView.setFitWidth(vacuum.getSize() * scale);
        vImageView.setFitHeight(vacuum.getSize() * scale);
        vImageView.setRotate(this.vacuum.getOrientation());
        this.getChildren().add(vImageView);
    }

    private void renderColliders() { // render colliders for debugging
        Rectangle vHitbox = new Rectangle();
        vHitbox.setX(offsetX + vacuum.getX() * scale);
        vHitbox.setY(offsetY + vacuum.getY() * scale);
        vHitbox.setWidth(vacuum.getSize() * scale);
        vHitbox.setHeight(vacuum.getSize() * scale);
        vHitbox.setRotate(vacuum.getOrientation());
        vHitbox.setFill(Color.TRANSPARENT);
        vHitbox.setStroke(Color.BLACK);
        vHitbox.setStrokeWidth(1);
        this.getChildren().add(vHitbox);
        for (Rectangle houseWall : vacuum.getWallColliders()) {
            Rectangle wallRender = new Rectangle();
            wallRender.setX(offsetX + houseWall.getX() * scale);
            wallRender.setY(offsetY + houseWall.getY() * scale);
            wallRender.setWidth(houseWall.getWidth() * scale);
            wallRender.setHeight(houseWall.getHeight() * scale);
            wallRender.setFill(Color.RED);
            wallRender.setStroke(Color.RED);
            wallRender.setStrokeWidth(1);
            this.getChildren().add(wallRender);
        }
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setScale(double scale) {
        this.scale = scale;
        render();
    }

    public double getScale() {
        return scale;
    }

    public House getHouse() {
        return house;
    }

    private double snapToGrid(double value) {
        return Math.round(value / GRID_SNAP) * GRID_SNAP;
    }

    // ── Add-room mode ────────────────────────────────────────────────────────

    public void setAddRoomMode(boolean enabled) {
        addRoomMode = editMode && enabled;
        if (!enabled) {
            isDraggingRoom = false;
            ghostRect.setVisible(false);
        }
        setCursor(addRoomMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public void setAddObstructionMode(boolean enabled, boolean blocking) {
        addObstructionMode = editMode && enabled;
        this.addBlockingObstruction = blocking;
        if (!enabled) {
            isDraggingObstruction = false;
            ghostRect.setVisible(false);
        }
        setCursor(addObstructionMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public void setEditMode(boolean enabled) {
        this.editMode = enabled;
        if (!enabled) {
            setAddRoomMode(false);
            setAddObstructionMode(false, false);
            deselectAll();
        }
        render();
    }

    public boolean isEditMode() {
        return editMode;
    }

    private Point2D sceneToModel(double sceneX, double sceneY) {
        Point2D local = sceneToLocal(sceneX, sceneY);
        double modelX = snapToGrid((local.getX() - offsetX) / scale);
        double modelY = snapToGrid((local.getY() - offsetY) / scale);
        return new Point2D(modelX, modelY);
    }

    private void updateGhostRect(double modelX, double modelY, double modelW, double modelH) {
        ghostRect.setX(offsetX + modelX * scale);
        ghostRect.setY(offsetY + modelY * scale);
        ghostRect.setWidth(modelW * scale);
        ghostRect.setHeight(modelH * scale);
        ghostRect.setFill(darkMode ? Color.web("#1a3545") : Color.web("#d8eef0"));
        ghostRect.setStroke(darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR);
        ghostRect.setVisible(true);
    }

    private void placeNewRoom(double x, double y, double w, double h) {
        if (house == null)
            return;
        try {
            Room newRoom = new Room(x, y, w, h, "Room " + (house.getRooms().size() + 1));
            house.addRoom(newRoom);
            selectedRoomModel = newRoom;
            selectedRoomRect = null;
            notifyStatus("Added " + newRoom.getName() + " (" + (int) w + " × " + (int) h + " m)");
            notifyHouseChanged();
        } catch (IllegalArgumentException e) {
            notifyStatus("Cannot place room: " + e.getMessage());
        }
        render();
    }

    private void placeNewObstruction(Room room, double x, double y, double w, double h) {
        if (house == null)
            return;
        try {
            Obstruction newObstruction;
            if (addBlockingObstruction) {
                newObstruction = new BlockingObstruction(room, x, y, w, h);
            } else {
                newObstruction = new PassUnderObstruction(room, x, y, w, h);
            }
            house.addObstruction(newObstruction);
            notifyStatus("Added obstruction (" + (int) w + " × " + (int) h + " m)");
            notifyHouseChanged();
        } catch (IllegalArgumentException e) {
            notifyStatus("Cannot place obstruction: " + e.getMessage());
        }
        render();
    }

    // ── Shared-wall door hints ────────────────────────────────────────────────

    private void renderSharedWallHints(Room selected) {
        final double EPS = 0.02;
        Color hintColor = darkMode ? DARK_SELECTION_COLOR : SELECTION_COLOR;

        for (Room other : house.getRooms()) {
            if (other == selected)
                continue;

            double wallCoord, overlapStart, overlapEnd;
            Door.Orientation orientation;

            if (Math.abs(selected.getMaxX() - other.getX()) < EPS) {
                wallCoord = selected.getMaxX();
                overlapStart = Math.max(selected.getY(), other.getY());
                overlapEnd = Math.min(selected.getMaxY(), other.getMaxY());
                orientation = Door.Orientation.VERTICAL;
            } else if (Math.abs(selected.getX() - other.getMaxX()) < EPS) {
                wallCoord = selected.getX();
                overlapStart = Math.max(selected.getY(), other.getY());
                overlapEnd = Math.min(selected.getMaxY(), other.getMaxY());
                orientation = Door.Orientation.VERTICAL;
            } else if (Math.abs(selected.getMaxY() - other.getY()) < EPS) {
                wallCoord = selected.getMaxY();
                overlapStart = Math.max(selected.getX(), other.getX());
                overlapEnd = Math.min(selected.getMaxX(), other.getMaxX());
                orientation = Door.Orientation.HORIZONTAL;
            } else if (Math.abs(selected.getY() - other.getMaxY()) < EPS) {
                wallCoord = selected.getY();
                overlapStart = Math.max(selected.getX(), other.getX());
                overlapEnd = Math.min(selected.getMaxX(), other.getMaxX());
                orientation = Door.Orientation.HORIZONTAL;
            } else {
                continue;
            }

            // The shared segment must be wide enough for a door plus both margins
            double minShared = Door.DEFAULT_WIDTH * 2.0;
            if (overlapEnd - overlapStart < minShared)
                continue;

            // Skip if a door already exists between these two rooms
            boolean hasDoor = selected.getDoors().stream().anyMatch(d -> d.connects(other));
            if (hasDoor)
                continue;

            final Door.Orientation fOrient = orientation;
            final double fWall = wallCoord;
            final double fStart = overlapStart;
            final double fEnd = overlapEnd;
            final Room fOther = other;

            Line hintLine = new Line();
            if (orientation == Door.Orientation.VERTICAL) {
                double px = offsetX + wallCoord * scale;
                hintLine.setStartX(px);
                hintLine.setStartY(offsetY + overlapStart * scale);
                hintLine.setEndX(px);
                hintLine.setEndY(offsetY + overlapEnd * scale);
            } else {
                double py = offsetY + wallCoord * scale;
                hintLine.setStartX(offsetX + overlapStart * scale);
                hintLine.setStartY(py);
                hintLine.setEndX(offsetX + overlapEnd * scale);
                hintLine.setEndY(py);
            }
            hintLine.setStroke(hintColor);
            hintLine.setStrokeWidth(3.0);
            hintLine.getStrokeDashArray().addAll(8.0, 4.0);
            hintLine.setOpacity(0.55);
            hintLine.setCursor(Cursor.HAND);

            hintLine.setOnMouseEntered(ev -> {
                hintLine.setOpacity(1.0);
                hintLine.setStrokeWidth(5.0);
                notifyStatus("Click to add door between " + selected.getName() + " and "
                        + fOther.getName());
            });
            hintLine.setOnMouseExited(ev -> {
                hintLine.setOpacity(0.55);
                hintLine.setStrokeWidth(3.0);
            });
            hintLine.setOnMouseClicked(ev -> {
                addDoorOnSharedWall(selected, fOther, fOrient, fWall, fStart, fEnd);
                ev.consume();
            });
            this.getChildren().add(hintLine);
        }
    }

    private void addDoorOnSharedWall(Room a, Room b, Door.Orientation orientation, double wallCoord,
            double overlapStart, double overlapEnd) {
        double margin = Door.DEFAULT_WIDTH * House.DOOR_MARGIN_FACTOR;
        double dw = Door.DEFAULT_WIDTH;
        double posMin = overlapStart + margin;
        double posMax = overlapEnd - dw - margin;

        if (posMax < posMin) {
            notifyStatus("Shared wall is too short to fit a door");
            return;
        }
        double pos = (posMin + posMax) / 2.0;
        double doorX = (orientation == Door.Orientation.VERTICAL) ? wallCoord : pos;
        double doorY = (orientation == Door.Orientation.VERTICAL) ? pos : wallCoord;

        Door door = new Door(a, b, doorX, doorY, orientation);
        try {
            house.addDoor(door);
        } catch (IllegalArgumentException e) {
            a.removeDoor(door);
            b.removeDoor(door);
            notifyStatus("Cannot add door: " + e.getMessage());
            return;
        }
        notifyStatus("Added door between " + a.getName() + " and " + b.getName());
        notifyHouseChanged();
        render();
    }
}
