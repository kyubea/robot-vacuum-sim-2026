package com.vacuum.util;

import com.vacuum.model.VacuumConfig;
import com.vacuum.model.Room;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.List;



public class Vacuum {
    public double x;
    private double y;
    private double startX; // x & y to reset to on simulation start
    private double startY;
    private double orientation; // rotation angle in degrees
    public Image vImage;
    public ImageView vImageView;
    public int moveMode = 1;
    private double battery = 100;
    private VacuumConfig config;
    private List<Room> rooms;

    private static final double BATTERY_DRAIN_RATE = 5; // percentage per second

    public Vacuum(double x, double y, List<Room> rooms) {
        this.x = x;
        this.y = y;
        this.startX = x;
        this.startY = y;
        this.orientation = 0;
        this.rooms = rooms;

    }


    // move the vacuum using its x y coordinates
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public void reset(double batteryStart, int moveMode) {
        this.x = startX;
        this.y = startY;
        this.orientation = 0;
        this.battery = batteryStart;
        this.moveMode = moveMode;

    }


    // move the vacuum forward based on its local direction and time that has passed since last
    // frame
    public void forward(double speed, double deltaTime) {
        double distance = speed * deltaTime;
        double radians = Math.toRadians(orientation);

        double dx = distance * Math.cos(radians);
        double dy = distance * Math.sin(radians);

        this.x += dx;
        this.y += dy;
    }

    public void rotate(double rotationSpeed, double deltaTime) {
        double deltaAngle = rotationSpeed * deltaTime;
        this.orientation += deltaAngle;
        // Keep angle in 0-360 range
        this.orientation = this.orientation % 360;
    }

    public void update(double deltaTime) {
        this.battery -= BATTERY_DRAIN_RATE * deltaTime;
        if (this.battery > 0) {
            double rollbackX = this.x;
            double rollbackY = this.y;
            switch (moveMode) {
                default:
                    alg1(deltaTime);
                    break;
                case 2:
                    alg2(deltaTime);
                    break;
                case 3:
                    alg3(deltaTime);
                    break;
                case 4:
                    alg4(deltaTime);
                    break;

            }
            testCollision(rollbackX, rollbackY);
        } else {
            System.out.println("Battery has run out");
        }
    }

    private void alg1(double deltaTime) {
        // placeholder movement alg, replace
        this.forward(10, deltaTime);
        this.rotate(30, deltaTime);

    }

    private void alg2(double deltaTime) {

    }

    private void alg3(double deltaTime) {

    }

    private void alg4(double deltaTime) {

    }

    private void testCollision(double rollbackX, double rollbackY) {
        boolean insideAnyRoom = false;
        for (Room room : this.rooms) {
            if ((x >= room.getX() + 3.2 && x <= room.getMaxX() - 3.2 && y >= room.getY() + 3.2
                    && y <= room.getMaxY() - 3.2)) {
                insideAnyRoom = true;
                break;
            }
        }
        if (!insideAnyRoom) {
            this.x = rollbackX;
            this.y = rollbackY;
            System.out.println("collision");
        }

    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getOrientation() {
        return orientation;
    }


    /*
     * public void constrainToBounds(double maxWidth, double maxHeight) { x = Math.max(0,
     * Math.min(x, maxWidth - image.getWidth())); y = Math.max(0, Math.min(y, maxHeight -
     * image.getHeight())); }
     * 
     * public void update(double deltaTime) { // Drain battery over time battery -=
     * BATTERY_DRAIN_RATE * deltaTime;
     * 
     * // Ensure battery doesn't go below 0 if (battery < 0) { battery = 0; }
     * 
     * // Only move if battery is available if (battery > 0) { switch (moveMode) { case 2:
     * alg2(deltaTime); break; case 3: alg3(deltaTime); break; case 4: alg4(deltaTime); break;
     * default: alg1(deltaTime); break; } }
     * 
     * }
     * 
     * private void alg1(double deltaTime) { this.rotate(60.0, deltaTime); this.forward(60.0,
     * deltaTime); }
     * 
     * private void alg2(double deltaTime) { this.rotate(600.0, deltaTime); }
     * 
     * private void alg3(double deltaTime) { this.forward(-300.0, deltaTime); }
     * 
     * private void alg4(double deltaTime) { this.rotate(-300.0, deltaTime); }
     * 
     * public void getRenderImage() {
     * 
     * 
     * }
     * 
     * 
     * public void render(GraphicsContext gc) { // Save current transform gc.save();
     * 
     * // Move to the image position and rotate around its center double centerX = x +
     * image.getWidth() / 2; double centerY = y + image.getHeight() / 2;
     * 
     * gc.translate(centerX, centerY); gc.rotate(angle); gc.translate(-centerX, -centerY);
     * 
     * // Draw the image gc.drawImage(image, x, y);
     * 
     * // Restore transform gc.restore(); }
     * 
     * 
     * 
     * public double getAngle() { return angle; }
     * 
     * public double getBattery() { return battery; }
     * 
     * public Image getImage() { return image; }
     * 
     * public void setX(double x) { this.x = x; }
     * 
     * public void setY(double y) { this.y = y; }
     * 
     * public void setAngle(double angle) { this.angle = angle; }
     */

}
