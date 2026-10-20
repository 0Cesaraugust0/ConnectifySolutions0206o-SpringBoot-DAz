package com.connectify.service;

import com.connectify.entity.Event;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DemonstrationEventPolicyTest {
    @Test
    void recognizesEachMarketplaceDemonstrationRule() {
        Event numbered = event(12L, "real@example.test", "Evento normal");
        Event account = event(22L, "ORG@Connectify.com", "Evento normal");
        Event legacyAccount = event(23L, "organizador.demo@connectify.local", "Evento normal");
        Event described = event(24L, "real@example.test", "Propuesta ficticia de demostración: programa referencial.");

        assertThat(DemonstrationEventPolicy.isDemonstration(numbered)).isTrue();
        assertThat(DemonstrationEventPolicy.isDemonstration(account)).isTrue();
        assertThat(DemonstrationEventPolicy.isDemonstration(legacyAccount)).isTrue();
        assertThat(DemonstrationEventPolicy.isDemonstration(described)).isTrue();
    }

    @Test
    void leavesRealOrganizerEventsPurchasable() {
        Event real = event(26L, "organizer@example.test", "Un evento real");

        assertThat(DemonstrationEventPolicy.isDemonstration(real)).isFalse();
        assertThat(DemonstrationEventPolicy.isDemonstration(null)).isFalse();
    }

    private Event event(Long id, String email, String description) {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", id);
        event.setOrganizerEmail(email);
        event.setDescription(description);
        return event;
    }
}
