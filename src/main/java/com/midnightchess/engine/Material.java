package com.midnightchess.engine;

/** Helpers for showing captured pieces and material balance. */
public final class Material {
    private static final int[] START_COUNT = {0, 8, 2, 2, 2, 1, 1};
    private static final int[] POINTS = {0, 1, 3, 3, 5, 9, 0};

    private Material() {
    }

    /** Number of missing pieces of each type for the given colour (index = piece type). */
    public static int[] missing(Board b, boolean white) {
        int[] count = new int[7];
        for (int s = 0; s < 64; s++) {
            int p = b.piece(s);
            if (p != 0 && (p > 0) == white) {
                count[Math.abs(p)]++;
            }
        }
        int[] missing = new int[7];
        for (int t = 1; t <= 5; t++) {
            missing[t] = Math.max(0, START_COUNT[t] - count[t]);
        }
        return missing;
    }

    /** Material points of white minus black. */
    public static int balance(Board b) {
        int score = 0;
        for (int s = 0; s < 64; s++) {
            int p = b.piece(s);
            score += p > 0 ? POINTS[p] : -POINTS[-p];
        }
        return score;
    }
}
