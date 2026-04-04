package me.stella.engine;

import me.stella.common.ResilientScheduler;
import me.stella.config.CrystalSettings;
import me.stella.profile.OreProfile;
import me.stella.profile.ProfileSelector;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTickEngineManager {

    private final JavaPlugin plugin;
    private final ResilientScheduler scheduler;
    private final CrystalSettings settings;
    private final ProfileSelector profileSelector;
    private final Map<UUID, PlayerTickAgent> agents;

    public PlayerTickEngineManager(JavaPlugin plugin,
                                   ResilientScheduler scheduler,
                                   CrystalSettings settings,
                                   ProfileSelector profileSelector) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.settings = settings;
        this.profileSelector = profileSelector;
        this.agents = new ConcurrentHashMap<>();
    }

    public void assign(Player player) {
        OreProfile selected = profileSelector.selectFor(player);
        PlayerTickAgent agent = new PlayerTickAgent(player.getUniqueId(), selected, settings, scheduler);
        agent.start();

        PlayerTickAgent previous = agents.put(player.getUniqueId(), agent);
        if (previous != null) {
            previous.close();
        }

        plugin.getLogger().fine("Assigned profile " + selected.id() + " to " + player.getName());
    }

    public void unassign(UUID playerId) {
        PlayerTickAgent agent = agents.remove(playerId);
        if (agent != null) {
            agent.close();
        }
    }

    public Optional<PlayerTickAgent> getAgent(UUID playerId) {
        return Optional.ofNullable(agents.get(playerId));
    }

    public Optional<PlayerTickAgent> findClosestAgent(Location target) {
        if (target.getWorld() == null) {
            return Optional.empty();
        }

        return target.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(target) >= 0)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(target)))
                .map(Player::getUniqueId)
                .map(agents::get);
    }

    public void shutdown() {
        for (PlayerTickAgent agent : agents.values()) {
            agent.close();
        }
        agents.clear();
    }
}

