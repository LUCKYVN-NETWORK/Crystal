package me.stella.profile;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;

public record OreProfile(
        String id,
        String permission,
        int priority,
        Map<Material, Double> weights
) {

    public OreProfile {
        weights = Collections.unmodifiableMap(weights);
    }

    public boolean hasWeights() {
        return !weights.isEmpty();
    }
}

