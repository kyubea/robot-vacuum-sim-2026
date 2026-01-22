package com.vacuum.model;

/**
 * Blocking obstruction that vacuum cannot traverse. Examples: chests, low furniture, appliances Req
 * 3.2, 3.3: Vacuum shall not traverse blocking obstructions
 */
public class BlockingObstruction extends Obstruction {

    public BlockingObstruction(double x, double y, double width, double height) {
        super(x, y, width, height);
    }

    @Override
    public boolean isTraversable() {
        return false; // Vacuum cannot pass through
    }

    @Override
    public boolean blocksCleanableArea() {
        return true; // Reduces cleanable area (Req 3.5, 3.6)
    }

    @Override
    public String toString() {
        return String.format("BlockingObstruction[id=%s, pos=(%.1f,%.1f), size=%.1fx%.1f]",
                getId().substring(0, 8), getX(), getY(), getWidth(), getHeight());
    }
}
