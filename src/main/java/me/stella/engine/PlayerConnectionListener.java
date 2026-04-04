package me.stella.engine;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final PlayerTickEngineManager tickEngineManager;

    public PlayerConnectionListener(PlayerTickEngineManager tickEngineManager) {
        this.tickEngineManager = tickEngineManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tickEngineManager.assign(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tickEngineManager.unassign(event.getPlayer().getUniqueId());
    }
}

