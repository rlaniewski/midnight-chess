package com.midnightchess.ui;

import com.midnightchess.service.Player;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

/** The player bound to a browser tab (UI instance). */
public final class CurrentPlayer {
    private CurrentPlayer() {
    }

    public static Player get() {
        UI ui = UI.getCurrent();
        return ui == null ? null : get(ui);
    }

    public static Player get(UI ui) {
        return ComponentUtil.getData(ui, Player.class);
    }

    static void set(UI ui, Player player) {
        ComponentUtil.setData(ui, Player.class, player);
    }
}
