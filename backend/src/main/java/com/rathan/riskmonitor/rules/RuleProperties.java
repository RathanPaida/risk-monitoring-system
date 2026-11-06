package com.rathan.riskmonitor.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rules")
public class RuleProperties {

    private Velocity velocity = new Velocity();
    private UnusualAmount unusualAmount = new UnusualAmount();
    private GeoMismatch geoMismatch = new GeoMismatch();
    private NewDevice newDevice = new NewDevice();

    public static class Velocity {
        private int windowMinutes = 10;
        private int thresholdMedium = 5;
        private int thresholdHigh = 10;

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getThresholdMedium() {
            return thresholdMedium;
        }

        public void setThresholdMedium(int thresholdMedium) {
            this.thresholdMedium = thresholdMedium;
        }

        public int getThresholdHigh() {
            return thresholdHigh;
        }

        public void setThresholdHigh(int thresholdHigh) {
            this.thresholdHigh = thresholdHigh;
        }
    }

    public static class UnusualAmount {
        private double multiplierMedium = 5.0;
        private double multiplierHigh = 10.0;
        private double minAmount = 500.0;
        private int trailingDays = 30;

        public double getMultiplierMedium() {
            return multiplierMedium;
        }

        public void setMultiplierMedium(double multiplierMedium) {
            this.multiplierMedium = multiplierMedium;
        }

        public double getMultiplierHigh() {
            return multiplierHigh;
        }

        public void setMultiplierHigh(double multiplierHigh) {
            this.multiplierHigh = multiplierHigh;
        }

        public double getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(double minAmount) {
            this.minAmount = minAmount;
        }

        public int getTrailingDays() {
            return trailingDays;
        }

        public void setTrailingDays(int trailingDays) {
            this.trailingDays = trailingDays;
        }
    }

    public static class GeoMismatch {
        private int hopWindowMinutes = 60;

        public int getHopWindowMinutes() {
            return hopWindowMinutes;
        }

        public void setHopWindowMinutes(int hopWindowMinutes) {
            this.hopWindowMinutes = hopWindowMinutes;
        }
    }

    public static class NewDevice {
        private double minAmountLow = 200.0;
        private double minAmountMedium = 1000.0;

        public double getMinAmountLow() {
            return minAmountLow;
        }

        public void setMinAmountLow(double minAmountLow) {
            this.minAmountLow = minAmountLow;
        }

        public double getMinAmountMedium() {
            return minAmountMedium;
        }

        public void setMinAmountMedium(double minAmountMedium) {
            this.minAmountMedium = minAmountMedium;
        }
    }

    public Velocity getVelocity() {
        return velocity;
    }

    public void setVelocity(Velocity velocity) {
        this.velocity = velocity;
    }

    public UnusualAmount getUnusualAmount() {
        return unusualAmount;
    }

    public void setUnusualAmount(UnusualAmount unusualAmount) {
        this.unusualAmount = unusualAmount;
    }

    public GeoMismatch getGeoMismatch() {
        return geoMismatch;
    }

    public void setGeoMismatch(GeoMismatch geoMismatch) {
        this.geoMismatch = geoMismatch;
    }

    public NewDevice getNewDevice() {
        return newDevice;
    }

    public void setNewDevice(NewDevice newDevice) {
        this.newDevice = newDevice;
    }
}
