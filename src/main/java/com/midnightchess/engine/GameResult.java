package com.midnightchess.engine;

public enum GameResult {
    ONGOING("*"),
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("½-½");

    private final String score;

    GameResult(String score) {
        this.score = score;
    }

    public String score() {
        return score;
    }
}
