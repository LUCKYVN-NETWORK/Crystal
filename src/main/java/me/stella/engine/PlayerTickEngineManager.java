package me.stella.engine;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import me.stella.common.ResilientScheduler;
import me.stella.config.CrystalSettings;
import me.stella.profile.OreProfile;
import me.stella.profile.ProfileSelector;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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

    public Optional<PlayerTickAgent> findBestAgent(@NotNull Location target) {
        Island island = SuperiorSkyblockAPI.getIslandAt(target);

        if (island != null) {
            Optional<PlayerTickAgent> agent = island.getIslandMembers(true).stream()
                    // Map straight to the agent. If they exist in the map, they are online.
                    .map(member -> agents.get(member.getUniqueId()))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt(a -> a.getProfile().priority()));
            if (agent.isPresent())
                return agent;
        }

        return findClosestAgent(target);
    }

    public Optional<PlayerTickAgent> findClosestAgent(Location target) {
        if (target.getWorld() == null) {
            return Optional.empty();
        }

        // ponytail: 256-block radius bounds the chunk-slice scan; raise if generators span larger islands.
        double radius = 256.0;

        return target.getNearbyPlayers(radius).stream()
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
