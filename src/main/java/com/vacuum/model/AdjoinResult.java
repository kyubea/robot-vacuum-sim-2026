package com.vacuum.model;

/**
 * Room adjacency test result.
 */

public class AdjoinResult {
    Side side;
    double minCoord, maxCoord;

    public enum Side {
        LEFT, TOP, RIGHT, BOTTOM
    }

    AdjoinResult(Side side, double min, double max) {
        this.side = side;
        this.minCoord = min;
        this.maxCoord = max;
    }
}
