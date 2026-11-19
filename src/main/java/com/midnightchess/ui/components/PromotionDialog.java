package com.midnightchess.ui.components;

import com.midnightchess.engine.Piece;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.function.IntConsumer;

public class PromotionDialog extends Dialog {

    public PromotionDialog(boolean white, IntConsumer onPick) {
        setHeaderTitle("Promote pawn to…");
        addClassName("promotion-dialog");
        Div row = new Div();
        row.addClassName("promotion-row");
        for (int type : new int[]{Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT}) {
            Span glyph = new Span(Piece.glyph(white ? type : -type));
            glyph.addClassNames("piece", white ? "pw" : "pb");
            Button b = new Button(glyph, e -> {
                close();
                onPick.accept(type);
            });
            b.addClassName("promotion-choice");
            b.setTooltipText(Piece.name(type));
            row.add(b);
        }
        add(row);
    }
}
