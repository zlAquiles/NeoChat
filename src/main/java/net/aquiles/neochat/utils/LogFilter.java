package net.aquiles.neochat.utils;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public class LogFilter extends AbstractFilter {
    @Override
    public Result filter(LogEvent event) {
        if (event != null && event.getMessage() != null) {
            String message = event.getMessage().getFormattedMessage();
            if (message != null && message.contains("issued server command: /neochat viewinv")) {
                return Result.DENY;
            }
        }
        return Result.NEUTRAL;
    }
}