package com.vacuum.util;

import com.vacuum.ui.*;
import javafx.animation.AnimationTimer;

/*
 * class managing the animationTimer for simulation starts/stops
 */
public class simulationTimer {

    private final Vacuum vacuum;
    private final HouseVisualizationPane visualizationPane;
    private final AnimationTimer timer;

    private boolean simActive = false;
    private long prevTime = -1;
    private long simulationStartNanos = -1;
    private long elapsedAtStopNanos = -1;
    private double timeMultiplier = 1.0; // Speed multiplier for simulation (1x, 2x, 10x, 1000x,
                                         // etc)
    private Runnable simulationCompleteHandler;


    public simulationTimer(Vacuum vacuum, HouseVisualizationPane visualizationPane) {
        this.vacuum = vacuum;
        this.visualizationPane = visualizationPane;

        timer = new AnimationTimer() {
            @Override
            public void handle(long currentTime) {
                if (prevTime > 0) {
                    double deltaTime = (currentTime - prevTime) / 1_000_000_000.0;
                    deltaTime *= timeMultiplier; // Apply speed multiplier
                    // Bound catch-up work to prevent frame-time spirals under heavy load.
                    // deltaTime = Math.min(deltaTime, 0.30);
                    // Process in smaller slices to reduce tunneling through thin colliders.
                    double remaining = deltaTime;
                    final double maxStep = 1.0 / 60.0;
                    // int iterations = 0;
                    // final int maxIterations = 30;
                    while (remaining > 0) {
                        double step = Math.min(maxStep, remaining);
                        vacuum.update(step);
                        visualizationPane.updateCleaningFromVacuumFrontHitbox();

                        if (vacuum.getBattery() <= 0 || visualizationPane.isHouseFullyCleaned()) {
                            stop();
                            if (simulationCompleteHandler != null) {
                                simulationCompleteHandler.run();
                            }
                            visualizationPane.render();
                            return;
                        }
                        remaining -= step;
                    }
                }
                visualizationPane.render();
                prevTime = currentTime;
            }
        };
    }

    public void start(double startBattery, int moveMode) {
        vacuum.reset(startBattery, moveMode);
        visualizationPane.resetCleaningMap();
        prevTime = System.nanoTime();
        simulationStartNanos = prevTime;
        elapsedAtStopNanos = -1;
        timer.start();
        simActive = true;
    }

    public void resume() {
        prevTime = System.nanoTime();
        elapsedAtStopNanos = -1;
        if (simulationStartNanos < 0) {
            simulationStartNanos = prevTime;
        }
        timer.start();
        simActive = true;
    }

    public void toggleSimTimer() {
        if (simActive == true) {
            stop();
        } else {
            start(100, 1);
        }
    }



    public void stop() {
        timer.stop();
        if (prevTime > 0) {
            elapsedAtStopNanos = prevTime;
        }
        prevTime = -1;
        simActive = false;
    }

    public boolean isActive() {
        return simActive;
    }

    public void setTimeMultiplier(double multiplier) {
        this.timeMultiplier = Math.max(0.1, multiplier); // Ensure at least 0.1x speed min
    }

    public double getTimeMultiplier() {
        return timeMultiplier;
    }

    public double getElapsedSeconds() {
        if (simulationStartNanos < 0) {
            return 0.0;
        }

        long endTime = simActive ? System.nanoTime()
                : elapsedAtStopNanos > 0 ? elapsedAtStopNanos : System.nanoTime();
        return Math.max(0.0, (endTime - simulationStartNanos) / 1_000_000_000.0);
    }

    public void resetElapsedTime() {
        simulationStartNanos = -1;
        elapsedAtStopNanos = -1;
    }

    public void setSimulationCompleteHandler(Runnable simulationCompleteHandler) {
        this.simulationCompleteHandler = simulationCompleteHandler;
    }

}
