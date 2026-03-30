package org.example.wavelio.bus;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> subscribers = new ConcurrentHashMap<>();


    public <E> void subscribe(Class<E> eventType, Consumer<E> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }


    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        if (event == null) return;
        CopyOnWriteArrayList<Consumer<?>> list = subscribers.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        List<Consumer<?>> copy = new ArrayList<>(list);
        Platform.runLater(() -> {
            for (Consumer<?> c : copy) {
                ((Consumer<E>) c).accept(event);
            }
        });
    }

    @FunctionalInterface
    public interface Consumer<E> {
        void accept(E event);
    }
}
