package me.stella.engine;

import me.stella.common.ResilientScheduler;
import me.stella.config.CrystalSettings;
import me.stella.profile.OreProfile;
import org.bukkit.Material;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerTickAgent {

    private final UUID playerId;
    private final CrystalSettings settings;
    private final OreProfile profile;
    private final WeightedMaterialPicker picker;
    private final ConcurrentLinkedDeque<Material> cache;
    private final AtomicInteger cacheSize;
    private final ResilientScheduler scheduler;
    private final AtomicBoolean closed;
    private final AtomicBoolean refillRunning;

    public PlayerTickAgent(UUID playerId, OreProfile profile, CrystalSettings settings, ResilientScheduler scheduler) {
        this.playerId = playerId;
        this.settings = settings;
        this.scheduler = scheduler;
        this.profile = profile;
        this.picker = new WeightedMaterialPicker(profile.weights());
        this.cache = new ConcurrentLinkedDeque<>();
        this.cacheSize = new AtomicInteger(0);
        this.closed = new AtomicBoolean(false);
        this.refillRunning = new AtomicBoolean(false);
    }

    public OreProfile getProfile() {
        return this.profile;
    }

    public void start() {
        // No per-agent repeating task: external engine should call tickRefill().
        refillNow();
    }

    public void tickRefill() {
        if (closed.get()) {
            return;
        }

        if (cacheSize.get() < settings.refillThresholdSize()) {
            refillNow();
        }
    }

    public Material pop() {
        if (closed.get()) {
            return settings.fallbackType();
        }

        Material material = cache.pollFirst();
        if (material == null) {
            refillNow();
            return settings.fallbackType();
        }

        cacheSize.decrementAndGet();

        if (cacheSize.get() < settings.refillThresholdSize()) {
            refillNow();
        }

        return material;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            cache.clear();
            cacheSize.set(0);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    private void refillNow() {
        if (closed.get() || !refillRunning.compareAndSet(false, true)) {
            return;
        }

        scheduler.async(() -> {
            try {
                int maxSize = settings.poolSize();
                int currentSize = cacheSize.get();
                int toAdd = maxSize - currentSize;
                if (toAdd <= 0) {
                    return;
                }

                Material previous = null;
                Material secondPrevious = null;
                Iterator<Material> descending = cache.descendingIterator();
                if (descending.hasNext()) {
                    previous = descending.next();
                }
                if (descending.hasNext()) {
                    secondPrevious = descending.next();
                }

                int added = 0;
                for (int i = 0; i < toAdd && !closed.get(); i++) {
                    Material next = picker.nextMaterial(previous, secondPrevious, settings.fallbackType());
                    cache.offerLast(next);
                    secondPrevious = previous;
                    previous = next;
                    added++;
                }

                if (added > 0) {
                    cacheSize.addAndGet(added);
                }
            } finally {
                refillRunning.set(false);
            }
        });
    }
}
