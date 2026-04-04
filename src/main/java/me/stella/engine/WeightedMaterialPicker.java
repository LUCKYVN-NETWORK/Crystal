package me.stella.engine;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedMaterialPicker {

    private final List<Entry> entries;
    private final double totalWeight;

    public WeightedMaterialPicker(Map<Material, Double> weights) {
        this.entries = new ArrayList<>();

        double running = 0.0D;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            double weight = Math.max(0.0D, entry.getValue());
            if (weight <= 0.0D) {
                continue;
            }
            running += weight;
            entries.add(new Entry(entry.getKey(), running));
        }

        this.totalWeight = running;
    }

    public Material nextMaterial(Material previous, Material secondPrevious, Material fallback) {
        if (entries.isEmpty() || totalWeight <= 0.0D) {
            return fallback;
        }

        Material selected = fallback;
        int attempts = Math.max(6, entries.size() * 2);
        for (int i = 0; i < attempts; i++) {
            selected = pickOne();
            if (!(selected == previous && selected == secondPrevious)) {
                return selected;
            }
        }

        return selected;
    }

    private Material pickOne() {
        double value = ThreadLocalRandom.current().nextDouble(totalWeight);
        for (Entry entry : entries) {
            if (value <= entry.upperBound) {
                return entry.material;
            }
        }
        return entries.get(entries.size() - 1).material;
    }

    private record Entry(Material material, double upperBound) {
    }
}

