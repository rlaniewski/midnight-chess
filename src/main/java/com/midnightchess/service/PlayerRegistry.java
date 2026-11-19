package com.midnightchess.service;

import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Tracks who is online. Players linger for a short grace period so page reloads don't drop them. */
@Service
public class PlayerRegistry {
    private static final long GRACE_SECONDS = 20;

    private final Map<String, Player> byToken = new ConcurrentHashMap<>();
    private final Map<String, Player> byId = new ConcurrentHashMap<>();
    private final Listeners<Void> changes = new Listeners<>();
    private final Listeners<Player> departures = new Listeners<>();
    private final AtomicInteger colorCounter = new AtomicInteger();
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "player-reaper");
        t.setDaemon(true);
        return t;
    });

    public Optional<Player> findByToken(String token) {
        return Optional.ofNullable(byToken.get(token));
    }

    public Optional<Player> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized Player register(String token, String requestedName) {
        String base = sanitize(requestedName);
        Player existing = byToken.get(token);
        if (existing != null) {
            existing.setName(uniqueName(base, existing));
            changes.fire(null);
            return existing;
        }
        Player p = new Player(token, uniqueName(base, null), colorCounter.getAndIncrement() % 7);
        byToken.put(token, p);
        byId.put(p.getId(), p);
        changes.fire(null);
        return p;
    }

    public static String sanitize(String name) {
        String n = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (n.length() > 20) {
            n = n.substring(0, 20).strip();
        }
        return n.isEmpty() ? "Guest" : n;
    }

    private String uniqueName(String base, Player self) {
        String candidate = base;
        int i = 2;
        while (taken(candidate, self)) {
            candidate = base + " " + i++;
        }
        return candidate;
    }

    private boolean taken(String name, Player self) {
        return byId.values().stream().anyMatch(p -> p != self && p.getName().equalsIgnoreCase(name));
    }

    public void connected(Player p) {
        p.connect();
        changes.fire(null);
    }

    public void disconnected(Player p) {
        if (p.disconnect() <= 0) {
            reaper.schedule(() -> {
                if (!p.isOnline()) {
                    remove(p);
                }
            }, GRACE_SECONDS, TimeUnit.SECONDS);
        }
        changes.fire(null);
    }

    private void remove(Player p) {
        if (byId.remove(p.getId()) != null) {
            byToken.remove(p.getToken());
            departures.fire(p);
            changes.fire(null);
        }
    }

    public List<Player> online() {
        return byId.values().stream()
                .filter(Player::isOnline)
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .toList();
    }

    void setStatus(Player p, Player.Status status) {
        if (p.getStatus() != status) {
            p.setStatus(status);
            changes.fire(null);
        }
    }

    public Registration onChange(Runnable listener) {
        return changes.add(v -> listener.run());
    }

    Registration onDeparture(Consumer<Player> listener) {
        return departures.add(listener);
    }

    @PreDestroy
    void shutdown() {
        reaper.shutdownNow();
    }
}
