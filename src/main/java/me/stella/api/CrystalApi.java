package me.stella.api;

import me.stella.marker.GeneratedBlockMarkerService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class CrystalApi {

    private CrystalApi() {
    }

    public static Optional<GeneratedBlockMarkerService> generatedBlockMarkerService() {
        RegisteredServiceProvider<GeneratedBlockMarkerService> provider = Bukkit.getServicesManager()
                .getRegistration(GeneratedBlockMarkerService.class);

        if (provider == null) {
            return Optional.empty();
        }

        return Optional.of(provider.getProvider());
    }
}

