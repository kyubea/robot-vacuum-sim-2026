package com.vacuum.util;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;


public class Vacuum {
    private double x;
    private double y;
    private double angle; // rotation angle in degrees
    private Image image;
    public int moveMode = 0;
    private double battery = 100;

    private static final double BATTERY_DRAIN_RATE = 5.0; // percentage per second

    public Vacuum(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.angle = 0;

        // image was rotated to fit 0 degrees being the right facing direction
        java.io.File imageFile = new java.io.File("src/main/assets/vacuumRotated.png");
        String absolutePath = imageFile.getAbsolutePath();
        this.image = new Image("file:" + absolutePath);

    }


    // move the vacuum using its x y coordinates
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public void reset(float xStart, float yStart, double batteryStart, int moveMode) {
        this.x = xStart;
        this.y = yStart;
        this.angle = 0;
        this.battery = batteryStart;
        this.moveMode = moveMode;

    }


    // move the vacuum forward based on its local direction and time that has passed since last
    // frame
    public void forward(double speed, double deltaTime) {
        double distance = speed * deltaTime;
        double radians = Math.toRadians(angle);

        // Calculate dx and dy based on current orientation
        double dx = distance * Math.cos(radians);
        double dy = distance * Math.sin(radians);

        this.x += dx;
        this.y += dy;
    }

    public void rotate(double rotationSpeed, double deltaTime) {
        double deltaAngle = rotationSpeed * deltaTime;
        this.angle += deltaAngle;
        // Keep angle in 0-360 range
        this.angle = this.angle % 360;
    }


    /**
     * Constrain the vacuum's position within bounds
     */
    public void constrainToBounds(double maxWidth, double maxHeight) {
        x = Math.max(0, Math.min(x, maxWidth - image.getWidth()));
        y = Math.max(0, Math.min(y, maxHeight - image.getHeight()));
    }

    public void update(double deltaTime) {
        // Drain battery over time
        battery -= BATTERY_DRAIN_RATE * deltaTime;

        // Ensure battery doesn't go below 0
        if (battery < 0) {
            battery = 0;
        }

        // Only move if battery is available
        if (battery > 0) {
            switch (moveMode) {
                case 2:
                    alg2(deltaTime);
                    break;
                case 3:
                    alg3(deltaTime);
                    break;
                case 4:
                    alg4(deltaTime);
                    break;
                default:
                    alg1(deltaTime);
                    break;
            }
        }

    }

    private void alg1(double deltaTime) {
        this.rotate(60.0, deltaTime);
        this.forward(60.0, deltaTime);
    }

    private void alg2(double deltaTime) {
        this.rotate(600.0, deltaTime);
    }

    private void alg3(double deltaTime) {
        this.forward(-300.0, deltaTime);
    }

    private void alg4(double deltaTime) {
        this.rotate(-300.0, deltaTime);
    }

    /**
     * Render the vacuum on the canvas
     */
    public void render(GraphicsContext gc) {
        // Save current transform
        gc.save();

        // Move to the image position and rotate around its center
        double centerX = x + image.getWidth() / 2;
        double centerY = y + image.getHeight() / 2;

        gc.translate(centerX, centerY);
        gc.rotate(angle);
        gc.translate(-centerX, -centerY);

        // Draw the image
        gc.drawImage(image, x, y);

        // Restore transform
        gc.restore();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getAngle() {
        return angle;
    }

    public double getBattery() {
        return battery;
    }

    public Image getImage() {
        return image;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

}
