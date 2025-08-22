package org.apache.logging;

import java.io.IOException;
import java.io.InputStream;
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

    public Logger getLogger() {
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
}
