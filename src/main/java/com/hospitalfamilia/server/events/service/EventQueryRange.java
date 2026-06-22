package com.hospitalfamilia.server.events.service;

import com.hospitalfamilia.server.events.exception.EventException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public record EventQueryRange(Instant from, Instant to, boolean explicit) {

    static final int DEFAULT_UPCOMING_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;

    public static EventQueryRange resolve(String fromValue, String toValue) {
        return resolve(fromValue, toValue, Clock.systemUTC());
    }

    static EventQueryRange resolve(String fromValue, String toValue, Clock clock) {
        boolean hasFrom = hasText(fromValue);
        boolean hasTo = hasText(toValue);

        if (!hasFrom && !hasTo) {
            Instant from = clock.instant();
            return new EventQueryRange(from, from.plus(Duration.ofDays(DEFAULT_UPCOMING_DAYS)), false);
        }
        if (!hasFrom || !hasTo) {
            throw new EventException("Los parametros from y to deben enviarse juntos");
        }

        Instant from = parseInstant("from", fromValue);
        Instant to = parseInstant("to", toValue);
        if (!from.isBefore(to)) {
            throw new EventException("El parametro from debe ser anterior a to");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new EventException("El rango de eventos no puede superar 366 dias");
        }
        return new EventQueryRange(from, to, true);
    }

    private static Instant parseInstant(String parameter, String value) {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new EventException("El parametro " + parameter + " debe usar formato ISO-8601");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
