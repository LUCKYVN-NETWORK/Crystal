package me.stella.generation;

import me.stella.config.CrystalSettings;
import me.stella.engine.PlayerTickAgent;
import me.stella.engine.PlayerTickEngineManager;
import me.stella.marker.GeneratedBlockMarkerService;
import me.stella.water.WaterSourceLocator;
import me.stella.water.WaterSourceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.Optional;

public class OreGenerationListener implements Listener {

    private final CrystalSettings settings;
    private final PlayerTickEngineManager tickEngineManager;
    private final WaterSourceRegistry waterSourceRegistry;
    private final GeneratedBlockMarkerService generatedBlockMarkerService;

    public OreGenerationListener(CrystalSettings settings,
                                  PlayerTickEngineManager tickEngineManager,
                                  WaterSourceRegistry waterSourceRegistry,
                                  GeneratedBlockMarkerService generatedBlockMarkerService) {
        this.settings = settings;
        this.tickEngineManager = tickEngineManager;
        this.waterSourceRegistry = waterSourceRegistry;
        this.generatedBlockMarkerService = generatedBlockMarkerService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        Block flowing = event.getBlock();

        // Fast direct material enum check
        if (flowing.getType() != Material.WATER) return;

        if (!settings.isWorldEnabled(flowing.getWorld().getName())) {
            return;
        }

        Block toBlock = event.getToBlock();
        Block generatorBase = toBlock.getRelative(event.getFace());

        // Cheap enum check first: BFS source hunt + SSB island lookup only run for real generators.
        if (!settings.isGeneratorBlock(generatorBase.getType())) return;

        Optional<PlayerTickAgent> optionalAgent = tickEngineManager.findBestAgent(toBlock.getLocation());
        if (optionalAgent.isEmpty()) return;

        Block source = WaterSourceLocator.findSource(flowing);
        if (source != null) {
            waterSourceRegistry.register(source.getLocation());
        }

        PlayerTickAgent agent = optionalAgent.get();
        Material generated = agent.pop();

        CrystalOreGenerateEvent generateEvent = new CrystalOreGenerateEvent(
                flowing,
                toBlock,
                Bukkit.getPlayer(agent.playerId()),
                generated
        );

        Bukkit.getPluginManager().callEvent(generateEvent);
        if (generateEvent.isCancelled()) return;

        event.setCancelled(true);
        toBlock.setType(generateEvent.generatedMaterial());
        generatedBlockMarkerService.markGenerated(toBlock);
    }
}
