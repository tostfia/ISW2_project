package org.apache.whatif;

import org.apache.logging.Printer;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PredictionWhatIf {


    private PredictionWhatIf(){}
    public static void runPrediction(String datasetAPath, String outputPath,String projectName) throws Exception {
        int featureIdx;
        Instances datasetRaW= loadDataset(datasetAPath);
        Instances datasetA= preprocess(datasetRaW);
        datasetA=reorderBugginessValues(datasetA);
        final Instances modelHeader= new Instances(datasetA,0);
        Instances datasetBplus= new Instances(modelHeader,0);
        Instances datasetC = new Instances(modelHeader,0);

        if (projectName.equalsIgnoreCase("BOOKKEEPER")){
            featureIdx= datasetA.attribute("LOC").index();
        } else if (projectName.equalsIgnoreCase("STORM")){
            featureIdx= datasetA.attribute("NestingDepth").index();
        } else {
            throw new IllegalArgumentException("Unsupported project for What-If analysis");
        }
        for (int i=0;i<datasetA.numInstances();i++){
            if(datasetRaW.instance(i).value(featureIdx)>0){
                datasetBplus.add(datasetA.instance(i));
            }else{
                datasetC.add(datasetA.instance(i));
            }
        }
        Instances datasetB= new Instances(datasetBplus);
        for(int i=0;i<datasetBplus.numInstances();i++){
            datasetB.instance(i).setValue(featureIdx,0);
        }

        Instances datasetTrain = new Instances(datasetA);
        if (projectName.equalsIgnoreCase("BOOKKEEPER")){
            datasetTrain= applySMOTE(datasetTrain);
        } else if (projectName.equalsIgnoreCase("STORM")){
            datasetTrain = downSample(datasetTrain);
        }

        Classifier model = buildClassifier(datasetTrain);

        PredictionSummary predictA= predict("A", datasetA,model, modelHeader);
        PredictionSummary predictB= predict("B", datasetB,model, modelHeader);
        PredictionSummary predictC= predict("C", datasetC,model, modelHeader);
        PredictionSummary predictBplus= predict("B+", datasetBplus,model, modelHeader);
        List<PredictionSummary> summaries = new ArrayList<>();
        summaries.add(predictA);
        summaries.add(predictB);
        summaries.add(predictC);
        summaries.add(predictBplus);

        exportSummaryToCsv(summaries, outputPath);



        Printer.printGreen("-----VERIFICA WHAT-IF ANALYSIS RESULTS-----");
        Printer.printGreen(STR."Righe: \{datasetA.numInstances()} = \{datasetBplus.numInstances() + datasetC.numInstances()}");
        Printer.printGreen("predizione E: "+ predictA.predictedBuggy + " = " + (predictBplus.predictedBuggy + predictC.predictedBuggy));
    }

    private static Instances loadDataset(String path) throws Exception {
        Instances data = new ConverterUtils.DataSource(path).getDataSet();
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        return data;
    }
    private static Instances preprocess(Instances data) throws Exception {

        // 1. RIMUOVI ATTRIBUTI STRINGA (Project e Method)
        RemoveType removeStrings = new RemoveType();
        removeStrings.setOptions(new String[]{"-T", "string"}); // -T string specifica il tipo da rimuovere
        removeStrings.setInputFormat(data);
        data = Filter.useFilter(data, removeStrings);

        // 2. RIMUOVI releaseID (se presente)
        // Nota: Se releaseID era nominale, è rimasto. Se era stringa, è già sparito sopra.
        if (data.attribute("releaseID") != null || data.attribute("ReleaseID") != null) {
            Attribute relAttr = data.attribute("releaseID") != null ?
                    data.attribute("releaseID") : data.attribute("ReleaseID");
            Remove remove = new Remove();
            remove.setAttributeIndices("" + (relAttr.index() + 1));
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }


        AttributeSelection fs = new AttributeSelection();
        InfoGainAttributeEval eval = new InfoGainAttributeEval();
        Ranker search = new Ranker();
        search.setThreshold(0.00);

        fs.setEvaluator(eval);
        fs.setSearch(search);
        fs.setInputFormat(data);
        data = Filter.useFilter(data, fs);

        return data;
    }

    // Applica SMOTE per riequilibrare le classi
    private static Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        smote.setPercentage(65.0);
        return Filter.useFilter(data, smote);
    }

    // Campionamento semplice delle istanze (per limitare dimensioni)
    private static Instances downSample(Instances data) {
        if (data.size() <= 40000) return data;

        // Shuffle con seed fisso per riproducibilità
        data.randomize(new java.util.Random(42));

        // Estrae casualmente le prime N istanze
        return new Instances(data, 0, 40000);
    }


    // Costruisce il classificatore ottimale in base al progetto selezionato.
    private static Classifier buildClassifier(Instances trainData) throws Exception {

            //  Random Forest
            weka.classifiers.trees.RandomForest rf = new weka.classifiers.trees.RandomForest();

            // Parametri ottimizzati: I=30, depth=12, M=50, K=0 (auto), S=1, slots=1
            // Nota: BagSizePercent impostato al 50% come indicato nell'ultimo snippet
            String[] options = Utils.splitOptions("-I 30 -depth 12 -M 50 -K 0 -S 1 -num-slots 1");
            rf.setOptions(options);
            rf.setBagSizePercent(50);
            rf.buildClassifier(trainData);
            return rf;

    }

    // METODO PREDICT CON ALLINEAMENTO DATASET
    private static PredictionSummary predict(String name, Instances data, Classifier model, Instances header) throws Exception {
        int actualBuggy = 0;
        int predictedBuggy = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);

            // Conteggio reali (usando l'indice della classe del dataset corrente)
            if (inst.stringValue(inst.classIndex()).equalsIgnoreCase("yes")) {
                actualBuggy++;
            }

            inst.setDataset(header);
            double pred = model.classifyInstance(inst);
            String label = header.classAttribute().value((int) pred);

            if (label.equalsIgnoreCase("yes")) {
                predictedBuggy++;
            }
        }
        return new PredictionSummary(name, actualBuggy, predictedBuggy);
    }

    // Esporta la tabella dei risultati nel formato richiesto (A,E)
    private static void exportSummaryToCsv(List<PredictionSummary> summaries, String path) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Dataset,A,E\n");
            for (PredictionSummary s : summaries) {
                String aValue = s.datasetName.equals("B") ? "" : String.valueOf(s.realBuggy);
                writer.write(s.datasetName + "," + aValue + "," + s.predictedBuggy + "\n");
            }
        } catch (IOException e) {
           Printer.errorPrint(e.getMessage());
        }
    }


    // Riordina i valori della classe Bugginess
    private static Instances reorderBugginessValues(Instances data) throws Exception {
        int classIdx = data.classIndex() == -1 ? data.numAttributes() - 1 : data.classIndex();
        data.setClassIndex(classIdx);

        Attribute classAttr = data.classAttribute();
        if (classAttr.indexOfValue("no") == 0 && classAttr.indexOfValue("yes") == 1) return data;

        ArrayList<String> newValues = new ArrayList<>();
        newValues.add("no");
        newValues.add("yes");
        Attribute newClassAttr = new Attribute("Bugginess_New", newValues);
        data.insertAttributeAt(newClassAttr, data.numAttributes());

        int newIdx = data.numAttributes() - 1;
        for (int i = 0; i < data.numInstances(); i++) {
            String val = data.instance(i).stringValue(classIdx).toLowerCase();
            data.instance(i).setValue(newIdx, val.equals("yes") ? "yes" : "no");
        }

        data.setClassIndex(newIdx);
        Remove remove = new Remove();
        remove.setAttributeIndices("" + (classIdx + 1));
        remove.setInputFormat(data);
        Instances filtered = Filter.useFilter(data, remove);
        filtered.renameAttribute(filtered.numAttributes() - 1, "Bugginess");
        filtered.setClassIndex(filtered.numAttributes() - 1);
        return filtered;
    }



}







