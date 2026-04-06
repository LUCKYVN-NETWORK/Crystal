package me.stella.engine;

import me.stella.common.ResilientScheduler;
import me.stella.config.CrystalSettings;
import me.stella.profile.OreProfile;
import org.bukkit.Material;

import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerTickAgent {

    private final UUID playerId;
    private final CrystalSettings settings;
    private final OreProfile profile;
    private final WeightedMaterialPicker picker;
    private final ConcurrentLinkedDeque<Material> cache;
    private final ResilientScheduler scheduler;
    private final AtomicBoolean closed;
    private final AtomicBoolean refillRunning;

    private ResilientScheduler.TaskHandle<Void> refillHandle;

    public PlayerTickAgent(UUID playerId, OreProfile profile, CrystalSettings settings, ResilientScheduler scheduler) {
        this.playerId = playerId;
        this.settings = settings;
        this.scheduler = scheduler;
        this.profile = profile;
        this.picker = new WeightedMaterialPicker(profile.weights());
        this.cache = new ConcurrentLinkedDeque<>();
        this.closed = new AtomicBoolean(false);
        this.refillRunning = new AtomicBoolean(false);
        this.refillHandle = ResilientScheduler.TaskHandle.NO_OP;
    }

    public OreProfile getProfile() {
        return this.profile;
    }

    public void start() {
        refillNow();
        refillHandle = scheduler.scheduleAsyncAtFixedRate(
                Duration.ofMillis(settings.refillIntervalMs()),
                Duration.ofMillis(settings.refillIntervalMs()),
                this::refillNow
        );
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

        if (cache.size() < settings.refillThresholdSize()) {
            refillNow();
        }

        return material;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            refillHandle.cancel();
            cache.clear();
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
                if (cache.size() >= maxSize) {
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

                while (!closed.get() && cache.size() < maxSize) {
                    Material next = picker.nextMaterial(previous, secondPrevious, settings.fallbackType());
                    cache.offerLast(next);
                    secondPrevious = previous;
                    previous = next;
                }
            } finally {
                refillRunning.set(false);
            }
        });
    }
}

