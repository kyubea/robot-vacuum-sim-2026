package com.vacuum.ui;

import java.awt.Color;

public class HeatmapColorGradientTest {
    /**
     * // Make the color gradient from 0 (unclean) blue to 1 or 100 (fully clean) cyan Color
     * minColor = Color.BLUE; Color maxColor = Color.CYAN; int steps = 100;
     * 
     * // As parts of the floor get more or less clean, the color gets +0.01 in the green (closer to
     * cyan)
     */

    Color minColor = Color.BLUE;
    Color maxColor = Color.CYAN;

    public Color interpColor(double value, Color minColor, Color maxColor) {
        value = Math.max(0, Math.min(1, value));

        int r = (int) (minColor.getRed() + (maxColor.getRed() - minColor.getRed()) * value);
        int g = (int) (minColor.getGreen() + (maxColor.getGreen() - minColor.getGreen()) * value);
        int b = (int) (minColor.getBlue() + (maxColor.getBlue() - minColor.getBlue()) * value);

        return new Color(r, g, b);
    }

    // Maybe include the transparency slider on the VacuumSimulatorApp.java file since that is a UI
    // thing
    // I will work on implementation later, but I decided to go ahead and get some coloring code
    // Plus I chose colors so that color blindness would not be significantly affected
}
