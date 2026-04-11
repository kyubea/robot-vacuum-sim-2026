package com.vacuum.util;

import com.vacuum.model.Door;
import com.vacuum.model.Door.Orientation;
import com.vacuum.model.Room;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Vacuum {
    public enum MoveMode {
        STRAIGHT(1, "Straight"), ZIG_ZAG(2, "Zig-Zag"), SPIRAL(3, "Spiral"), RANDOM_BOUNCE(4,
                "Random Bounce");

        private final int code;
        private final String displayName;

        MoveMode(int code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public int getCode() {
            return code;
        }

        public static MoveMode fromCode(int code) {
            for (MoveMode mode : values()) {
                if (mode.code == code) {
                    return mode;
                }
            }
            return STRAIGHT;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public double x;
    private double y;
    private double startX; // x & y to reset to on simulation start
    private double startY;
    private double orientation; // rotation angle in degrees
    public Image vImage;
    public ImageView vImageView;
    private int moveMode = MoveMode.STRAIGHT.getCode();
    private double battery = 100;
    private double batteryDrainRate = 5; // percentage per second (configurable)
    private double lastSpeed = 0; // Track actual speed for display
    // private VacuumConfig config;
    private Image image;
    private ImageView imageView;
    private List<Rectangle> wallColliders;

    private static final double BATTERY_DRAIN_RATE_DEFAULT = 5; // percentage per second
    private static final double VACUUM_SIZE = 2;

    // Zig-zag variables
    private boolean movingRight = true;
    private double stepSize = 5;
    private double movementSpeed = 20;

    // Spiral variables
    private double spiralAngle = 0;
    private double spiralRadius = 1;
    private double spiralGrowth = 5;

    // Random bounce variables
    private double randomDirection = Math.random() * 360;

    public Vacuum(double x, double y) {
        this.x = x;
        this.y = y;
        this.startX = x;
        this.startY = y;
        this.orientation = 0;
        image = new Image(getClass().getResourceAsStream("/vacuumRotated.png"));
        imageView = new ImageView();
        imageView.setImage(image);
        wallColliders = new ArrayList<Rectangle>();
    }

    // old movement function, delete this after code has been merged if it isn't being used
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public void reset(double batteryStart, int moveMode) {
        this.x = startX;
        this.y = startY;
        this.orientation = 0;
        this.battery = batteryStart;
        setMoveMode(moveMode);
        this.lastSpeed = 0;
        this.movingRight = true;
        this.spiralAngle = 0;
        this.spiralRadius = 1;
        this.randomDirection = Math.random() * 360;

    }

    /*
     * move the vacuum forward based on its local direction and time that has passed since last
     * frame
     */
    public void forward(double speed, double deltaTime) {
        double distance = speed * deltaTime;
        double radians = Math.toRadians(orientation);

        double dx = distance * Math.cos(radians);
        double dy = distance * Math.sin(radians);

        this.lastSpeed = speed; // Track speed
        this.x += dx;
        this.y += dy;
    }

    public void rotate(double rotationSpeed, double deltaTime) {
        double deltaAngle = rotationSpeed * deltaTime;
        this.orientation += deltaAngle;
        // Keeps angle in 0-360 range
        this.orientation = this.orientation % 360;
    }

    public void update(double deltaTime, double offsetX, double offsetY, double screenScale) {
        // Drain battery and clamp to [0, 100]
        this.battery -= batteryDrainRate * deltaTime;
        this.battery = Math.max(0, Math.min(100, this.battery));

        if (this.battery > 0) {
            double rollbackX = this.x;
            double rollbackY = this.y;
            double rollbackSpeed = this.lastSpeed;
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
            testCollision(rollbackX, rollbackY, rollbackSpeed, offsetX, offsetY, screenScale);
        } else {
            this.lastSpeed = 0;
            System.out.println("Battery has run out");
        }
    }

    private void alg1(double deltaTime) {
        this.forward(10, deltaTime);
    }

    private void alg2(double deltaTime) {
        double rollbackX = this.x;
        double rollbackY = this.y;

        if (movingRight) {
            this.x += movementSpeed * deltaTime;
        } else {
            this.x -= movementSpeed * deltaTime;
        }
        this.lastSpeed = movementSpeed;

        if (checkCollisionAt(this.x, this.y, 0, 0, 1)) {
            this.x = rollbackX;
            this.y = rollbackY + stepSize;
            movingRight = !movingRight;
            this.lastSpeed = stepSize / Math.max(deltaTime, 1e-9);
        }

    }

    private void alg3(double deltaTime) {
        spiralAngle += 2 * deltaTime;
        spiralRadius += spiralGrowth * deltaTime;

        double dx = spiralRadius * Math.cos(spiralAngle) * deltaTime;
        double dy = spiralRadius * Math.sin(spiralAngle) * deltaTime;

        this.x += dx;
        this.y += dy;
        this.lastSpeed = Math.hypot(dx, dy) / Math.max(deltaTime, 1e-9);

    }

    private void alg4(double deltaTime) {
        double rollbackX = this.x;
        double rollbackY = this.y;

        double radians = Math.toRadians(randomDirection);
        double dx = movementSpeed * Math.cos(radians) * deltaTime;
        double dy = movementSpeed * Math.sin(radians) * deltaTime;

        this.x += dx;
        this.y += dy;
        this.lastSpeed = movementSpeed;

        if (checkCollisionAt(this.x, this.y, 0, 0, 1)) {
            this.x = rollbackX;
            this.y = rollbackY;
            randomDirection = Math.random() * 360;
            this.lastSpeed = 0;
        }

    }

    /**
     * Improved collision detection with continuous collision checking for high-speed movement
     */
    private void testCollision(double rollbackX, double rollbackY, double lastSpeed, double offsetX,
            double offsetY, double screenScale) {
        // Check along the full travel path in world space so zoom/offset never affect collision.
        double distance = Math.hypot(x - rollbackX, y - rollbackY);
        int substeps = Math.max(1, (int) Math.ceil(distance / 0.10));
        substeps = Math.min(substeps, 250);

        double stepX = (x - rollbackX) / substeps;
        double stepY = (y - rollbackY) / substeps;

        for (int step = 1; step <= substeps; step++) {
            double testX = rollbackX + (stepX * step);
            double testY = rollbackY + (stepY * step);

            if (checkCollisionAt(testX, testY, offsetX, offsetY, screenScale)) {
                // Collision detected, restore to position before this step
                x = rollbackX + (stepX * (step - 1));
                y = rollbackY + (stepY * (step - 1));
                return;
            }
        }
    }

    /**
     * Check if there's a collision at the given position
     */
    private boolean checkCollisionAt(double testX, double testY, double offsetX, double offsetY,
            double screenScale) {
        Rectangle hitbox = new Rectangle();
        hitbox.setX(testX);
        hitbox.setY(testY);
        hitbox.setWidth(VACUUM_SIZE);
        hitbox.setHeight(VACUUM_SIZE);

        for (Rectangle wall : wallColliders) {
            if (hitbox.intersects(wall.getBoundsInLocal())) {
                return true;
            }
        }
        return false;
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

    public Image getImage() {
        return image;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public double getSize() {
        return VACUUM_SIZE;
    }

    public double getBattery() {
        return battery;
    }

    public double getSpeed() {
        return lastSpeed;
    }

    public void setBatteryDrainRate(double drainRate) {
        this.batteryDrainRate = Math.max(0, drainRate); // Ensure non-negative
    }

    public double getBatteryDrainRate() {
        return batteryDrainRate;
    }

    public void setBattery(double newBattery) {
        this.battery = Math.max(0, Math.min(100, newBattery)); // Clamp to [0, 100]
    }

    public void setMoveMode(int modeCode) {
        this.moveMode = MoveMode.fromCode(modeCode).getCode();
    }

    public int getMoveMode() {
        return moveMode;
    }

    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }

    public void setStartPosition(double newStartX, double newStartY) {
        this.startX = newStartX;
        this.startY = newStartY;
    }

    public void createWallColliders(List<Room> rooms) { // create walls of the house's rooms
        this.wallColliders = new ArrayList<Rectangle>();
        for (Room room : rooms) {

            /*
             * arrays representing a number line for the left to right side of each of the house's
             * walls
             */
            ArrayList<Double> leftInterrupts = new ArrayList<Double>();
            ArrayList<Double> rightInterrupts = new ArrayList<Double>();
            ArrayList<Double> topInterrupts = new ArrayList<Double>();
            ArrayList<Double> bottomInterrupts = new ArrayList<Double>();
            leftInterrupts.add(room.getY());
            leftInterrupts.add(room.getMaxY());
            rightInterrupts.add(room.getY());
            rightInterrupts.add(room.getMaxY());
            topInterrupts.add(room.getX());
            topInterrupts.add(room.getMaxX());
            bottomInterrupts.add(room.getX());
            bottomInterrupts.add(room.getMaxX());

            for (Door door : room.getDoors()) {
                /*
                 * get door orientation to find hort/vertical orientation, then find where the door
                 * overrlaps /w room. On success use the door's position & width/height to split up
                 * the respective room wall number line
                 */
                if (door.getOrientation() == Orientation.HORIZONTAL) {
                    if (door.getY() == room.getY()) {
                        topInterrupts.add(door.getX());
                        topInterrupts.add(door.getX() + door.getWidth());
                    } else if (door.getY() == room.getMaxY()) {
                        bottomInterrupts.add(door.getX());
                        bottomInterrupts.add(door.getX() + door.getWidth());
                    } else {
                        System.err.println("error getting door horz. position");
                    }
                } else if (door.getOrientation() == Orientation.VERTICAL) {
                    if (door.getX() == room.getX()) {
                        leftInterrupts.add(door.getY());
                        leftInterrupts.add(door.getY() + door.getWidth());
                    } else if (door.getX() == room.getMaxX()) {
                        rightInterrupts.add(door.getY());
                        rightInterrupts.add(door.getY() + door.getWidth());
                    } else {
                        System.err.println("error getting door vert. position");
                        System.err.println(door.getX());
                        System.err.println(room.getMaxX());

                    }
                } else {
                    System.err.println("error getting door orientation");
                }
            }

            Collections.sort(leftInterrupts);
            Collections.sort(rightInterrupts);
            Collections.sort(topInterrupts);
            Collections.sort(bottomInterrupts);

            /*
             * get 2 numbers of array as rectangle span unless this room index is on the other side
             * of an already created wall
             */

            for (int i = 0; i < leftInterrupts.size(); i += 2)
                addColliderIfUnique(room.getX(), leftInterrupts.get(i), 0.1,
                        leftInterrupts.get(i + 1) - leftInterrupts.get(i));

            for (int i = 0; i < rightInterrupts.size(); i += 2)
                addColliderIfUnique(room.getMaxX(), rightInterrupts.get(i), 0.1,
                        rightInterrupts.get(i + 1) - rightInterrupts.get(i));

            for (int i = 0; i < topInterrupts.size(); i += 2)
                addColliderIfUnique(topInterrupts.get(i), room.getY(),
                        topInterrupts.get(i + 1) - topInterrupts.get(i), 0.1);

            for (int i = 0; i < bottomInterrupts.size(); i += 2)
                addColliderIfUnique(bottomInterrupts.get(i), room.getMaxY(),
                        bottomInterrupts.get(i + 1) - bottomInterrupts.get(i), 0.1);
        }
    }

    private void addColliderIfUnique(double x, double y, double width, double height) {
        for (Rectangle existing : wallColliders) {
            if (existing.getX() == x && existing.getY() == y && existing.getWidth() == width
                    && existing.getHeight() == height) {
                return;
            }
        }
        Rectangle rect = new Rectangle();
        rect.setX(x);
        rect.setY(y);
        rect.setWidth(width);
        rect.setHeight(height);
        rect.setFill(Color.RED);
        rect.setStroke(Color.RED);
        rect.setStrokeWidth(1.0);
        wallColliders.add(rect);
    }

    public List<Rectangle> getWallColliders() {
        return this.wallColliders;
    }
}
