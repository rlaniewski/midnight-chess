package com.midnightchess.ui.components;

import com.midnightchess.engine.Board;
import com.midnightchess.engine.Move;
import com.midnightchess.engine.Piece;
import com.midnightchess.engine.Square;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interactive board built from plain divs. Supports click-to-move and drag & drop,
 * legal-move hints, last-move/check highlighting, flipping and a result overlay.
 */
public class ChessBoard extends Div {
    private final Div grid = new Div();
    private final Div overlay = new Div();
    private final Div[] squares = new Div[64];
    private final List<DragSource<Div>> dragSources = new ArrayList<>(64);

    private Board board = Board.initial();
    private Board renderedBoard;
    private Move lastMove;
    private boolean flipped;
    private boolean interactive;
    private int selected = -1;
    private Consumer<Move> moveHandler = m -> {
    };

    public ChessBoard() {
        addClassName("board-frame");
        grid.addClassName("board");
        for (int s = 0; s < 64; s++) {
            final int sq = s;
            Div d = new Div();
            squares[s] = d;
            d.addClickListener(e -> onSquareClicked(sq));

            DragSource<Div> drag = DragSource.create(d);
            drag.setDraggable(false);
            drag.addDragStartListener(e -> {
                if (selected != sq) {
                    selected = sq;
                    updateHighlights();
                }
            });
            dragSources.add(drag);

            DropTarget<Div> drop = DropTarget.create(d);
            drop.addDropListener(e -> {
                int from = e.getDragSourceComponent().map(this::squareOf).orElse(selected);
                if (from >= 0 && from != sq) {
                    tryMove(from, sq);
                }
            });
        }
        overlay.addClassName("board-overlay");
        overlay.setVisible(false);
        add(grid, overlay);
        layoutSquares();
        render();
    }

    public void setMoveHandler(Consumer<Move> handler) {
        this.moveHandler = handler;
    }

    public void setFlipped(boolean flipped) {
        if (this.flipped != flipped) {
            this.flipped = flipped;
            layoutSquares();
            renderedBoard = null;
            render();
        }
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setPosition(Board board, Move lastMove, boolean interactive) {
        this.board = board;
        this.lastMove = lastMove;
        this.interactive = interactive;
        if (selected >= 0 && (!interactive || !isOwnPiece(selected))) {
            selected = -1;
        }
        render();
    }

    public void showOverlay(String title, String subtitle, Component... actions) {
        overlay.removeAll();
        Span t = new Span(title);
        t.addClassName("overlay-title");
        Span s = new Span(subtitle);
        s.addClassName("overlay-subtitle");
        Div row = new Div(actions);
        row.addClassName("overlay-actions");
        overlay.add(t, s, row);
        overlay.setVisible(true);
    }

    public void hideOverlay() {
        overlay.setVisible(false);
    }

    /** Short synthesized move/capture sound via Web Audio (no assets needed). */
    public void playSound(boolean capture) {
        getElement().executeJs("""
                try {
                  const C = window.AudioContext || window.webkitAudioContext; if (!C) return;
                  const ctx = window.__mcAudio || (window.__mcAudio = new C());
                  const t = ctx.currentTime, o = ctx.createOscillator(), g = ctx.createGain();
                  o.type = 'triangle'; o.frequency.setValueAtTime($0 ? 180 : 300, t);
                  o.frequency.exponentialRampToValueAtTime($0 ? 90 : 220, t + 0.12);
                  g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.22, t + 0.008);
                  g.gain.exponentialRampToValueAtTime(0.0001, t + 0.14);
                  o.connect(g).connect(ctx.destination); o.start(t); o.stop(t + 0.15);
                } catch (e) {}
                """, capture);
    }

    // ------------------------------------------------------------------ interaction

    private int squareOf(Component c) {
        for (int i = 0; i < 64; i++) {
            if (squares[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOwnPiece(int s) {
        int p = board.piece(s);
        return p != 0 && (p > 0) == board.whiteToMove();
    }

    private void onSquareClicked(int s) {
        if (!interactive) {
            return;
        }
        if (selected >= 0 && selected != s && !isOwnPiece(s)) {
            tryMove(selected, s);
            return;
        }
        selected = (isOwnPiece(s) && selected != s) ? s : -1;
        updateHighlights();
    }

    private void tryMove(int from, int to) {
        if (!interactive) {
            return;
        }
        List<Move> candidates = board.legalMovesFrom(from).stream().filter(m -> m.to() == to).toList();
        selected = -1;
        if (candidates.isEmpty()) {
            if (isOwnPiece(to)) {
                selected = to;
            }
            updateHighlights();
            return;
        }
        updateHighlights();
        if (candidates.get(0).promotion() != 0) {
            new PromotionDialog(board.whiteToMove(), type -> moveHandler.accept(new Move(from, to, type))).open();
        } else {
            moveHandler.accept(candidates.get(0));
        }
    }

    // ------------------------------------------------------------------ rendering

    private int displayRow(int s) {
        return flipped ? Square.rank(s) : 7 - Square.rank(s);
    }

    private int displayCol(int s) {
        return flipped ? 7 - Square.file(s) : Square.file(s);
    }

    private void layoutSquares() {
        grid.removeAll();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int file = flipped ? 7 - col : col;
                int rank = flipped ? row : 7 - row;
                grid.add(squares[Square.of(file, rank)]);
            }
        }
    }

    private void render() {
        if (renderedBoard != board) {
            renderPieces();
            renderedBoard = board;
        }
        updateHighlights();
    }

    private void renderPieces() {
        for (int s = 0; s < 64; s++) {
            Div d = squares[s];
            d.removeAll();
            int p = board.piece(s);
            if (p != 0) {
                Span piece = new Span(Piece.glyph(p));
                piece.addClassNames("piece", p > 0 ? "pw" : "pb");
                d.add(piece);
            }
            if (displayRow(s) == 7) {
                Span file = new Span(String.valueOf((char) ('a' + Square.file(s))));
                file.addClassNames("coord", "coord-file");
                d.add(file);
            }
            if (displayCol(s) == 0) {
                Span rank = new Span(String.valueOf((char) ('1' + Square.rank(s))));
                rank.addClassNames("coord", "coord-rank");
                d.add(rank);
            }
        }
    }

    private void updateHighlights() {
        Set<Integer> targets = new HashSet<>();
        if (selected >= 0) {
            board.legalMovesFrom(selected).forEach(m -> targets.add(m.to()));
        }
        int checkSquare = board.inCheck() ? board.kingSquare(board.whiteToMove()) : -1;
        for (int s = 0; s < 64; s++) {
            StringBuilder cls = new StringBuilder("sq ").append(Square.isLight(s) ? "light" : "dark");
            if (lastMove != null && (s == lastMove.from() || s == lastMove.to())) {
                cls.append(" last");
            }
            if (s == selected) {
                cls.append(" selected");
            }
            if (targets.contains(s)) {
                boolean capture = board.piece(s) != 0
                        || (s == board.epSquare() && Math.abs(board.piece(selected)) == Piece.PAWN);
                cls.append(capture ? " capture" : " target");
            }
            if (s == checkSquare) {
                cls.append(" check");
            }
            boolean movable = interactive && isOwnPiece(s);
            if (movable) {
                cls.append(" movable");
            }
            squares[s].setClassName(cls.toString());
            dragSources.get(s).setDraggable(movable);
        }
        getElement().setAttribute("data-interactive", interactive ? "true" : "false");
    }
}
