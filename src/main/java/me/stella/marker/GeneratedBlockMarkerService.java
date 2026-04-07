package me.stella.marker;

import org.bukkit.Location;
import org.bukkit.block.Block;

public interface GeneratedBlockMarkerService {

    boolean isGenerated(Block block);

    boolean isGenerated(Location location);

    void markGenerated(Block block);

    void clearMarker(Block block);
}

