package com.midnightchess.ui;

import com.midnightchess.service.GameService;
import com.midnightchess.service.GameService.ChallengeDeclined;
import com.midnightchess.service.GameService.ChallengeExpired;
import com.midnightchess.service.GameService.ChallengeReceived;
import com.midnightchess.service.GameService.GameStarted;
import com.midnightchess.service.GameService.PlayerEvent;
import com.midnightchess.service.Player;
import com.midnightchess.service.PlayerRegistry;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;

import java.util.UUID;

/**
 * App shell: top bar, per-tab player identity and personal notifications
 * (incoming challenges, game starts).
 * <p>
 * Identity is stored in the tab's sessionStorage, so two tabs of the same
 * browser can be two different players – handy for trying out multiplayer.
 */
public class MainLayout extends AppLayout {
    private static final String TOKEN_KEY = "mc.token";
    private static final String NAME_KEY = "mc.name";

    private final PlayerRegistry registry;
    private final GameService games;
    private final Div userChip = new Div();
    private final Span onlineBadge = new Span();

    private UI ui;
    private Player player;
    private Registration eventsReg;
    private Registration registryReg;

    public MainLayout(PlayerRegistry registry, GameService games) {
        this.registry = registry;
        this.games = games;

        RouterLink brand = new RouterLink();
        brand.setRoute(LobbyView.class);
        brand.addClassName("brand");
        Span mark = new Span("♞︎");
        mark.addClassName("brand-mark");
        Span name = new Span("Midnight Chess");
        name.addClassName("brand-name");
        brand.add(mark, name);

        RouterLink lobby = new RouterLink("Lobby", LobbyView.class);
        lobby.addClassName("nav-link");

        Div spacer = new Div();
        spacer.addClassName("topbar-spacer");

        onlineBadge.addClassName("online-badge");
        userChip.addClassName("user-chip");
        userChip.setVisible(false);

        Div bar = new Div(brand, lobby, spacer, onlineBadge, userChip);
        bar.addClassName("topbar");
        addToNavbar(bar);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        ui = event.getUI();
        ui.getPage().executeJs("""
                window.__mcChime = () => { try {
                  const C = window.AudioContext || window.webkitAudioContext; if (!C) return;
                  const ctx = window.__mcAudio || (window.__mcAudio = new C());
                  [660, 880].forEach((f, i) => {
                    const t = ctx.currentTime + i * 0.12, o = ctx.createOscillator(), g = ctx.createGain();
                    o.type = 'sine'; o.frequency.value = f;
                    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.18, t + 0.01);
                    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.25);
                    o.connect(g).connect(ctx.destination); o.start(t); o.stop(t + 0.26);
                  });
                } catch (e) {} };
                """);
        registryReg = registry.onChange(() -> ui.access(this::updateOnline));
        updateOnline();
        Player known = CurrentPlayer.get(ui);
        if (known != null) {
            bind(known);
        } else {
            identify();
        }
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (registryReg != null) {
            registryReg.remove();
        }
        if (eventsReg != null) {
            eventsReg.remove();
            eventsReg = null;
        }
        if (player != null) {
            registry.disconnected(player);
            player = null;
        }
        super.onDetach(event);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        super.showRouterLayoutContent(content);
        if (player != null && content instanceof PlayerAware aware) {
            aware.playerReady(player);
        }
    }

    private void identify() {
        ui.getPage().executeJs("return (sessionStorage.getItem($0) || '') + '\\n' + (sessionStorage.getItem($1) || '')",
                TOKEN_KEY, NAME_KEY).then(String.class, stored -> {
            String[] parts = stored.split("\n", -1);
            String token = parts[0];
            String name = parts.length > 1 ? parts[1] : "";
            if (token.isBlank()) {
                token = UUID.randomUUID().toString();
                ui.getPage().executeJs("sessionStorage.setItem($0, $1)", TOKEN_KEY, token);
            }
            final String finalToken = token;
            registry.findByToken(token).ifPresentOrElse(this::bind, () -> {
                if (!name.isBlank()) {
                    bind(registry.register(finalToken, name));
                } else {
                    new NameDialog(chosen -> bind(registry.register(finalToken, chosen))).open();
                }
            });
        });
    }

    private void bind(Player p) {
        player = p;
        CurrentPlayer.set(ui, p);
        registry.connected(p);
        ui.getPage().executeJs("sessionStorage.setItem($0, $1)", NAME_KEY, p.getName());
        if (eventsReg != null) {
            eventsReg.remove();
        }
        eventsReg = games.subscribe(p, e -> ui.access(() -> handle(e)));

        Avatar avatar = new Avatar(p.getName());
        avatar.setColorIndex(p.getColorIndex());
        Span name = new Span(p.getName());
        name.addClassName("user-chip-name");
        userChip.removeAll();
        userChip.add(avatar, name);
        userChip.setVisible(true);

        if (getContent() instanceof PlayerAware aware) {
            aware.playerReady(p);
        }
    }

    private void updateOnline() {
        int n = registry.online().size();
        onlineBadge.setText(n + (n == 1 ? " player online" : " players online"));
    }

    private void handle(PlayerEvent event) {
        switch (event) {
            case GameStarted started -> ui.navigate(GameView.class, new RouteParameters("gameId", started.gameId()));
            case ChallengeReceived received -> showChallenge(received.challenge());
            case ChallengeDeclined declined -> toast(declined.challenge().to().getName() + " declined your challenge.");
            case ChallengeExpired expired -> toast(expired.challenge().from().getName() + " is no longer online.");
        }
    }

    private void showChallenge(GameService.Challenge c) {
        Notification n = new Notification();
        n.addClassName("challenge-toast");
        n.setPosition(Notification.Position.TOP_END);
        n.setDuration(45_000);

        Avatar avatar = new Avatar(c.from().getName());
        avatar.setColorIndex(c.from().getColorIndex());
        String color = switch (c.fromColor()) {
            case WHITE -> "you play Black";
            case BLACK -> "you play White";
            case RANDOM -> "random colours";
        };
        Span title = new Span(c.from().getName() + (c.rematch() ? " wants a rematch" : " challenges you"));
        title.addClassName("challenge-title");
        Span detail = new Span(color);
        detail.addClassName("challenge-detail");
        Div text = new Div(title, detail);
        text.addClassName("challenge-text");

        Button accept = new Button("Accept", e -> {
            n.close();
            if (!games.accept(c.id(), player)) {
                toast("That challenge is no longer available.");
            }
        });
        accept.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        Button decline = new Button("Decline", e -> {
            n.close();
            games.decline(c.id(), player);
        });
        decline.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Div actions = new Div(decline, accept);
        actions.addClassName("challenge-actions");

        Div layout = new Div(avatar, text, actions);
        layout.addClassName("challenge-layout");
        n.add(layout);
        n.open();
        ui.getPage().executeJs("window.__mcChime && window.__mcChime()");
    }

    static void toast(String message) {
        Notification n = Notification.show(message, 3500, Notification.Position.BOTTOM_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }

    static Component spacer() {
        Div d = new Div();
        d.addClassName("flex-spacer");
        return d;
    }
}
