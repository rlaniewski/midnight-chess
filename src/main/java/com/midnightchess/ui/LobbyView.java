package com.midnightchess.ui;

import com.midnightchess.engine.AiLevel;
import com.midnightchess.service.ChatService;
import com.midnightchess.service.GameService;
import com.midnightchess.service.GameService.ColorChoice;
import com.midnightchess.service.GameSession;
import com.midnightchess.service.Player;
import com.midnightchess.service.PlayerRegistry;
import com.midnightchess.ui.components.ChatPanel;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Lobby · Midnight Chess")
public class LobbyView extends Div implements PlayerAware {
    private final GameService games;
    private final PlayerRegistry registry;

    private final Select<AiLevel> level = new Select<>();
    private final RadioButtonGroup<ColorChoice> color = new RadioButtonGroup<>();
    private final Button playComputer = new Button("Play the computer", VaadinIcon.DESKTOP.create());
    private final Button seekButton = new Button("Find an opponent", VaadinIcon.BOLT.create());
    private final Div seekStatus = new Div();
    private final Div playerList = new Div();
    private final Div gameList = new Div();
    private final Span playersCount = new Span();
    private final Span gamesCount = new Span();
    private final ChatPanel chatPanel;
    private final List<Registration> registrations = new ArrayList<>();

    private Player player;

    public LobbyView(GameService games, PlayerRegistry registry, ChatService chat) {
        this.games = games;
        this.registry = registry;
        addClassName("lobby-page");

        chatPanel = new ChatPanel(chat, ChatService.LOBBY, "Lobby chat",
                List.of("👋 Hi all!", "Anyone up for a game?", "♟️", "🔥"));
        chatPanel.addClassName("lobby-chat");

        Div main = new Div(hero(), playGrid(), listsGrid());
        main.addClassName("lobby-main");
        add(main, chatPanel);
        setEnabledActions(false);
    }

    // ------------------------------------------------------------------ layout

    private Component hero() {
        H1 title = new H1("Ready for a game?");
        Paragraph sub = new Paragraph("Challenge someone in the lobby, jump into a quick match, or sharpen your skills against the computer.");
        Div text = new Div(title, sub);
        text.addClassName("hero-text");
        Span pieces = new Span("♚︎♛︎♞︎");
        pieces.addClassName("hero-pieces");
        Div hero = new Div(text, pieces);
        hero.addClassName("hero");
        return hero;
    }

    private Component playGrid() {
        level.setLabel("Difficulty");
        level.setItems(AiLevel.values());
        level.setItemLabelGenerator(l -> l.label() + " — " + l.description());
        level.setValue(AiLevel.CASUAL);
        level.setWidthFull();

        color.setLabel("Your colour");
        color.setItems(ColorChoice.values());
        color.setItemLabelGenerator(c -> switch (c) {
            case WHITE -> "♔︎ White";
            case RANDOM -> "⚄ Random";
            case BLACK -> "♚︎ Black";
        });
        color.setValue(ColorChoice.WHITE);
        color.addClassName("color-choice");

        playComputer.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        playComputer.setWidthFull();
        playComputer.addClickListener(e -> {
            GameSession g = games.playComputer(player, color.getValue(), level.getValue());
            UI.getCurrent().navigate(GameView.class, new RouteParameters("gameId", g.id()));
        });
        Div computer = card("vs Computer", "Four levels from friendly to fierce.", VaadinIcon.DESKTOP,
                level, color, playComputer);
        computer.addClassName("card-computer");

        seekButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        seekButton.setWidthFull();
        seekButton.addClickListener(e -> {
            if (games.isSeeking(player)) {
                games.cancelSeek(player);
            } else {
                games.seek(player);
            }
            refresh();
        });
        seekStatus.addClassName("seek-status");
        Div quick = card("Quick match", "Get paired with the next player who's looking for a game.", VaadinIcon.BOLT,
                seekStatus, seekButton);
        quick.addClassName("card-quick");

        Div grid = new Div(computer, quick);
        grid.addClassName("lobby-grid");
        return grid;
    }

    private Component listsGrid() {
        playerList.addClassName("list");
        gameList.addClassName("list");
        Div players = card("Players online", null, VaadinIcon.USERS, playerList);
        players.getChildren().findFirst().ifPresent(h -> ((Div) h).add(playersCount));
        playersCount.addClassName("count-pill");
        Div live = card("Live games", null, VaadinIcon.EYE, gameList);
        live.getChildren().findFirst().ifPresent(h -> ((Div) h).add(gamesCount));
        gamesCount.addClassName("count-pill");
        Div grid = new Div(players, live);
        grid.addClassName("lobby-grid");
        return grid;
    }

    static Div card(String title, String subtitle, VaadinIcon icon, Component... content) {
        Span ic = new Span(icon.create());
        ic.addClassName("card-icon");
        Span t = new Span(title);
        t.addClassName("card-title");
        Div header = new Div(ic, t);
        header.addClassName("card-header");
        Div card = new Div(header);
        card.addClassName("card");
        if (subtitle != null) {
            Paragraph p = new Paragraph(subtitle);
            p.addClassName("card-subtitle");
            card.add(p);
        }
        Div body = new Div(content);
        body.addClassName("card-body");
        card.add(body);
        return card;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        registrations.add(registry.onChange(() -> ui.access(this::refresh)));
        registrations.add(games.onGamesChanged(() -> ui.access(this::refresh)));
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent event) {
        registrations.forEach(Registration::remove);
        registrations.clear();
        super.onDetach(event);
    }

    @Override
    public void playerReady(Player p) {
        this.player = p;
        setEnabledActions(true);
        refresh();
    }

    private void setEnabledActions(boolean enabled) {
        playComputer.setEnabled(enabled);
        seekButton.setEnabled(enabled);
    }

    // ------------------------------------------------------------------ rendering

    private void refresh() {
        List<Player> online = registry.online();
        chatPanel.setSubtitle(online.size() + (online.size() == 1 ? " player here" : " players here"));
        playersCount.setText(String.valueOf(online.size()));
        renderPlayers(online);
        renderGames();
        renderSeek();
    }

    private void renderSeek() {
        boolean seeking = player != null && games.isSeeking(player);
        seekStatus.removeAll();
        if (seeking) {
            ProgressBar bar = new ProgressBar();
            bar.setIndeterminate(true);
            seekStatus.add(new Span("Searching for an opponent… open a second tab to play yourself!"), bar);
            seekButton.setText("Cancel search");
            seekButton.setIcon(VaadinIcon.CLOSE_SMALL.create());
            seekButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
            seekButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        } else {
            int waiting = games.seekerCount();
            seekStatus.add(new Span(waiting > 0 ? "Someone is waiting for a game right now!" : "Nobody is waiting at the moment."));
            seekButton.setText("Find an opponent");
            seekButton.setIcon(VaadinIcon.BOLT.create());
            seekButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
            seekButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }
    }

    private void renderPlayers(List<Player> online) {
        playerList.removeAll();
        if (online.isEmpty()) {
            playerList.add(emptyRow("Nobody here yet."));
            return;
        }
        for (Player p : online) {
            Avatar avatar = new Avatar(p.getName());
            avatar.setColorIndex(p.getColorIndex());
            Span name = new Span(p.getName() + (p == player ? " (you)" : ""));
            name.addClassName("row-title");
            Span status = new Span(p.getStatus().label());
            status.getElement().getThemeList().add("badge pill small");
            switch (p.getStatus()) {
                case PLAYING -> status.getElement().getThemeList().add("contrast");
                case SEEKING -> status.getElement().getThemeList().add("success");
                default -> { }
            }
            Div text = new Div(name, status);
            text.addClassName("row-text");
            Div row = new Div(avatar, text);
            row.addClassName("row");
            if (player != null && p != player) {
                Button challenge = new Button("Challenge", VaadinIcon.SWORD.create(), e -> {
                    if (games.challenge(player, p, ColorChoice.RANDOM) != null) {
                        MainLayout.toast("Challenge sent to " + p.getName() + ".");
                    }
                });
                challenge.addThemeVariants(ButtonVariant.LUMO_SMALL);
                challenge.setEnabled(p.getStatus() != Player.Status.PLAYING);
                row.add(challenge);
            }
            playerList.add(row);
        }
    }

    private void renderGames() {
        gameList.removeAll();
        List<GameSession> live = games.liveGames();
        gamesCount.setText(String.valueOf(live.size()));
        if (live.isEmpty()) {
            gameList.add(emptyRow("No games in progress."));
            return;
        }
        for (GameSession g : live) {
            Span title = new Span(g.white().name() + "  vs  " + g.black().name());
            title.addClassName("row-title");
            int ply = g.ply();
            Span detail = new Span(ply == 0 ? "Just started" : "Move " + (ply / 2 + 1));
            detail.addClassName("row-detail");
            Div text = new Div(title, detail);
            text.addClassName("row-text");
            Span icon = new Span("♟︎");
            icon.addClassName("row-glyph");
            boolean mine = player != null && g.isParticipant(player.getId());
            Button open = new Button(mine ? "Resume" : "Watch", e ->
                    UI.getCurrent().navigate(GameView.class, new RouteParameters("gameId", g.id())));
            open.addThemeVariants(ButtonVariant.LUMO_SMALL, mine ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
            Div row = new Div(icon, text, open);
            row.addClassName("row");
            gameList.add(row);
        }
    }

    private static Component emptyRow(String text) {
        Div d = new Div(new Span(text));
        d.addClassName("row-empty");
        return d;
    }
}
