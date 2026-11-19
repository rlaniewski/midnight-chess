package com.midnightchess.engine;

public enum AiLevel {
    BEGINNER("Beginner", "Friendly and forgiving", 1, 300, 180),
    CASUAL("Casual", "Knows the basics", 2, 700, 50),
    CLUB("Club", "Solid tactical play", 3, 1500, 12),
    MASTER("Master", "Thinks deep – bring your A-game", 5, 3500, 0);

    private final String label;
    private final String description;
    private final int depth;
    private final long timeMillis;
    private final int noise;

    AiLevel(String label, String description, int depth, long timeMillis, int noise) {
        this.label = label;
        this.description = description;
        this.depth = depth;
        this.timeMillis = timeMillis;
        this.noise = noise;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Maximum search depth in plies. */
    public int depth() {
        return depth;
    }

    /** Soft time budget per move. */
    public long timeMillis() {
        return timeMillis;
    }

    /** Random score noise (centipawns) applied at the root to make weaker levels err. */
    public int noise() {
        return noise;
    }
}
