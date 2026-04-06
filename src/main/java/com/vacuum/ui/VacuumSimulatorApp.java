package com.vacuum.ui;

import com.vacuum.model.*;
import com.vacuum.model.Door.Orientation;
import com.vacuum.util.Vacuum;
import com.vacuum.util.simulationTimer;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.event.EventHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.css.Rect;
import javafx.scene.shape.Rectangle;


import javafx.stage.Modality;
import javafx.stage.Stage;
import java.net.URL;
import java.util.List;

/**
 * Main JavaFX application for the vacuum simulator Req 1.1: Standalone Windows application
 */
public class VacuumSimulatorApp extends Application {

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;

    private HouseVisualizationPane visualizationPane;
    private ScrollPane scrollPane;
    private House house;
    private BorderPane root;
    private Stage primaryStage;
    private Stage controlsStage;

    private Label statusLabel;
    private Label zoomLabel;

    private Vacuum vacuum;
    private simulationTimer simTimer;
    private Label roomsValueLabel;
    private Label doorsValueLabel;
    private Label obstructionsValueLabel;
    private Label totalAreaValueLabel;
    private Label cleanableAreaValueLabel;
    private Label floorCoveringValueLabel;
    private Label seedValueLabel;
    private Label validStateValueLabel;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Create default house (Req 1.2: valid default state)
        house = createDefaultHouse();
        vacuum = new Vacuum(43, 20);
        vacuum.createWallColliders(house.getRooms());

        // Create app shell
        root = new BorderPane();
        root.getStyleClass().add("app-root");

        // Top: menu + quick controls strip
        MenuBar menuBar = createMenuBar(primaryStage);
        menuBar.getStyleClass().add("main-menu");

        VBox topShell = new VBox();
        topShell.getStyleClass().add("top-shell");
        topShell.getChildren().addAll(menuBar, createToolStrip());
        root.setTop(topShell);

        // Center: scrollable, pannable visualization
        visualizationPane = new HouseVisualizationPane();
        visualizationPane.setVacuum(vacuum);
        visualizationPane.setStatusMessageHandler(this::updateStatus);
        visualizationPane.setHouseChangedHandler(this::handleHouseChanged);
        visualizationPane.setHouse(house);
        simTimer = new simulationTimer(vacuum, visualizationPane);

        scrollPane = new ScrollPane(visualizationPane);
        scrollPane.setPannable(true); // Enable panning by dragging
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.getStyleClass().add("house-scroll-pane");

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

        StackPane centerShell = new StackPane(scrollPane);
        centerShell.getStyleClass().add("center-shell");
        centerShell.setPadding(new Insets(16));
        root.setCenter(centerShell);

        // Right: info panel
        VBox infoPanel = createInfoPanel();
        root.setRight(infoPanel);



        // Bottom: status bar
        root.setBottom(createStatusBar());

        // Create scene and show
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        URL cssUrl = getClass().getResource("/styles/simulator-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("Robot Vacuum Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();

        updateInfoPanel();
        updateZoomLabel();

        List<String> validationErrors = house.validate();
        if (validationErrors.isEmpty()) {
            updateStatus("House verified - " + house.getRooms().size() + " rooms connected");
        } else {
            updateStatus("House invalid: " + validationErrors.get(0));
        }
    }

    /**
     * Quick access controls for common view interactions
     */
    private HBox createToolStrip() {
        HBox strip = new HBox(8);
        strip.setPadding(new Insets(10, 12, 10, 12));
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("tool-strip");

        Label viewLabel = new Label("View");
        viewLabel.getStyleClass().add("tool-group-label");

        Button zoomOutButton = new Button("-");
        zoomOutButton.getStyleClass().add("strip-button");
        zoomOutButton.setOnAction(e -> zoom(0.85));

        Button zoomInButton = new Button("+");
        zoomInButton.getStyleClass().add("strip-button");
        zoomInButton.setOnAction(e -> zoom(1.15));

        Button resetZoomButton = new Button("100%");
        resetZoomButton.getStyleClass().add("strip-button");
        resetZoomButton.setOnAction(e -> {
            visualizationPane.setScale(10.0);
            updateZoomLabel();
            updateStatus("Zoom reset to 100%");
        });

        // Add Room toggle
        ToggleButton addRoomToggle = new ToggleButton("+ Room");
        addRoomToggle.getStyleClass().add("strip-button");
        addRoomToggle.setOnAction(e -> {
            boolean active = addRoomToggle.isSelected();
            visualizationPane.setAddRoomMode(active);
            updateStatus(active ? "Draw mode: click and drag on the canvas to place a new room"
                    : "Ready");
        });

        Button controlsButton = new Button("Controls");
        controlsButton.getStyleClass().add("strip-button");
        controlsButton.setOnAction(e -> toggleControlsWindow());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Dark / Light mode toggle
        ToggleButton darkToggle = new ToggleButton("Dark");
        darkToggle.getStyleClass().add("strip-button");
        darkToggle.setOnAction(e -> {
            boolean isDark = darkToggle.isSelected();
            darkToggle.setText(isDark ? "Light" : "Dark");
            if (isDark) {
                root.getStyleClass().add("dark-mode");
            } else {
                root.getStyleClass().remove("dark-mode");
            }
            updateControlsWindowTheme(isDark);
            visualizationPane.setDarkMode(isDark);
        });

        strip.getChildren().addAll(viewLabel, zoomOutButton, zoomInButton, resetZoomButton,
                addRoomToggle, controlsButton, spacer, darkToggle);
        return strip;
    }

    /**
     * Bottom status bar
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 12, 8, 12));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusHint = new Label("Scale baseline: 10 px / ft at 100%");
        statusHint.getStyleClass().add("status-hint");

        statusBar.getChildren().addAll(statusLabel, spacer, statusHint);
        return statusBar;
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
                refreshUiAfterModelChange("Floor covering changed to " + covering.getDisplayName(),
                        false, true);
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

        zoomInItem.setOnAction(e -> zoom(1.2));
        zoomOutItem.setOnAction(e -> zoom(0.8));
        resetZoomItem.setOnAction(e -> {
            visualizationPane.setScale(10.0);
            updateZoomLabel();
            updateStatus("Zoom reset to 100%");
        });
        fitToWindowItem.setDisable(true); // TODO: implement

        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, resetZoomItem, new SeparatorMenuItem(),
                fitToWindowItem);

        // Simulation menu
        Menu simMenu = new Menu("Simulation");
        MenuItem startItem = new MenuItem("Start");
        MenuItem stopItem = new MenuItem("Stop");
        MenuItem pauseItem = new MenuItem("Pause");
        MenuItem resetItem = new MenuItem("Reset");
        MenuItem regenerateSeedItem = new MenuItem("Regenerate From Seed...");

        startItem.setOnAction(e -> {
            simTimer.start(100, 1);
            startItem.setDisable(true);
            stopItem.setDisable(false);
            pauseItem.setDisable(false);
        });
        stopItem.setOnAction(e -> {
            simTimer.stop();
            startItem.setDisable(false);
            stopItem.setDisable(true);
            pauseItem.setDisable(true);
            visualizationPane.render();
        });
        pauseItem.setOnAction(e -> {
            simTimer.stop();
            startItem.setDisable(false);
            stopItem.setDisable(true);
            pauseItem.setDisable(true);
        });
        resetItem.setOnAction(e -> {
            simTimer.stop();
            vacuum.reset(100, 1);
            visualizationPane.render();
            startItem.setDisable(false);
            stopItem.setDisable(true);
            pauseItem.setDisable(true);
        });
        regenerateSeedItem.setOnAction(e -> promptAndRegenerateFromSeed());

        startItem.setDisable(false);
        stopItem.setDisable(true);
        pauseItem.setDisable(true);

        simMenu.getItems().addAll(startItem, stopItem, pauseItem, new SeparatorMenuItem(),
                regenerateSeedItem, resetItem);

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
        updateStatus(String.format("Zoom %.0f%%", (newScale / 10.0) * 100));
    }

    /**
     * Update zoom label in info panel
     */
    private void updateZoomLabel() {
        if (zoomLabel != null) {
            double percentage = (visualizationPane.getScale() / 10.0) * 100;
            zoomLabel.setText(String.format("%.0f%%", percentage));
        }
    }

    /**
     * Update right panel metrics to reflect latest state
     */
    private void updateInfoPanel() {
        if (house == null || roomsValueLabel == null) {
            return;
        }

        roomsValueLabel.setText(Integer.toString(house.getRooms().size()));
        doorsValueLabel.setText(Integer.toString(house.getDoors().size()));
        obstructionsValueLabel.setText(Integer.toString(house.getObstructions().size()));
        totalAreaValueLabel.setText(String.format("%.1f ft²", house.getTotalArea()));
        cleanableAreaValueLabel.setText(String.format("%.1f ft²", house.getCleanableArea()));
        floorCoveringValueLabel.setText(house.getFloorCovering().getDisplayName());
        seedValueLabel.setText(Long.toString(house.getSeed()));

        validStateValueLabel.getStyleClass().removeAll("valid-state", "invalid-state");
        if (house.isValid()) {
            validStateValueLabel.setText("Valid");
            validStateValueLabel.getStyleClass().add("valid-state");
        } else {
            validStateValueLabel.setText("Invalid");
            validStateValueLabel.getStyleClass().add("invalid-state");
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
                "Version 1.0\nBuilt with JavaFX\n\nTeam: Ian, Ray, Malachi, Vo, Beatrice");
        alert.showAndWait();
    }

    private void toggleControlsWindow() {
        if (controlsStage != null && controlsStage.isShowing()) {
            controlsStage.hide();
            return;
        }

        if (controlsStage == null) {
            controlsStage = createControlsWindow();
        }

        controlsStage.show();
        controlsStage.toFront();
    }

    private Stage createControlsWindow() {
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.initModality(Modality.NONE);
        stage.setTitle("Controls");
        stage.setMinWidth(420);
        stage.setMinHeight(320);

        VBox rootBox = new VBox(12);
        rootBox.setPadding(new Insets(16));
        rootBox.getStyleClass().add("app-root");
        rootBox.getStyleClass().add("info-panel");

        Label titleLabel = new Label("Controls");
        titleLabel.getStyleClass().add("panel-title");

        VBox toolbarCard = new VBox(8);
        toolbarCard.getStyleClass().add("info-card");
        Label toolbarTitle = new Label("Toolbar");
        toolbarTitle.getStyleClass().add("card-title");
        Label toolbarText = new Label(
                "- - and +: zoom out or in\n- 100%: reset zoom level\n- + Room: draw a new room on the canvas\n- Controls: open or hide this window\n- Dark / Light: switch the application theme");
        toolbarText.setWrapText(true);
        toolbarText.getStyleClass().add("hint-text");
        toolbarCard.getChildren().addAll(toolbarTitle, toolbarText);

        VBox canvasCard = new VBox(8);
        canvasCard.getStyleClass().add("info-card");
        Label canvasTitle = new Label("Canvas");
        canvasTitle.getStyleClass().add("card-title");
        Label canvasText = new Label(
                "- Drag the background to pan\n- Hold Ctrl and use the mouse wheel to zoom\n- Click a room to select it\n- Drag room edge handles to resize in 1-foot increments\n- In + Room mode, click and drag to place a room\n- Select a room and click a dashed shared wall to add a door");
        canvasText.setWrapText(true);
        canvasText.getStyleClass().add("hint-text");
        canvasCard.getChildren().addAll(canvasTitle, canvasText);

        rootBox.getChildren().addAll(titleLabel, toolbarCard, canvasCard);

        Scene scene = new Scene(rootBox, 440, 340);
        URL cssUrl = getClass().getResource("/styles/simulator-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        updateControlsWindowTheme(root.getStyleClass().contains("dark-mode"), rootBox);

        stage.setScene(scene);
        return stage;
    }

    private void updateControlsWindowTheme(boolean darkMode) {
        if (controlsStage == null || controlsStage.getScene() == null
                || !(controlsStage.getScene().getRoot() instanceof VBox rootBox)) {
            return;
        }
        updateControlsWindowTheme(darkMode, rootBox);
    }

    private void updateControlsWindowTheme(boolean darkMode, VBox rootBox) {
        if (darkMode) {
            if (!rootBox.getStyleClass().contains("dark-mode")) {
                rootBox.getStyleClass().add("dark-mode");
            }
        } else {
            rootBox.getStyleClass().remove("dark-mode");
        }
    }

    private void promptAndRegenerateFromSeed() {
        TextInputDialog dialog = new TextInputDialog(Long.toString(house.getSeed()));
        dialog.setTitle("Regenerate From Seed");
        dialog.setHeaderText("Generate a new floor plan from a seed");
        dialog.setContentText("Seed:");

        dialog.showAndWait().ifPresent(raw -> {
            String value = raw == null ? "" : raw.trim();
            try {
                long seed = Long.parseLong(value);
                house.setSeed(seed);
                house.generateDefaultFloorPlan();
                refreshUiAfterModelChange("Regenerated floor plan from seed " + seed, true, true);
            } catch (NumberFormatException ex) {
                updateStatus("Invalid seed. Please enter a 64-bit integer value.");
            }
        });
    }

    /**
     * Create default house for testing (Req 1.2) 2000 ft² with 4 rooms
     */
    private House createDefaultHouse() {
        /**
         * Seed for the random number generator. If a constant is given, re-use of that constant as
         * the seed will generate a deterministic sequence of pseudorandom numbers (so room
         * generation should be the same every time that seed is used). Using a moving value (like
         * time in milliseconds) will produce a more random- looking result (less deterministic).
         * The seed is printed so that it can be captured if room/door generation fails with a
         * particular value.
         */
        // long seed = 42L; // any constant seed is deterministic
        // long seed = 0.0; //System.currentTimeMillis(); // moving target is not deterministic
        long seed = 0; // debug placement problems by capturing the seed that failed

        System.out.printf("Generating house floor plan using seed = %d%n", seed);
        House h = new House(seed);

        if (h.hashCode() == 0x1234) { // can't be true, preserves original code
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
        } else {
            h.generateDefaultFloorPlan();
        }
        return h;
    }

    private void handleHouseChanged() {
        refreshUiAfterModelChange(null, false, true);
    }

    private void refreshUiAfterModelChange(String statusMessage, boolean clearSelection,
            boolean rerender) {
        if (clearSelection) {
            visualizationPane.deselectRoom();
        }
        // Recreate vacuum wall colliders for the new house layout
        vacuum.createWallColliders(house.getRooms());
        if (rerender) {
            visualizationPane.render();
        }
        updateInfoPanel();
        if (statusMessage != null && !statusMessage.isBlank()) {
            updateStatus(statusMessage);
        }
    }

    /**
     * Create info panel showing house details
     */
    private VBox createInfoPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(300);
        panel.setMinWidth(280);
        panel.getStyleClass().add("info-panel");

        Label titleLabel = new Label("Simulation Overview");
        titleLabel.getStyleClass().add("panel-title");

        VBox houseCard = new VBox(10);
        houseCard.getStyleClass().add("info-card");
        Label houseCardTitle = new Label("House");
        houseCardTitle.getStyleClass().add("card-title");

        GridPane houseGrid = new GridPane();
        houseGrid.setHgap(10);
        houseGrid.setVgap(8);

        roomsValueLabel = createMetricValueLabel();
        doorsValueLabel = createMetricValueLabel();
        obstructionsValueLabel = createMetricValueLabel();
        totalAreaValueLabel = createMetricValueLabel();
        cleanableAreaValueLabel = createMetricValueLabel();
        floorCoveringValueLabel = createMetricValueLabel();
        seedValueLabel = createMetricValueLabel();
        validStateValueLabel = createMetricValueLabel();

        addMetricRow(houseGrid, 0, "Rooms", roomsValueLabel);
        addMetricRow(houseGrid, 1, "Doors", doorsValueLabel);
        addMetricRow(houseGrid, 2, "Obstructions", obstructionsValueLabel);
        addMetricRow(houseGrid, 3, "Total Area", totalAreaValueLabel);
        addMetricRow(houseGrid, 4, "Cleanable", cleanableAreaValueLabel);
        addMetricRow(houseGrid, 5, "Floor", floorCoveringValueLabel);
        addMetricRow(houseGrid, 6, "Seed", seedValueLabel);
        addMetricRow(houseGrid, 7, "State", validStateValueLabel);
        houseCard.getChildren().addAll(houseCardTitle, houseGrid);

        VBox viewCard = new VBox(10);
        viewCard.getStyleClass().add("info-card");
        Label viewCardTitle = new Label("View");
        viewCardTitle.getStyleClass().add("card-title");
        GridPane viewGrid = new GridPane();
        viewGrid.setHgap(10);
        viewGrid.setVgap(8);
        zoomLabel = createMetricValueLabel();
        addMetricRow(viewGrid, 0, "Zoom", zoomLabel);
        viewCard.getChildren().addAll(viewCardTitle, viewGrid);

        VBox actionsCard = new VBox(10);
        actionsCard.getStyleClass().add("info-card");
        Label actionsCardTitle = new Label("Simulation");
        actionsCardTitle.getStyleClass().add("card-title");

        Button startButton = new Button("Start Simulation");
        Button stopButton = new Button("Stop Simulation");
        startButton.getStyleClass().add("shell-button");
        stopButton.getStyleClass().add("shell-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        stopButton.setMaxWidth(Double.MAX_VALUE);
        Button timeButton = new Button("Timespeed: x1");
        timeButton.getStyleClass().add("shell-button");
        timeButton.setMaxWidth(Double.MAX_VALUE);


        startButton.setOnAction(e -> {
            simTimer.start(100, 1);
            startButton.setDisable(true);
            stopButton.setDisable(false);
        });
        stopButton.setOnAction(e -> {
            simTimer.stop();
            startButton.setDisable(false);
            stopButton.setDisable(true);
            visualizationPane.render();
        });
        timeButton.setOnAction(e -> {
            if (simTimer.getTimescale() == 1.0) {
                timeButton.setText("Timespeed: x5");
                simTimer.setTimeScale(5);

            } else if (simTimer.getTimescale() == 5.0) {
                timeButton.setText("Timespeed: x50");
                simTimer.setTimeScale(50);
            } else {
                timeButton.setText("Timespeed: x1");
                simTimer.setTimeScale(1);
            }
        });
        startButton.setDisable(false);
        stopButton.setDisable(true);
        actionsCard.getChildren().addAll(actionsCardTitle, startButton, stopButton, timeButton);

        panel.getChildren().addAll(titleLabel, houseCard, viewCard, actionsCard);
        return panel;
    }

    private Label createMetricValueLabel() {
        Label value = new Label("--");
        value.getStyleClass().add("metric-value");
        return value;
    }

    private void addMetricRow(GridPane grid, int row, String name, Label valueLabel) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metric-label");
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
