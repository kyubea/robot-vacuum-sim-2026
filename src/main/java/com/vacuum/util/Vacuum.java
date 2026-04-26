package com.vacuum.util;

import com.vacuum.model.Obstruction;
import com.vacuum.model.BlockingObstruction;
import com.vacuum.model.PassUnderObstruction;
import com.vacuum.model.Door;
import com.vacuum.model.Door.Orientation;
import com.vacuum.model.Room;
import com.vacuum.model.VacuumConfig;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Vacuum {
    public enum MoveMode {
        STRAIGHT(1, "Wall Snake"), ZIG_ZAG(2, "Zig-Zag"), SPIRAL(3, "Spot Spiral"), RANDOM_BOUNCE(4,
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
            if (code == 5) {
                // Backward compatibility with older saved layouts that used Spot Spiral code 5.
                return SPIRAL;
            }
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
    private double batteryDrainRate = BATTERY_DRAIN_RATE_DEFAULT; // percentage per second
    private double lastSpeed = 0; // Track actual speed for display
    // private VacuumConfig config;
    private Image image;
    private ImageView imageView;
    private List<Rectangle> wallColliders;

    // ~90 minute runtime from full charge => about 0.0185% per second.
    private static final double BATTERY_DRAIN_RATE_DEFAULT = 100.0 / (90.0 * 60.0);
    private static final double VACUUM_SIZE = VacuumConfig.DIAMETER / 12.0;
    private static final double COLLISION_RADIUS_RATIO = 0.46;
    private static final double DEFAULT_MOVE_SPEED_FT_PER_SEC = 1.0;
    private static final double MIN_MOVE_SPEED_FT_PER_SEC = 0.25;
    private static final double MAX_MOVE_SPEED_FT_PER_SEC = 3.0;

    // Zig-zag variables
    private double laneAdvance = 0.9;
    private double moveSpeedFeetPerSec = DEFAULT_MOVE_SPEED_FT_PER_SEC;
    private ZigZagPhase zigZagPhase = ZigZagPhase.SWEEP;
    private double zigZagTurnTarget = 0;
    private double zigZagLaneStartX = 0;
    private double zigZagLaneStartY = 0;
    private double zigZagSweepHeading = 0;
    private int zigZagLaneDirection = 1;

    // Spiral variables
    private double spiralRadius = 0.75;

    // Wall-follow (straight mode) variables
    private double wallFollowTargetHeading = 0;

    // Spot-spiral variables
    private double spotSpiralTargetHeading = 0;
    private boolean spotSpiralTurning = false;

    private enum ZigZagPhase {
        SWEEP, TURN_TO_LANE, LANE_SHIFT, TURN_TO_SWEEP
    }

    // Random bounce variables
    private double randomDirection = Math.random() * 360;

    // Recovery state for algorithm hardening around doors and corners
    private double stuckTimeSeconds = 0;
    private double recoveryTargetHeading = 0;
    private boolean recoveringFromStuck = false;

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
        this.spiralRadius = 0.75;
        this.randomDirection = Math.random() * 360;
        this.zigZagPhase = ZigZagPhase.SWEEP;
        this.zigZagSweepHeading = this.orientation;
        this.zigZagLaneDirection = 1;
        this.wallFollowTargetHeading = this.orientation;
        this.spotSpiralTurning = false;
        this.spotSpiralTargetHeading = this.orientation;
        this.stuckTimeSeconds = 0;
        this.recoveringFromStuck = false;
        this.recoveryTargetHeading = this.orientation;
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
        alignOrientationWithVector(dx, dy);
    }

    public void rotate(double rotationSpeed, double deltaTime) {
        double deltaAngle = rotationSpeed * deltaTime;
        this.orientation += deltaAngle;
        // Keeps angle in 0-360 range
        this.orientation = this.orientation % 360;
    }

    public void update(double deltaTime) {
        // Drain battery and clamp to [0, 100]
        this.battery -= batteryDrainRate * deltaTime;
        this.battery = Math.max(0, Math.min(100, this.battery));

        if (this.battery > 0) {
            double rollbackX = this.x;
            double rollbackY = this.y;

            if (recoveringFromStuck) {
                rotateToward(recoveryTargetHeading, 220.0, deltaTime);
                if (isAngleClose(orientation, recoveryTargetHeading, 3.0)) {
                    recoveringFromStuck = false;
                }
                this.lastSpeed = 0;
                return;
            }

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

            boolean collided = testCollision(rollbackX, rollbackY);
            double movedDistance = Math.hypot(x - rollbackX, y - rollbackY);

            if ((collided || movedDistance < 0.01) && this.lastSpeed > 0.05) {
                stuckTimeSeconds += deltaTime;
            } else {
                stuckTimeSeconds = Math.max(0.0, stuckTimeSeconds - deltaTime * 2.0);
            }

            if (stuckTimeSeconds > 1.0) {
                recoveringFromStuck = true;
                recoveryTargetHeading = normalizeAngle(orientation + 70.0 + Math.random() * 80.0);
                stuckTimeSeconds = 0;
            }
        } else {
            this.lastSpeed = 0;
            System.out.println("Battery has run out");
        }
    }

    private void alg1(double deltaTime) {
        // Straight mode now behaves like practical wall-follow cleaning: hug a wall and snake.
        double speed = moveSpeedFeetPerSec;
        double turnRate = 120.0;

        double aheadDistance = distanceToObstacle(orientation, 2.0);
        double leftNearDistance = distanceToObstacle(orientation - 90.0, 2.0);
        double leftFarDistance = distanceToObstacle(orientation - 90.0, 4.0);

        if (aheadDistance < VACUUM_SIZE * 0.7) {
            // Corner or dead-end: turn right naturally and keep wall-follow continuity.
            wallFollowTargetHeading = normalizeAngle(orientation + 82.0);
        } else if (leftFarDistance < 3.95) {
            // We can sense a wall on the left: steer to hold a soft stand-off distance.
            double desiredLeftGap = 1.1;
            double error = desiredLeftGap - leftNearDistance;
            double steering = Math.max(-16.0, Math.min(16.0, error * 26.0));
            wallFollowTargetHeading = normalizeAngle(orientation - steering);
        } else {
            // No nearby wall: keep current heading instead of turning in circles.
            wallFollowTargetHeading = orientation;
        }

        rotateToward(wallFollowTargetHeading, turnRate, deltaTime);
        if (!isObstacleNear(orientation, VACUUM_SIZE * 0.45)) {
            forward(speed, deltaTime);
        } else {
            this.lastSpeed = 0;
        }
    }

    private void alg2(double deltaTime) {
        double speed = moveSpeedFeetPerSec;
        double turnRate = 160.0;
        double laneHeading = normalizeAngle(zigZagSweepHeading + 90.0 * zigZagLaneDirection);

        switch (zigZagPhase) {
            case SWEEP:
                rotateToward(zigZagSweepHeading, turnRate, deltaTime);
                if (isObstacleNear(orientation, VACUUM_SIZE * 0.70)) {
                    zigZagPhase = ZigZagPhase.TURN_TO_LANE;
                    zigZagTurnTarget = laneHeading;
                    break;
                }
                forward(speed, deltaTime);
                break;
            case TURN_TO_LANE:
                rotateToward(zigZagTurnTarget, turnRate, deltaTime);
                if (isAngleClose(orientation, zigZagTurnTarget, 3.0)) {
                    zigZagPhase = ZigZagPhase.LANE_SHIFT;
                    zigZagLaneStartX = x;
                    zigZagLaneStartY = y;
                }
                this.lastSpeed = 0;
                break;
            case LANE_SHIFT:
                if (!isObstacleNear(orientation, VACUUM_SIZE * 0.60)) {
                    forward(speed * 0.7, deltaTime);
                }
                double laneDistance = Math.hypot(x - zigZagLaneStartX, y - zigZagLaneStartY);
                if (laneDistance >= Math.max(0.5, laneAdvance)
                        || isObstacleNear(orientation, VACUUM_SIZE * 0.60)) {
                    if (laneDistance < 0.20) {
                        // Lane shift blocked quickly; swap side to avoid thrashing same corridor.
                        zigZagLaneDirection *= -1;
                    }
                    zigZagSweepHeading = normalizeAngle(zigZagSweepHeading + 180.0);
                    zigZagTurnTarget = zigZagSweepHeading;
                    zigZagPhase = ZigZagPhase.TURN_TO_SWEEP;
                }
                break;
            case TURN_TO_SWEEP:
                rotateToward(zigZagTurnTarget, turnRate, deltaTime);
                if (isAngleClose(orientation, zigZagTurnTarget, 3.0)) {
                    zigZagPhase = ZigZagPhase.SWEEP;
                }
                this.lastSpeed = 0;
                break;
            default:
                zigZagPhase = ZigZagPhase.SWEEP;
                break;
        }

    }

    private void alg3(double deltaTime) {
        // Spiral mode replaced by spot-spiral behavior for a more realistic consumer profile.
        alg5(deltaTime);

    }

    private void alg4(double deltaTime) {
        double speed = moveSpeedFeetPerSec;
        double turnRate = 180.0;

        if (isObstacleNear(randomDirection, VACUUM_SIZE * 0.65)) {
            randomDirection = normalizeAngle(randomDirection + 120.0 + Math.random() * 80.0);
        }

        rotateToward(randomDirection, turnRate, deltaTime);

        if (!isObstacleNear(orientation, VACUUM_SIZE * 0.45)) {
            forward(speed, deltaTime);
        } else {
            randomDirection = normalizeAngle(orientation + 140.0 + Math.random() * 60.0);
            this.lastSpeed = 0;
        }
    }

    private void alg5(double deltaTime) {
        // Spot-clean spiral used by consumer robots for concentrated dirty regions.
        double speed = Math.max(0.45, moveSpeedFeetPerSec * 0.7);
        double baseTurnRate = 220.0 / Math.max(1.0, spiralRadius * 2.2);

        if (spotSpiralTurning) {
            rotateToward(spotSpiralTargetHeading, 210.0, deltaTime);
            if (isAngleClose(orientation, spotSpiralTargetHeading, 4.0)) {
                spotSpiralTurning = false;
            }
            this.lastSpeed = 0;
            return;
        }

        if (isObstacleNear(orientation, VACUUM_SIZE * 0.45)) {
            spotSpiralTargetHeading = normalizeAngle(orientation + 130.0 + Math.random() * 80.0);
            spotSpiralTurning = true;
            spiralRadius = Math.max(0.75, spiralRadius * 0.8);
            this.lastSpeed = 0;
            return;
        }

        rotate(baseTurnRate, deltaTime);
        forward(speed, deltaTime);
        spiralRadius = Math.min(10.0, spiralRadius + 0.12 * deltaTime);
    }

    private void rotateToward(double targetAngle, double turnRateDegPerSec, double deltaTime) {
        double diff = shortestSignedAngleDiff(targetAngle, orientation);
        double maxStep = Math.max(0, turnRateDegPerSec) * deltaTime;
        double applied = Math.max(-maxStep, Math.min(maxStep, diff));
        orientation = normalizeAngle(orientation + applied);
    }

    private boolean isObstacleNear(double directionDegrees, double probeDistance) {
        double radians = Math.toRadians(directionDegrees);
        double probeX = x + Math.cos(radians) * probeDistance;
        double probeY = y + Math.sin(radians) * probeDistance;
        return checkCollisionAt(probeX, probeY);
    }

    private double distanceToObstacle(double directionDegrees, double maxDistance) {
        double radians = Math.toRadians(directionDegrees);
        double step = 0.08;
        for (double d = step; d <= maxDistance; d += step) {
            double probeX = x + Math.cos(radians) * d;
            double probeY = y + Math.sin(radians) * d;
            if (checkCollisionAt(probeX, probeY)) {
                return d;
            }
        }
        return maxDistance;
    }

    private double shortestSignedAngleDiff(double target, double current) {
        double diff = normalizeAngle(target) - normalizeAngle(current);
        if (diff > 180.0) {
            diff -= 360.0;
        } else if (diff < -180.0) {
            diff += 360.0;
        }
        return diff;
    }

    private boolean isAngleClose(double a, double b, double toleranceDeg) {
        return Math.abs(shortestSignedAngleDiff(a, b)) <= toleranceDeg;
    }

    private double normalizeAngle(double value) {
        double normalized = value % 360.0;
        return normalized < 0 ? normalized + 360.0 : normalized;
    }

    /**
     * Improved collision detection with continuous collision checking for high-speed movement
     */
    private boolean testCollision(double rollbackX, double rollbackY) {
        // Check along the full travel path in world space so zoom/offset never affect collision.
        double distance = Math.hypot(x - rollbackX, y - rollbackY);
        int substeps = Math.max(1, (int) Math.ceil(distance / 0.10));
        substeps = Math.min(substeps, 250);

        double stepX = (x - rollbackX) / substeps;
        double stepY = (y - rollbackY) / substeps;

        for (int step = 1; step <= substeps; step++) {
            double testX = rollbackX + (stepX * step);
            double testY = rollbackY + (stepY * step);

            if (checkCollisionAt(testX, testY)) {
                // Collision detected, restore to position before this step
                x = rollbackX + (stepX * (step - 1));
                y = rollbackY + (stepY * (step - 1));
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there's a collision at the given position
     */
    private boolean checkCollisionAt(double testX, double testY) {
        Circle hitbox = new Circle();
        double circleOffset = VACUUM_SIZE * 0.5;
        hitbox.setCenterX(testX + circleOffset);
        hitbox.setCenterY(testY + circleOffset);
        hitbox.setRadius(VACUUM_SIZE * COLLISION_RADIUS_RATIO);

        for (Rectangle wall : wallColliders) {
            if (hitboxCollides(hitbox, wall)) {
                return true;
            }
        }
        return false;
    }

    private void alignOrientationWithVector(double dx, double dy) {
        if (Math.hypot(dx, dy) < 1e-9) {
            return;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        this.orientation = angle < 0 ? angle + 360 : angle;
    }


    private boolean hitboxCollides(Circle a, Rectangle b) {
        double testX = a.getCenterX();
        double testY = a.getCenterY();

        /*
         * Determine which edge of the rectangle is closest to the circle, then see if there is a
         * collision using the Pythagorean Theorem
         * https://www.jeffreythompson.org/collision-detection/circle-rect.php
         */

        if (a.getCenterX() < b.getX()) {
            testX = b.getX();
        } else if (a.getCenterX() > b.getX() + b.getWidth()) {
            testX = b.getX() + b.getWidth();
        }

        if (a.getCenterY() < b.getY()) {
            testY = b.getY();
        } else if (a.getCenterY() > b.getY() + b.getHeight()) {
            testY = b.getY() + b.getHeight();
        }

        double distX = a.getCenterX() - testX;
        double distY = a.getCenterY() - testY;
        double distance = Math.sqrt((distX * distX) + (distY * distY));
        if (distance <= a.getRadius()) {
            return true;
        } else {
            return false;
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

    public double getBattery() {
        return battery;
    }

    public double getSpeed() {
        return lastSpeed;
    }

    public void setBatteryDrainRate(double drainRate) {
        this.batteryDrainRate = Math.max(0, drainRate); // Ensure non-negative
        System.out.println(batteryDrainRate);
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

    public void setMoveSpeedFeetPerSec(double speed) {
        this.moveSpeedFeetPerSec =
                Math.max(MIN_MOVE_SPEED_FT_PER_SEC, Math.min(MAX_MOVE_SPEED_FT_PER_SEC, speed));
    }

    public double getMoveSpeedFeetPerSec() {
        return moveSpeedFeetPerSec;
    }

    public int getMoveMode() {
        return moveMode;
    }

    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public void setOrientation(double orientation) {
        double normalized = orientation % 360.0;
        this.orientation = normalized < 0 ? normalized + 360.0 : normalized;
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

    /**
     * Add colliders for obstructions. Blocking obstructions are treated as full colliders, while
     * pass-under obstructions only have colliders for their legs, allowing the vacuum to pass
     * through the open space between legs.
     *
     * I'm abusing the "wallColliders" list to also store obstruction colliders since they function
     * the same for collision detection. This way we don't need to check multiple lists for
     * collisions, just one.
     */
    public void addObstructions(List<Obstruction> obstructions) {
        for (Obstruction obstruction : obstructions) {
            if (obstruction instanceof BlockingObstruction) {
                // Blocking obstructions are treated as full colliders
                BlockingObstruction block = (BlockingObstruction) obstruction;
                wallColliders.add(block.getObstructedRectangle());
            } else {
                // For pass-under obstructions, we add colliders for the legs only, not the whole
                // area.
                PassUnderObstruction pass = (PassUnderObstruction) obstruction;
                // Leg 1: LL corner of obstruction
                Rectangle rect = new Rectangle(pass.getX(), pass.getY(), pass.getLegDiameter(),
                        pass.getLegDiameter());
                wallColliders.add(rect);
                // Leg 2: LR corner of obstruction
                rect = new Rectangle(
                        pass.getX() + pass.getHSpaceBetweenLegs() - pass.getLegDiameter(),
                        pass.getY(), pass.getLegDiameter(), pass.getLegDiameter());
                wallColliders.add(rect);
                // Leg 3: UR corner of obstruction
                rect = new Rectangle(
                        pass.getX() + pass.getHSpaceBetweenLegs() - pass.getLegDiameter(),
                        pass.getY() + pass.getVSpaceBetweenLegs() - pass.getLegDiameter(),
                        pass.getLegDiameter(), pass.getLegDiameter());
                wallColliders.add(rect);
                // Leg 4: UL corner of obstruction
                rect = new Rectangle(pass.getX(),
                        pass.getY() + pass.getVSpaceBetweenLegs() - pass.getLegDiameter(),
                        pass.getLegDiameter(), pass.getLegDiameter());
                wallColliders.add(rect);
            }
        }
    }
}
