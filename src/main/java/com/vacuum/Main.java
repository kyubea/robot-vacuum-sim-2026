package com.vacuum;

import com.vacuum.util.Vacuum;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private Vacuum vacuum;
    private boolean movingToTarget = false;
    private double targetX;
    private double targetY;

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

        // Animation loop
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
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
        targetX = 700;
        targetY = 260;
        movingToTarget = true;
    }

    private void update() {
        if (movingToTarget) {
            vacuum.rotate((1));
            vacuum.forward(1);
        }

    }

    private void render(GraphicsContext gc) {
        // Clear canvas
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        vacuum.render(gc);

        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(14));
        gc.fillText(String.format("Position: (%.0f, %.0f) | Angle: %.1f°", vacuum.getX(),
                vacuum.getY(), vacuum.getAngle()), 10, 40);


    }

    public static void main(String[] args) {
        launch(args);
    }
}
