package com.vacuum.ui;

import com.vacuum.util.Vacuum;
import com.vacuum.util.simulationTimer;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Parameters Panel - embedded in main window, displays and allows adjustment of vacuum simulation
 * parameters in real-time. Designed for easy hookup of data sources and shows vacuum status,
 * battery, speed, and position with adjustable parameters.
 */
public class ParametersPanel extends VBox {

    private Vacuum vacuum;
    private simulationTimer simTimer;
    private HouseVisualizationPane visualizationPane;

    // HOOKUP POINTS - Labels that can be easily bound to data sources
    private Label speedValueLabel;
    private Label batteryValueLabel;
    private Label posXValueLabel;
    private Label posYValueLabel;
    private Label orientationValueLabel;
    private Label cleanedAreaLabel;

    // Adjustable parameters
    private Spinner<Double> batteryDrainSpinner;
    private Spinner<Integer> batteryStartSpinner;
    private Spinner<Double> robotSpeedSpinner;
    private ComboBox<Vacuum.MoveMode> movementAlgorithmCombo;

    // Speed multiplier buttons
    private ToggleButton speed1xButton;
    private ToggleButton speed2xButton;
    private ToggleButton speed10xButton;
    private Label speedMultiplierLabel;
    private Runnable parametersChangedHandler;

    public ParametersPanel(Vacuum vacuum, simulationTimer simTimer,
            HouseVisualizationPane visualizationPane) {
        super(12);
        this.vacuum = vacuum;
        this.simTimer = simTimer;
        this.visualizationPane = visualizationPane;

        this.setPadding(new Insets(16));
        this.setStyle("-fx-border-color: transparent transparent transparent #CCCCCC;" +
                "-fx-border-width: 0 0 0 1;");        
        this.getStyleClass().add("parameters-panel");
        this.setPrefWidth(320);
        this.setMinWidth(280);

        // Build the panel
        buildPanel();

        // Start live update loop
        startLiveUpdates();
    }

    private void buildPanel() {
        Label titleLabel = new Label("Simulation Parameters");
        titleLabel.getStyleClass().add("panel-title");

        // Vacuum Status Card
        VBox vacuumCard = createVacuumStatusCard();

        // Simulation Speed Card
        VBox speedCard = createSpeedControlCard();

        // Parameters Adjustment Card
        VBox parametersCard = createParametersCard();

        this.getChildren().addAll(titleLabel, vacuumCard, speedCard, parametersCard);
    }

    /**
     * Creates the vacuum status card showing battery, position, and speed
     */
    private VBox createVacuumStatusCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("info-card");

        Label cardTitle = new Label("Vacuum Status");
        cardTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Vacuum Speed - NOW HOOKED UP to real speed
        speedValueLabel = createValueLabel("0.0 ft/s");
        addParameterRow(grid, 0, "Speed", speedValueLabel, null);

        // Battery Life - HOOKED UP
        batteryValueLabel = createValueLabel("100%");
        addParameterRow(grid, 1, "Battery", batteryValueLabel, null);

        // Position X - HOOKED UP
        posXValueLabel = createValueLabel("0.0");
        addParameterRow(grid, 2, "Position X", posXValueLabel, null);

        // Position Y - HOOKED UP
        posYValueLabel = createValueLabel("0.0");
        addParameterRow(grid, 3, "Position Y", posYValueLabel, null);

        // Orientation - HOOKED UP
        orientationValueLabel = createValueLabel("0°");
        addParameterRow(grid, 4, "Orientation", orientationValueLabel, null);

        cleanedAreaLabel = createValueLabel("0°");
        addParameterRow(grid, 5, "Area cleaned ", cleanedAreaLabel, null);

        card.getChildren().addAll(cardTitle, grid);
        return card;
    }

    /**
     * Creates the speed control card with multiplier buttons
     */
    private VBox createSpeedControlCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("info-card");

        Label cardTitle = new Label("Simulation Speed");
        cardTitle.getStyleClass().add("card-title");

        // Speed multiplier display
        HBox multiplierDisplay = new HBox(5);
        multiplierDisplay.setAlignment(Pos.CENTER_LEFT);
        Label multiplierLabel = new Label("Current Speed:");
        multiplierLabel.getStyleClass().add("metric-label");
        speedMultiplierLabel = new Label("1.0x");
        speedMultiplierLabel.getStyleClass().add("metric-value");
        multiplierDisplay.getChildren().addAll(multiplierLabel, speedMultiplierLabel);

        // Speed buttons container
        VBox buttonContainer = new VBox(8);
        Label controlsLabel = new Label("Speed Multipliers");
        controlsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        HBox buttonRow1 = new HBox(6);
        buttonRow1.setAlignment(Pos.CENTER);

        speed1xButton = createSpeedButton("1x", 1.0);
        speed2xButton = createSpeedButton("5x", 5.0);
        speed10xButton = createSpeedButton("50x", 50.0);

        speed1xButton.setSelected(true); // Default selection

        buttonRow1.getChildren().addAll(speed1xButton, speed2xButton, speed10xButton);

        buttonContainer.getChildren().addAll(controlsLabel, buttonRow1);

        card.getChildren().addAll(cardTitle, multiplierDisplay, new Separator(), buttonContainer);
        return card;
    }

    /**
     * Creates a card for adjustable simulation parameters
     */
    private VBox createParametersCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("info-card");

        Label cardTitle = new Label("Adjustable Parameters");
        cardTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Battery Drain Rate (percent per second)
        batteryDrainSpinner = new Spinner<>(0.001, 0.200, 100.0 / (100.0 * 60.0), 0.001);
        batteryDrainSpinner.setEditable(true);
        batteryDrainSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                vacuum.setBatteryDrainRate(newVal);
            }
            notifyParametersChanged();
        });
        addParameterRowWithSpinner(grid, 0, "Battery Drain (%/s)", batteryDrainSpinner,
                "Roomba-like runtime is around 0.01-0.03 %/s depending on mode and load");

        // Battery Start Value
        batteryStartSpinner = new Spinner<>(0, 100, 100, 5);
        batteryStartSpinner.setEditable(true);
        batteryStartSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            notifyParametersChanged();
        });
        addParameterRowWithSpinner(grid, 1, "Start Battery (%)", batteryStartSpinner,
                "Set initial battery level for next simulation");

        // Movement algorithm
        movementAlgorithmCombo = new ComboBox<>();
        movementAlgorithmCombo.getItems().addAll(Vacuum.MoveMode.values());
        movementAlgorithmCombo.setValue(Vacuum.MoveMode.STRAIGHT);
        movementAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                vacuum.setMoveMode(newVal.getCode());
                notifyParametersChanged();
            }
        });
        addParameterRowWithComboBox(grid, 2, "Movement Algorithm", movementAlgorithmCombo,
                "Choose how the vacuum moves during simulation");

        // Robot speed in ft/s
        robotSpeedSpinner = new Spinner<>(2, 6.00, 2.00, 0.05);
        robotSpeedSpinner.setEditable(true);
        robotSpeedSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                vacuum.setMoveSpeedFeetPerSec(newVal);
                notifyParametersChanged();
            }
        });
        addParameterRowWithSpinner(grid, 3, "Robot Speed (ft/s)", robotSpeedSpinner,
                "Adjust physical movement speed used by cleaning algorithms");

        Label noteLabel = new Label("Click on the canvas to move the vacuum to a location.");
        noteLabel.setWrapText(true);
        noteLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");

        card.getChildren().addAll(cardTitle, grid, new Separator(), noteLabel);
        return card;
    }

    /**
     * Creates a labeled parameter row in a grid with spinner
     */
    private void addParameterRowWithSpinner(GridPane grid, int row, String label,
            Spinner<?> valueControl, String tooltip) {
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("metric-label");
        GridPane.setHgrow(valueControl, Priority.ALWAYS);
        valueControl.setMaxWidth(Double.MAX_VALUE);

        if (tooltip != null) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setWrapText(true);
            tip.setPrefWidth(250);
            nameLabel.setTooltip(tip);
        }

        grid.add(nameLabel, 0, row);
        grid.add(valueControl, 1, row);
    }

    private void addParameterRowWithComboBox(GridPane grid, int row, String label,
            ComboBox<?> comboBox, String tooltip) {
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("metric-label");
        GridPane.setHgrow(comboBox, Priority.ALWAYS);
        comboBox.setMaxWidth(Double.MAX_VALUE);

        if (tooltip != null) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setWrapText(true);
            tip.setPrefWidth(250);
            nameLabel.setTooltip(tip);
        }

        grid.add(nameLabel, 0, row);
        grid.add(comboBox, 1, row);
    }

    /**
     * Creates a labeled parameter row in a grid
     */
    private void addParameterRow(GridPane grid, int row, String label, Label valueLabel,
            String tooltip) {
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("metric-label");
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);

        if (tooltip != null) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setWrapText(true);
            tip.setPrefWidth(250);
            nameLabel.setTooltip(tip);
        }

        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    /**
     * Creates a value label with appropriate styling
     */
    private Label createValueLabel(String text) {
        Label value = new Label(text);
        value.getStyleClass().add("metric-value");
        return value;
    }

    /**
     * Creates a speed multiplier button
     */
    private ToggleButton createSpeedButton(String text, double multiplier) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("shell-button");
        button.setMinWidth(60);
        button.setPrefWidth(75);

        button.setOnAction(e -> {
            simTimer.setTimeMultiplier(multiplier);
            updateSpeedMultiplierLabel();
            // Ensure only one button is selected at a time
            if (button.isSelected()) {
                speed1xButton.setSelected(button == speed1xButton);
                speed2xButton.setSelected(button == speed2xButton);
                speed10xButton.setSelected(button == speed10xButton);
            }
            notifyParametersChanged();
        });

        return button;
    }

    /**
     * Updates the speed multiplier display label
     */
    private void updateSpeedMultiplierLabel() {
        double multiplier = simTimer.getTimeMultiplier();
        if (multiplier == 10.0) {
            speedMultiplierLabel.setText("10x");
        } else if (multiplier == 2.0) {
            speedMultiplierLabel.setText("2x");
        } else {
            speedMultiplierLabel.setText(String.format("%.1fx", multiplier));
        }
    }

    /**
     * Starts an animation loop to update live values from vacuum/simulation
     */
    private void startLiveUpdates() {
        AnimationTimer updateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateValues();
            }
        };
        updateTimer.start();
    }

    /**
     * Updates all displayed values from vacuum/simulation data
     */
    private void updateValues() {
        // Battery - HOOKED UP
        double battery = vacuum.getBattery();
        batteryValueLabel.setText(String.format("%.1f%%", battery));

        // Position - HOOKED UP
        double x = vacuum.getX();
        double y = vacuum.getY();
        posXValueLabel.setText(String.format("%.2f", x));
        posYValueLabel.setText(String.format("%.2f", y));

        // Orientation - HOOKED UP
        double orientation = vacuum.getOrientation();
        orientationValueLabel.setText(String.format("%.1f°", orientation));

        // Speed - NOW HOOKED UP to real speed
        double speed = vacuum.getSpeed();
        speedValueLabel.setText(String.format("%.2f ft/s", speed));

        double areaCleaned = visualizationPane.getCleanedArea();
        double coveragePercent =
                (areaCleaned / visualizationPane.getHouse().getCleanableArea()) * 100;
        cleanedAreaLabel.setText(String.format("%.1f ft² (%.1f%%)", areaCleaned, coveragePercent));
    }

    public int getStartBattery() {
        return batteryStartSpinner.getValue();
    }

    public double getBatteryDrainRatePercent() {
        return batteryDrainSpinner.getValue();
    }

    public double getSpeedMultiplier() {
        return simTimer.getTimeMultiplier();
    }

    public int getSelectedMoveMode() {
        Vacuum.MoveMode selected = movementAlgorithmCombo.getValue();
        return selected != null ? selected.getCode() : Vacuum.MoveMode.STRAIGHT.getCode();
    }

    public double getRobotSpeedFeetPerSec() {
        return robotSpeedSpinner.getValue();
    }

    public void setBatteryDrainRatePercent(double percent) {
        double clamped = Math.max(0.001, Math.min(0.200, percent));
        batteryDrainSpinner.getValueFactory().setValue(clamped);
        vacuum.setBatteryDrainRate(clamped);
        notifyParametersChanged();
    }

    public void setStartBattery(int startBattery) {
        int clamped = Math.max(0, Math.min(100, startBattery));
        batteryStartSpinner.getValueFactory().setValue(clamped);
        notifyParametersChanged();
    }

    public void setSelectedMoveMode(int modeCode) {
        Vacuum.MoveMode mode = Vacuum.MoveMode.fromCode(modeCode);
        movementAlgorithmCombo.setValue(mode);
        vacuum.setMoveMode(mode.getCode());
        notifyParametersChanged();
    }

    public void setRobotSpeedFeetPerSec(double speedFeetPerSec) {
        double clamped = Math.max(0.25, Math.min(3.0, speedFeetPerSec));
        robotSpeedSpinner.getValueFactory().setValue(clamped);
        vacuum.setMoveSpeedFeetPerSec(clamped);
        notifyParametersChanged();
    }

    public void setSpeedMultiplier(double multiplier) {
        double effective = Math.max(0.1, multiplier);
        simTimer.setTimeMultiplier(effective);

        speed1xButton.setSelected(Math.abs(effective - 1.0) < 0.01);
        speed2xButton.setSelected(Math.abs(effective - 2.0) < 0.01);
        speed10xButton.setSelected(Math.abs(effective - 10.0) < 0.01);
        if (!speed1xButton.isSelected() && !speed2xButton.isSelected()
                && !speed10xButton.isSelected()) {
            speed1xButton.setSelected(true);
        }

        updateSpeedMultiplierLabel();
        notifyParametersChanged();
    }

    public void setParametersEditable(boolean editable) {
        batteryDrainSpinner.setDisable(!editable);
        batteryStartSpinner.setDisable(!editable);
        movementAlgorithmCombo.setDisable(!editable);
        robotSpeedSpinner.setDisable(!editable);
        speed1xButton.setDisable(!editable);
        speed2xButton.setDisable(!editable);
        speed10xButton.setDisable(!editable);
    }

    public void setParametersChangedHandler(Runnable parametersChangedHandler) {
        this.parametersChangedHandler = parametersChangedHandler;
    }

    private void notifyParametersChanged() {
        if (parametersChangedHandler != null) {
            parametersChangedHandler.run();
        }
    }

    public void setDarkMode(boolean darkMode) {
        if (darkMode) {
            if (!getStyleClass().contains("dark-mode")) {
                getStyleClass().add("dark-mode");
            }
        } else {
            getStyleClass().remove("dark-mode");
        }
    }
}
