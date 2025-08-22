package org.apache.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class CollectLogger {

    private Logger logger;

    private CollectLogger() {}

    private static class Holder {
        private static final CollectLogger INSTANCE = new CollectLogger();
    }

    public static CollectLogger getInstance() {
        return Holder.INSTANCE;
    }

    private Logger getInternalLogger() {
        if (logger == null) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("logging.properties")) {
                if (input != null) {
                    LogManager.getLogManager().readConfiguration(input);
                }
                this.logger = Logger.getLogger(CollectLogger.class.getSimpleName());
            } catch (IOException e) {
                this.logger = Logger.getLogger(CollectLogger.class.getSimpleName());
            }
        }
        return this.logger;
    }

    // ---------- API "sicura" che evita Sonar issues ----------

    public void info(String msg, Object... args) {
        getInternalLogger().log(Level.INFO, msg, args);
    }

    public void warning(String msg, Object... args) {
        getInternalLogger().log(Level.WARNING, msg, args);
    }

    public void severe(String msg, Object... args) {
        getInternalLogger().log(Level.SEVERE, msg, args);
    }

    public void log(Level level, String msg, Object... args) {
        getInternalLogger().log(level, msg, args);
    }

    // Variante lazy: valutata solo se il livello è abilitato
    public void log(Level level, Supplier<String> msgSupplier) {
        Logger l = getInternalLogger();
        if (l.isLoggable(level)) {
            l.log(level, msgSupplier);
        }
    }
}
