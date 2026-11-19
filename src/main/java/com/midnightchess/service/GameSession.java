package com.midnightchess.service;

import com.midnightchess.engine.Board;
import com.midnightchess.engine.ChessGame;
import com.midnightchess.engine.GameResult;
import com.midnightchess.engine.Move;
import com.vaadin.flow.shared.Registration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A live game shared by two seats and any number of spectators.
 * State changes happen under the session's monitor; listeners are notified afterwards.
 */
public final class GameSession {

    public record Snapshot(Board board, List<String> sanMoves, Move lastMove, GameResult result,
                           String reason, String drawOfferBy, boolean aiThinking) {
        public boolean isOver() {
            return result != GameResult.ONGOING;
        }

        public int ply() {
            return sanMoves.size();
        }

        public String resultText() {
            return switch (result) {
                case WHITE_WINS -> "White wins";
                case BLACK_WINS -> "Black wins";
                case DRAW -> "Draw";
                case ONGOING -> "In progress";
            };
        }

        public String reasonText() {
            return switch (result) {
                case WHITE_WINS, BLACK_WINS -> "by " + reason;
                case DRAW -> "by " + reason;
                case ONGOING -> "";
            };
        }
    }

    private final String id;
    private final Seat white;
    private final Seat black;
    private final Instant createdAt = Instant.now();
    private final ChessGame game = new ChessGame();
    private final Listeners<GameSession> listeners = new Listeners<>();
    private String drawOfferBy;
    private boolean aiThinking;

    GameSession(String id, Seat white, Seat black) {
        this.id = id;
        this.white = white;
        this.black = black;
    }

    public String id() {
        return id;
    }

    public Seat white() {
        return white;
    }

    public Seat black() {
        return black;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean hasComputer() {
        return white.isComputer() || black.isComputer();
    }

    /** {@code Optional.of(true)} if the player is white, {@code false} if black, empty for spectators. */
    public Optional<Boolean> colorOf(String playerId) {
        if (white.isPlayer(playerId)) {
            return Optional.of(true);
        }
        if (black.isPlayer(playerId)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    public boolean isParticipant(String playerId) {
        return colorOf(playerId).isPresent();
    }

    public Seat opponentOf(String playerId) {
        return white.isPlayer(playerId) ? black : white;
    }

    public Registration subscribe(Consumer<GameSession> listener) {
        return listeners.add(listener);
    }

    private void fire() {
        listeners.fire(this);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(game.board(), game.sanMoves(), game.lastMove(), game.result(), game.reason(),
                drawOfferBy, aiThinking);
    }

    public synchronized Board board() {
        return game.board();
    }

    public synchronized boolean isOver() {
        return game.isOver();
    }

    public synchronized int ply() {
        return game.moveCount();
    }

    public synchronized Seat seatToMove() {
        return game.board().whiteToMove() ? white : black;
    }

    boolean move(String playerId, Move move) {
        synchronized (this) {
            if (game.isOver() || aiThinking) {
                return false;
            }
            Seat s = seatToMove();
            if (s.isComputer() || !s.isPlayer(playerId) || !game.play(move)) {
                return false;
            }
            drawOfferBy = null;
        }
        fire();
        return true;
    }

    boolean computerMove(Board expected, Move move) {
        boolean ok;
        synchronized (this) {
            aiThinking = false;
            ok = !game.isOver() && game.board() == expected && move != null && game.play(move);
        }
        fire();
        return ok;
    }

    void setAiThinking(boolean thinking) {
        synchronized (this) {
            aiThinking = thinking;
        }
        fire();
    }

    boolean resign(String playerId, String why) {
        synchronized (this) {
            Optional<Boolean> color = colorOf(playerId);
            if (game.isOver() || color.isEmpty()) {
                return false;
            }
            game.resign(color.get(), why);
            drawOfferBy = null;
        }
        fire();
        return true;
    }

    boolean offerDraw(String playerId) {
        synchronized (this) {
            if (game.isOver() || !isParticipant(playerId) || drawOfferBy != null) {
                return false;
            }
            drawOfferBy = playerId;
        }
        fire();
        return true;
    }

    boolean respondToDraw(String playerId, boolean accept) {
        synchronized (this) {
            if (game.isOver() || drawOfferBy == null || drawOfferBy.equals(playerId) || !isParticipant(playerId)) {
                return false;
            }
            drawOfferBy = null;
            if (accept) {
                game.agreeDraw();
            }
        }
        fire();
        return true;
    }

    boolean takeback(String playerId) {
        synchronized (this) {
            if (!hasComputer() || game.isOver() || aiThinking || !seatToMove().isPlayer(playerId)) {
                return false;
            }
            if (!game.undo(2)) {
                return false;
            }
        }
        fire();
        return true;
    }
}
