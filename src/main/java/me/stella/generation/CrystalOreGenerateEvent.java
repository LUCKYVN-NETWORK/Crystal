package me.stella.generation;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CrystalOreGenerateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block sourceBlock;
    private final Block targetBlock;
    private final Player nearestPlayer;
    private final Material generatedMaterial;
    private boolean cancelled;

    public CrystalOreGenerateEvent(Block sourceBlock, Block targetBlock, Player nearestPlayer, Material generatedMaterial) {
        this.sourceBlock = sourceBlock;
        this.targetBlock = targetBlock;
        this.nearestPlayer = nearestPlayer;
        this.generatedMaterial = generatedMaterial;
    }

    public Block sourceBlock() {
        return sourceBlock;
    }

    public Block targetBlock() {
        return targetBlock;
    }

    public Player nearestPlayer() {
        return nearestPlayer;
    }

    public Material generatedMaterial() {
        return generatedMaterial;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

