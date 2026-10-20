package com.connectify.service;

import com.connectify.entity.Event;

import java.util.Locale;

public final class DemonstrationEventPolicy {
    private static final String DEMO_ORGANIZER_EMAIL = "org@connectify.com";

    private DemonstrationEventPolicy() {
    }

    public static boolean isDemonstration(Event event) {
        if (event == null) return false;
        Long eventId = event.getId();
        if (eventId != null && eventId >= 1 && eventId <= 12) return true;
        String email = event.getOrganizerEmail() == null ? ""
                : event.getOrganizerEmail().trim().toLowerCase(Locale.ROOT);
        return DEMO_ORGANIZER_EMAIL.equals(email)
                || email.endsWith("@connectify.local")
                || (event.getDescription() != null
                && event.getDescription().contains("Propuesta ficticia de demostración"));
    }

    public static void requirePurchasable(Event event) {
        if (isDemonstration(event)) {
            throw new IllegalArgumentException("Este evento es demostrativo y no admite compras.");
        }
    }
}
