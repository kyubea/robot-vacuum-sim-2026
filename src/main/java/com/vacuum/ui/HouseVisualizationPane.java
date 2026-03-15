package com.vacuum.ui;

import com.vacuum.model.*;
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
import javafx.scene.shape.Line;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Visualization pane that renders a House with rooms, doors, and obstructions
 */
public class HouseVisualizationPane extends Pane {

    private static final Color WALL_COLOR = Color.web("#2b353d");
    private static final Color HOVER_COLOR = Color.web("#6a8ea3");
    private static final Color SELECTION_COLOR = Color.web("#0f7b82");
    private static final Color DOOR_COLOR = Color.web("#2c8c50");

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
            if (e.getTarget() == this) {
                deselectRoom();
                hoverTooltip.setVisible(false);
            }
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
                resizeRoom(e.getSceneX(), e.getSceneY());
            }
            e.consume();
        });

        handle.setOnMouseReleased(e -> {
            resizeEdge = null;
            e.consume();
        });
    }

    public void setHouse(House house) {
        this.house = house;
        render();
    }

    public void render() {
        this.getChildren().retainAll(hoverTooltip, leftHandle, rightHandle, topHandle,
                bottomHandle);

        if (house == null) {
            return;
        }

        double maxX = 0;
        double maxY = 0;
        for (Room room : house.getRooms()) {
            maxX = Math.max(maxX, room.getX() + room.getWidth());
            maxY = Math.max(maxY, room.getY() + room.getHeight());
        }

        this.setPrefSize((maxX * scale) + offsetX * 2 + 200, (maxY * scale) + offsetY * 2 + 200);

        renderFloorBoundary(maxX, maxY);

        selectedRoomRect = null;
        renderRooms();
        renderObstructions();
        renderDoors();

        if (selectedRoomModel != null) {
            if (selectedRoomRect != null) {
                updateSelectedRoom();
            } else {
                deselectRoom();
            }
        }

        hoverTooltip.toFront();
        leftHandle.toFront();
        rightHandle.toFront();
        topHandle.toFront();
        bottomHandle.toFront();
    }

    private void renderFloorBoundary(double maxX, double maxY) {
        double planWidth = maxX * scale;
        double planHeight = maxY * scale;

        Rectangle frame =
                new Rectangle(offsetX - 20, offsetY - 20, planWidth + 40, planHeight + 40);
        frame.setArcWidth(18);
        frame.setArcHeight(18);
        frame.setFill(Color.web("#f7f8f9"));
        frame.setStroke(Color.web("#c3ced4"));
        frame.setStrokeWidth(1.6);
        frame.setMouseTransparent(true);

        Rectangle workSurface = new Rectangle(offsetX, offsetY, planWidth, planHeight);
        workSurface.setFill(Color.web("#fdfdfd"));
        workSurface.setStroke(Color.web("#d8dfe3"));
        workSurface.setStrokeWidth(1.2);
        workSurface.setMouseTransparent(true);

        this.getChildren().addAll(frame, workSurface);
    }

    private ImagePattern getCurrentFloorPattern() {
        House.FloorCovering covering = house.getFloorCovering();
        FlooringTypes floorType;
        try {
            floorType = FlooringTypes.valueOf(covering.name());
        } catch (IllegalArgumentException ex) {
            floorType = FlooringTypes.HARDWOOD;
        }
        return new ImagePattern(floorType.getFloor(), 0, 0, 64, 64, false);
    }

    private void renderRooms() {
        ImagePattern floorPattern = getCurrentFloorPattern();

        for (Room room : house.getRooms()) {
            Rectangle rect = new Rectangle();

            rect.setX(offsetX + room.getX() * scale);
            rect.setY(offsetY + room.getY() * scale);
            rect.setWidth(room.getWidth() * scale);
            rect.setHeight(room.getHeight() * scale);

            rect.setFill(floorPattern);

            rect.setStroke(WALL_COLOR);
            rect.setStrokeWidth(2.0);

            rect.setUserData(room);

            boolean isSelected = (selectedRoomModel == room);
            if (isSelected) {
                rect.setStroke(SELECTION_COLOR);
                rect.setStrokeWidth(4.0);
                selectedRoomRect = rect;
            }

            rect.setOnMouseEntered(e -> {
                if (selectedRoomModel != room) {
                    rect.setStroke(HOVER_COLOR);
                    rect.setStrokeWidth(3.0);
                }
                currentHoverRoom = room;
                showRoomTooltip(room, e.getSceneX(), e.getSceneY());
            });

            rect.setOnMouseExited(e -> {
                if (selectedRoomModel != room) {
                    rect.setStroke(WALL_COLOR);
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
                selectRoom(room, rect);
                e.consume();
            });

            this.getChildren().add(rect);
        }
    }

    private void selectRoom(Room room, Rectangle rect) {
        if (selectedRoomRect != null && selectedRoomRect != rect) {
            selectedRoomRect.setStroke(WALL_COLOR);
            selectedRoomRect.setStrokeWidth(2.0);
        }

        selectedRoomModel = room;
        selectedRoomRect = rect;
        rect.setStroke(SELECTION_COLOR);
        rect.setStrokeWidth(4.0);

        updateResizeHandles(rect);
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
                new Label(String.format("Size: %.1f × %.1f ft", room.getWidth(), room.getHeight()));
        Label areaLabel = new Label(String.format("Area: %.1f ft²", room.getArea()));
        Label doorsLabel = new Label("Doors: " + room.getDoors().size());

        posLabel.getStyleClass().add("room-tooltip-detail");
        sizeLabel.getStyleClass().add("room-tooltip-detail");
        areaLabel.getStyleClass().add("room-tooltip-detail");
        doorsLabel.getStyleClass().add("room-tooltip-detail");

        hoverTooltip.getChildren().addAll(idLabel, posLabel, sizeLabel, areaLabel, doorsLabel);
        positionTooltip(x, y);
        hoverTooltip.setVisible(true);
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

        try {
            double newX = originalX;
            double newY = originalY;
            double newWidth = originalWidth;
            double newHeight = originalHeight;

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

            if (newWidth > 0 && newHeight > 0) {
                selectedRoomModel.setDimensions(newX, newY, newWidth, newHeight);
                render();
            }
        } catch (IllegalArgumentException e) {
            // Room too small, ignore
        }
    }

    public void deselectRoom() {
        if (selectedRoomRect != null) {
            selectedRoomRect.setStroke(WALL_COLOR);
            selectedRoomRect.setStrokeWidth(2.0);
        }
        selectedRoomModel = null;
        selectedRoomRect = null;

        leftHandle.setVisible(false);
        rightHandle.setVisible(false);
        topHandle.setVisible(false);
        bottomHandle.setVisible(false);
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

            doorLine.setStroke(DOOR_COLOR);
            doorLine.setStrokeWidth(4.0);
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
                rect.setFill(Color.web("#5e6a72"));
                rect.setStroke(Color.web("#2f3a40"));
            } else if (obstruction instanceof PassUnderObstruction) {
                rect.setFill(Color.web("#ccd3d8"));
                rect.setStroke(Color.web("#67747d"));
                rect.getStrokeDashArray().addAll(5.0, 5.0);
            }

            rect.setStrokeWidth(1.5);
            rect.setMouseTransparent(true);

            this.getChildren().add(rect);
        }
    }

    public void setScale(double scale) {
        this.scale = scale;
        render();
    }

    public double getScale() {
        return scale;
    }
}
