package com.midnightchess.engine;

/**
 * Pieces are encoded as ints: positive = white, negative = black, 0 = empty.
 * The absolute value is the piece type.
 */
public final class Piece {
    public static final int NONE = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    private static final String LETTERS = " pnbrqk";
    // Solid glyphs are used for both colours; colour comes from CSS.
    private static final String[] GLYPHS = {"", "♟", "♞", "♝", "♜", "♛", "♚"};
    /** Forces text (not emoji) presentation of the glyph. */
    private static final String TEXT_VS = "︎";

    private Piece() {
    }

    public static int type(int piece) {
        return Math.abs(piece);
    }

    public static boolean isWhite(int piece) {
        return piece > 0;
    }

    public static int fromChar(char c) {
        int t = LETTERS.indexOf(Character.toLowerCase(c));
        if (t <= 0) {
            throw new IllegalArgumentException("Unknown piece: " + c);
        }
        return Character.isUpperCase(c) ? t : -t;
    }

    public static char toChar(int piece) {
        char c = LETTERS.charAt(Math.abs(piece));
        return piece > 0 ? Character.toUpperCase(c) : c;
    }

    /** Upper-case SAN letter (empty for pawns). */
    public static String sanLetter(int type) {
        return type == PAWN ? "" : String.valueOf(Character.toUpperCase(LETTERS.charAt(type)));
    }

    public static String glyph(int piece) {
        return GLYPHS[Math.abs(piece)] + TEXT_VS;
    }

    public static String name(int type) {
        return switch (type) {
            case PAWN -> "Pawn";
            case KNIGHT -> "Knight";
            case BISHOP -> "Bishop";
            case ROOK -> "Rook";
            case QUEEN -> "Queen";
            case KING -> "King";
            default -> "";
        };
    }
}
