package com.fhaicl.aidevhub.environment;

import java.io.IOException;

final class CommandExecutionException extends IOException {
    enum Kind { START_FAILED, TIMEOUT, INTERRUPTED }
    private final Kind kind;

    CommandExecutionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    Kind kind() { return kind; }
}
