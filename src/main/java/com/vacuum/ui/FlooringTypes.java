package com.vacuum.ui;

import javafx.scene.image.Image;

// Can probably delete this file now, it has been worked into HouseVisualizationPane.java

/*
 * If this gets "approved", then maybe we can move this into the similar enum in House.java,
 * including the additional imports, otherwise I can try to rework this implementation. The Image
 * stuff is here so I can make each image into an ImagePattern, most likely in its own file unless
 * it needs to be somewhere else.
 */

public enum FlooringTypes {
    HARDWOOD("vac_hardwood.png", "Hardwood", 0.75), TILE("vac_tile.png", "Tile", 0.60), LAMINATE(
            "vac_laminate.png", "Laminate", 0.75), BERBERPILE("vac_berberpile.png", "Berber Pile",
                    0.2857), CUTPILE("vac_cutpile.png", "Cut Pile", 0.2857), CALIFORNIASHAG(
                            "vac_californiashag.png", "California Shag", 0.2857);

    private final Image userFloor;
    private final String displayName;
    private final Double defaultEfficiency;

    FlooringTypes(String imageName, String displayName, double defaultEfficiency) {
        String path = "/" + imageName;
        this.userFloor = new Image(getClass().getResourceAsStream(path));

        this.displayName = displayName;
        this.defaultEfficiency = defaultEfficiency;
    }

    public Image getFloor() {
        return this.userFloor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultEfficiency() {
        return defaultEfficiency;
    }
}
