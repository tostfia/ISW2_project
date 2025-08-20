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

}
