package com.vacuum.ui;

import javafx.scene.image.Image;
import java.io.InputStream;
import java.io.IOException;
import java.util.Objects;

/*
 * If this gets "approved", then maybe we can move this into the similar enum in House.java,
 * including the additional imports, otherwise I can try to rework this implementation. The Image
 * stuff is here so I can make each image into an ImagePattern, most likely in its own file unless
 * it needs to be somewhere else.
 */

public enum FlooringTypes {
    HARDWOOD("vac_hardwood.png", "Hardwood", 0.90), TILE("vac_tile.png", "Tile", 0.90), LAMINATE(
            "vac_laminate.png", "Laminate", 0.90), BERBERPILE("vac_berberpile.png", "Berber Pile",
                    0.75), CUTPILE("vac_cutpile.png", "Cut Pile", 0.70), CALIFORNIASHAG(
                            "vac_californiashag.png", "California Shag", 0.65);

    private final Image userFloor;
    private final String displayName;
    private final Double defaultEfficiency;

    FlooringTypes(String imageName, String displayName, double defaultEfficiency) {
        String floorImagePath = "src//main//resources//" + imageName;

        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(floorImagePath),
                "Image resource not found: " + floorImagePath)) {
            this.userFloor = new Image(in);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading image for " + name(), e);
        } catch (NullPointerException e) {
            throw new IllegalStateException("Image path is invalid: " + floorImagePath, e);
        }

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
