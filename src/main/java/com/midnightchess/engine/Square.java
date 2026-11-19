package com.midnightchess.engine;

/** Squares are 0..63 with a1 = 0, h1 = 7, a8 = 56, h8 = 63. */
public final class Square {
    private Square() {
    }

    public static int file(int sq) {
        return sq & 7;
    }

    public static int rank(int sq) {
        return sq >> 3;
    }

    public static int of(int file, int rank) {
        return rank * 8 + file;
    }

    public static boolean onBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    public static boolean isLight(int sq) {
        return ((file(sq) + rank(sq)) & 1) == 1;
    }

    public static String name(int sq) {
        return "" + (char) ('a' + file(sq)) + (char) ('1' + rank(sq));
    }

    public static int parse(String name) {
        return of(name.charAt(0) - 'a', name.charAt(1) - '1');
    }
}
