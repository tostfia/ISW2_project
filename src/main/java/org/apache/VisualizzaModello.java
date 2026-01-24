package org.apache;

import weka.classifiers.Classifier;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;

public class VisualizzaModello {
    public static void main(String[] args) throws Exception {
        // Percorso del file .model
        String percorsoModello ="models/STORM_best.model";

        // Carica il modello
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(percorsoModello));
        Classifier modello = (Classifier) in.readObject();
        in.close();

        // Salva la rappresentazione del modello su un file
        try (FileWriter writer = new FileWriter("models/STORM_modello.txt")) {
            writer.write(modello.toString());
        }
    }
}

