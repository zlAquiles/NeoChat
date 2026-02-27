package net.aquiles.neochat.utils;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class ConsoleFilter implements Filter {
    @Override
    public boolean isLoggable(LogRecord record) {
        String message = record.getMessage();
        if (message != null && message.contains("issued server command: /neochat viewinv")) {
            return false;
        }
        return true;
    }
}