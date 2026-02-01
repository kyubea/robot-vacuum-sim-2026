package com.vacuum.ui;

import javafx.scene.image.Image;
import java.io.InputStream;
import java.io.IOException;
import java.util.Objects;

public enum FlooringTypes {
    HARDWOOD("vac_hardwood.png"), TILE("vac_tile.png"), LAMINATE("vac_laminate.png"), BERBERPILE(
            "vac_berberpile.png"), CUTPILE(
                    "vac_cutpile.png"), CALIFORNIASHAG("vac_californiashag.png");

    private final Image userFloor;

    FlooringTypes(String imageName) {
        String floorImagePath = "src//main//resources//" + imageName;

        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(floorImagePath),
                "Image resource not found: " + floorImagePath)) {
            this.userFloor = new Image(in);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading image for " + name(), e);
        } catch (NullPointerException e) {
            throw new IllegalStateException("Image path is invalid: " + floorImagePath, e);
        }
    }

    public Image getFloor() {
        return this.userFloor;
    }
}
