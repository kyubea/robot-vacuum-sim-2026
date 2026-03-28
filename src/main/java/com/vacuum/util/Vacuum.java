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
    public double x;
    private double y;
    private double startX; // x & y to reset to on simulation start
    private double startY;
    private double orientation; // rotation angle in degrees
    public Image vImage;
    public ImageView vImageView;
    public int moveMode = 1;
    private double battery = 100;
    // private VacuumConfig config;
    private Image image;
    private ImageView imageView;
    private List<Rectangle> wallColliders;

    private static final double BATTERY_DRAIN_RATE = 5; // percentage per second
    private static final double VACUUM_SIZE = 2;

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
        this.moveMode = moveMode;

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
            testCollision(rollbackX, rollbackY, offsetX, offsetY, screenScale);
        } else {
            System.out.println("Battery has run out");
        }
    }

    private void alg1(double deltaTime) {
        // placeholder movement alg, replace
        this.forward(10, deltaTime);
        // this.rotate(30, deltaTime);

    }

    private void alg2(double deltaTime) {

    }

    private void alg3(double deltaTime) {

    }

    private void alg4(double deltaTime) {

    }

    private void testCollision(double rollbackX, double rollbackY, double offsetX, double offsetY,
            double screenScale) {
        // scale vacuum hitbox to visually match screen scale
        Rectangle scaledHitbox = new Rectangle();
        scaledHitbox.setX(100 + x * screenScale);
        scaledHitbox.setY(100 + y * screenScale);
        scaledHitbox.setWidth(VACUUM_SIZE * screenScale);
        scaledHitbox.setHeight(VACUUM_SIZE * screenScale);
        for (Rectangle wall : wallColliders) {
            // scale walls to visually match screen scale
            Rectangle scaledWall = new Rectangle();
            scaledWall.setX(offsetX + wall.getX() * screenScale);
            scaledWall.setY(offsetY + wall.getY() * screenScale);
            scaledWall.setWidth(wall.getWidth() * screenScale);
            scaledWall.setHeight(wall.getHeight() * screenScale);
            // check for collision at world size
            if (scaledHitbox.intersects(scaledWall.getBoundsInParent())) {
                x = rollbackX;
                y = rollbackY;
                break;
            }
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

    public Image getImage() {
        return image;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public double getSize() {
        return VACUUM_SIZE;
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
