package me.stella.water;

import org.bukkit.Location;

public record WaterSource(String worldName, int x, int y, int z) {

    public static WaterSource fromLocation(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        return new WaterSource(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String key() {
        return worldName + "," + x + "," + y + "," + z;
    }
}

