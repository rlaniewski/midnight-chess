package com.midnightchess.engine;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    private static long perft(Board b, int depth) {
        if (depth == 0) {
            return 1;
        }
        long n = 0;
        for (Move m : b.legalMoves()) {
            n += perft(b.makeMove(m), depth - 1);
        }
        return n;
    }

    @Test
    void perftStartPosition() {
        Board b = Board.initial();
        assertEquals(20, perft(b, 1));
        assertEquals(400, perft(b, 2));
        assertEquals(8902, perft(b, 3));
        assertEquals(197281, perft(b, 4));
    }

    @Test
    void perftKiwipete() {
        Board b = Board.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertEquals(48, perft(b, 1));
        assertEquals(2039, perft(b, 2));
        assertEquals(97862, perft(b, 3));
    }

    @Test
    void perftEndgameWithEnPassantPins() {
        Board b = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        assertEquals(14, perft(b, 1));
        assertEquals(191, perft(b, 2));
        assertEquals(2812, perft(b, 3));
        assertEquals(43238, perft(b, 4));
    }

    @Test
    void perftPromotionsAndCastling() {
        Board b = Board.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
        assertEquals(6, perft(b, 1));
        assertEquals(264, perft(b, 2));
        assertEquals(9467, perft(b, 3));
    }

    @Test
    void sanAndFoolsMate() {
        ChessGame g = new ChessGame();
        for (String uci : new String[]{"f2f3", "e7e5", "g2g4", "d8h4"}) {
            assertTrue(g.play(Move.parseUci(uci)), uci);
        }
        assertEquals(java.util.List.of("f3", "e5", "g4", "Qh4#"), g.sanMoves());
        assertEquals(GameResult.BLACK_WINS, g.result());
        assertEquals("checkmate", g.reason());
        assertFalse(g.play(Move.parseUci("e2e4")));
    }

    @Test
    void sanDisambiguationAndCastling() {
        Board b = Board.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertEquals("O-O", b.toSan(new Move(4, 6)));
        assertEquals("O-O-O", b.toSan(new Move(4, 2)));
        Board r = Board.fromFen("4k3/8/8/R7/8/8/4K3/R6R w - - 0 1");
        assertEquals("Rad1", r.toSan(Move.parseUci("a1d1")));
        assertEquals("Rhd1", r.toSan(Move.parseUci("h1d1")));
        assertEquals("R1a3", r.toSan(Move.parseUci("a1a3")));
        assertEquals("R5a3", r.toSan(Move.parseUci("a5a3")));
    }

    @Test
    void stalemateAndInsufficientMaterial() {
        ChessGame s = new ChessGame(Board.fromFen("k7/8/1Q6/8/8/8/8/7K w - - 0 1"));
        assertTrue(s.play(Move.parseUci("b6c7")));
        assertEquals(GameResult.DRAW, s.result());
        assertEquals("stalemate", s.reason());
        assertTrue(Board.fromFen("8/8/4k3/8/8/2B5/8/4K3 w - - 0 1").isInsufficientMaterial());
        assertFalse(Board.fromFen("8/8/4k3/8/8/2B5/8/4KN2 w - - 0 1").isInsufficientMaterial());
    }

    @Test
    void aiFindsMateInOne() {
        Board b = Board.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1");
        for (AiLevel level : new AiLevel[]{AiLevel.CASUAL, AiLevel.CLUB, AiLevel.MASTER}) {
            assertEquals("d1d8", new ChessAi(new Random(1)).chooseMove(b, level).uci(), level.name());
        }
    }

    @Test
    void aiTakesHangingQueen() {
        Board b = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p2q/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1");
        // Nothing hangs here for white except ... ensure AI returns a legal move quickly.
        Move m = new ChessAi(new Random(2)).chooseMove(b, AiLevel.CLUB);
        assertTrue(b.legalMoves().contains(m));
        Board hanging = Board.fromFen("4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1");
        assertEquals("d1d5", new ChessAi(new Random(3)).chooseMove(hanging, AiLevel.CLUB).uci());
    }
}
