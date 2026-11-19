package com.midnightchess.service;

import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Tiny thread-safe broadcaster used by the services to push updates to UIs. */
public final class Listeners<T> {
    private static final Logger log = LoggerFactory.getLogger(Listeners.class);
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public Registration add(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void fire(T value) {
        for (Consumer<T> l : listeners) {
            try {
                l.accept(value);
            } catch (RuntimeException e) {
                log.debug("Listener failed", e);
            }
        }
    }
}
