package com.midnightchess.service;

import com.midnightchess.engine.AiLevel;

/** One side of the board: either a human player or the computer. */
public record Seat(String playerId, String name, int colorIndex, AiLevel level) {

    public static Seat of(Player p) {
        return new Seat(p.getId(), p.getName(), p.getColorIndex(), null);
    }

    public static Seat computer(AiLevel level) {
        return new Seat(null, "Computer · " + level.label(), 6, level);
    }

    public boolean isComputer() {
        return level != null;
    }

    public boolean isPlayer(String id) {
        return playerId != null && playerId.equals(id);
    }
}
