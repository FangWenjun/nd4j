package org.nd4j.imports;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.autodiff.execution.NativeGraphExecutioner;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Created by susaneraly on 11/6/17.
 */
@Slf4j
@RunWith(Parameterized.class)
public class TFGraphTestAll {

    private Map<String, INDArray> inputs;
    private Map<String, INDArray> predictions;
    private String modelName;
    private String modelDir;

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws IOException {
        String rootDir = new ClassPathResource("tf_graphs/examples").getFile().getAbsolutePath();
        String[] modelNames = modelDirNames(rootDir);
        List<Object[]> modelParams = new ArrayList<>();
        for (int i = 0; i < modelNames.length; i++) {
            String baseDir = new File(rootDir, modelNames[i]).getAbsolutePath();
            Object[] currentParams = new Object[3];
            currentParams[0] = inputVars(baseDir); //input variable map - could be null
            currentParams[1] = outputVars(baseDir); //saved off predictions
            currentParams[2] = modelNames[i];
            modelParams.add(currentParams);
        }
        return modelParams;
    }

    public TFGraphTestAll(Map<String, INDArray> inputs, Map<String, INDArray> predictions, String modelName) throws IOException {
        this.inputs = inputs;
        this.predictions = predictions;
        this.modelName = modelName;
        this.modelDir = new File(new ClassPathResource("tf_graphs/examples").getFile(), modelName).getAbsolutePath();
    }

    //Missing bias add fix currently
    @Test
    public void test() throws Exception {
        Nd4j.create(1);
        testSingle(inputs, predictions, modelName, modelDir);
    }

    protected static void testSingle(Map<String, INDArray> inputs, Map<String, INDArray> predictions, String modelName, String modelDir) {
        Nd4j.EPS_THRESHOLD = 1e-4;
        log.info("\n\tRUNNING TEST " + modelName + "...");
        //val tg = TensorFlowImport.importIntermediate(new File(modelDir, "frozen_model.pb"));
        val tg = TensorFlowImport.importGraph(new File(modelDir, "frozen_model.pb"));

        for (String input : inputs.keySet()) {
            //tg.provideArrayForVariable(input, inputs.get(input));
        }
        val executioner = new NativeGraphExecutioner();
        INDArray[] res = executioner.executeGraph(tg);

        for (int i = 0; i < res.length; i++) {
            if (i > 0)
                throw new IllegalArgumentException("NOT CURRENTLY SUPPORTED BY WORKFLOW"); //figure out how to support multiple outputs with freezing in TF
            INDArray nd4jPred = res[i];
            INDArray tfPred = predictions.get("output");
            assertEquals("Predictions do not match on " + modelName, tfPred.reshape(nd4jPred.shape()), nd4jPred);
            /*
            try {
                assertTrue(Transforms.abs(tfPred.reshape(nd4jPred.shape()).sub(nd4jPred)).maxNumber().floatValue() < 1e-8);
            } catch (Throwable t) {
                assertEquals("Predictions do not match on " + modelName, tfPred.reshape(nd4jPred.shape()), nd4jPred);
            }
            */
        }
        log.info("\n\tTEST " + modelName + " PASSED...");
        log.info("\n========================================================\n");

    }

    protected static String[] modelDirNames(String dir) {
        return new File(dir).list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

    }

    protected static Map<String, INDArray> inputVars(String dir) throws IOException {
        Map<String, INDArray> inputVarMap = new HashMap<>();
        File[] listOfFiles = new File(dir).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".shape");
            }
        });
        for (int i = 0; i < listOfFiles.length; i++) {
            File inputFile = listOfFiles[i];
            String inputName = inputFile.getName().split(".shape")[0];
            int[] inputShape = Nd4j.readNumpy(inputFile.getAbsolutePath(), ",").data().asInt();
            INDArray input = Nd4j.readNumpy(new File(dir, inputName + ".csv").getAbsolutePath(), ",").reshape(inputShape);
            inputVarMap.put(inputName, input);
        }
        return inputVarMap;
    }


    //TODO: I don't check shapes
    protected static Map<String, INDArray> outputVars(String dir) throws IOException {
        Map<String, INDArray> outputVarMap = new HashMap<>();
        File[] listOfFiles = new File(dir).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".prediction.csv");
            }
        });
        for (int i = 0; i < listOfFiles.length; i++) {
            File outputFile = listOfFiles[i];
            String outputName = outputFile.getName().split(".prediction.csv")[0];
            INDArray output = Nd4j.readNumpy(outputFile.getAbsolutePath(), ",");
            outputVarMap.put(outputName, output);
        }
        return outputVarMap;
    }
}