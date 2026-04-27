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
    private long runStartNanos = -1;
    private double accumulatedRealSeconds = 0.0;
    private double accumulatedSimulationSeconds = 0.0;
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
                    double realDeltaTime = (currentTime - prevTime) / 1_000_000_000.0;
                    if (realDeltaTime <= 0.0) {
                        prevTime = currentTime;
                        return;
                    }
                    // Clamp very large catch-up steps to reduce visible hitching after stalls.
                    double deltaTime = Math.min(0.5, realDeltaTime * timeMultiplier);
                    // Bound catch-up work to prevent frame-time spirals under heavy load.
                    // Process in bounded slices to reduce tunneling through thin colliders.
                    final double maxSubStep = 1.0 / 60.0;
                    int subSteps = Math.max(1, (int) Math.ceil(deltaTime / maxSubStep));
                    subSteps = Math.min(subSteps, 30);
                    double step = deltaTime / subSteps;
                    for (int i = 0; i < subSteps; i++) {
                        vacuum.update(step);
                        visualizationPane.updateCleaningFromVacuumFrontHitbox();
                        accumulatedSimulationSeconds += step;

                        if (vacuum.getBattery() <= 0 || visualizationPane.isHouseFullyCleaned()) {
                            stopAtNanos(currentTime);
                            if (simulationCompleteHandler != null) {
                                simulationCompleteHandler.run();
                            }
                            visualizationPane.render();
                            return;
                        }
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
        accumulatedRealSeconds = 0.0;
        accumulatedSimulationSeconds = 0.0;
        runStartNanos = System.nanoTime();
        prevTime = runStartNanos;
        timer.start();
        simActive = true;
    }

    public void resume() {
        runStartNanos = System.nanoTime();
        prevTime = runStartNanos;
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
        stopAtNanos(System.nanoTime());
    }

    private void stopAtNanos(long stopTimeNanos) {
        timer.stop();
        if (simActive && runStartNanos > 0) {
            accumulatedRealSeconds +=
                    Math.max(0.0, (stopTimeNanos - runStartNanos) / 1_000_000_000.0);
        }
        runStartNanos = -1;
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

    public double getRealElapsedSeconds() {
        if (simActive && runStartNanos > 0) {
            double currentSegment = (System.nanoTime() - runStartNanos) / 1_000_000_000.0;
            return Math.max(0.0, accumulatedRealSeconds + currentSegment);
        }
        return Math.max(0.0, accumulatedRealSeconds);
    }

    public double getSimulationElapsedSeconds() {
        return Math.max(0.0, accumulatedSimulationSeconds);
    }

    public double getElapsedSeconds() {
        return getRealElapsedSeconds();
    }

    public void resetElapsedTime() {
        runStartNanos = -1;
        prevTime = -1;
        accumulatedRealSeconds = 0.0;
        accumulatedSimulationSeconds = 0.0;
    }

    public void setSimulationCompleteHandler(Runnable simulationCompleteHandler) {
        this.simulationCompleteHandler = simulationCompleteHandler;
    }

}
