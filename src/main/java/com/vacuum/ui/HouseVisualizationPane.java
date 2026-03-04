package com.vacuum.ui;

import java.io.FileInputStream;
import java.io.InputStream;
import com.vacuum.model.*;
import com.vacuum.util.Vacuum;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Circle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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
    public Vacuum vacuum;

    public HouseVisualizationPane() {
        this.setStyle("-fx-background-color: #f0f0f0;");

        hoverTooltip = new VBox(5);
        hoverTooltip.setPadding(new Insets(8));
        hoverTooltip.setBackground(new Background(new BackgroundFill(Color.rgb(255, 255, 220, 0.95),
                new CornerRadii(5), Insets.EMPTY)));
        hoverTooltip.setStyle("-fx-border-color: #888; -fx-border-width: 1; -fx-border-radius: 5;");
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

    public void setVacuum(Vacuum vacuum) {
        this.vacuum = vacuum;
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

        renderRooms();
        renderDoors();
        renderObstructions();
        renderVacuum();

        if (selectedRoomModel != null) {
            updateSelectedRoom();
        }

        hoverTooltip.toFront();
        leftHandle.toFront();
        rightHandle.toFront();
        topHandle.toFront();
        bottomHandle.toFront();
    }

    private void renderVacuum() {
        Image vImage = new Image(getClass().getResourceAsStream("/vacuumRotated.png"));
        ImageView vImageView = new ImageView();

        double screenX = offsetX + vacuum.getX() * scale;
        double screenY = offsetY + vacuum.getY() * scale;
        vImageView.setImage(vImage);


        vImageView.relocate(screenX - vImage.getWidth() / 2, screenY - vImage.getHeight() / 2);

        vImageView.setRotate(this.vacuum.getOrientation());
        this.getChildren().add(vImageView);
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

            this.getChildren().add(rect);
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
            selectedRoomRect.setStroke(Color.BLACK);
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

            doorLine.setStroke(Color.GREEN);
            doorLine.setStrokeWidth(4.0);

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
                rect.setFill(Color.DARKGRAY);
                rect.setStroke(Color.BLACK);
            } else if (obstruction instanceof PassUnderObstruction) {
                rect.setFill(Color.LIGHTGRAY);
                rect.setStroke(Color.DARKGRAY);
                rect.getStrokeDashArray().addAll(5.0, 5.0);
            }

            rect.setStrokeWidth(1.5);

            this.getChildren().add(rect);
        }
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

    public void setScale(double scale) {
        this.scale = scale;
        render();
    }

    public double getScale() {
        return scale;
    }
}
