package me.stella.water;

import me.stella.config.CrystalSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WaterTickerService {

    private static final double PLAYER_RANGE_SQUARED = 32.0D * 32.0D;

    private final JavaPlugin plugin;
    private final CrystalSettings settings;
    private final WaterSourceRegistry registry;

    private int taskId = -1;
    private List<WaterSource> sweepSnapshot = List.of();
    private int sweepCursor = 0;

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
        sweepSnapshot = List.of();
        sweepCursor = 0;
    }

    private void tick() {
        if (registry.size() == 0) {
            sweepSnapshot = List.of();
            sweepCursor = 0;
            return;
        }

        if (sweepCursor >= sweepSnapshot.size()) {
            sweepSnapshot = new ArrayList<>(registry.view());
            sweepCursor = 0;
            if (sweepSnapshot.isEmpty()) {
                return;
            }
        }

        int chunkSize = settings.waterTickChunkSize();
        int processed = 0;
        while (processed < chunkSize && sweepCursor < sweepSnapshot.size()) {
            WaterSource source = sweepSnapshot.get(sweepCursor++);
            processed++;

            if (!registry.view().contains(source)) {
                continue;
            }

            World world = Bukkit.getWorld(source.worldName());
            if (world == null) {
                registry.view().remove(source);
                continue;
            }

            Block block = world.getBlockAt(source.x(), source.y(), source.z());
            if (!isWater(block.getType())) {
                registry.view().remove(source);
                continue;
            }

            boolean hasNearbyPlayer = world.getPlayers().stream()
                    .anyMatch(player -> player.getLocation().distanceSquared(block.getLocation()) <= PLAYER_RANGE_SQUARED);
            if (!hasNearbyPlayer) {
                registry.view().remove(source);
                continue;
            }

            block.getState().update(true);
        }
    }

    private boolean isWater(Material material) {
        return material.name().toUpperCase(Locale.ROOT).contains("WATER");
    }
}
