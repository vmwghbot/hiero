package org.hillview.sketch;

import org.hillview.dataset.api.IDataSet;
import org.hillview.maps.PCAProjectionMap;
import org.hillview.sketches.CorrMatrix;
import org.hillview.sketches.FullCorrelationSketch;
import org.hillview.storage.CsvFileReader;
import org.hillview.table.Schema;
import org.hillview.table.api.ContentsKind;
import org.hillview.table.api.ITable;
import org.hillview.utils.Converters;
import org.hillview.utils.LinAlg;
import org.hillview.utils.TestTables;
import org.jblas.DoubleMatrix;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PCATest {
    @Test
    public void testLinearDataset() {
        ITable table = TestTables.getLinearTable(10000, 30);
        List<String> colNames = new ArrayList<String>(table.getSchema().getColumnNames());

        IDataSet<ITable> dataset = TestTables.makeParallel(table, 1000);

        FullCorrelationSketch fcs = new FullCorrelationSketch(colNames);
        CorrMatrix cm = dataset.blockingSketch(fcs);

        DoubleMatrix corrMatrix = new DoubleMatrix(cm.getCorrelationMatrix());
        // Get just the eigenvector corresponding to the largest eigenvalue (because we know the data is approximately
        // linear).
        DoubleMatrix eigenVectors = LinAlg.eigenVectors(corrMatrix, 1);
        eigenVectors.print();

        for (int i = 2; i < eigenVectors.columns; i++) {
            // The eigenvector should have reasonably large components in the first two columns, compared to the
            // other components in the eigenvector.
            Assert.assertTrue(
                    "First component of eigenvector not large enough.",
                    Math.abs(eigenVectors.get(0, 0)) > 3 * Math.abs(eigenVectors.get(0, i))
            );
            Assert.assertTrue(
                    "Second component of eigenvector not large enough.",
                    Math.abs(eigenVectors.get(0, 1)) > 3 * Math.abs(eigenVectors.get(0, i))
            );
        }
    }

    @Test
    public void testMNIST() throws IOException {
        // Read the data from file
        String dataFolder = "../data";
        String csvFile = "mnist_train.csv";
        String schemaFile = "mnist.schema";
        Path path = Paths.get(dataFolder, schemaFile);
        Schema schema = Schema.readFromJsonFile(path);
        path = Paths.get(dataFolder, csvFile);
        CsvFileReader.CsvConfiguration config = new CsvFileReader.CsvConfiguration();
        config.allowFewerColumns = false;
        config.hasHeaderRow = true;
        config.allowMissingData = false;
        config.schema = schema;
        CsvFileReader r = new CsvFileReader(path, config);
        ITable table = r.read();
        table = Converters.checkNull(table);

        // List the numeric columns
        List<String> numericColNames = new ArrayList<String>();
        Set<String> colNames = table.getSchema().getColumnNames();
        for (String colName : colNames) {
            ContentsKind kind = table.getSchema().getDescription(colName).kind;
            if (kind == ContentsKind.Double || kind == ContentsKind.Integer) {
                numericColNames.add(colName);
            }
        }

        // Project
        PCAProjectionMap pcaMap = new PCAProjectionMap(numericColNames, 2);
        ITable result = pcaMap.apply(table);

        Assert.assertEquals(table.getSchema().getColumnCount() + 2, result.getSchema().getColumnCount());
    }
}
