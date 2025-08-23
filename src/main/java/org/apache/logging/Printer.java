package org.apache.logging;




import java.io.InputStream;
import java.io.IOException;
import java.util.logging.*;

public class Printer {

    // Codici ANSI
    private static final String WHITE  = "\u001B[97m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE   = "\u001B[34m";
    private static final String RESET  = "\u001B[0m";

    private static final String LOGGER_NAME = Printer.class.getSimpleName();

    private static Printer instance = null;
    private Logger logger = null;

    private Printer() {}

    public static synchronized Printer getInstance() {
        if (instance == null) {
            instance = new Printer();
        }
        return instance;
    }

    private Logger getLogger() {
        if (logger == null) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("logging.properties")) {
                if (inputStream != null) {
                    LogManager.getLogManager().readConfiguration(inputStream);
                }

                logger = Logger.getLogger(LOGGER_NAME);
                logger.setUseParentHandlers(false);

                ConsoleHandler handler = new ConsoleHandler();
                handler.setFormatter(new Formatter() {
                    @Override
                    public String format(LogRecord record) {
                        String color = switch (record.getLevel().getName()) {
                            case "SEVERE"  -> RED;
                            case "WARNING" -> YELLOW;
                            case "INFO"    -> GREEN;
                            case "CONFIG"  -> BLUE;
                            default        -> WHITE;
                        };

                        // parentesi bianche, livello colorato, messaggio lasciato "così com'è"
                        return String.format(
                                "%s[%s%s%s%s] %s%n",
                                WHITE,      // apre parentesi bianca
                                color,      // colore del livello
                                record.getLevel(),
                                RESET,      // reset colore
                                WHITE,      // chiusura parentesi bianca
                                record.getMessage() // il messaggio viene deciso dal chiamante
                        );
                    }

                });

                logger.addHandler(handler);
                logger.setLevel(Level.ALL);

            } catch (IOException e) {
                System.exit(-1);
            }
        }
        return logger;
    }

    // --- Metodi pubblici compatibili ---
    public static void print(String s) {
        getInstance().getLogger().log(Level.INFO, WHITE+ s+ RESET);
    }

    public static void println(String s) {
        getInstance().getLogger().log(Level.INFO, WHITE +s+ RESET);
    }

    public static void printlnBlue(String s) {
        getInstance().getLogger().log(Level.INFO, BLUE + s + RESET);
    }

    public static void printBlue(String s) {
        getInstance().getLogger().log(Level.INFO, BLUE + s + RESET);
    }

    public static void printlnGreen(String s) {
        getInstance().getLogger().config(GREEN + s + RESET);
    }

    public static void printGreen(String s) {
        getInstance().getLogger().config(GREEN + s + RESET);
    }

    public static void printYellow(String s) {
        getInstance().getLogger().warning(YELLOW + s + RESET);
    }

    public static void errorPrint(String s) {
        getInstance().getLogger().severe(RED + s + RESET);
    }
}
