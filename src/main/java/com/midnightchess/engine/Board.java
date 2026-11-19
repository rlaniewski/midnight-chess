package com.midnightchess.engine;

import java.util.ArrayList;
import java.util.List;

import static com.midnightchess.engine.Piece.BISHOP;
import static com.midnightchess.engine.Piece.KING;
import static com.midnightchess.engine.Piece.KNIGHT;
import static com.midnightchess.engine.Piece.PAWN;
import static com.midnightchess.engine.Piece.QUEEN;
import static com.midnightchess.engine.Piece.ROOK;

/**
 * Immutable chess position. {@link #makeMove(Move)} returns a new board,
 * which makes positions safe to share between threads (UI, AI, etc.).
 */
public final class Board {
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static final int WK = 1, WQ = 2, BK = 4, BQ = 8;

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ORTHOGONALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] PROMOTIONS = {QUEEN, ROOK, BISHOP, KNIGHT};

    private final int[] sq;
    private final boolean whiteToMove;
    private final int castling;
    private final int epSquare;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private volatile List<Move> legalCache;

    private Board(int[] sq, boolean whiteToMove, int castling, int epSquare, int halfmoveClock, int fullmoveNumber) {
        this.sq = sq;
        this.whiteToMove = whiteToMove;
        this.castling = castling;
        this.epSquare = epSquare;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Board initial() {
        return fromFen(START_FEN);
    }

    public static Board fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        int[] sq = new int[64];
        int rank = 7, file = 0;
        for (char c : parts[0].toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += c - '0';
            } else {
                sq[Square.of(file, rank)] = Piece.fromChar(c);
                file++;
            }
        }
        boolean white = parts.length < 2 || parts[1].equals("w");
        int castling = 0;
        if (parts.length > 2) {
            for (char c : parts[2].toCharArray()) {
                switch (c) {
                    case 'K' -> castling |= WK;
                    case 'Q' -> castling |= WQ;
                    case 'k' -> castling |= BK;
                    case 'q' -> castling |= BQ;
                    default -> {
                    }
                }
            }
        }
        int ep = parts.length > 3 && !parts[3].equals("-") ? Square.parse(parts[3]) : -1;
        int half = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int full = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        return new Board(sq, white, castling, ep, half, full);
    }

    // ------------------------------------------------------------------ accessors

    public int piece(int square) {
        return sq[square];
    }

    public boolean whiteToMove() {
        return whiteToMove;
    }

    public int castlingRights() {
        return castling;
    }

    public int epSquare() {
        return epSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public int kingSquare(boolean white) {
        int king = white ? KING : -KING;
        for (int s = 0; s < 64; s++) {
            if (sq[s] == king) {
                return s;
            }
        }
        return -1;
    }

    public boolean inCheck() {
        int k = kingSquare(whiteToMove);
        return k >= 0 && isAttacked(k, !whiteToMove);
    }

    // ------------------------------------------------------------------ attacks

    /** Is {@code target} attacked by any piece of the given colour? */
    public boolean isAttacked(int target, boolean byWhite) {
        int f = Square.file(target), r = Square.rank(target);
        int sign = byWhite ? 1 : -1;

        // Pawns: a white pawn attacks upwards, so it sits one rank below the target.
        int pr = r - sign;
        if (pr >= 0 && pr < 8) {
            for (int df = -1; df <= 1; df += 2) {
                int pf = f + df;
                if (pf >= 0 && pf < 8 && sq[Square.of(pf, pr)] == sign * PAWN) {
                    return true;
                }
            }
        }
        for (int[] d : KNIGHT_STEPS) {
            int nf = f + d[0], nr = r + d[1];
            if (Square.onBoard(nf, nr) && sq[Square.of(nf, nr)] == sign * KNIGHT) {
                return true;
            }
        }
        for (int[] d : KING_STEPS) {
            int nf = f + d[0], nr = r + d[1];
            if (Square.onBoard(nf, nr) && sq[Square.of(nf, nr)] == sign * KING) {
                return true;
            }
        }
        if (slidingAttack(f, r, DIAGONALS, sign * BISHOP, sign * QUEEN)) {
            return true;
        }
        return slidingAttack(f, r, ORTHOGONALS, sign * ROOK, sign * QUEEN);
    }

    private boolean slidingAttack(int f, int r, int[][] dirs, int slider, int queen) {
        for (int[] d : dirs) {
            int nf = f + d[0], nr = r + d[1];
            while (Square.onBoard(nf, nr)) {
                int p = sq[Square.of(nf, nr)];
                if (p != 0) {
                    if (p == slider || p == queen) {
                        return true;
                    }
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ move generation

    public List<Move> legalMoves() {
        List<Move> cached = legalCache;
        if (cached == null) {
            List<Move> pseudo = new ArrayList<>(48);
            generatePseudo(pseudo);
            List<Move> legal = new ArrayList<>(pseudo.size());
            for (Move m : pseudo) {
                if (isLegal(m)) {
                    legal.add(m);
                }
            }
            cached = List.copyOf(legal);
            legalCache = cached;
        }
        return cached;
    }

    public List<Move> legalMovesFrom(int from) {
        List<Move> out = new ArrayList<>();
        for (Move m : legalMoves()) {
            if (m.from() == from) {
                out.add(m);
            }
        }
        return out;
    }

    /** Legal captures and promotions only – used by the AI's quiescence search. */
    public List<Move> legalTacticalMoves() {
        List<Move> pseudo = new ArrayList<>(48);
        generatePseudo(pseudo);
        List<Move> out = new ArrayList<>();
        for (Move m : pseudo) {
            if (isCapture(m) || m.promotion() != 0) {
                if (isLegal(m)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    public boolean isCapture(Move m) {
        return sq[m.to()] != 0 || (Math.abs(sq[m.from()]) == PAWN && m.to() == epSquare);
    }

    private boolean isLegal(Move m) {
        Board after = makeMove(m);
        int k = after.kingSquare(whiteToMove);
        return k >= 0 && !after.isAttacked(k, !whiteToMove);
    }

    private boolean isEnemy(int s) {
        int p = sq[s];
        return p != 0 && (p > 0) != whiteToMove;
    }

    private void generatePseudo(List<Move> out) {
        for (int s = 0; s < 64; s++) {
            int p = sq[s];
            if (p == 0 || (p > 0) != whiteToMove) {
                continue;
            }
            int f = Square.file(s), r = Square.rank(s);
            switch (Math.abs(p)) {
                case PAWN -> pawnMoves(s, f, r, out);
                case KNIGHT -> steps(s, f, r, KNIGHT_STEPS, out);
                case BISHOP -> slides(s, f, r, DIAGONALS, out);
                case ROOK -> slides(s, f, r, ORTHOGONALS, out);
                case QUEEN -> {
                    slides(s, f, r, DIAGONALS, out);
                    slides(s, f, r, ORTHOGONALS, out);
                }
                case KING -> {
                    steps(s, f, r, KING_STEPS, out);
                    castles(out);
                }
                default -> {
                }
            }
        }
    }

    private void steps(int s, int f, int r, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nf = f + d[0], nr = r + d[1];
            if (Square.onBoard(nf, nr)) {
                int t = Square.of(nf, nr);
                if (sq[t] == 0 || isEnemy(t)) {
                    out.add(new Move(s, t));
                }
            }
        }
    }

    private void slides(int s, int f, int r, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nf = f + d[0], nr = r + d[1];
            while (Square.onBoard(nf, nr)) {
                int t = Square.of(nf, nr);
                if (sq[t] == 0) {
                    out.add(new Move(s, t));
                } else {
                    if (isEnemy(t)) {
                        out.add(new Move(s, t));
                    }
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
    }

    private void pawnMoves(int s, int f, int r, List<Move> out) {
        int dr = whiteToMove ? 1 : -1;
        int startRank = whiteToMove ? 1 : 6;
        int lastRank = whiteToMove ? 7 : 0;
        int nr = r + dr;
        if (nr < 0 || nr > 7) {
            return;
        }
        int one = Square.of(f, nr);
        if (sq[one] == 0) {
            addPawnMove(s, one, nr == lastRank, out);
            if (r == startRank) {
                int two = Square.of(f, r + 2 * dr);
                if (sq[two] == 0) {
                    out.add(new Move(s, two));
                }
            }
        }
        for (int df = -1; df <= 1; df += 2) {
            int nf = f + df;
            if (nf < 0 || nf > 7) {
                continue;
            }
            int t = Square.of(nf, nr);
            if (isEnemy(t) || t == epSquare) {
                addPawnMove(s, t, nr == lastRank, out);
            }
        }
    }

    private static void addPawnMove(int from, int to, boolean promotes, List<Move> out) {
        if (promotes) {
            for (int p : PROMOTIONS) {
                out.add(new Move(from, to, p));
            }
        } else {
            out.add(new Move(from, to));
        }
    }

    private void castles(List<Move> out) {
        if (whiteToMove) {
            if (sq[4] != KING || isAttacked(4, false)) {
                return;
            }
            if ((castling & WK) != 0 && sq[7] == ROOK && sq[5] == 0 && sq[6] == 0
                    && !isAttacked(5, false) && !isAttacked(6, false)) {
                out.add(new Move(4, 6));
            }
            if ((castling & WQ) != 0 && sq[0] == ROOK && sq[1] == 0 && sq[2] == 0 && sq[3] == 0
                    && !isAttacked(3, false) && !isAttacked(2, false)) {
                out.add(new Move(4, 2));
            }
        } else {
            if (sq[60] != -KING || isAttacked(60, true)) {
                return;
            }
            if ((castling & BK) != 0 && sq[63] == -ROOK && sq[61] == 0 && sq[62] == 0
                    && !isAttacked(61, true) && !isAttacked(62, true)) {
                out.add(new Move(60, 62));
            }
            if ((castling & BQ) != 0 && sq[56] == -ROOK && sq[57] == 0 && sq[58] == 0 && sq[59] == 0
                    && !isAttacked(59, true) && !isAttacked(58, true)) {
                out.add(new Move(60, 58));
            }
        }
    }

    // ------------------------------------------------------------------ making moves

    /** Applies a (pseudo-)legal move and returns the resulting position. */
    public Board makeMove(Move m) {
        int[] n = sq.clone();
        int piece = n[m.from()];
        int type = Math.abs(piece);
        boolean white = piece > 0;
        int captured = n[m.to()];

        n[m.to()] = piece;
        n[m.from()] = 0;

        if (type == PAWN && m.to() == epSquare) {
            n[m.to() + (white ? -8 : 8)] = 0;
            captured = white ? -PAWN : PAWN;
        }
        if (m.promotion() != 0) {
            n[m.to()] = white ? m.promotion() : -m.promotion();
        }
        if (type == KING && Math.abs(m.to() - m.from()) == 2) {
            switch (m.to()) {
                case 6 -> { n[5] = n[7]; n[7] = 0; }
                case 2 -> { n[3] = n[0]; n[0] = 0; }
                case 62 -> { n[61] = n[63]; n[63] = 0; }
                case 58 -> { n[59] = n[56]; n[56] = 0; }
                default -> { }
            }
        }

        int c = castling;
        if (type == KING) {
            c &= white ? ~(WK | WQ) : ~(BK | BQ);
        }
        c &= ~rightsTouchedBy(m.from());
        c &= ~rightsTouchedBy(m.to());

        int ep = (type == PAWN && Math.abs(m.to() - m.from()) == 16) ? (m.from() + m.to()) / 2 : -1;
        int half = (type == PAWN || captured != 0) ? 0 : halfmoveClock + 1;
        int full = fullmoveNumber + (whiteToMove ? 0 : 1);
        return new Board(n, !whiteToMove, c, ep, half, full);
    }

    private static int rightsTouchedBy(int s) {
        return switch (s) {
            case 0 -> WQ;
            case 7 -> WK;
            case 56 -> BQ;
            case 63 -> BK;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ notation & draws

    /** Standard algebraic notation for a legal move in this position. */
    public String toSan(Move m) {
        int piece = sq[m.from()];
        int type = Math.abs(piece);
        String san;
        if (type == KING && Math.abs(m.to() - m.from()) == 2) {
            san = m.to() > m.from() ? "O-O" : "O-O-O";
        } else {
            boolean capture = isCapture(m);
            StringBuilder sb = new StringBuilder();
            if (type == PAWN) {
                if (capture) {
                    sb.append((char) ('a' + Square.file(m.from())));
                }
            } else {
                sb.append(Piece.sanLetter(type));
                boolean ambiguous = false, sameFile = false, sameRank = false;
                for (Move o : legalMoves()) {
                    if (o.to() == m.to() && o.from() != m.from() && sq[o.from()] == piece) {
                        ambiguous = true;
                        sameFile |= Square.file(o.from()) == Square.file(m.from());
                        sameRank |= Square.rank(o.from()) == Square.rank(m.from());
                    }
                }
                if (ambiguous) {
                    if (!sameFile) {
                        sb.append((char) ('a' + Square.file(m.from())));
                    } else if (!sameRank) {
                        sb.append((char) ('1' + Square.rank(m.from())));
                    } else {
                        sb.append(Square.name(m.from()));
                    }
                }
            }
            if (capture) {
                sb.append('x');
            }
            sb.append(Square.name(m.to()));
            if (m.promotion() != 0) {
                sb.append('=').append(Piece.sanLetter(m.promotion()));
            }
            san = sb.toString();
        }
        Board after = makeMove(m);
        if (after.inCheck()) {
            san += after.legalMoves().isEmpty() ? "#" : "+";
        }
        return san;
    }

    public boolean isInsufficientMaterial() {
        int knights = 0, lightBishops = 0, darkBishops = 0;
        for (int s = 0; s < 64; s++) {
            switch (Math.abs(sq[s])) {
                case PAWN, ROOK, QUEEN -> {
                    return false;
                }
                case KNIGHT -> knights++;
                case BISHOP -> {
                    if (Square.isLight(s)) {
                        lightBishops++;
                    } else {
                        darkBishops++;
                    }
                }
                default -> {
                }
            }
        }
        int minors = knights + lightBishops + darkBishops;
        if (minors <= 1) {
            return true;
        }
        // Any number of bishops all on the same colour can never mate.
        return knights == 0 && (lightBishops == 0 || darkBishops == 0);
    }

    /** Key identifying a position for repetition detection. */
    public String positionKey() {
        String[] fen = toFen().split(" ");
        return fen[0] + ' ' + fen[1] + ' ' + fen[2] + ' ' + fen[3];
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = sq[Square.of(f, r)];
                if (p == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.toChar(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r > 0) {
                sb.append('/');
            }
        }
        sb.append(whiteToMove ? " w " : " b ");
        String c = ((castling & WK) != 0 ? "K" : "") + ((castling & WQ) != 0 ? "Q" : "")
                + ((castling & BK) != 0 ? "k" : "") + ((castling & BQ) != 0 ? "q" : "");
        sb.append(c.isEmpty() ? "-" : c);
        sb.append(' ').append(epSquare >= 0 ? Square.name(epSquare) : "-");
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    @Override
    public String toString() {
        return toFen();
    }
}
