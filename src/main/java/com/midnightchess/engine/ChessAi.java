package com.midnightchess.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.midnightchess.engine.Piece.BISHOP;
import static com.midnightchess.engine.Piece.KING;
import static com.midnightchess.engine.Piece.KNIGHT;
import static com.midnightchess.engine.Piece.PAWN;
import static com.midnightchess.engine.Piece.QUEEN;
import static com.midnightchess.engine.Piece.ROOK;

/**
 * Negamax + alpha-beta with iterative deepening, quiescence search,
 * MVV-LVA move ordering and piece-square-table evaluation.
 * One instance per search (not thread-safe).
 */
public final class ChessAi {
    private static final int INF = 1_000_000;
    private static final int MATE = 100_000;
    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
    private static final int[] ORDER_VALUE = {0, 1, 3, 3, 5, 9, 20};

    private static final class Timeout extends RuntimeException {
        Timeout() {
            super(null, null, false, false);
        }
    }

    private final Random random;
    private long deadline;
    private long nodes;
    private boolean timed;

    public ChessAi() {
        this(new Random());
    }

    public ChessAi(Random random) {
        this.random = random;
    }

    public long nodes() {
        return nodes;
    }

    public Move chooseMove(Board root, AiLevel level) {
        List<Move> legal = root.legalMoves();
        if (legal.isEmpty()) {
            return null;
        }
        if (legal.size() == 1) {
            return legal.get(0);
        }
        deadline = System.nanoTime() + level.timeMillis() * 1_000_000L;
        List<Move> ordered = new ArrayList<>(legal);
        sort(root, ordered);
        Move best = ordered.get(0);
        for (int depth = 1; depth <= level.depth(); depth++) {
            timed = depth > 1; // depth 1 always completes
            try {
                best = searchRoot(root, ordered, depth, level.noise());
            } catch (Timeout t) {
                break;
            }
            ordered.remove(best);
            ordered.add(0, best);
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        return best;
    }

    private Move searchRoot(Board root, List<Move> moves, int depth, int noise) {
        int bestRaw = -INF;
        int bestNoisy = -INF;
        Move best = moves.get(0);
        for (Move m : moves) {
            // Moves that can't come within the noise band of the best one only need a bound.
            int alpha = bestRaw == -INF ? -INF : bestRaw - 2 * noise - 1;
            int score = -negamax(root.makeMove(m), depth - 1, -INF, -alpha, 1);
            bestRaw = Math.max(bestRaw, score);
            int noisy = score + (noise > 0 ? random.nextInt(2 * noise + 1) - noise : 0);
            if (noisy > bestNoisy) {
                bestNoisy = noisy;
                best = m;
            }
        }
        return best;
    }

    private void tick() {
        nodes++;
        if (timed && (nodes & 1023) == 0 && System.nanoTime() > deadline) {
            throw new Timeout();
        }
    }

    private int negamax(Board b, int depth, int alpha, int beta, int ply) {
        tick();
        List<Move> moves = b.legalMoves();
        boolean inCheck = b.inCheck();
        if (moves.isEmpty()) {
            return inCheck ? -MATE + ply : 0;
        }
        if (b.halfmoveClock() >= 100 || b.isInsufficientMaterial()) {
            return 0;
        }
        if (inCheck && ply < 12) {
            depth++;
        }
        if (depth <= 0) {
            return quiesce(b, alpha, beta, 0);
        }
        List<Move> ordered = new ArrayList<>(moves);
        sort(b, ordered);
        for (Move m : ordered) {
            int score = -negamax(b.makeMove(m), depth - 1, -beta, -alpha, ply + 1);
            if (score >= beta) {
                return score;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    private int quiesce(Board b, int alpha, int beta, int qdepth) {
        tick();
        int standPat = evaluate(b);
        if (standPat >= beta) {
            return standPat;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }
        if (qdepth >= 8) {
            return alpha;
        }
        List<Move> tactical = b.legalTacticalMoves();
        sort(b, tactical);
        for (Move m : tactical) {
            int score = -quiesce(b.makeMove(m), -beta, -alpha, qdepth + 1);
            if (score >= beta) {
                return score;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    private static void sort(Board b, List<Move> moves) {
        moves.sort(Comparator.comparingInt((Move m) -> orderScore(b, m)).reversed());
    }

    private static int orderScore(Board b, Move m) {
        int score = 0;
        if (m.promotion() != 0) {
            score += 800 + ORDER_VALUE[m.promotion()];
        }
        if (b.isCapture(m)) {
            int victim = Math.abs(b.piece(m.to()));
            if (victim == 0) {
                victim = PAWN; // en passant
            }
            int attacker = Math.abs(b.piece(m.from()));
            score += 1000 + 10 * ORDER_VALUE[victim] - ORDER_VALUE[attacker];
        }
        return score;
    }

    // ------------------------------------------------------------------ evaluation

    /** Static evaluation in centipawns from the side-to-move's point of view. */
    public static int evaluate(Board b) {
        int nonPawnMaterial = 0;
        for (int s = 0; s < 64; s++) {
            int t = Math.abs(b.piece(s));
            if (t >= KNIGHT && t <= QUEEN) {
                nonPawnMaterial += VALUE[t];
            }
        }
        boolean endgame = nonPawnMaterial <= 2600;
        int score = 0;
        int whiteBishops = 0, blackBishops = 0;
        for (int s = 0; s < 64; s++) {
            int p = b.piece(s);
            if (p == 0) {
                continue;
            }
            int t = Math.abs(p);
            int idx = p > 0 ? (7 - Square.rank(s)) * 8 + Square.file(s) : Square.rank(s) * 8 + Square.file(s);
            int v = VALUE[t] + pst(t, idx, endgame);
            if (p > 0) {
                score += v;
                if (t == BISHOP) {
                    whiteBishops++;
                }
            } else {
                score -= v;
                if (t == BISHOP) {
                    blackBishops++;
                }
            }
        }
        if (whiteBishops >= 2) {
            score += 30;
        }
        if (blackBishops >= 2) {
            score -= 30;
        }
        return b.whiteToMove() ? score : -score;
    }

    private static int pst(int type, int idx, boolean endgame) {
        return switch (type) {
            case PAWN -> PAWN_T[idx];
            case KNIGHT -> KNIGHT_T[idx];
            case BISHOP -> BISHOP_T[idx];
            case ROOK -> ROOK_T[idx];
            case QUEEN -> QUEEN_T[idx];
            case KING -> endgame ? KING_END_T[idx] : KING_MID_T[idx];
            default -> 0;
        };
    }

    // Tables are written from White's view with rank 8 on the first row.
    private static final int[] PAWN_T = {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] KNIGHT_T = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] BISHOP_T = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] ROOK_T = {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] QUEEN_T = {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] KING_MID_T = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] KING_END_T = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};
}
