package me.stella.marker;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public class ChunkPersistentGeneratedBlockMarkerService implements GeneratedBlockMarkerService {

    private final NamespacedKey generatedBlocksKey;

    public ChunkPersistentGeneratedBlockMarkerService(Plugin plugin) {
        this.generatedBlocksKey = new NamespacedKey(plugin, "generated_blocks");
    }

    @Override
    public boolean isGenerated(Block block) {
        return isGenerated(block.getLocation());
    }

    @Override
    public boolean isGenerated(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        int packed = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return contains(readChunkEntries(chunk), packed);
    }

    @Override
    public void markGenerated(Block block) {
        Chunk chunk = block.getChunk();
        int packed = pack(block.getX(), block.getY(), block.getZ());
        int[] existing = readChunkEntries(chunk);

        if (contains(existing, packed)) {
            return;
        }

        int[] updated = Arrays.copyOf(existing, existing.length + 1);
        updated[existing.length] = packed;
        writeChunkEntries(chunk, updated);
    }

    @Override
    public void clearMarker(Block block) {
        Chunk chunk = block.getChunk();
        int packed = pack(block.getX(), block.getY(), block.getZ());
        int[] existing = readChunkEntries(chunk);

        int index = indexOf(existing, packed);
        if (index == -1) {
            return;
        }

        if (existing.length == 1) {
            chunk.getPersistentDataContainer().remove(generatedBlocksKey);
            return;
        }

        int[] updated = new int[existing.length - 1];
        if (index > 0) {
            System.arraycopy(existing, 0, updated, 0, index);
        }
        if (index < existing.length - 1) {
            System.arraycopy(existing, index + 1, updated, index, existing.length - index - 1);
        }

        writeChunkEntries(chunk, updated);
    }

    private int[] readChunkEntries(Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        int[] values = container.get(generatedBlocksKey, PersistentDataType.INTEGER_ARRAY);
        return values == null ? new int[0] : values;
    }

    private void writeChunkEntries(Chunk chunk, int[] values) {
        chunk.getPersistentDataContainer().set(generatedBlocksKey, PersistentDataType.INTEGER_ARRAY, values);
    }

    private static boolean contains(int[] values, int target) {
        return indexOf(values, target) != -1;
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static int pack(int x, int y, int z) {
        return (y << 8) | ((z & 15) << 4) | (x & 15);
    }
}

