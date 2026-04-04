package me.stella.water;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class WaterSourceLocator {

    private static final int MAX_VISITS = 128;
    private static final BlockFace[] FACES = new BlockFace[]{
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    };

    private WaterSourceLocator() {
    }

    public static Block findSource(Block start) {
        if (start == null || !isWater(start.getType())) {
            return null;
        }

        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty() && visited.size() < MAX_VISITS) {
            Block candidate = queue.pollFirst();
            String key = key(candidate);
            if (!visited.add(key)) {
                continue;
            }

            if (isSource(candidate)) {
                return candidate;
            }

            for (BlockFace face : FACES) {
                Block relative = candidate.getRelative(face);
                if (isWater(relative.getType())) {
                    queue.addLast(relative);
                }
            }
        }

        return null;
    }

    private static boolean isSource(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Levelled levelled) {
            return levelled.getLevel() == 0;
        }
        return true;
    }

    private static boolean isWater(Material material) {
        return material.name().toUpperCase(Locale.ROOT).contains("WATER");
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }
}

