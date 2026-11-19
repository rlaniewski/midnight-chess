package com.midnightchess.ui.components;

import com.midnightchess.engine.Board;
import com.midnightchess.engine.Material;
import com.midnightchess.engine.Piece;
import com.midnightchess.service.Seat;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Name plate shown above/below the board with captured pieces and turn indicator. */
public class PlayerBar extends Div {
    private final Avatar avatar = new Avatar();
    private final Span name = new Span();
    private final Span detail = new Span();
    private final Div captured = new Div();
    private final Span turn = new Span("to move");

    public PlayerBar() {
        addClassName("player-bar");
        name.addClassName("pb-name");
        detail.addClassName("pb-detail");
        captured.addClassName("pb-captured");
        turn.addClassName("pb-turn");
        Div info = new Div(name, detail);
        info.addClassName("pb-info");
        add(avatar, info, captured, turn);
    }

    public void update(Seat seat, boolean seatIsWhite, boolean isYou, boolean toMove, Board board) {
        avatar.setName(seat.name());
        avatar.setColorIndex(seat.colorIndex());
        if (seat.isComputer()) {
            avatar.setAbbreviation("AI");
        } else {
            avatar.setAbbreviation(null);
        }
        name.setText(seat.isComputer() ? "Computer" : seat.name());
        String side = seatIsWhite ? "White" : "Black";
        detail.setText(seat.isComputer()
                ? side + " · " + seat.level().label() + " level"
                : side + (isYou ? " · you" : ""));
        setClassName("player-bar" + (toMove ? " active" : "") + (isYou ? " you" : ""));

        // Pieces this seat has taken = opponent pieces missing from the board.
        captured.removeAll();
        int[] missing = Material.missing(board, !seatIsWhite);
        for (int type : new int[]{Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT, Piece.PAWN}) {
            for (int i = 0; i < missing[type]; i++) {
                Span g = new Span(Piece.glyph(seatIsWhite ? -type : type));
                g.addClassNames("cap", seatIsWhite ? "pb" : "pw");
                captured.add(g);
            }
        }
        int balance = Material.balance(board) * (seatIsWhite ? 1 : -1);
        if (balance > 0) {
            Span adv = new Span("+" + balance);
            adv.addClassName("pb-advantage");
            captured.add(adv);
        }
    }
}
