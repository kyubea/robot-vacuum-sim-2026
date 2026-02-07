package com.vacuum;

import com.vacuum.util.Vacuum;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private Vacuum vacuum;
    private boolean simStart = false;
    private long lastFrameTime = 0;
    private double timeSpeed = 1.0; // 1.0 = normal speed, 2.0 = 2x speed, etc.

    @Override
    public void start(Stage primaryStage) {

        // Create the vacuum object
        vacuum = new Vacuum(360, 260);

        // Create canvas
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Handle mouse clicks on canvas
        canvas.setOnMouseClicked(this::handleMouseClick);

        // Create scene
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        // Handle keyboard input for time speed control
        scene.setOnKeyPressed(this::handleKeyPress);

        // Animation loop
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Calculate delta time in seconds
                double deltaTime;
                if (lastFrameTime == 0) {
                    deltaTime = 0.016; // ~60 FPS initial value
                } else {
                    deltaTime = (now - lastFrameTime) / 1_000_000_000.0; // Convert nanoseconds to
                                                                         // seconds
                }
                lastFrameTime = now;

                update(deltaTime);
                render(gc);
            }
        };
        timer.start();

        // Set up stage
        primaryStage.setTitle("Robot Vacuum Simulator");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void handleMouseClick(MouseEvent event) {
        if (simStart == false) {
            // Cycle through movement modes 1-4
            int nextMode = (vacuum.moveMode % 4) + 1;
            vacuum.reset(360, 260, 100, nextMode);
            simStart = true;
            lastFrameTime = 0; // Reset delta time calculation
        } else {
            simStart = false;
        }

    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            // Increase time speed (max 5x)
            timeSpeed = Math.min(timeSpeed + 0.5, 5.0);
        } else if (event.getCode() == KeyCode.DOWN) {
            // Decrease time speed (min 0.25x)
            timeSpeed = Math.max(timeSpeed - 0.5, 0.25);
        } else if (event.getCode() == KeyCode.R) {
            // Reset to normal speed
            timeSpeed = 1.0;
        }
    }

    private void update(double deltaTime) {
        if (simStart) {
            // Apply time speed multiplier
            double adjustedDeltaTime = deltaTime * timeSpeed;
            vacuum.update(adjustedDeltaTime);

            // Check if battery is depleted
            if (vacuum.getBattery() <= 0) {
                simStart = false;
            }
        }

    }

    private void render(GraphicsContext gc) {
        // Clear canvas
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        vacuum.render(gc);

        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(14));
        gc.fillText(String.format("Position: (%.0f, %.0f) | Angle: %.1f° | Mode: %d", vacuum.getX(),
                vacuum.getY(), vacuum.getAngle(), vacuum.moveMode), 10, 40);
        gc.fillText(String.format("Battery: %.1f%%", vacuum.getBattery()), 10, 60);
        gc.fillText(String.format("Time Speed: %.2fx (↑/↓ to adjust, R to reset)", timeSpeed), 10,
                80);


    }

    public static void main(String[] args) {
        launch(args);
    }
}
