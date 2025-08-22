package org.apache.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CollectLogger {

    private Logger logger;

    private CollectLogger() {}

    // Holder idiom per garantire thread-safety senza synchronized
    private static class Holder {
        private static final CollectLogger INSTANCE = new CollectLogger();
    }

    public static CollectLogger getInstance() {
        return Holder.INSTANCE;
    }

    public Logger getLogger() {
        if (logger == null) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("logging.properties")) {
                if (input != null) {
                    LogManager.getLogManager().readConfiguration(input);
                }
                this.logger = Logger.getLogger(CollectLogger.class.getSimpleName());
            } catch (IOException e) {
                System.err.println("Could not load logging.properties: " + e.getMessage());
                this.logger = Logger.getLogger(CollectLogger.class.getSimpleName());
            }
        }
        return this.logger;
    }



    public void info(String message, Object... args) {
        Logger log = getLogger();
        if (log.isLoggable(Level.INFO)) {
            log.log(Level.INFO, () -> String.format(message, args));
        }
    }



    public void error(String message, Throwable t, Object... args) {
        Logger log = getLogger();
        if (log.isLoggable(Level.SEVERE)) {
            log.log(Level.SEVERE, String.format(message, args), t);
        }
    }
}

