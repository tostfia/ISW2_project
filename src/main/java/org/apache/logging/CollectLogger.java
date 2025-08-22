package org.apache.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class CollectLogger {


    private static CollectLogger instance;
    private Logger logger;

    private CollectLogger() {}
    public static synchronized CollectLogger getInstance() {
        if (instance == null) {
            instance = new CollectLogger();
        }
        return instance;
    }
    public Logger getLogger() {
        if (logger == null) {
            try {
                InputStream input = getClass().getClassLoader().getResourceAsStream("logging.properties");
                LogManager.getLogManager().readConfiguration(input);
                this.logger = Logger.getLogger(CollectLogger.class.getSimpleName());
            } catch (IOException ignored) {
                System.exit(-1);

            }
        }
        return this.logger;
    }
}
