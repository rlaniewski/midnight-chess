package com.midnightchess.engine;

/**
 * A move from one square to another. {@code promotion} is a piece type
 * (KNIGHT..QUEEN) or 0 when the move is not a promotion.
 */
public record Move(int from, int to, int promotion) {

    public Move(int from, int to) {
        this(from, to, 0);
    }

    public static Move parseUci(String uci) {
        int promo = uci.length() > 4 ? Math.abs(Piece.fromChar(uci.charAt(4))) : 0;
        return new Move(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)), promo);
    }

    public String uci() {
        String s = Square.name(from) + Square.name(to);
        return promotion == 0 ? s : s + Character.toLowerCase(Piece.toChar(promotion));
    }

    @Override
    public String toString() {
        return uci();
    }
}
