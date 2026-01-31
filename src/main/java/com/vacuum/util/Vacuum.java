package com.vacuum.util;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;


public class Vacuum {
    private double x;
    private double y;
    private double angle; // rotation angle in degrees
    private Image image;

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

    // move the vacuum forward based on its local direction
    public void forward(double distance) {
        double radians = Math.toRadians(angle);

        // Calculate dx and dy based on current orientation
        double dx = distance * Math.cos(radians);
        double dy = distance * Math.sin(radians);

        this.x += dx;
        this.y += dy;
    }

    public void rotate(double deltaAngle) {
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

    // Getters
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getAngle() {
        return angle;
    }

    public Image getImage() {
        return image;
    }

    // Setters
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
