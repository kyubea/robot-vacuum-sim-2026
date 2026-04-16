package com.vacuum.ui;

import java.awt.Color;
import java.awt.Insets;
import java.util.ArrayList;
import com.vacuum.model.Room;
import com.vacuum.model.House;
import com.vacuum.ui.VacuumSimulatorApp;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;

public class HeatmapColorGradientTest {
    private House house;

    int transparency = 255;
    Color minColor = Color.BLUE; // (R, G, B, A) = (0, 0, 255, 255), this is the "fully dirty" color
    Color maxColor = new Color(255, 153, 0, transparency); // a custom orange color, the "fully
                                                           // clean" color

    public Color interpColor(double value, Color minColor, Color maxColor, int transparency) {
        value = Math.max(0, Math.min(1, value)); // value comes from the floor efficiency

        int r = (int) (minColor.getRed() + (maxColor.getRed() - minColor.getRed()) * value);
        int g = (int) (minColor.getGreen() + (maxColor.getGreen() - minColor.getGreen()) * value);
        int b = (int) (minColor.getBlue() + (maxColor.getBlue() - minColor.getBlue()) * value);
        int a = transparency; // Add slider-related stuff later

        return new Color(r, g, b, a);
    }

    private GridPane makeHeatmapGridPane(Room room) {
        GridPane heatmapGridPane = new GridPane();
        int gridRows = 20;
        int gridColumns = 16;
        double[][] heatmapGrid = new double[gridRows][gridColumns];

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridColumns; c++) {
                heatmapGrid[r][c] = Math.random();
                Color gridSquareValue =
                        interpColor(heatmapGrid[r][c], minColor, maxColor, transparency);
                Region heatmapSquare = new Region();
                String stringColor = String.format("rgba(%d, %d, %d, %.2f)",
                        gridSquareValue.getRed(), gridSquareValue.getGreen(),
                        gridSquareValue.getBlue(), transparency);
                heatmapSquare.setStyle("-fx-background-color: " + stringColor + ";");
                heatmapSquare.setPrefSize(10, 10);
                heatmapGridPane.add(heatmapSquare, c, r);
                ColumnConstraints colConstraints = new ColumnConstraints();
                colConstraints.setHgrow(Priority.ALWAYS);
                heatmapGridPane.getColumnConstraints().add(colConstraints);
                // Now I just need to figure out how to actually display it
            }
        }

        return heatmapGridPane;
    }

    public void showHeatmap() {
        for (Room room : house.getRooms()) {
            makeHeatmapGridPane(room);
        }
    }
    // Maybe include the transparency slider on the VacuumSimulatorApp.java file since that is a UI
    // thing


}
