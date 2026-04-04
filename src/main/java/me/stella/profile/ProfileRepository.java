package me.stella.profile;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class ProfileRepository {

    private static final String ROOT = "profiles";

    private final JavaPlugin plugin;
    private final File profilesFile;
    private final Logger logger;

    public ProfileRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.profilesFile = new File(plugin.getDataFolder(), "profiles.yml");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultProfileExists(String defaultProfileId) {
        YamlConfiguration target = YamlConfiguration.loadConfiguration(profilesFile);
        ConfigurationSection targetRoot = target.getConfigurationSection(ROOT);
        if (targetRoot == null) {
            targetRoot = target.createSection(ROOT);
        }

        if (targetRoot.isConfigurationSection(defaultProfileId)) {
            return;
        }

        YamlConfiguration bundled = loadBundledProfiles();
        ConfigurationSection bundledRoot = bundled.getConfigurationSection(ROOT);

        Map<String, Object> sectionData = null;
        if (bundledRoot != null) {
            ConfigurationSection configuredDefault = bundledRoot.getConfigurationSection(defaultProfileId);
            if (configuredDefault != null) {
                sectionData = configuredDefault.getValues(true);
            } else {
                for (String key : bundledRoot.getKeys(false)) {
                    ConfigurationSection candidate = bundledRoot.getConfigurationSection(key);
                    if (candidate != null) {
                        sectionData = candidate.getValues(true);
                        break;
                    }
                }
            }
        }

        if (sectionData == null || sectionData.isEmpty()) {
            sectionData = defaultSection();
        }

        target.createSection(ROOT + "." + defaultProfileId, sectionData);
        save(target);
        logger.info("Injected missing default profile '" + defaultProfileId + "' into profiles.yml");
    }

    public Map<String, OreProfile> loadProfiles() {
        YamlConfiguration source = YamlConfiguration.loadConfiguration(profilesFile);
        ConfigurationSection root = source.getConfigurationSection(ROOT);
        Map<String, OreProfile> profiles = new LinkedHashMap<>();

        if (root == null) {
            return profiles;
        }

        for (String profileId : root.getKeys(false)) {
            ConfigurationSection profileSection = root.getConfigurationSection(profileId);
            if (profileSection == null) {
                continue;
            }

            String permission = profileSection.getString("permission", "").trim();
            int priority = profileSection.getInt("priority", 0);

            ConfigurationSection weightsSection = profileSection.getConfigurationSection("weights");
            Map<Material, Double> weights = new LinkedHashMap<>();
            if (weightsSection != null) {
                for (String materialKey : weightsSection.getKeys(false)) {
                    Material material = parseMaterial(materialKey);
                    if (material == null) {
                        continue;
                    }
                    double weight = Math.max(0.0D, weightsSection.getDouble(materialKey, 0.0D));
                    if (weight > 0.0D) {
                        weights.put(material, weight);
                    }
                }
            }

            if (weights.isEmpty()) {
                weights.put(Material.STONE, 1.0D);
            }

            profiles.put(profileId, new OreProfile(
                    profileId,
                    permission.isBlank() ? null : permission,
                    priority,
                    weights
            ));
        }

        return profiles;
    }

    private YamlConfiguration loadBundledProfiles() {
        InputStream inputStream = plugin.getResource("profiles.yml");
        if (inputStream == null) {
            return new YamlConfiguration();
        }

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            logger.warning("Unable to read bundled profiles.yml: " + ex.getMessage());
            return new YamlConfiguration();
        }
    }

    private void save(YamlConfiguration configuration) {
        try {
            configuration.save(profilesFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save profiles.yml", ex);
        }
    }

    private Material parseMaterial(String key) {
        try {
            return Material.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid material key in profile weights: " + key);
            return null;
        }
    }

    private Map<String, Object> defaultSection() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("priority", 0);
        data.put("permission", "");

        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("STONE", 100.0D);
        weights.put("COAL_ORE", 35.0D);
        weights.put("IRON_ORE", 18.0D);
        data.put("weights", weights);

        return data;
    }
}

