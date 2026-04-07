package me.stella.generation;

import me.stella.config.CrystalSettings;
import me.stella.engine.PlayerTickAgent;
import me.stella.engine.PlayerTickEngineManager;
import me.stella.water.WaterSourceLocator;
import me.stella.water.WaterSourceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.Locale;
import java.util.Optional;

public class OreGenerationListener implements Listener {

    private final CrystalSettings settings;
    private final PlayerTickEngineManager tickEngineManager;
    private final WaterSourceRegistry waterSourceRegistry;

    public OreGenerationListener(CrystalSettings settings,
                                 PlayerTickEngineManager tickEngineManager,
                                 WaterSourceRegistry waterSourceRegistry) {
        this.settings = settings;
        this.tickEngineManager = tickEngineManager;
        this.waterSourceRegistry = waterSourceRegistry;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        Block flowing = event.getBlock();
        if (!isWater(flowing.getType()))
            return;
        if (!settings.isWorldEnabled(flowing.getWorld().getName())) {
            return;
        }
        Block source = WaterSourceLocator.findSource(flowing);
        if (source != null) {
            waterSourceRegistry.register(source.getLocation());
        }
        Block toBlock = event.getToBlock();
        Block generatorBase = toBlock.getRelative(event.getFace());
        if (!settings.isGeneratorBlock(generatorBase.getType()))
            return;
        Optional<PlayerTickAgent> optionalAgent = tickEngineManager.findBestAgent(toBlock.getLocation());
        if (optionalAgent.isEmpty())
            return;
        PlayerTickAgent agent = optionalAgent.get();
        Material generated = agent.pop();
        CrystalOreGenerateEvent generateEvent = new CrystalOreGenerateEvent(
                flowing,
                toBlock,
                Bukkit.getPlayer(agent.playerId()),
                generated
        );
        Bukkit.getPluginManager().callEvent(generateEvent);
        if (generateEvent.isCancelled())
            return;
        event.setCancelled(true);
        toBlock.setType(generateEvent.generatedMaterial());
    }

    private boolean isWater(Material material) {
        return material.name().toUpperCase(Locale.ROOT).contains("WATER");
    }
}
