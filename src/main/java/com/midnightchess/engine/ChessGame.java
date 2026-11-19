package com.midnightchess.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A game in progress: position history, notation and result.
 * Not thread-safe; callers (GameSession) synchronise access.
 */
public final class ChessGame {
    private final List<Board> positions = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private final List<String> sanMoves = new ArrayList<>();
    private GameResult result = GameResult.ONGOING;
    private String reason = "";

    public ChessGame() {
        this(Board.initial());
    }

    public ChessGame(Board start) {
        positions.add(start);
    }

    public Board board() {
        return positions.get(positions.size() - 1);
    }

    public boolean play(Move move) {
        if (isOver()) {
            return false;
        }
        Board b = board();
        if (!b.legalMoves().contains(move)) {
            return false;
        }
        sanMoves.add(b.toSan(move));
        moves.add(move);
        positions.add(b.makeMove(move));
        updateResult();
        return true;
    }

    private void updateResult() {
        Board b = board();
        if (b.legalMoves().isEmpty()) {
            if (b.inCheck()) {
                finish(b.whiteToMove() ? GameResult.BLACK_WINS : GameResult.WHITE_WINS, "checkmate");
            } else {
                finish(GameResult.DRAW, "stalemate");
            }
        } else if (b.isInsufficientMaterial()) {
            finish(GameResult.DRAW, "insufficient material");
        } else if (b.halfmoveClock() >= 100) {
            finish(GameResult.DRAW, "the 50-move rule");
        } else if (repetitions(b.positionKey()) >= 3) {
            finish(GameResult.DRAW, "threefold repetition");
        }
    }

    private int repetitions(String key) {
        int n = 0;
        for (Board p : positions) {
            if (p.positionKey().equals(key)) {
                n++;
            }
        }
        return n;
    }

    public void resign(boolean whiteResigns, String why) {
        if (!isOver()) {
            finish(whiteResigns ? GameResult.BLACK_WINS : GameResult.WHITE_WINS, why);
        }
    }

    public void agreeDraw() {
        if (!isOver()) {
            finish(GameResult.DRAW, "agreement");
        }
    }

    public boolean undo(int count) {
        if (isOver() || count <= 0 || moves.size() < count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            positions.remove(positions.size() - 1);
            moves.remove(moves.size() - 1);
            sanMoves.remove(sanMoves.size() - 1);
        }
        return true;
    }

    private void finish(GameResult r, String why) {
        result = r;
        reason = why;
    }

    public boolean isOver() {
        return result != GameResult.ONGOING;
    }

    public GameResult result() {
        return result;
    }

    public String reason() {
        return reason;
    }

    public int moveCount() {
        return moves.size();
    }

    public Move lastMove() {
        return moves.isEmpty() ? null : moves.get(moves.size() - 1);
    }

    public List<String> sanMoves() {
        return List.copyOf(sanMoves);
    }
}
