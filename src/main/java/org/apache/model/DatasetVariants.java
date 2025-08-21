package org.apache.model;

import tech.tablesaw.api.Table;

public class DatasetVariants {
    public final Table B;
    public final Table Bplus;
    public final Table C;

    public DatasetVariants(Table B, Table Bplus, Table C) {
        this.B = B;
        this.Bplus = Bplus;
        this.C = C;
    }
}

