package com.midnightchess.service;

import com.midnightchess.engine.AiLevel;
import com.midnightchess.engine.Board;
import com.midnightchess.engine.ChessAi;
import com.midnightchess.engine.Move;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Pairing (challenges, quick match, computer games) and game lifecycle. */
@Service
public class GameService {
    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final long MIN_AI_DELAY_MS = 450;

    public enum ColorChoice { WHITE, RANDOM, BLACK }

    public record Challenge(String id, Player from, Player to, ColorChoice fromColor, boolean rematch) {
    }

    /** Events delivered to an individual player's UI. */
    public sealed interface PlayerEvent permits ChallengeReceived, ChallengeDeclined, ChallengeExpired, GameStarted {
    }

    public record ChallengeReceived(Challenge challenge) implements PlayerEvent {
    }

    public record ChallengeDeclined(Challenge challenge) implements PlayerEvent {
    }

    public record ChallengeExpired(Challenge challenge) implements PlayerEvent {
    }

    public record GameStarted(String gameId) implements PlayerEvent {
    }

    private final PlayerRegistry registry;
    private final ChatService chat;
    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Listeners<PlayerEvent>> playerEvents = new ConcurrentHashMap<>();
    private final Listeners<Void> gamesChanged = new Listeners<>();
    private final ExecutorService aiPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chess-ai");
        t.setDaemon(true);
        return t;
    });
    private Player seeker;

    public GameService(PlayerRegistry registry, ChatService chat) {
        this.registry = registry;
        this.chat = chat;
        registry.onDeparture(this::playerLeft);
    }

    @PreDestroy
    void shutdown() {
        aiPool.shutdownNow();
    }

    // ------------------------------------------------------------------ queries & subscriptions

    public Optional<GameSession> find(String id) {
        return Optional.ofNullable(games.get(id));
    }

    public List<GameSession> liveGames() {
        return games.values().stream()
                .filter(g -> !g.isOver())
                .sorted(Comparator.comparing(GameSession::createdAt).reversed())
                .toList();
    }

    public Registration onGamesChanged(Runnable listener) {
        return gamesChanged.add(v -> listener.run());
    }

    public Registration subscribe(Player p, Consumer<PlayerEvent> listener) {
        return playerEvents.computeIfAbsent(p.getId(), k -> new Listeners<>()).add(listener);
    }

    private void send(Player p, PlayerEvent event) {
        Listeners<PlayerEvent> l = playerEvents.get(p.getId());
        if (l != null) {
            l.fire(event);
        }
    }

    // ------------------------------------------------------------------ pairing

    public GameSession playComputer(Player p, ColorChoice color, AiLevel level) {
        cancelSeek(p);
        boolean white = resolve(color);
        Seat me = Seat.of(p);
        Seat bot = Seat.computer(level);
        GameSession g = create(white ? me : bot, white ? bot : me);
        maybeComputerMove(g);
        return g;
    }

    /** Joins the quick-match queue. Returns true if an opponent was found right away. */
    public boolean seek(Player p) {
        Player opponent;
        synchronized (this) {
            if (seeker != null && seeker != p && seeker.isOnline()) {
                opponent = seeker;
                seeker = null;
            } else {
                seeker = p;
                registry.setStatus(p, Player.Status.SEEKING);
                gamesChanged.fire(null);
                return false;
            }
        }
        boolean pWhite = ThreadLocalRandom.current().nextBoolean();
        startHumanGame(pWhite ? p : opponent, pWhite ? opponent : p);
        return true;
    }

    public void cancelSeek(Player p) {
        synchronized (this) {
            if (seeker != p) {
                return;
            }
            seeker = null;
        }
        refreshStatus(p);
        gamesChanged.fire(null);
    }

    public synchronized boolean isSeeking(Player p) {
        return seeker == p;
    }

    public synchronized int seekerCount() {
        return seeker == null ? 0 : 1;
    }

    public Challenge challenge(Player from, Player to, ColorChoice fromColor) {
        return challenge(from, to, fromColor, false);
    }

    private Challenge challenge(Player from, Player to, ColorChoice fromColor, boolean rematch) {
        if (from == to || !to.isOnline()) {
            return null;
        }
        challenges.values().removeIf(c -> c.from() == from && c.to() == to);
        Challenge c = new Challenge(UUID.randomUUID().toString(), from, to, fromColor, rematch);
        challenges.put(c.id(), c);
        send(to, new ChallengeReceived(c));
        return c;
    }

    public boolean accept(String challengeId, Player p) {
        Challenge c = challenges.get(challengeId);
        if (c == null || c.to() != p || !challenges.remove(challengeId, c)) {
            return false;
        }
        if (!c.from().isOnline()) {
            return false;
        }
        boolean fromWhite = resolve(c.fromColor());
        startHumanGame(fromWhite ? c.from() : p, fromWhite ? p : c.from());
        return true;
    }

    public void decline(String challengeId, Player p) {
        Challenge c = challenges.get(challengeId);
        if (c != null && c.to() == p && challenges.remove(challengeId, c)) {
            send(c.from(), new ChallengeDeclined(c));
        }
    }

    private GameSession startHumanGame(Player white, Player black) {
        cancelSeek(white);
        cancelSeek(black);
        GameSession g = create(Seat.of(white), Seat.of(black));
        send(white, new GameStarted(g.id()));
        send(black, new GameStarted(g.id()));
        return g;
    }

    private GameSession create(Seat white, Seat black) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        GameSession g = new GameSession(id, white, black);
        games.put(id, g);
        refreshStatuses(g);
        chat.system(ChatService.gameRoom(id),
                "New game: %s (White) vs %s (Black). Good luck & have fun!".formatted(white.name(), black.name()));
        gamesChanged.fire(null);
        return g;
    }

    private static boolean resolve(ColorChoice c) {
        return switch (c) {
            case WHITE -> true;
            case BLACK -> false;
            case RANDOM -> ThreadLocalRandom.current().nextBoolean();
        };
    }

    // ------------------------------------------------------------------ playing

    public boolean move(GameSession g, Player p, Move m) {
        if (!g.move(p.getId(), m)) {
            return false;
        }
        afterMove(g);
        return true;
    }

    private void afterMove(GameSession g) {
        if (g.isOver()) {
            gameOver(g);
        } else {
            gamesChanged.fire(null);
            maybeComputerMove(g);
        }
    }

    private void maybeComputerMove(GameSession g) {
        Seat seat = g.seatToMove();
        if (!seat.isComputer() || g.isOver()) {
            return;
        }
        Board position = g.board();
        g.setAiThinking(true);
        aiPool.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                Move m = new ChessAi().chooseMove(position, seat.level());
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < MIN_AI_DELAY_MS) {
                    Thread.sleep(MIN_AI_DELAY_MS - elapsed);
                }
                if (g.computerMove(position, m)) {
                    afterMove(g);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.error("Computer move failed", e);
                g.setAiThinking(false);
            }
        });
    }

    public void resign(GameSession g, Player p) {
        if (g.resign(p.getId(), "resignation")) {
            chat.system(ChatService.gameRoom(g.id()), p.getName() + " resigned.");
            gameOver(g);
        }
    }

    public void offerDraw(GameSession g, Player p) {
        if (g.offerDraw(p.getId())) {
            chat.system(ChatService.gameRoom(g.id()), p.getName() + " offers a draw.");
        }
    }

    public void respondToDraw(GameSession g, Player p, boolean accept) {
        if (g.respondToDraw(p.getId(), accept)) {
            chat.system(ChatService.gameRoom(g.id()),
                    p.getName() + (accept ? " accepted the draw." : " declined the draw."));
            if (accept) {
                gameOver(g);
            }
        }
    }

    public boolean takeback(GameSession g, Player p) {
        return g.takeback(p.getId());
    }

    /**
     * Computer games restart immediately (colours swapped). Against a human a rematch
     * challenge is sent and an empty result is returned.
     */
    public Optional<GameSession> rematch(GameSession g, Player p) {
        Optional<Boolean> wasWhite = g.colorOf(p.getId());
        if (wasWhite.isEmpty()) {
            return Optional.empty();
        }
        ColorChoice next = wasWhite.get() ? ColorChoice.BLACK : ColorChoice.WHITE;
        Seat opp = g.opponentOf(p.getId());
        if (opp.isComputer()) {
            return Optional.of(playComputer(p, next, opp.level()));
        }
        registry.findById(opp.playerId()).ifPresent(o -> challenge(p, o, next, true));
        return Optional.empty();
    }

    private void gameOver(GameSession g) {
        GameSession.Snapshot s = g.snapshot();
        chat.system(ChatService.gameRoom(g.id()),
                "Game over — %s %s (%s).".formatted(s.resultText(), s.reasonText(), s.result().score()));
        refreshStatuses(g);
        gamesChanged.fire(null);
    }

    // ------------------------------------------------------------------ presence

    private void refreshStatuses(GameSession g) {
        for (Seat seat : List.of(g.white(), g.black())) {
            if (!seat.isComputer()) {
                registry.findById(seat.playerId()).ifPresent(this::refreshStatus);
            }
        }
    }

    private void refreshStatus(Player p) {
        boolean playing = games.values().stream().anyMatch(g -> !g.isOver() && g.isParticipant(p.getId()));
        Player.Status status = playing ? Player.Status.PLAYING
                : isSeeking(p) ? Player.Status.SEEKING : Player.Status.IDLE;
        registry.setStatus(p, status);
    }

    private void playerLeft(Player p) {
        cancelSeek(p);
        challenges.values().removeIf(c -> {
            if (c.from() == p) {
                send(c.to(), new ChallengeExpired(c));
                return true;
            }
            return c.to() == p;
        });
        for (GameSession g : games.values()) {
            if (!g.isOver() && g.isParticipant(p.getId()) && !g.hasComputer()
                    && g.resign(p.getId(), "abandonment")) {
                chat.system(ChatService.gameRoom(g.id()), p.getName() + " left the game.");
                gameOver(g);
            }
        }
        playerEvents.remove(p.getId());
    }
}
