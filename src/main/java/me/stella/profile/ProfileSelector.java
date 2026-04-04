package me.stella.profile;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ProfileSelector {

    private final Map<String, OreProfile> profiles;
    private final String defaultProfileId;
    private final List<OreProfile> byPriority;

    public ProfileSelector(Map<String, OreProfile> profiles, String defaultProfileId) {
        this.profiles = profiles;
        this.defaultProfileId = defaultProfileId;
        this.byPriority = new ArrayList<>(profiles.values());
        this.byPriority.sort(Comparator.comparingInt(OreProfile::priority).reversed());
    }

    public OreProfile selectFor(Player player) {
        for (OreProfile profile : byPriority) {
            String permission = profile.permission();
            if (permission == null || permission.isBlank() || player.hasPermission(permission)) {
                return profile;
            }
        }

        OreProfile fallback = profiles.get(defaultProfileId);
        if (fallback != null) {
            return fallback;
        }

        if (!byPriority.isEmpty()) {
            return byPriority.get(0);
        }

        throw new IllegalStateException("No profiles available for selection.");
    }
}

