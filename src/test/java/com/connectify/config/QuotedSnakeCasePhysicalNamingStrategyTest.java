package com.connectify.config;

import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuotedSnakeCasePhysicalNamingStrategyTest {

    private final QuotedSnakeCasePhysicalNamingStrategy strategy =
            new QuotedSnakeCasePhysicalNamingStrategy();

    @Test
    void convertsQuotedCamelCaseColumnsWithoutDroppingQuotes() {
        Identifier result = strategy.toPhysicalColumnName(Identifier.toIdentifier("actorEmail", true), null);

        assertThat(result.getText()).isEqualTo("actor_email");
        assertThat(result.isQuoted()).isTrue();
    }
}
