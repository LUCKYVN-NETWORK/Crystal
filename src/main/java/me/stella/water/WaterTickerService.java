package me.stella.water;

import me.stella.config.CrystalSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Locale;

public class WaterTickerService {

    private static final double PLAYER_RANGE_SQUARED = 32.0D * 32.0D;

    private final JavaPlugin plugin;
    private final CrystalSettings settings;
    private final WaterSourceRegistry registry;

    private int taskId = -1;

    public WaterTickerService(JavaPlugin plugin, CrystalSettings settings, WaterSourceRegistry registry) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        long interval = settings.waterTickIntervalTicks();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        registry.view().clear();
    }

    private void tick() {
        Iterator<WaterSource> iterator = registry.view().iterator();
        while (iterator.hasNext()) {
            WaterSource source = iterator.next();
            World world = Bukkit.getWorld(source.worldName());
            if (world == null) {
                iterator.remove();
                continue;
            }

            Block block = world.getBlockAt(source.x(), source.y(), source.z());
            if (!isWater(block.getType())) {
                iterator.remove();
                continue;
            }

            boolean hasNearbyPlayer = world.getPlayers().stream()
                    .anyMatch(player -> player.getLocation().distanceSquared(block.getLocation()) <= PLAYER_RANGE_SQUARED);
            if (!hasNearbyPlayer) {
                iterator.remove();
            }
        }
    }

    private boolean isWater(Material material) {
        return material.name().toUpperCase(Locale.ROOT).contains("WATER");
    }
}

