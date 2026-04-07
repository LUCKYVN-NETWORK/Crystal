package me.stella.water;

import me.stella.config.CrystalSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class WaterTickerService {

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

            if (!registry.view().contains(source)) continue;

            World world = Bukkit.getWorld(source.worldName());
            if (world == null) {
                // World is gone entirely, safe to purge
                registry.view().remove(source);
                continue;
            }

            // Fast chunk check: Bit shift block coords to get chunk coords
            int chunkX = source.x() >> 4;
            int chunkZ = source.z() >> 4;

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Skip the tick, DO NOT remove from registry, DO NOT call getBlockAt()
                continue;
            }

            Block block = world.getBlockAt(source.x(), source.y(), source.z());
            if (block.getType() != Material.WATER) {
                // Lazy Cleanup: It's no longer water, safely remove it from memory
                registry.view().remove(source);
                continue;
            }

            // Trigger flow physics
            block.getState().update(true);
        }
    }
}