package com.vacuum.ui;

import com.vacuum.model.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main JavaFX application for the vacuum simulator Req 1.1: Standalone Windows application
 */
public class VacuumSimulatorApp extends Application {

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;

    private HouseVisualizationPane visualizationPane;
    private ScrollPane scrollPane;
    private House house;

    private Label statusLabel;
    private Label zoomLabel;

    private VBox inspectorPanel;
    private BorderPane root;
    private boolean inspectorVisible = true;
    private Room currentSelectedRoom = null;

    @Override
    public void start(Stage primaryStage) {
        // Create default house (Req 1.2: valid default state)
        house = createDefaultHouse();

        // Create UI components
        root = new BorderPane();

        // Top: Menu bar
        MenuBar menuBar = createMenuBar(primaryStage);
        root.setTop(menuBar);

        // Center: scrollable, pannable visualization
        visualizationPane = new HouseVisualizationPane();
        visualizationPane.setHouse(house);

        // Setup selection listener
        visualizationPane.setOnRoomSelected(room -> {
            currentSelectedRoom = room;
            updateInspector();
        });

        visualizationPane.setOnRoomDeselected(() -> {
            currentSelectedRoom = null;
            updateInspector();
        });

        visualizationPane.setOnValidationChange(() -> {
            updateInspector();
        });

        scrollPane = new ScrollPane(visualizationPane);
        scrollPane.setPannable(true); // Enable panning by dragging
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");

        // Add zoom functionality with mouse wheel
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                double deltaY = event.getDeltaY();
                double scaleFactor = (deltaY > 0) ? 1.1 : 0.9;

                double newScale = visualizationPane.getScale() * scaleFactor;
                // Clamp zoom between 2x and 50x
                newScale = Math.max(2.0, Math.min(50.0, newScale));

                visualizationPane.setScale(newScale);
                updateZoomLabel();
            }
        });

        root.setCenter(scrollPane);

        // Right: inspector panel
        inspectorPanel = createInspectorPanel();
        root.setRight(inspectorPanel);

        // Bottom: status bar
        statusLabel = new Label("Ready");
        statusLabel.setPadding(new Insets(5, 10, 5, 10));
        statusLabel.setStyle(
                "-fx-background-color: #e0e0e0; -fx-border-color: #c0c0c0; -fx-border-width: 1 0 0 0;");
        root.setBottom(statusLabel);

        // Create scene and show
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setTitle("Robot Vacuum Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();

        updateStatus("Default house loaded - " + house.getRooms().size() + " rooms");
    }

    /**
     * Create menu bar with File, Edit, View, Simulation menus
     */
    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New House...");
        MenuItem openItem = new MenuItem("Open House...");
        MenuItem saveItem = new MenuItem("Save House...");
        MenuItem saveAsItem = new MenuItem("Save House As...");
        MenuItem exitItem = new MenuItem("Exit");

        newItem.setDisable(true); // TODO: implement
        openItem.setDisable(true);
        saveItem.setDisable(true);
        saveAsItem.setDisable(true);
        exitItem.setOnAction(e -> stage.close());

        fileMenu.getItems().addAll(newItem, openItem, new SeparatorMenuItem(), saveItem, saveAsItem,
                new SeparatorMenuItem(), exitItem);

        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem editHouseItem = new MenuItem("Edit House...");
        MenuItem editRobotItem = new MenuItem("Robot Configuration...");
        MenuItem floorCoveringItem = new MenuItem("Floor Covering...");

        editHouseItem.setDisable(true); // TODO: implement
        editRobotItem.setDisable(true);

        // Floor covering submenu
        Menu floorSubmenu = new Menu("Floor Covering");
        ToggleGroup floorGroup = new ToggleGroup();
        for (House.FloorCovering covering : House.FloorCovering.values()) {
            RadioMenuItem item = new RadioMenuItem(covering.getDisplayName());
            item.setToggleGroup(floorGroup);
            item.setSelected(house.getFloorCovering() == covering);
            item.setOnAction(e -> {
                house.setFloorCovering(covering);
                visualizationPane.render();
                updateStatus("Floor covering changed to " + covering.getDisplayName());
            });
            floorSubmenu.getItems().add(item);
        }

        editMenu.getItems().addAll(editHouseItem, editRobotItem, new SeparatorMenuItem(),
                floorSubmenu);

        // View menu
        Menu viewMenu = new Menu("View");
        MenuItem zoomInItem = new MenuItem("Zoom In");
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        MenuItem resetZoomItem = new MenuItem("Reset Zoom");
        MenuItem fitToWindowItem = new MenuItem("Fit to Window");
        CheckMenuItem toggleInspectorItem = new CheckMenuItem("Show Inspector");
        toggleInspectorItem.setSelected(true);

        zoomInItem.setOnAction(e -> zoom(1.2));
        zoomOutItem.setOnAction(e -> zoom(0.8));
        resetZoomItem.setOnAction(e -> {
            visualizationPane.setScale(10.0);
            updateZoomLabel();
            updateStatus("Zoom reset to 100%");
        });
        fitToWindowItem.setDisable(true); // TODO: implement
        toggleInspectorItem.setOnAction(e -> {
            inspectorVisible = toggleInspectorItem.isSelected();
            toggleInspector();
        });

        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, resetZoomItem, new SeparatorMenuItem(),
                fitToWindowItem, new SeparatorMenuItem(), toggleInspectorItem);

        // Simulation menu
        Menu simMenu = new Menu("Simulation");
        MenuItem startItem = new MenuItem("Start");
        MenuItem stopItem = new MenuItem("Stop");
        MenuItem pauseItem = new MenuItem("Pause");
        MenuItem resetItem = new MenuItem("Reset");

        startItem.setDisable(true); // TODO: implement
        stopItem.setDisable(true);
        pauseItem.setDisable(true);
        resetItem.setDisable(true);

        simMenu.getItems().addAll(startItem, stopItem, pauseItem, new SeparatorMenuItem(),
                resetItem);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog(stage));
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, simMenu, helpMenu);
        return menuBar;
    }

    /**
     * Zoom in or out by a factor
     */
    private void zoom(double factor) {
        double newScale = visualizationPane.getScale() * factor;
        newScale = Math.max(2.0, Math.min(50.0, newScale));
        visualizationPane.setScale(newScale);
        updateZoomLabel();
        updateStatus(String.format("Zoom: %.0f%%", (newScale / 10.0) * 100));
    }

    /**
     * Update zoom label in info panel
     */
    private void updateZoomLabel() {
        if (zoomLabel != null) {
            double percentage = (visualizationPane.getScale() / 10.0) * 100;
            zoomLabel.setText(String.format("Zoom: %.0f%%", percentage));
        }
    }

    /**
     * Update status bar
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Show about dialog
     */
    private void showAboutDialog(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("About");
        alert.setHeaderText("Robot Vacuum Simulator");
        alert.setContentText(
                "Version 1.0\nBuilt with JavaFX\n\nTeam: Beatrice, Ian, Malachi, Ray, Vo");
        alert.showAndWait();
    }

    /**
     * Create default house for testing (Req 1.2) 2000 ft² with 4 rooms
     */
    private House createDefaultHouse() {
        House h = new House();

        // Create rooms (total ~845 ft²)
        Room livingRoom = new Room(0, 0, 25, 20); // 500 ft²
        Room kitchen = new Room(25, 0, 15, 15); // 225 ft²
        Room bedroom = new Room(0, 20, 20, 12); // 240 ft²
        Room bathroom = new Room(25, 15, 10, 8); // 80 ft²

        h.addRoom(livingRoom);
        h.addRoom(kitchen);
        h.addRoom(bedroom);
        h.addRoom(bathroom);

        // Add doors
        Door door1 = new Door(livingRoom, kitchen, 25, 5, Door.Orientation.VERTICAL);
        Door door2 = new Door(livingRoom, bedroom, 5, 20, Door.Orientation.HORIZONTAL);
        Door door3 = new Door(kitchen, bathroom, 25, 15, Door.Orientation.HORIZONTAL);

        h.addDoor(door1);
        h.addDoor(door2);
        h.addDoor(door3);

        // Add some test obstructions
        BlockingObstruction couch = new BlockingObstruction(5, 5, 6, 3);
        PassUnderObstruction table = new PassUnderObstruction(15, 22, 4, 3);

        h.addObstruction(couch);
        h.addObstruction(table);

        return h;
    }

    /**
     * Create inspector panel
     */
    private VBox createInspectorPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle(
                "-fx-background-color: #e8e8e8; -fx-border-color: #c0c0c0; -fx-border-width: 0 0 0 1;");
        panel.setPrefWidth(280);
        panel.setMinWidth(280);
        panel.setMaxWidth(280);

        // Will be populated by updateInspector()
        return panel;
    }

    /**
     * Update inspector panel content based on selection
     */
    private void updateInspector() {
        inspectorPanel.getChildren().clear();

        // Title
        Label titleLabel = new Label("Inspector");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        inspectorPanel.getChildren().add(titleLabel);
        inspectorPanel.getChildren().add(new Separator());

        if (currentSelectedRoom == null) {
            // No selection - show general info
            Label noSelectionLabel = new Label("No room selected");
            noSelectionLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
            inspectorPanel.getChildren().add(noSelectionLabel);

            inspectorPanel.getChildren().add(new Separator());

            // House summary
            Label houseTitleLabel = new Label("House Summary");
            houseTitleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            inspectorPanel.getChildren().add(houseTitleLabel);

            GridPane houseGrid = new GridPane();
            houseGrid.setHgap(10);
            houseGrid.setVgap(8);
            houseGrid.setPadding(new Insets(10, 0, 0, 0));

            addGridRow(houseGrid, 0, "Rooms:", String.valueOf(house.getRooms().size()));
            addGridRow(houseGrid, 1, "Doors:", String.valueOf(house.getDoors().size()));
            addGridRow(houseGrid, 2, "Obstructions:",
                    String.valueOf(house.getObstructions().size()));
            addGridRow(houseGrid, 3, "Total Area:",
                    String.format("%.1f ft²", house.getTotalArea()));
            addGridRow(houseGrid, 4, "Cleanable:",
                    String.format("%.1f ft²", house.getCleanableArea()));
            addGridRow(houseGrid, 5, "Floor Type:", house.getFloorCovering().name());

            inspectorPanel.getChildren().add(houseGrid);

            // Validation status
            Label validLabel =
                    new Label(house.isValid() ? "Valid Configuration" : "Invalid Configuration");
            validLabel.setStyle(house.isValid()
                    ? "-fx-text-fill: green; -fx-font-weight: bold; -fx-padding: 10 0 0 0;"
                    : "-fx-text-fill: red; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");
            inspectorPanel.getChildren().add(validLabel);

            // Show validation errors if any
            if (!house.isValid()) {
                java.util.List<String> errors = house.validate();
                if (!errors.isEmpty()) {
                    Label errorsLabel = new Label("Issues:");
                    errorsLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
                    inspectorPanel.getChildren().add(errorsLabel);

                    VBox errorBox = new VBox(3);
                    errorBox.setPadding(new Insets(0, 0, 0, 10));
                    for (String error : errors) {
                        Label errorItem = new Label("- " + error);
                        errorItem.setStyle("-fx-text-fill: #c00; -fx-font-size: 11px;");
                        errorItem.setWrapText(true);
                        errorBox.getChildren().add(errorItem);
                    }
                    inspectorPanel.getChildren().add(errorBox);
                }
            }

        } else {
            // Room selected - show detailed info
            Label roomTitleLabel = new Label("Room Details");
            roomTitleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            inspectorPanel.getChildren().add(roomTitleLabel);

            // Room ID (shortened)
            Label idLabel = new Label("ID: " + currentSelectedRoom.getId().substring(0, 8) + "...");
            idLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            inspectorPanel.getChildren().add(idLabel);

            // Room properties grid
            GridPane roomGrid = new GridPane();
            roomGrid.setHgap(10);
            roomGrid.setVgap(8);
            roomGrid.setPadding(new Insets(10, 0, 0, 0));

            addGridRow(roomGrid, 0, "Position:", String.format("(%.1f, %.1f)",
                    currentSelectedRoom.getX(), currentSelectedRoom.getY()));
            addGridRow(roomGrid, 1, "Width:",
                    String.format("%.1f ft", currentSelectedRoom.getWidth()));
            addGridRow(roomGrid, 2, "Height:",
                    String.format("%.1f ft", currentSelectedRoom.getHeight()));
            addGridRow(roomGrid, 3, "Area:",
                    String.format("%.1f ft²", currentSelectedRoom.getArea()));
            addGridRow(roomGrid, 4, "Doors:",
                    String.valueOf(currentSelectedRoom.getDoors().size()));

            inspectorPanel.getChildren().add(roomGrid);

            // Room validation
            inspectorPanel.getChildren().add(new Separator());
            Label validationLabel = new Label("Validation");
            validationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            inspectorPanel.getChildren().add(validationLabel);

            boolean validConnectivity = currentSelectedRoom.hasValidConnectivity();
            boolean validArea = currentSelectedRoom.getArea() >= Room.MIN_AREA;

            // Check for overlaps
            boolean hasOverlap = false;
            for (Room otherRoom : house.getRooms()) {
                if (otherRoom != currentSelectedRoom && currentSelectedRoom.intersects(otherRoom)) {
                    hasOverlap = true;
                    break;
                }
            }

            boolean isRoomValid = validConnectivity && validArea && !hasOverlap;

            Label connectivityLabel =
                    new Label(validConnectivity ? "Has door connections" : "No door connections");
            connectivityLabel
                    .setStyle(validConnectivity ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

            Label areaLabel = new Label(validArea ? "Meets minimum area" : "Below minimum area");
            areaLabel.setStyle(validArea ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

            Label overlapLabel = new Label(hasOverlap ? "Overlaps with other room" : "No overlaps");
            overlapLabel.setStyle(hasOverlap ? "-fx-text-fill: red;" : "-fx-text-fill: green;");

            inspectorPanel.getChildren().addAll(connectivityLabel, areaLabel, overlapLabel);

            // Show requirements if room is invalid
            if (!isRoomValid) {
                Label requirementsLabel = new Label("Requirements:");
                requirementsLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
                inspectorPanel.getChildren().add(requirementsLabel);

                VBox reqBox = new VBox(3);
                reqBox.setPadding(new Insets(0, 0, 0, 10));

                if (!validConnectivity) {
                    Label req = new Label("- Add at least one door connection");
                    req.setStyle("-fx-text-fill: #c00; -fx-font-size: 11px;");
                    reqBox.getChildren().add(req);
                }
                if (!validArea) {
                    Label req = new Label(
                            String.format("- Increase area to %.1f ft² minimum", Room.MIN_AREA));
                    req.setStyle("-fx-text-fill: #c00; -fx-font-size: 11px;");
                    reqBox.getChildren().add(req);
                }
                if (hasOverlap) {
                    Label req = new Label("- Move or resize room to eliminate overlap");
                    req.setStyle("-fx-text-fill: #c00; -fx-font-size: 11px;");
                    reqBox.getChildren().add(req);
                }

                inspectorPanel.getChildren().add(reqBox);
            }

            // Actions
            inspectorPanel.getChildren().add(new Separator());
            Label actionsLabel = new Label("Actions");
            actionsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            inspectorPanel.getChildren().add(actionsLabel);

            Button deselectButton = new Button("Deselect Room");
            deselectButton.setPrefWidth(240);
            deselectButton.setOnAction(e -> {
                visualizationPane.deselectRoom();
            });

            Button deleteButton = new Button("Delete Room");
            deleteButton.setPrefWidth(240);
            deleteButton.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;");
            deleteButton.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Delete");
                confirm.setHeaderText("Delete Room?");
                confirm.setContentText("This will remove the room and all its connections.");
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        house.removeRoom(currentSelectedRoom);
                        visualizationPane.deselectRoom();
                        visualizationPane.render();
                        updateStatus("Room deleted");
                    }
                });
            });

            inspectorPanel.getChildren().addAll(deselectButton, deleteButton);
        }

        // Zoom info at bottom
        inspectorPanel.getChildren().add(new Separator());
        zoomLabel = new Label("Zoom: 100%");
        zoomLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        inspectorPanel.getChildren().add(zoomLabel);
        updateZoomLabel();

        // Tips
        Label tipLabel = new Label("Tip: Click a room to select it\nDrag handles to resize");
        tipLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888; -fx-padding: 10 0 0 0;");
        inspectorPanel.getChildren().add(tipLabel);
    }

    /**
     * Helper to add a row to GridPane
     */
    private void addGridRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: #333;");
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    /**
     * Toggle inspector panel visibility
     */
    private void toggleInspector() {
        if (inspectorVisible) {
            root.setRight(inspectorPanel);
            updateInspector();
            updateStatus("Inspector shown");
        } else {
            root.setRight(null);
            updateStatus("Inspector hidden");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
