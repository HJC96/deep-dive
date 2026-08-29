package dev.deepdive.paymentsystem.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class IdempotencyCreator {

    private IdempotencyCreator() {
    }

    public static String create(Object data) {
        return UUID.nameUUIDFromBytes(data.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
