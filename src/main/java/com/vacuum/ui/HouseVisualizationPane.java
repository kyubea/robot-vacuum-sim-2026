package com.vacuum.ui;

import com.vacuum.model.*;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Visualization pane that renders a House with rooms, doors, and obstructions
 */
public class HouseVisualizationPane extends Pane {

    private House house;
    private double scale = 10.0;
    private double offsetX = 100;
    private double offsetY = 100;

    private VBox hoverTooltip;
    private Room currentHoverRoom;

    private Rectangle selectedRoomRect;
    private Room selectedRoomModel;
    private Circle leftHandle, rightHandle, topHandle, bottomHandle;

    private String resizeEdge;
    private double dragStartX;
    private double dragStartY;
    private double originalX, originalY, originalWidth, originalHeight;

    // Room dragging
    private boolean isDraggingRoom = false;
    private double roomDragStartX;
    private double roomDragStartY;
    private double roomOriginalX;
    private double roomOriginalY;

    // Smart resizing features
    private static final double SNAP_THRESHOLD = 0.5; // feet, for snapping to adjacent rooms
    private static final double GRID_SIZE = 1.0; // feet, for grid-based resizing
    private Rectangle resizePreview;
    private VBox dimensionLabel;
    private boolean isColliding = false;
    private Room snappedToRoom = null;

    // Validation overlay pattern
    private ImagePattern invalidPattern;

    // Grid lines for background
    private Pane gridPane;

    // Selection callbacks
    private RoomSelectionCallback onRoomSelected;
    private RoomDeselectionCallback onRoomDeselected;
    private ValidationChangeCallback onValidationChange;

    @FunctionalInterface
    public interface RoomSelectionCallback {
        void onRoomSelected(Room room);
    }

    @FunctionalInterface
    public interface RoomDeselectionCallback {
        void onRoomDeselected();
    }

    @FunctionalInterface
    public interface ValidationChangeCallback {
        void onValidationChange();
    }

    public HouseVisualizationPane() {
        this.setStyle("-fx-background-color: #f0f0f0;");

        // Set minimum and preferred size for consistent background
        this.setMinWidth(2000);
        this.setMinHeight(2000);
        this.setPrefWidth(3000);
        this.setPrefHeight(3000);

        // Create grid pane
        gridPane = new Pane();
        gridPane.setMouseTransparent(true);
        this.getChildren().add(gridPane);
        drawGrid();

        // Create invalid pattern (red diagonal pinstripes)
        invalidPattern = createInvalidPattern();

        hoverTooltip = new VBox(5);
        hoverTooltip.setPadding(new Insets(8));
        hoverTooltip.setBackground(new Background(new BackgroundFill(Color.rgb(255, 255, 220, 0.95),
                new CornerRadii(5), Insets.EMPTY)));
        hoverTooltip.setStyle("-fx-border-color: #888; -fx-border-width: 1; -fx-border-radius: 5;");
        hoverTooltip.setVisible(false);
        hoverTooltip.setMouseTransparent(true);
        this.getChildren().add(hoverTooltip);

        // Initialize resize preview rectangle
        resizePreview = new Rectangle();
        resizePreview.setFill(Color.TRANSPARENT);
        resizePreview.setStroke(Color.BLUE);
        resizePreview.setStrokeWidth(2.0);
        resizePreview.getStrokeDashArray().addAll(5.0, 5.0);
        resizePreview.setVisible(false);
        resizePreview.setMouseTransparent(true);
        this.getChildren().add(resizePreview);

        // Initialize dimension label
        dimensionLabel = new VBox(2);
        dimensionLabel.setPadding(new Insets(5, 8, 5, 8));
        dimensionLabel.setBackground(new Background(
                new BackgroundFill(Color.rgb(50, 50, 50, 0.85), new CornerRadii(3), Insets.EMPTY)));
        dimensionLabel.setVisible(false);
        dimensionLabel.setMouseTransparent(true);
        this.getChildren().add(dimensionLabel);

        leftHandle = createResizeHandle();
        rightHandle = createResizeHandle();
        topHandle = createResizeHandle();
        bottomHandle = createResizeHandle();

        setupResizeHandle(leftHandle, "left", Cursor.W_RESIZE);
        setupResizeHandle(rightHandle, "right", Cursor.E_RESIZE);
        setupResizeHandle(topHandle, "top", Cursor.N_RESIZE);
        setupResizeHandle(bottomHandle, "bottom", Cursor.S_RESIZE);
    }

    /**
     * Create a red pinstripe pattern for invalid rooms
     */
    private ImagePattern createInvalidPattern() {
        int size = 20;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Transparent background
        gc.setFill(Color.TRANSPARENT);
        gc.fillRect(0, 0, size, size);

        // Red diagonal pinstripes
        gc.setStroke(Color.rgb(255, 0, 0, 0.15));
        gc.setLineWidth(1.5);

        // Draw diagonal lines
        for (int i = -size; i < size * 2; i += 6) {
            gc.strokeLine(i, 0, i + size, size);
        }

        WritableImage image = new WritableImage(size, size);
        canvas.snapshot(null, image);
        return new ImagePattern(image, 0, 0, size, size, false);
    }

    /**
     * Draw grid lines in the background
     */
    private void drawGrid() {
        gridPane.getChildren().clear();

        // Grid extends to a large area
        double gridWidth = 3000;
        double gridHeight = 3000;
        double gridSpacing = GRID_SIZE * scale; // 1 foot grid

        // Draw vertical lines
        for (double x = offsetX; x < gridWidth; x += gridSpacing) {
            Line line = new Line(x, 0, x, gridHeight);
            line.setStroke(Color.rgb(200, 200, 200, 0.3));
            line.setStrokeWidth(0.5);
            gridPane.getChildren().add(line);
        }

        // Draw horizontal lines
        for (double y = offsetY; y < gridHeight; y += gridSpacing) {
            Line line = new Line(0, y, gridWidth, y);
            line.setStroke(Color.rgb(200, 200, 200, 0.3));
            line.setStrokeWidth(0.5);
            gridPane.getChildren().add(line);
        }

        // Draw thicker lines every 10 feet
        double majorGridSpacing = 10.0 * scale;

        // Major vertical lines
        for (double x = offsetX; x < gridWidth; x += majorGridSpacing) {
            Line line = new Line(x, 0, x, gridHeight);
            line.setStroke(Color.rgb(180, 180, 180, 0.5));
            line.setStrokeWidth(1.0);
            gridPane.getChildren().add(line);
        }

        // Major horizontal lines
        for (double y = offsetY; y < gridHeight; y += majorGridSpacing) {
            Line line = new Line(0, y, gridWidth, y);
            line.setStroke(Color.rgb(180, 180, 180, 0.5));
            line.setStrokeWidth(1.0);
            gridPane.getChildren().add(line);
        }

        gridPane.toBack();
    }

    private Circle createResizeHandle() {
        Circle handle = new Circle(6);
        handle.setFill(Color.WHITE);
        handle.setStroke(Color.BLUE);
        handle.setStrokeWidth(2);
        handle.setVisible(false);
        this.getChildren().add(handle);
        return handle;
    }

    private void setupResizeHandle(Circle handle, String edge, Cursor cursor) {
        handle.setCursor(cursor);

        handle.setOnMouseEntered(e -> {
            handle.setFill(Color.LIGHTBLUE);
            handle.setRadius(7);
        });

        handle.setOnMouseExited(e -> {
            handle.setFill(Color.WHITE);
            handle.setRadius(6);
        });

        handle.setOnMousePressed(e -> {
            resizeEdge = edge;
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
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
                resizeRoomSmart(e.getSceneX(), e.getSceneY());
            }
            e.consume();
        });

        handle.setOnMouseReleased(e -> {
            if (resizeEdge != null) {
                applyResize();
            }
            resizeEdge = null;
            resizePreview.setVisible(false);
            dimensionLabel.setVisible(false);
            isColliding = false;
            snappedToRoom = null;
            e.consume();
        });
    }

    public void setHouse(House house) {
        this.house = house;
        render();
    }

    public void setOnRoomSelected(RoomSelectionCallback callback) {
        this.onRoomSelected = callback;
    }

    public void setOnRoomDeselected(RoomDeselectionCallback callback) {
        this.onRoomDeselected = callback;
    }

    public void setOnValidationChange(ValidationChangeCallback callback) {
        this.onValidationChange = callback;
    }

    public void render() {
        this.getChildren().retainAll(hoverTooltip, leftHandle, rightHandle, topHandle, bottomHandle,
                resizePreview, dimensionLabel, gridPane);

        if (house == null) {
            return;
        }

        // Don't resize pane - keep it at fixed large size for consistent background
        // The ScrollPane will handle scrolling to view all content

        renderRooms();
        renderDoors();
        renderObstructions();

        if (selectedRoomModel != null) {
            updateSelectedRoom();
        }

        // Ensure grid stays at back
        gridPane.toBack();

        hoverTooltip.toFront();
        resizePreview.toFront();
        dimensionLabel.toFront();
        leftHandle.toFront();
        rightHandle.toFront();
        topHandle.toFront();
        bottomHandle.toFront();
    }

    private void renderRooms() {
        for (Room room : house.getRooms()) {
            Rectangle rect = new Rectangle();

            rect.setX(offsetX + room.getX() * scale);
            rect.setY(offsetY + room.getY() * scale);
            rect.setWidth(room.getWidth() * scale);
            rect.setHeight(room.getHeight() * scale);

            Color fillColor = getFloorCoveringColor(house.getFloorCovering());
            rect.setFill(fillColor);
            rect.setStroke(Color.BLACK);
            rect.setStrokeWidth(2.0);

            rect.setUserData(room);

            boolean isSelected = (selectedRoomModel == room);
            if (isSelected) {
                rect.setStroke(Color.rgb(0, 100, 255));
                rect.setStrokeWidth(4.0);
                selectedRoomRect = rect;
            }

            // Add the room rectangle first
            this.getChildren().add(rect);

            // Add validation overlay for invalid rooms - AFTER adding rect so it's on top
            boolean isValid = isRoomValid(room);
            if (!isValid) {
                Rectangle overlay = new Rectangle();
                overlay.setX(rect.getX());
                overlay.setY(rect.getY());
                overlay.setWidth(rect.getWidth());
                overlay.setHeight(rect.getHeight());
                overlay.setFill(invalidPattern);
                overlay.setMouseTransparent(true);
                this.getChildren().add(overlay);
            }

            rect.setOnMouseEntered(e -> {
                if (selectedRoomModel != room) {
                    rect.setStroke(Color.rgb(100, 150, 255));
                    rect.setStrokeWidth(3.0);
                }
                currentHoverRoom = room;
                showRoomTooltip(room, e.getSceneX(), e.getSceneY());
            });

            rect.setOnMouseExited(e -> {
                if (selectedRoomModel != room) {
                    rect.setStroke(Color.BLACK);
                    rect.setStrokeWidth(2.0);
                }
                currentHoverRoom = null;
                hoverTooltip.setVisible(false);
            });

            rect.setOnMouseMoved(e -> {
                if (currentHoverRoom == room) {
                    hoverTooltip.setLayoutX(e.getSceneX() + 15);
                    hoverTooltip.setLayoutY(e.getSceneY() + 15);
                }
            });

            rect.setOnMouseClicked(e -> {
                selectRoom(room, rect);
                e.consume();
            });

            rect.setOnMousePressed(e -> {
                if (selectedRoomModel == room) {
                    // Start dragging selected room
                    isDraggingRoom = true;
                    roomDragStartX = e.getSceneX();
                    roomDragStartY = e.getSceneY();
                    roomOriginalX = room.getX();
                    roomOriginalY = room.getY();
                    e.consume();
                }
            });

            rect.setOnMouseDragged(e -> {
                if (isDraggingRoom && selectedRoomModel == room) {
                    double deltaX = (e.getSceneX() - roomDragStartX) / scale;
                    double deltaY = (e.getSceneY() - roomDragStartY) / scale;

                    double newX = roomOriginalX + deltaX;
                    double newY = roomOriginalY + deltaY;

                    // Apply grid snapping
                    newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
                    newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;

                    // Check for collisions
                    boolean collision =
                            checkCollision(newX, newY, room.getWidth(), room.getHeight(), room);

                    if (!collision) {
                        // Update preview position
                        rect.setX(offsetX + newX * scale);
                        rect.setY(offsetY + newY * scale);
                        updateResizeHandles(rect);
                    }
                    e.consume();
                }
            });

            rect.setOnMouseReleased(e -> {
                if (isDraggingRoom && selectedRoomModel == room) {
                    double deltaX = (e.getSceneX() - roomDragStartX) / scale;
                    double deltaY = (e.getSceneY() - roomDragStartY) / scale;

                    double newX = roomOriginalX + deltaX;
                    double newY = roomOriginalY + deltaY;

                    // Apply grid snapping
                    newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
                    newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;

                    // Check for collisions
                    boolean collision =
                            checkCollision(newX, newY, room.getWidth(), room.getHeight(), room);

                    if (!collision) {
                        try {
                            // Store old position for door adjustment
                            double oldX = room.getX();
                            double oldY = room.getY();

                            room.setDimensions(newX, newY, room.getWidth(), room.getHeight());

                            // Update door positions
                            updateDoorsForMovedRoom(room, oldX, oldY);

                            render();

                            // Trigger validation change callback
                            if (onValidationChange != null) {
                                onValidationChange.onValidationChange();
                            }
                        } catch (IllegalArgumentException ex) {
                            // Invalid position, revert
                            render();
                        }
                    }

                    isDraggingRoom = false;
                    e.consume();
                }
            });
        }
    }

    private void selectRoom(Room room, Rectangle rect) {
        if (selectedRoomRect != null && selectedRoomRect != rect) {
            selectedRoomRect.setStroke(Color.BLACK);
            selectedRoomRect.setStrokeWidth(2.0);
        }

        selectedRoomModel = room;
        selectedRoomRect = rect;
        rect.setStroke(Color.rgb(0, 100, 255));
        rect.setStrokeWidth(4.0);

        updateResizeHandles(rect);

        // Trigger callback
        if (onRoomSelected != null) {
            onRoomSelected.onRoomSelected(room);
        }
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
        idLabel.setStyle("-fx-font-weight: bold;");

        Label posLabel =
                new Label(String.format("Position: (%.1f, %.1f)", room.getX(), room.getY()));
        Label sizeLabel =
                new Label(String.format("Size: %.1f × %.1f ft", room.getWidth(), room.getHeight()));
        Label areaLabel = new Label(String.format("Area: %.1f ft²", room.getArea()));
        Label doorsLabel = new Label("Doors: " + room.getDoors().size());

        hoverTooltip.getChildren().addAll(idLabel, posLabel, sizeLabel, areaLabel, doorsLabel);
        hoverTooltip.setLayoutX(x + 15);
        hoverTooltip.setLayoutY(y + 15);
        hoverTooltip.setVisible(true);
    }

    /**
     * Smart resize with collision detection and snap-to-wall
     */
    private void resizeRoomSmart(double currentX, double currentY) {
        if (selectedRoomModel == null || resizeEdge == null) {
            return;
        }

        double deltaX = (currentX - dragStartX) / scale;
        double deltaY = (currentY - dragStartY) / scale;

        double newX = originalX;
        double newY = originalY;
        double newWidth = originalWidth;
        double newHeight = originalHeight;

        // Calculate proposed new dimensions
        if (resizeEdge.equals("left")) {
            newX = originalX + deltaX;
            newWidth = originalWidth - deltaX;
        } else if (resizeEdge.equals("right")) {
            newWidth = originalWidth + deltaX;
        } else if (resizeEdge.equals("top")) {
            newY = originalY + deltaY;
            newHeight = originalHeight - deltaY;
        } else if (resizeEdge.equals("bottom")) {
            newHeight = originalHeight + deltaY;
        }

        // Apply grid snapping
        newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
        newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;
        newWidth = Math.round(newWidth / GRID_SIZE) * GRID_SIZE;
        newHeight = Math.round(newHeight / GRID_SIZE) * GRID_SIZE;

        // Validate minimum size
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        // Check minimum area
        if (newWidth * newHeight < Room.MIN_AREA) {
            updatePreview(newX, newY, newWidth, newHeight, true, false);
            return;
        }

        // Apply snap-to-wall logic
        SnapResult snapResult = calculateSnap(newX, newY, newWidth, newHeight, resizeEdge);
        newX = snapResult.x;
        newY = snapResult.y;
        newWidth = snapResult.width;
        newHeight = snapResult.height;
        snappedToRoom = snapResult.snappedRoom;

        // Check for collisions with other rooms
        boolean collision = checkCollision(newX, newY, newWidth, newHeight, selectedRoomModel);
        isColliding = collision;

        // Update preview
        updatePreview(newX, newY, newWidth, newHeight, collision, snapResult.snappedRoom != null);
    }

    /**
     * Apply the resize if valid
     */
    private void applyResize() {
        if (isColliding || resizePreview == null || !resizePreview.isVisible()) {
            return;
        }

        try {
            double newX = (resizePreview.getX() - offsetX) / scale;
            double newY = (resizePreview.getY() - offsetY) / scale;
            double newWidth = resizePreview.getWidth() / scale;
            double newHeight = resizePreview.getHeight() / scale;

            // Store old dimensions for door adjustment
            double oldX = selectedRoomModel.getX();
            double oldY = selectedRoomModel.getY();
            double oldWidth = selectedRoomModel.getWidth();
            double oldHeight = selectedRoomModel.getHeight();

            selectedRoomModel.setDimensions(newX, newY, newWidth, newHeight);

            // Update door positions to stay on walls
            updateDoorsForResizedRoom(selectedRoomModel, oldX, oldY, oldWidth, oldHeight);

            render();

            // Trigger validation change callback
            if (onValidationChange != null) {
                onValidationChange.onValidationChange();
            }
        } catch (IllegalArgumentException e) {
            // Invalid dimensions, ignore
        }
    }

    /**
     * Update door positions when a room is resized
     */
    private void updateDoorsForResizedRoom(Room room, double oldX, double oldY, double oldWidth,
            double oldHeight) {
        for (Door door : house.getDoors()) {
            if (!door.connects(room)) {
                continue;
            }

            Room otherRoom = door.getOtherRoom(room);
            if (otherRoom == null) {
                continue;
            }

            // Calculate which wall the door was on
            double doorX = door.getX();
            double doorY = door.getY();

            final double EPS = 0.01;

            // Check if door was on left wall of room
            if (Math.abs(doorX - oldX) < EPS
                    && door.getOrientation() == Door.Orientation.VERTICAL) {
                door.setPosition(room.getX(), doorY);

                // Ensure door stays within valid range
                double overlapTop = Math.max(room.getY(), otherRoom.getY());
                double overlapBottom = Math.min(room.getY() + room.getHeight(),
                        otherRoom.getY() + otherRoom.getHeight());
                double clampedY =
                        Math.max(overlapTop, Math.min(doorY, overlapBottom - door.getWidth()));
                door.setPosition(room.getX(), clampedY);
            }
            // Check if door was on right wall of room
            else if (Math.abs(doorX - (oldX + oldWidth)) < EPS
                    && door.getOrientation() == Door.Orientation.VERTICAL) {
                door.setPosition(room.getX() + room.getWidth(), doorY);

                // Ensure door stays within valid range
                double overlapTop = Math.max(room.getY(), otherRoom.getY());
                double overlapBottom = Math.min(room.getY() + room.getHeight(),
                        otherRoom.getY() + otherRoom.getHeight());
                double clampedY =
                        Math.max(overlapTop, Math.min(doorY, overlapBottom - door.getWidth()));
                door.setPosition(room.getX() + room.getWidth(), clampedY);
            }
            // Check if door was on top wall of room
            else if (Math.abs(doorY - oldY) < EPS
                    && door.getOrientation() == Door.Orientation.HORIZONTAL) {
                door.setPosition(doorX, room.getY());

                // Ensure door stays within valid range
                double overlapLeft = Math.max(room.getX(), otherRoom.getX());
                double overlapRight = Math.min(room.getX() + room.getWidth(),
                        otherRoom.getX() + otherRoom.getWidth());
                double clampedX =
                        Math.max(overlapLeft, Math.min(doorX, overlapRight - door.getWidth()));
                door.setPosition(clampedX, room.getY());
            }
            // Check if door was on bottom wall of room
            else if (Math.abs(doorY - (oldY + oldHeight)) < EPS
                    && door.getOrientation() == Door.Orientation.HORIZONTAL) {
                door.setPosition(doorX, room.getY() + room.getHeight());

                // Ensure door stays within valid range
                double overlapLeft = Math.max(room.getX(), otherRoom.getX());
                double overlapRight = Math.min(room.getX() + room.getWidth(),
                        otherRoom.getX() + otherRoom.getWidth());
                double clampedX =
                        Math.max(overlapLeft, Math.min(doorX, overlapRight - door.getWidth()));
                door.setPosition(clampedX, room.getY() + room.getHeight());
            }
        }
    }

    /**
     * Update door positions when a room is moved
     */
    private void updateDoorsForMovedRoom(Room room, double oldX, double oldY) {
        double deltaX = room.getX() - oldX;
        double deltaY = room.getY() - oldY;

        for (Door door : house.getDoors()) {
            if (!door.connects(room)) {
                continue;
            }

            // Move the door by the same delta
            door.setPosition(door.getX() + deltaX, door.getY() + deltaY);
        }
    }

    /**
     * Check if proposed dimensions would collide with other rooms
     */
    private boolean checkCollision(double x, double y, double width, double height,
            Room excludeRoom) {
        for (Room room : house.getRooms()) {
            if (room == excludeRoom) {
                continue;
            }

            // Check for intersection
            boolean intersects = !(x + width <= room.getX() || room.getX() + room.getWidth() <= x
                    || y + height <= room.getY() || room.getY() + room.getHeight() <= y);

            if (intersects) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate snap-to-wall adjustments
     */
    private SnapResult calculateSnap(double x, double y, double width, double height, String edge) {
        SnapResult result = new SnapResult(x, y, width, height, null);

        for (Room room : house.getRooms()) {
            if (room == selectedRoomModel) {
                continue;
            }

            double roomLeft = room.getX();
            double roomRight = room.getX() + room.getWidth();
            double roomTop = room.getY();
            double roomBottom = room.getY() + room.getHeight();

            // Check for snap based on resize edge
            if (edge.equals("left")) {
                // Snap left edge to right edge of adjacent room
                if (Math.abs(x - roomRight) < SNAP_THRESHOLD) {
                    double adjustment = roomRight - x;
                    result.x = roomRight;
                    result.width = width - adjustment;
                    result.snappedRoom = room;
                }
            } else if (edge.equals("right")) {
                // Snap right edge to left edge of adjacent room
                if (Math.abs((x + width) - roomLeft) < SNAP_THRESHOLD) {
                    result.width = roomLeft - x;
                    result.snappedRoom = room;
                }
            } else if (edge.equals("top")) {
                // Snap top edge to bottom edge of adjacent room
                if (Math.abs(y - roomBottom) < SNAP_THRESHOLD) {
                    double adjustment = roomBottom - y;
                    result.y = roomBottom;
                    result.height = height - adjustment;
                    result.snappedRoom = room;
                }
            } else if (edge.equals("bottom")) {
                // Snap bottom edge to top edge of adjacent room
                if (Math.abs((y + height) - roomTop) < SNAP_THRESHOLD) {
                    result.height = roomTop - y;
                    result.snappedRoom = room;
                }
            }
        }

        return result;
    }

    /**
     * Update visual preview during resize
     */
    private void updatePreview(double x, double y, double width, double height, boolean collision,
            boolean snapped) {
        resizePreview.setX(offsetX + x * scale);
        resizePreview.setY(offsetY + y * scale);
        resizePreview.setWidth(width * scale);
        resizePreview.setHeight(height * scale);
        resizePreview.setVisible(true);

        // Update color based on state
        if (collision) {
            resizePreview.setStroke(Color.RED);
        } else if (snapped) {
            resizePreview.setStroke(Color.GREEN);
        } else {
            resizePreview.setStroke(Color.BLUE);
        }

        // Update dimension label
        updateDimensionLabel(x, y, width, height, collision);
    }

    /**
     * Update dimension label showing size and area
     */
    private void updateDimensionLabel(double x, double y, double width, double height,
            boolean collision) {
        dimensionLabel.getChildren().clear();

        Label sizeLabel = new Label(String.format("%.1f × %.1f ft", width, height));
        sizeLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        sizeLabel.setTextFill(collision ? Color.rgb(255, 100, 100) : Color.WHITE);

        Label areaLabel = new Label(String.format("Area: %.1f ft²", width * height));
        areaLabel.setFont(Font.font("System", 10));
        areaLabel.setTextFill(collision ? Color.rgb(255, 150, 150) : Color.rgb(200, 200, 200));

        if (collision) {
            Label errorLabel = new Label("⚠ Collision!");
            errorLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
            errorLabel.setTextFill(Color.rgb(255, 100, 100));
            dimensionLabel.getChildren().add(errorLabel);
        } else if (width * height < Room.MIN_AREA) {
            Label errorLabel = new Label("⚠ Too small!");
            errorLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
            errorLabel.setTextFill(Color.rgb(255, 200, 100));
            dimensionLabel.getChildren().add(errorLabel);
        }

        dimensionLabel.getChildren().addAll(sizeLabel, areaLabel);

        // Position label near the resize handle
        double labelX = offsetX + (x + width / 2) * scale - 40;
        double labelY = offsetY + (y + height / 2) * scale - 25;
        dimensionLabel.setLayoutX(labelX);
        dimensionLabel.setLayoutY(labelY);
        dimensionLabel.setVisible(true);
    }

    /**
     * Helper class for snap calculation results
     */
    private static class SnapResult {
        double x, y, width, height;
        Room snappedRoom;

        SnapResult(double x, double y, double width, double height, Room snappedRoom) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.snappedRoom = snappedRoom;
        }
    }

    public void deselectRoom() {
        if (selectedRoomRect != null) {
            selectedRoomRect.setStroke(Color.BLACK);
            selectedRoomRect.setStrokeWidth(2.0);
        }
        selectedRoomModel = null;
        selectedRoomRect = null;

        leftHandle.setVisible(false);
        rightHandle.setVisible(false);
        topHandle.setVisible(false);
        bottomHandle.setVisible(false);

        // Trigger callback
        if (onRoomDeselected != null) {
            onRoomDeselected.onRoomDeselected();
        }
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

            // Check if door position is valid
            boolean isValidPosition = door.isValidPosition();

            // Also check that both rooms still exist in the house
            boolean roomsExist = house.getRooms().contains(door.getRoom1())
                    && house.getRooms().contains(door.getRoom2());

            boolean isValid = isValidPosition && roomsExist;
            doorLine.setStroke(isValid ? Color.GREEN : Color.RED);
            doorLine.setStrokeWidth(4.0);

            // Add dashed line for invalid doors
            if (!isValid) {
                doorLine.getStrokeDashArray().addAll(5.0, 5.0);
            }

            // Make door draggable
            doorLine.setCursor(Cursor.HAND);
            doorLine.setUserData(door);

            doorLine.setOnMousePressed(e -> {
                Door d = (Door) doorLine.getUserData();
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();
                originalX = d.getX();
                originalY = d.getY();
                e.consume();
            });

            doorLine.setOnMouseDragged(e -> {
                Door d = (Door) doorLine.getUserData();

                double deltaX = (e.getSceneX() - dragStartX) / scale;
                double deltaY = (e.getSceneY() - dragStartY) / scale;

                double newX = originalX;
                double newY = originalY;

                // Only allow dragging along the door's orientation
                if (d.getOrientation() == Door.Orientation.HORIZONTAL) {
                    newX = originalX + deltaX;
                    // Snap to grid
                    newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
                } else {
                    newY = originalY + deltaY;
                    // Snap to grid
                    newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;
                }

                // Update door position temporarily for preview
                d.setPosition(newX, newY);

                // Check if still valid
                boolean stillValid = d.isValidPosition();
                doorLine.setStroke(stillValid ? Color.BLUE : Color.RED);

                // Update visual position
                if (d.getOrientation() == Door.Orientation.HORIZONTAL) {
                    double lineX = offsetX + newX * scale;
                    doorLine.setStartX(lineX);
                    doorLine.setEndX(lineX + width);
                } else {
                    double lineY = offsetY + newY * scale;
                    doorLine.setStartY(lineY);
                    doorLine.setEndY(lineY + width);
                }

                e.consume();
            });

            doorLine.setOnMouseReleased(e -> {
                Door d = (Door) doorLine.getUserData();

                // Check if final position is valid
                if (!d.isValidPosition()) {
                    // Revert to original position
                    d.setPosition(originalX, originalY);
                }

                render();

                // Trigger validation change callback
                if (onValidationChange != null) {
                    onValidationChange.onValidationChange();
                }

                e.consume();
            });

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

            // Check if obstruction is inside any room
            boolean isInRoom = isObstructionInRoom(obstruction);

            if (obstruction instanceof BlockingObstruction) {
                rect.setFill(isInRoom ? Color.DARKGRAY : Color.rgb(139, 69, 69, 0.5));
                rect.setStroke(isInRoom ? Color.BLACK : Color.RED);
            } else if (obstruction instanceof PassUnderObstruction) {
                rect.setFill(isInRoom ? Color.LIGHTGRAY : Color.rgb(211, 211, 211, 0.5));
                rect.setStroke(isInRoom ? Color.DARKGRAY : Color.RED);
                rect.getStrokeDashArray().addAll(5.0, 5.0);
            }

            rect.setStrokeWidth(1.5);

            this.getChildren().add(rect);
        }
    }

    /**
     * Check if an obstruction is inside any room
     */
    private boolean isObstructionInRoom(Obstruction obstruction) {
        double obsX = obstruction.getX();
        double obsY = obstruction.getY();
        double obsWidth = obstruction.getWidth();
        double obsHeight = obstruction.getHeight();

        for (Room room : house.getRooms()) {
            double roomX = room.getX();
            double roomY = room.getY();
            double roomWidth = room.getWidth();
            double roomHeight = room.getHeight();

            // Check if obstruction is completely inside room
            if (obsX >= roomX && obsY >= roomY && (obsX + obsWidth) <= (roomX + roomWidth)
                    && (obsY + obsHeight) <= (roomY + roomHeight)) {
                return true;
            }
        }
        return false;
    }

    private Color getFloorCoveringColor(House.FloorCovering covering) {
        switch (covering) {
            case HARD:
                return Color.WHEAT;
            case LOOP_PILE:
                return Color.SANDYBROWN;
            case CUT_PILE:
                return Color.TAN;
            case FRIEZE:
                return Color.BURLYWOOD;
            default:
                return Color.LIGHTGRAY;
        }
    }

    /**
     * Check if a room is valid (has at least one door, meets minimum area, and doesn't overlap)
     */
    private boolean isRoomValid(Room room) {
        // Check connectivity
        if (!room.hasValidConnectivity()) {
            return false;
        }

        // Check minimum area
        if (room.getArea() < Room.MIN_AREA) {
            return false;
        }

        // Check for overlaps with other rooms
        for (Room otherRoom : house.getRooms()) {
            if (otherRoom == room) {
                continue;
            }
            if (room.intersects(otherRoom)) {
                return false;
            }
        }

        return true;
    }

    public void setScale(double scale) {
        this.scale = scale;
        drawGrid(); // Redraw grid with new scale
        render();
    }

    public double getScale() {
        return scale;
    }
}
