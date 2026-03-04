package com.vacuum.util;

import com.vacuum.ui.*;
import javafx.animation.AnimationTimer;

/**
 * Manages the simulation lifecycle (start, stop, pause) and drives the AnimationTimer game loop.
 * Decoupled from VacuumSimulatorApp UI logic.
 */
public class simulationTimer {

    private final Vacuum vacuum;
    private final HouseVisualizationPane visualizationPane;
    private final AnimationTimer timer;

    private boolean simActive = false;
    private long prevTime = -1;


    public simulationTimer(Vacuum vacuum, HouseVisualizationPane visualizationPane) {
        this.vacuum = vacuum;
        this.visualizationPane = visualizationPane;

        timer = new AnimationTimer() {
            @Override
            public void handle(long currentTime) {
                if (prevTime > 0) {
                    double deltaTime = (currentTime - prevTime) / 1_000_000_000.0;
                    vacuum.update(deltaTime);
                }
                visualizationPane.render();
                prevTime = currentTime;
            }
        };
    }

    public void start(double startBattery, int moveMode) {
        vacuum.reset(startBattery, moveMode);
        prevTime = System.nanoTime();
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
        prevTime = -1;
        simActive = false;
    }

    public boolean isActive() {
        return simActive;
    }

}
