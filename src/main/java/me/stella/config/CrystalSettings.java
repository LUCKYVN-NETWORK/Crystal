package me.stella.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public record CrystalSettings(
        int poolSize,
        long refillIntervalMs,
        double refillThresholdRatio,
        Material fallbackType,
        String defaultProfileId,
        Set<String> enabledWorlds,
        Set<Material> generatorBlocks,
        long waterTickIntervalTicks
) {

    public static CrystalSettings load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        int poolSize = Math.max(1, config.getInt("pool_size", 64));
        long refillIntervalMs = Math.max(50L, config.getLong("refill_interval", 5000L));
        double refillThresholdRatio = clamp(config.getDouble("refill_threshold_ratio", 0.30D), 0.05D, 0.95D);

        Material fallbackType = parseMaterial(config.getString("fallback_type", "STONE"), Material.STONE, logger, "fallback_type");
        String defaultProfileId = config.getString("default_profile", "default");
        if (defaultProfileId == null || defaultProfileId.isBlank()) {
            defaultProfileId = "default";
        }

        Set<String> enabledWorlds = new LinkedHashSet<>();
        for (String worldName : config.getStringList("enabled_worlds")) {
            if (worldName != null && !worldName.isBlank()) {
                enabledWorlds.add(worldName);
            }
        }

        Set<Material> generatorBlocks = new LinkedHashSet<>();
        for (String value : config.getStringList("generator_blocks")) {
            Material material = parseMaterial(value, null, logger, "generator_blocks");
            if (material != null) {
                generatorBlocks.add(material);
            }
        }
        if (generatorBlocks.isEmpty()) {
            generatorBlocks.add(Material.COBBLESTONE);
        }

        long waterTickIntervalTicks = Math.max(1L, config.getLong("water_tick.interval", 20L));

        return new CrystalSettings(
                poolSize,
                refillIntervalMs,
                refillThresholdRatio,
                fallbackType,
                defaultProfileId,
                Collections.unmodifiableSet(enabledWorlds),
                Collections.unmodifiableSet(generatorBlocks),
                waterTickIntervalTicks
        );
    }

    public int refillThresholdSize() {
        return Math.max(1, (int) Math.floor(poolSize * refillThresholdRatio));
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    public boolean isGeneratorBlock(Material material) {
        return generatorBlocks.contains(material);
    }

    private static Material parseMaterial(String value, Material fallback, Logger logger, String key) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid material '" + value + "' in " + key + ". Using fallback.");
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

