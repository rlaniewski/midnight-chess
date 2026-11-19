package com.midnightchess.ui;

import com.midnightchess.service.Player;

/** Views implementing this are told once the tab's player identity is known. */
public interface PlayerAware {
    void playerReady(Player player);
}
