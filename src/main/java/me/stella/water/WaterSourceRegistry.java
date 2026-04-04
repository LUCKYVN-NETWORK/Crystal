package me.stella.water;

import org.bukkit.Location;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WaterSourceRegistry {

    private final Set<WaterSource> sources;

    public WaterSourceRegistry() {
        this.sources = ConcurrentHashMap.newKeySet();
    }

    public void register(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        sources.add(WaterSource.fromLocation(location));
    }

    public Set<WaterSource> view() {
        return sources;
    }

    public int size() {
        return sources.size();
    }
}

