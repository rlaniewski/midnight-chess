package com.midnightchess.service;

import com.vaadin.flow.shared.Registration;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** In-memory chat rooms: the lobby plus one room per game. */
@Service
public class ChatService {
    public static final String LOBBY = "lobby";
    private static final int HISTORY = 150;
    private static final int MAX_LENGTH = 500;

    public record Message(String authorId, String author, int colorIndex, String text, Instant time, boolean system) {
    }

    private final Map<String, Deque<Message>> history = new ConcurrentHashMap<>();
    private final Map<String, Listeners<Message>> listeners = new ConcurrentHashMap<>();

    public static String gameRoom(String gameId) {
        return "game:" + gameId;
    }

    public void post(String room, Player author, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String t = text.strip();
        if (t.length() > MAX_LENGTH) {
            t = t.substring(0, MAX_LENGTH) + "…";
        }
        publish(room, new Message(author.getId(), author.getName(), author.getColorIndex(), t, Instant.now(), false));
    }

    public void system(String room, String text) {
        publish(room, new Message(null, "Arbiter", 0, text, Instant.now(), true));
    }

    private void publish(String room, Message m) {
        Deque<Message> h = history.computeIfAbsent(room, k -> new ArrayDeque<>());
        synchronized (h) {
            h.addLast(m);
            while (h.size() > HISTORY) {
                h.removeFirst();
            }
        }
        Listeners<Message> l = listeners.get(room);
        if (l != null) {
            l.fire(m);
        }
    }

    public List<Message> history(String room) {
        Deque<Message> h = history.get(room);
        if (h == null) {
            return List.of();
        }
        synchronized (h) {
            return List.copyOf(h);
        }
    }

    public Registration subscribe(String room, Consumer<Message> listener) {
        return listeners.computeIfAbsent(room, k -> new Listeners<>()).add(listener);
    }
}
