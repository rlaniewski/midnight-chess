package com.midnightchess.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** A connected person. Identity lives in the browser tab's sessionStorage token. */
public final class Player {

    public enum Status {
        IDLE("In lobby"), SEEKING("Seeking a game"), PLAYING("Playing");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String id = UUID.randomUUID().toString();
    private final String token;
    private final int colorIndex;
    private final AtomicInteger connections = new AtomicInteger();
    private volatile String name;
    private volatile Status status = Status.IDLE;

    Player(String token, String name, int colorIndex) {
        this.token = token;
        this.name = name;
        this.colorIndex = colorIndex;
    }

    public String getId() {
        return id;
    }

    String getToken() {
        return token;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public boolean isOnline() {
        return connections.get() > 0;
    }

    int connect() {
        return connections.incrementAndGet();
    }

    int disconnect() {
        return connections.decrementAndGet();
    }
}
