package de.julith.opencrm.shared.web;

import java.util.List;

/** Antwort-Envelope für Listen-Endpunkte: items plus nextCursor (null = Ende). */
public record PageEnvelope<T>(List<T> items, String nextCursor) {

    public static <T> PageEnvelope<T> of(List<T> items, int requestedLimit, java.util.function.Function<T, String> cursorOf) {
        if (items.size() > requestedLimit) {
            List<T> page = items.subList(0, requestedLimit);
            return new PageEnvelope<>(List.copyOf(page), cursorOf.apply(page.getLast()));
        }
        return new PageEnvelope<>(items, null);
    }
}
