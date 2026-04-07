package me.stella.engine;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedMaterialPicker {

    private final Material[] materials;
    private final double[] cumulativeWeights;
    private final double totalWeight;
    private final boolean singleItem;

    public WeightedMaterialPicker(Map<Material, Double> weights) {
        int validCount = 0;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            if (Math.max(0.0D, entry.getValue()) > 0.0D) {
                validCount++;
            }
        }

        this.materials = new Material[validCount];
        this.cumulativeWeights = new double[validCount];

        double running = 0.0D;
        int index = 0;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            double weight = Math.max(0.0D, entry.getValue());
            if (weight <= 0.0D) {
                continue;
            }

            running += weight;
            materials[index] = entry.getKey();
            cumulativeWeights[index] = running;
            index++;
        }

        this.totalWeight = running;
        this.singleItem = validCount == 1;
    }

    public Material nextMaterial(Material previous, Material secondPrevious, Material fallback) {
        if (materials.length == 0 || totalWeight <= 0.0D) {
            return fallback;
        }

        if (singleItem) {
            return materials[0];
        }

        Material selected = fallback;
        int attempts = Math.max(6, materials.length * 2);
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
        int index = Arrays.binarySearch(cumulativeWeights, value);
        if (index < 0) {
            index = -index - 1;
        }
        if (index >= materials.length) {
            index = materials.length - 1;
        }
        return materials[index];
    }
}
