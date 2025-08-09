package org.apache.logging;

import com.github.javaparser.quality.NotNull;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ColorLogger extends Formatter {
    private static final String WHITE = "\u001B[97m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String LOGGER_NAME = CollectLogger.class.getSimpleName();
    @Override
    public String format(@NotNull LogRecord record) {
        String color= switch (record.getLevel().getName()){
            case "SEVERE" -> RED;
            case "WARNING" -> YELLOW;
            case "INFO" -> GREEN;
            case "CONFIG" -> BLUE;
            default -> WHITE;
        };
        return WHITE +String.format("%s[%s%s%s]%s%n",LOGGER_NAME,color,record.getLevel(),WHITE,record.getMessage());
    }
}
