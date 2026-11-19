package com.midnightchess.ui;

import com.midnightchess.engine.Board;
import com.midnightchess.engine.Move;
import com.midnightchess.service.ChatService;
import com.midnightchess.service.GameService;
import com.midnightchess.service.GameSession;
import com.midnightchess.service.Player;
import com.midnightchess.service.Seat;
import com.midnightchess.ui.components.ChatPanel;
import com.midnightchess.ui.components.ChessBoard;
import com.midnightchess.ui.components.PlayerBar;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;

import java.util.List;
import java.util.Optional;

@Route(value = "game/:gameId", layout = MainLayout.class)
public class GameView extends Div implements BeforeEnterObserver, PlayerAware, HasDynamicTitle {
    private final GameService games;
    private final ChatService chat;

    private final ChessBoard board = new ChessBoard();
    private final PlayerBar topBar = new PlayerBar();
    private final PlayerBar bottomBar = new PlayerBar();
    private final Span statusTitle = new Span();
    private final Span statusDetail = new Span();
    private final Span roleBadge = new Span();
    private final Div drawBanner = new Div();
    private final Div moveList = new Div();
    private final Button resign = new Button("Resign", VaadinIcon.FLAG.create());
    private final Button offerDraw = new Button("Offer draw", VaadinIcon.HANDSHAKE.create());
    private final Button takeback = new Button("Take back", VaadinIcon.ARROW_BACKWARD.create());
    private final Button rematch = new Button("Rematch", VaadinIcon.REFRESH.create());
    private final Button flip = new Button(VaadinIcon.EXCHANGE.create());

    private GameSession session;
    private Player player;
    private Registration sessionRegistration;
    private boolean orientationChosen;
    private int lastPly = -1;
    private int overlayDismissedAtPly = -1;

    public GameView(GameService games, ChatService chat) {
        this.games = games;
        this.chat = chat;
        addClassName("game-page");

        board.setMoveHandler(this::onMove);
        resign.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        resign.addClickListener(e -> confirmResign());
        offerDraw.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        offerDraw.addClickListener(e -> games.offerDraw(session, player));
        takeback.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        takeback.addClickListener(e -> games.takeback(session, player));
        rematch.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        rematch.addClickListener(e -> doRematch());
        flip.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        flip.setTooltipText("Flip board");
        flip.setAriaLabel("Flip board");
        flip.addClickListener(e -> {
            board.setFlipped(!board.isFlipped());
            refresh();
        });
    }

    // ------------------------------------------------------------------ navigation & lifecycle

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getRouteParameters().get("gameId").orElse("");
        unsubscribe();
        removeAll();
        session = games.find(id).orElse(null);
        orientationChosen = false;
        lastPly = -1;
        overlayDismissedAtPly = -1;
        board.hideOverlay();
        if (session == null) {
            add(notFound());
            return;
        }
        build();
        if (isAttached()) {
            getUI().ifPresent(this::subscribe);
        }
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        if (session != null && sessionRegistration == null) {
            subscribe(event.getUI());
            refresh();
        }
    }

    @Override
    protected void onDetach(DetachEvent event) {
        unsubscribe();
        super.onDetach(event);
    }

    private void subscribe(UI ui) {
        sessionRegistration = session.subscribe(s -> ui.access(this::refresh));
    }

    private void unsubscribe() {
        if (sessionRegistration != null) {
            sessionRegistration.remove();
            sessionRegistration = null;
        }
    }

    @Override
    public void playerReady(Player p) {
        this.player = p;
        refresh();
    }

    @Override
    public String getPageTitle() {
        if (session == null) {
            return "Game not found · Midnight Chess";
        }
        return session.white().name() + " vs " + session.black().name() + " · Midnight Chess";
    }

    // ------------------------------------------------------------------ layout

    private Component notFound() {
        Div box = new Div();
        box.addClassName("not-found");
        Span glyph = new Span("♚︎");
        glyph.addClassName("not-found-glyph");
        box.add(glyph, new Span("This game doesn't exist (or the server restarted)."),
                new RouterLink("Back to the lobby", LobbyView.class));
        return box;
    }

    private void build() {
        Div boardCol = new Div(topBar, board, bottomBar);
        boardCol.addClassName("board-col");

        statusTitle.addClassName("status-title");
        statusDetail.addClassName("status-detail");
        roleBadge.addClassName("role-badge");
        Div statusText = new Div(statusTitle, statusDetail);
        statusText.addClassName("status-text");
        Div statusHead = new Div(statusText, roleBadge);
        statusHead.addClassName("status-head");

        drawBanner.addClassName("draw-banner");
        Div actions = new Div(rematch, takeback, offerDraw, resign, MainLayout.spacer(), flip);
        actions.addClassName("game-actions");
        Div statusCard = new Div(statusHead, drawBanner, actions);
        statusCard.addClassNames("card", "status-card");

        Span movesTitle = new Span("Moves");
        movesTitle.addClassName("card-title");
        Div movesHeader = new Div(movesTitle);
        movesHeader.addClassName("card-header");
        moveList.addClassName("move-list");
        Div movesCard = new Div(movesHeader, moveList);
        movesCard.addClassNames("card", "moves-card");

        ChatPanel chatPanel = new ChatPanel(chat, ChatService.gameRoom(session.id()), "Game chat",
                List.of("Good luck! 🍀", "Nice move 👏", "Oops 😅", "GG 🤝"));
        chatPanel.setSubtitle("Players & spectators");
        chatPanel.addClassName("game-chat");

        Div side = new Div(statusCard, movesCard, chatPanel);
        side.addClassName("side-col");
        add(boardCol, side);
    }

    // ------------------------------------------------------------------ actions

    private void onMove(Move m) {
        if (session != null && player != null && !games.move(session, player, m)) {
            refresh();
        }
    }

    private void confirmResign() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Resign this game?");
        d.add(new Paragraph("Your opponent will be awarded the win."));
        Button cancel = new Button("Keep playing", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button ok = new Button("Resign", e -> {
            d.close();
            games.resign(session, player);
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        d.getFooter().add(cancel, ok);
        d.open();
    }

    private void doRematch() {
        Optional<GameSession> next = games.rematch(session, player);
        if (next.isPresent()) {
            UI.getCurrent().navigate(GameView.class, new RouteParameters("gameId", next.get().id()));
        } else {
            MainLayout.toast("Rematch offer sent to " + session.opponentOf(player.getId()).name() + ".");
        }
    }

    // ------------------------------------------------------------------ rendering

    private void refresh() {
        if (session == null) {
            return;
        }
        GameSession.Snapshot snap = session.snapshot();
        Board pos = snap.board();
        Optional<Boolean> mine = player == null ? Optional.empty() : session.colorOf(player.getId());
        boolean participant = mine.isPresent();
        if (!orientationChosen && player != null) {
            board.setFlipped(mine.map(white -> !white).orElse(false));
            orientationChosen = true;
        }
        boolean myTurn = participant && !snap.isOver() && !snap.aiThinking() && pos.whiteToMove() == mine.get();
        board.setPosition(pos, snap.lastMove(), myTurn);

        if (lastPly >= 0 && snap.ply() > lastPly && !snap.sanMoves().isEmpty()) {
            board.playSound(snap.sanMoves().get(snap.ply() - 1).contains("x"));
        }
        lastPly = snap.ply();

        // Player bars
        boolean flipped = board.isFlipped();
        Seat top = flipped ? session.white() : session.black();
        Seat bottom = flipped ? session.black() : session.white();
        boolean ongoing = !snap.isOver();
        topBar.update(top, flipped, isYou(top), ongoing && pos.whiteToMove() == flipped, pos);
        bottomBar.update(bottom, !flipped, isYou(bottom), ongoing && pos.whiteToMove() != flipped, pos);

        renderStatus(snap, mine, myTurn);
        renderMoves(snap);
        renderActions(snap, participant, myTurn);
        renderOverlay(snap, participant);
    }

    private boolean isYou(Seat seat) {
        return player != null && seat.isPlayer(player.getId());
    }

    private void renderStatus(GameSession.Snapshot snap, Optional<Boolean> mine, boolean myTurn) {
        Board pos = snap.board();
        roleBadge.setText(mine.map(w -> w ? "Playing White" : "Playing Black").orElse("Spectating"));
        roleBadge.getElement().setAttribute("data-role", mine.isPresent() ? "player" : "spectator");
        if (snap.isOver()) {
            statusTitle.setText(snap.resultText());
            statusDetail.setText(capitalize(snap.reasonText()) + " · " + snap.result().score());
            statusTitle.getElement().setAttribute("data-state", "over");
            return;
        }
        Seat toMove = pos.whiteToMove() ? session.white() : session.black();
        if (snap.aiThinking()) {
            statusTitle.setText("Computer is thinking…");
            statusTitle.getElement().setAttribute("data-state", "thinking");
        } else if (myTurn) {
            statusTitle.setText("Your move");
            statusTitle.getElement().setAttribute("data-state", "your-turn");
        } else if (mine.isPresent()) {
            statusTitle.setText("Waiting for " + toMove.name() + "…");
            statusTitle.getElement().setAttribute("data-state", "waiting");
        } else {
            statusTitle.setText((pos.whiteToMove() ? "White" : "Black") + " to move");
            statusTitle.getElement().setAttribute("data-state", "waiting");
        }
        String detail = "Move " + pos.fullmoveNumber();
        if (pos.inCheck()) {
            detail = "Check! · " + detail;
        }
        statusDetail.setText(detail);
    }

    private void renderMoves(GameSession.Snapshot snap) {
        moveList.removeAll();
        List<String> san = snap.sanMoves();
        if (san.isEmpty()) {
            Div empty = new Div(new Span("No moves yet"));
            empty.addClassName("move-empty");
            moveList.add(empty);
            return;
        }
        for (int i = 0; i < san.size(); i += 2) {
            Span no = new Span((i / 2 + 1) + ".");
            no.addClassName("mv-no");
            Span w = new Span(san.get(i));
            w.addClassName("mv");
            if (i == san.size() - 1) {
                w.addClassName("current");
            }
            Span b = new Span(i + 1 < san.size() ? san.get(i + 1) : "");
            b.addClassName("mv");
            if (i + 1 == san.size() - 1) {
                b.addClassName("current");
            }
            Div row = new Div(no, w, b);
            row.addClassName("mv-row");
            moveList.add(row);
        }
        if (snap.isOver()) {
            Div result = new Div(new Span(snap.result().score()));
            result.addClassName("mv-result");
            moveList.add(result);
        }
        moveList.getElement().executeJs("const el = this; requestAnimationFrame(() => el.scrollTop = el.scrollHeight);");
    }

    private void renderActions(GameSession.Snapshot snap, boolean participant, boolean myTurn) {
        boolean ongoing = !snap.isOver();
        boolean vsComputer = session.hasComputer();
        resign.setVisible(participant && ongoing);
        offerDraw.setVisible(participant && ongoing && !vsComputer);
        boolean iOffered = player != null && player.getId().equals(snap.drawOfferBy());
        offerDraw.setEnabled(snap.drawOfferBy() == null);
        offerDraw.setText(iOffered ? "Draw offered" : "Offer draw");
        takeback.setVisible(participant && ongoing && vsComputer);
        takeback.setEnabled(myTurn && snap.ply() >= 2);
        rematch.setVisible(participant && !ongoing);
        rematch.setText(vsComputer ? "New game" : "Rematch");

        drawBanner.removeAll();
        boolean incomingDraw = participant && ongoing && snap.drawOfferBy() != null && !iOffered;
        drawBanner.setVisible(incomingDraw);
        if (incomingDraw) {
            Span text = new Span("🤝 " + session.opponentOf(player.getId()).name() + " offers a draw");
            Button accept = new Button("Accept", e -> games.respondToDraw(session, player, true));
            accept.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            Button decline = new Button("Decline", e -> games.respondToDraw(session, player, false));
            decline.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            drawBanner.add(text, MainLayout.spacer(), decline, accept);
        }
    }

    private void renderOverlay(GameSession.Snapshot snap, boolean participant) {
        if (!snap.isOver() || overlayDismissedAtPly == snap.ply()) {
            board.hideOverlay();
            return;
        }
        Button close = new Button("View board", e -> {
            overlayDismissedAtPly = snap.ply();
            board.hideOverlay();
        });
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button lobby = new Button("Lobby", e -> UI.getCurrent().navigate(LobbyView.class));
        lobby.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        String headline = headline(snap);
        String sub = capitalize(snap.reasonText()) + " · " + snap.result().score();
        if (participant) {
            Button again = new Button(session.hasComputer() ? "New game" : "Rematch", e -> doRematch());
            again.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            board.showOverlay(headline, sub, close, lobby, again);
        } else {
            board.showOverlay(headline, sub, close, lobby);
        }
    }

    private String headline(GameSession.Snapshot snap) {
        Optional<Boolean> mine = player == null ? Optional.empty() : session.colorOf(player.getId());
        return switch (snap.result()) {
            case DRAW -> "½ Draw";
            case WHITE_WINS, BLACK_WINS -> {
                boolean whiteWon = snap.result() == com.midnightchess.engine.GameResult.WHITE_WINS;
                if (mine.isEmpty()) {
                    yield (whiteWon ? "White" : "Black") + " wins";
                }
                yield mine.get() == whiteWon ? "🏆 You won!" : "You lost";
            }
            case ONGOING -> "";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
