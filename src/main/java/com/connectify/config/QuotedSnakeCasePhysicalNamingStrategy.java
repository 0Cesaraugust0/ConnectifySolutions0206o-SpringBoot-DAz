package com.connectify.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;

/**
 * Keeps PostgreSQL identifiers quoted while applying the snake_case convention
 * used by the existing Connectify schema.
 */
public final class QuotedSnakeCasePhysicalNamingStrategy extends PhysicalNamingStrategySnakeCaseImpl {

    @Override
    protected Identifier quotedIdentifier(Identifier identifier) {
        Identifier snakeCase = unquotedIdentifier(Identifier.toIdentifier(identifier.getText(), false));
        return Identifier.toIdentifier(snakeCase.getText(), true);
    }
}
