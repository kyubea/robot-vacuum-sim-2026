package com.vacuum.ui;

import java.awt.Color;
import java.util.ArrayList;
import com.vacuum.model.Room;
import com.vacuum.ui.VacuumSimulatorApp;
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

    /*
     * private GridPane makeHeatmapGridPane() { GridPane heatmapGridPane = new GridPane();
     * heatmapGridPane.setHgap(20); heatmapGridPane.setVgap(16); int gridRows = 20; int gridColumns
     * = 16; double[][] heatmapGrid = new double[gridRows][gridColumns];
     * 
     * for (int r = 0; r < gridRows; r++) { for (int c = 0; c < gridColumns; c++) {
     * heatmapGrid[r][c] = Math.random(); Color gridSquareValue = interpColor(heatmapGrid[r][c],
     * minColor, maxColor); } }
     * 
     * for (Room room : house.getRooms()) { makeHeatmapGridPane(); } // I can't get house and don't
     * want to tread on anyone else's code }
     */

    // Maybe include the transparency slider on the VacuumSimulatorApp.java file since that is a UI
    // thing


}
