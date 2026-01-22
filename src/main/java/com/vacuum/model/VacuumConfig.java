package com.vacuum.model;

/**
 * Configuration for the robot vacuum. Handles battery life, speed, efficiency settings per
 * requirements.
 */
public class VacuumConfig {
    // Physical constants
    public static final double DIAMETER = 12.8; // inches
    public static final double VACUUM_WIDTH = 5.8; // inches
    public static final double WHISKER_WIDTH = 13.5; // inches

    // Configuration bounds (Req 2.8, 2.9)
    public static final int MIN_BATTERY_MINUTES = 90;
    public static final int MAX_BATTERY_MINUTES = 200;
    public static final double MIN_SPEED_INCHES_PER_SEC = 6.0;
    public static final double MAX_SPEED_INCHES_PER_SEC = 18.0;
    public static final double MIN_VACUUM_EFFICIENCY = 0.10; // 10%
    public static final double MAX_VACUUM_EFFICIENCY = 0.90; // 90%
    public static final double MIN_WHISKER_EFFICIENCY = 0.10; // 10%
    public static final double MAX_WHISKER_EFFICIENCY = 0.50; // 50%

    // Default values
    public static final int DEFAULT_BATTERY_MINUTES = 150;
    public static final double DEFAULT_SPEED_INCHES_PER_SEC = 12.0;
    public static final double DEFAULT_WHISKER_EFFICIENCY = 0.30;

    // Configuration fields
    private int batteryMinutes;
    private double speedInchesPerSec;
    private double vacuumEfficiency;
    private double whiskerEfficiency;

    /**
     * Create vacuum config with default values
     */
    public VacuumConfig() {
        this(DEFAULT_BATTERY_MINUTES, DEFAULT_SPEED_INCHES_PER_SEC);
    }

    /**
     * Create vacuum config with specified battery and speed
     */
    public VacuumConfig(int batteryMinutes, double speedInchesPerSec) {
        setBatteryMinutes(batteryMinutes);
        setSpeedInchesPerSec(speedInchesPerSec);
        this.vacuumEfficiency = 0.90; // Will be set based on floor covering
        this.whiskerEfficiency = DEFAULT_WHISKER_EFFICIENCY;
    }

    /**
     * Set battery life in minutes (Req 2.8) Range: 90-200 minutes
     */
    public void setBatteryMinutes(int minutes) {
        if (minutes < MIN_BATTERY_MINUTES || minutes > MAX_BATTERY_MINUTES) {
            throw new IllegalArgumentException(
                    String.format("Battery must be between %d and %d minutes (got %d)",
                            MIN_BATTERY_MINUTES, MAX_BATTERY_MINUTES, minutes));
        }
        this.batteryMinutes = minutes;
    }

    /**
     * Set vacuum speed in inches per second (Req 2.9) Range: 6-18 inches/sec
     */
    public void setSpeedInchesPerSec(double speed) {
        if (speed < MIN_SPEED_INCHES_PER_SEC || speed > MAX_SPEED_INCHES_PER_SEC) {
            throw new IllegalArgumentException(
                    String.format("Speed must be between %.1f and %.1f inches/sec (got %.1f)",
                            MIN_SPEED_INCHES_PER_SEC, MAX_SPEED_INCHES_PER_SEC, speed));
        }
        this.speedInchesPerSec = speed;
    }

    /**
     * Set vacuum efficiency based on floor covering Range: 10%-90%
     */
    public void setVacuumEfficiency(double efficiency) {
        if (efficiency < MIN_VACUUM_EFFICIENCY || efficiency > MAX_VACUUM_EFFICIENCY) {
            throw new IllegalArgumentException(String.format(
                    "Vacuum efficiency must be between %.0f%% and %.0f%% (got %.0f%%)",
                    MIN_VACUUM_EFFICIENCY * 100, MAX_VACUUM_EFFICIENCY * 100, efficiency * 100));
        }
        this.vacuumEfficiency = efficiency;
    }

    /**
     * Set whisker efficiency Range: 10%-50%
     */
    public void setWhiskerEfficiency(double efficiency) {
        if (efficiency < MIN_WHISKER_EFFICIENCY || efficiency > MAX_WHISKER_EFFICIENCY) {
            throw new IllegalArgumentException(String.format(
                    "Whisker efficiency must be between %.0f%% and %.0f%% (got %.0f%%)",
                    MIN_WHISKER_EFFICIENCY * 100, MAX_WHISKER_EFFICIENCY * 100, efficiency * 100));
        }
        this.whiskerEfficiency = efficiency;
    }

    /**
     * Apply floor covering default efficiency to vacuum
     */
    public void applyFloorCovering(House.FloorCovering covering) {
        if (covering == null) {
            throw new IllegalArgumentException("Floor covering must not be null");
        }
        setVacuumEfficiency(covering.getDefaultEfficiency());
    }

    /**
     * Get battery life in seconds (for simulation)
     */
    public double getBatterySeconds() {
        return batteryMinutes * 60.0;
    }

    /**
     * Get speed in feet per second (for simulation)
     */
    public double getSpeedFeetPerSec() {
        return speedInchesPerSec / 12.0;
    }

    // Getters
    public int getBatteryMinutes() {
        return batteryMinutes;
    }

    public double getSpeedInchesPerSec() {
        return speedInchesPerSec;
    }

    public double getVacuumEfficiency() {
        return vacuumEfficiency;
    }

    public double getWhiskerEfficiency() {
        return whiskerEfficiency;
    }

    @Override
    public String toString() {
        return String.format(
                "VacuumConfig[battery=%dmin, speed=%.1fin/s, vacuum=%.0f%%, whiskers=%.0f%%]",
                batteryMinutes, speedInchesPerSec, vacuumEfficiency * 100, whiskerEfficiency * 100);
    }
}
