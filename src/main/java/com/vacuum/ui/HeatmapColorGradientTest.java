package com.vacuum.ui;

import java.awt.Color;
// import com.vacuum.model.Room;
// import javafx.scene.canvas.Canvas;
// import javafx.scene.canvas.GraphicsContext;

public class HeatmapColorGradientTest {
    /**
     * // Make the color gradient from 0 (unclean) blue to 1.00 (fully clean) cyan Color minColor =
     * Color.BLUE; Color maxColor = Color.CYAN; int steps = 100;
     * 
     * // As parts of the floor get more or less clean, the color gets +0.01 in the green (closer to
     * cyan)
     */

    /*
     * Does Java have a color library with modifiable opacity values? if so, get the opacity from
     * the slider
     */

    Color minColor = Color.BLUE; // (R, G, B) = (0.0, 0.0, 1.0), this is the "fully dirty" color
    Color maxColor = Color.CYAN; // (R, G, B) = (0.0, 1.0, 1.0), this is the "fully clean" color

    public Color interpColor(double value, Color minColor, Color maxColor) {
        value = Math.max(0, Math.min(1, value)); // value comes from the floor efficiency

        int r = (int) (minColor.getRed() + (maxColor.getRed() - minColor.getRed()) * value);
        int g = (int) (minColor.getGreen() + (maxColor.getGreen() - minColor.getGreen()) * value);
        int b = (int) (minColor.getBlue() + (maxColor.getBlue() - minColor.getBlue()) * value);

        return new Color(r, g, b);
        // Really it is just +0.01 or -0.01 in getGreen() for each interpolation
    }

    // Get value from data table, maybe use the grid built into the UI visualization?
    // I would have to double-check that it is strictly gridded and not just pixels I think

    // Maybe include the transparency slider on the VacuumSimulatorApp.java file since that is a UI
    // thing

    /*
     * for (Room room : house.getRooms) { Canvas heatmapCanvas = new Canvas(room.getWidth(),
     * room.getHeight()); GraphicsContext graphContext = heatmapCanvas.getGraphicsContext2D();
     * 
     * graphContext.setFill(Color.interpColor(0, minColor, maxColor)); (this one doesn't work) }
     */


}
