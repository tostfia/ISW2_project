package org.apache.utilities;

import org.apache.controller.GitController;
import org.apache.utilities.enums.FileExtension;
import org.apache.utilities.enums.ReportType;
import org.json.JSONObject;

public class Utility {


    public Utility() {
        // Constructor implementation
    }
    public static void setupCsv(GitController git){}
    public static void setupJson(String targetName, ReportType reportType, Object ob, FileExtension fileExtension) {
        if (ob instanceof  JSONObject jsonObject) {

        }


        // JSON setup implementation
    }
}
