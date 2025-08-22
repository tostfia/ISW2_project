package org.apache.logging;




public class Printer {

    // Costanti ANSI per i colori
    public static final String RESET  = "\u001B[0m";
    public static final String RED    = "\u001B[31m";
    public static final String GREEN  = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE   = "\u001B[34m";

    // Costruttore pubblico
    public Printer() {}

    // Stampa semplice
    public static void print(String s) {
        System.out.print(s);
    }

    public static void println(String s) {
        System.out.println(s);
    }

    // Stampa colorata
    public static void printlnBlue(String s) {
        System.out.println(BLUE + s + RESET);
    }

    public static void printBlue(String s) {
        System.out.print(BLUE + s + RESET);
    }

    public static void printlnGreen(String s) {
        System.out.println(GREEN + s + RESET);
    }

    public static void printGreen(String s) {
        System.out.print(GREEN + s + RESET);
    }

    public static void printYellow(String s) {
        System.out.print(YELLOW + s + RESET);
    }

    public static void errorPrint(String s) {
        System.out.println(RED + s + RESET);
    }
}



