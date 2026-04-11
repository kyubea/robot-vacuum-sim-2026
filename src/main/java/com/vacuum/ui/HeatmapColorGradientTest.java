package com.vacuum.ui;

import java.awt.Color;
import com.vacuum.model.Room;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;

public class HeatmapColorGradientTest {
    Color minColor = Color.BLUE; // (R, G, B) = (0, 0, 255), this is the "fully dirty" color
    Color maxColor = new Color(255, 153, 0); // a custom orange color, the "fully clean" color

    public Color interpColor(double value, Color minColor, Color maxColor) {
        value = Math.max(0, Math.min(1, value)); // value comes from the floor efficiency

        int r = (int) (minColor.getRed() + (maxColor.getRed() - minColor.getRed()) * value);
        int g = (int) (minColor.getGreen() + (maxColor.getGreen() - minColor.getGreen()) * value);
        int b = (int) (minColor.getBlue() + (maxColor.getBlue() - minColor.getBlue()) * value);

        return new Color(r, g, b);
    }

    // Get value from data table, maybe use the grid built into the UI visualization?
    // I would have to double-check that it is strictly gridded and not just pixels I think
    // It would need to be either houseGrid or viewGrid, but I need to figure out how to bring
    // either one of those in
    // From what I searched, each square would be a node and would likely receive the efficiency
    // value when the vacuum passes over it
    // Then use value in interpColor
    // For now though when I add a second function to this class I get bracket errors for some
    // reason

    // Maybe include the transparency slider on the VacuumSimulatorApp.java file since that is a UI
    // thing

    /*
     * for (Room room : house.getRooms) { Canvas heatmapCanvas = new Canvas(room.getWidth(),
     * room.getHeight()); GraphicsContext graphContext = heatmapCanvas.getGraphicsContext2D();
     * 
     * graphContext.setFill(Color.interpColor(0, minColor, maxColor)); (this one doesn't work) }
     */
}
