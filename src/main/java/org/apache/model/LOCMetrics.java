package org.apache.model;

import lombok.Getter;
import lombok.Setter;
@Setter
@Getter
public class LOCMetrics {

    private int maxVal;

    private double avgVal;

    private int val;

    public LOCMetrics(){
        maxVal = 0;
        avgVal = 0;
        val = 0;
    }
    public void updateMetrics(int value) {
        // Aggiunge il valore alla somma totale
        this.val += value;

        // Se il nuovo valore è più grande del massimo attuale, lo aggiorna
        if (value > this.maxVal) {
            this.maxVal = value;
        }
    }
}
