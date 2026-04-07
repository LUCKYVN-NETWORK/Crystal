package me.stella.water;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class WaterSourceLocator {

    private static final int MAX_VISITS = 128;

    private WaterSourceLocator() {
    }

    public static Block findSource(Block start) {
        if (start == null || start.getType() != Material.WATER) {
            return null;
        }

        Set<Long> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty() && visited.size() < MAX_VISITS) {
            Block candidate = queue.pollFirst();

            // Bit-pack coordinates for lightning-fast, zero-allocation hashing
            long key = packLocation(candidate.getX(), candidate.getY(), candidate.getZ());

            if (!visited.add(key)) {
                continue;
            }

            if (isSource(candidate)) {
                return candidate;
            }

            // Prioritize UP first, as water sources are usually above flowing water
            checkAndAdd(candidate, BlockFace.UP, queue);
            checkAndAdd(candidate, BlockFace.NORTH, queue);
            checkAndAdd(candidate, BlockFace.SOUTH, queue);
            checkAndAdd(candidate, BlockFace.EAST, queue);
            checkAndAdd(candidate, BlockFace.WEST, queue);
        }

        return null;
    }

    private static void checkAndAdd(Block candidate, BlockFace face, ArrayDeque<Block> queue) {
        Block relative = candidate.getRelative(face);
        if (relative.getType() == Material.WATER) {
            queue.addLast(relative);
        }
    }

    private static boolean isSource(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Levelled levelled) {
            return levelled.getLevel() == 0;
        }
        return true;
    }

    private static long packLocation(int x, int y, int z) {
        // Reserves 27 bits for X and Z (safely covers +/- 30 million block world limits)
        // and 10 bits for Y (covers up to 1024 height)
        return ((long) x & 0x7FFFFFF) | (((long) z & 0x7FFFFFF) << 27) | ((long) y << 54);
    }
}