package com.vacuum.ui;

import com.vacuum.model.*;
import com.vacuum.util.Vacuum;
import com.vacuum.util.simulationTimer;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.event.EventHandler;


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

    private Vacuum vacuum;
    private simulationTimer simTimer;

    @Override
    public void start(Stage primaryStage) {
        // Create default house (Req 1.2: valid default state)
        house = createDefaultHouse();


        Room startPos = house.getRooms().get(0);
        vacuum = new Vacuum(startPos.getX() + startPos.getWidth() / 2,
                startPos.getY() + startPos.getHeight() / 2, house.getRooms());

        // Create UI components
        BorderPane root = new BorderPane();

        // Top: Menu bar
        MenuBar menuBar = createMenuBar(primaryStage);
        root.setTop(menuBar);

        // Center: scrollable, pannable visualization
        visualizationPane = new HouseVisualizationPane();
        visualizationPane.setVacuum(vacuum);
        visualizationPane.setHouse(house);
        simTimer = new simulationTimer(vacuum, visualizationPane);



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

        // Right: info panel
        VBox infoPanel = createInfoPanel();
        root.setRight(infoPanel);



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
                "Version 1.0\nBuilt with JavaFX\n\nTeam: Beatrice (Tech Lead), Ian, Malachi, Ray");
        alert.showAndWait();
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
        long seed = System.currentTimeMillis(); // moving target is not deterministic
        // long seed = 1770586878168L; // debug placement problems by capturing the seed that failed

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
            h.generateFloorPlan(7, 800.0, 2100.0);
        }
        return h;
    }

    /**
     * Create info panel showing house details
     */
    private VBox createInfoPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: #e8e8e8; -fx-border-color: #c0c0c0; -fx-border-width: 0 0 0 1;");
        panel.setPrefWidth(250);

        // House info
        Label titleLabel = new Label("House Information");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label roomsLabel = new Label("Rooms: " + house.getRooms().size());
        Label doorsLabel = new Label("Doors: " + house.getDoors().size());
        Label obstLabel = new Label("Obstructions: " + house.getObstructions().size());
        Label areaLabel = new Label(String.format("Total Area: %.1f ft²", house.getTotalArea()));
        Label cleanableLabel =
                new Label(String.format("Cleanable: %.1f ft²", house.getCleanableArea()));
        Label coveringLabel = new Label("Floor: " + house.getFloorCovering().name());
        Label validLabel = new Label("Valid: " + (house.isValid() ? "Yes" : "No"));

        if (house.isValid()) {
            validLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            validLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }

        // Zoom info
        zoomLabel = new Label("Zoom: 100%");

        // View controls
        Label viewLabel = new Label("View Controls");
        viewLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label tipLabel =
                new Label("• Drag to pan\n• Ctrl+Scroll to zoom\n• Hover rooms for highlight");
        tipLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        // Placeholder buttons (will implement later)
        Button simToggleButton = new Button("Start Simulation");
        simToggleButton.setPrefWidth(200);



        EventHandler<ActionEvent> toggleSim = new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
                if (simTimer.isActive()) {
                    simToggleButton.setText("Start simulation");
                } else {
                    simToggleButton.setText("Stop simulation");
                }
                simTimer.toggleSimTimer();

            }
        };
        simToggleButton.setOnAction(toggleSim);



        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        Separator sep3 = new Separator();

        panel.getChildren().addAll(titleLabel, roomsLabel, doorsLabel, obstLabel, areaLabel,
                cleanableLabel, coveringLabel, validLabel, sep1, zoomLabel, sep2, viewLabel,
                tipLabel, sep3, simToggleButton);

        return panel;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
