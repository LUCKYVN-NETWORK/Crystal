package me.stella;

import me.stella.config.CrystalSettings;
import me.stella.engine.PlayerConnectionListener;
import me.stella.engine.PlayerTickEngineManager;
import me.stella.generation.OreGenerationListener;
import me.stella.profile.ProfileRepository;
import me.stella.profile.ProfileSelector;
import me.stella.common.ResilientScheduler;
import me.stella.water.WaterSourceRegistry;
import me.stella.water.WaterTickerService;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

public final class Crystal extends JavaPlugin {

    private static final ConsoleCommandSender console = Bukkit.getConsoleSender();

    private ResilientScheduler scheduler;
    private CrystalSettings settings;
    private ProfileRepository profileRepository;
    private ProfileSelector profileSelector;
    private PlayerTickEngineManager tickEngineManager;
    private WaterSourceRegistry waterSourceRegistry;
    private WaterTickerService waterTickerService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("profiles.yml", false);

        this.settings = CrystalSettings.load(this);
        this.profileRepository = new ProfileRepository(this);
        this.profileRepository.ensureDefaultProfileExists(settings.defaultProfileId());

        Map<String, me.stella.profile.OreProfile> profiles = profileRepository.loadProfiles();
        this.profileSelector = new ProfileSelector(profiles, settings.defaultProfileId());

        int cores = ResilientScheduler.detectCpuCores();
        int asyncThreads = ResilientScheduler.computeAsyncThreads(cores, 1, 2);
        this.scheduler = new ResilientScheduler(getLogger(), asyncThreads);

        this.tickEngineManager = new PlayerTickEngineManager(this, scheduler, settings, profileSelector);
        this.waterSourceRegistry = new WaterSourceRegistry();
        this.waterTickerService = new WaterTickerService(this, settings, waterSourceRegistry);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerConnectionListener(tickEngineManager), this);
        pluginManager.registerEvents(new OreGenerationListener(settings, tickEngineManager, waterSourceRegistry), this);

        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            tickEngineManager.assign(onlinePlayer);
        }

        waterTickerService.start();
        console.sendMessage("[Crystal] Operational core initialized.");
    }

    @Override
    public void onDisable() {
        if (waterTickerService != null) {
            waterTickerService.stop();
        }
        if (tickEngineManager != null) {
            tickEngineManager.shutdown();
        }
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Override
    public void saveResource(@NotNull String path, boolean replace) {
        File location = new File(this.getDataFolder(), path);
        if (!replace && location.exists()) {
            return;
        }
        super.saveResource(path, replace);
    }
}
