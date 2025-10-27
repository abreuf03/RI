package src;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.opencsv.*;
import java.util.Map;
import java.util.HashMap;
import com.opencsv.exceptions.CsvValidationException;

public class LukeIndex {

    boolean createIndex = true;
    private IndexWriter writer;
    private String indexPath;
    private Analyzer analyzer;
    private Similarity similarity;

    public LukeIndex(String indexPath, Analyzer analyzer, Similarity similarity) throws IOException {
        this.indexPath = indexPath;
        this.analyzer = analyzer;
        this.similarity = similarity;
        FSDirectory idxDir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setSimilarity(similarity);
        writer = new IndexWriter(idxDir, config);
    }

    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();

        for (String attr: map.keySet()) {
            doc.add(new StringField(attr, values.get(map.get(attr)), Field.Store.YES));
            System.out.println("Attribute: "+attr+", Value: "+values.get(map.get(attr)));
        }
//        Field idField = new StringField("id", "fff", Field.Store.YES);
//        ...
//        doc.add(idField);
        return doc;
    }

    private void indexEntry(Map<String, Integer> map, List<String> values) {
        System.out.println("Indexing...");
        Document doc = getDocument(map, values);
        try {
            writer.addDocument(doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int createIndex(String docPath, int limit) throws Exception {
        // Read the file before starting indexing
        File file = new File(docPath);

        List<List<String>> lines = new ArrayList<>();
        try {

            // Create an object of filereader
            // class with CSV file as a parameter.
            FileReader filereader = new FileReader(file);

            // create csvReader object passing
            // file reader as a parameter
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            List<String> rows = new ArrayList<>();
            int count = 0;
            // we are going to read data line by line
            while ((nextRecord = csvReader.readNext()) != null && count < limit) {
                for (String cell : nextRecord) {
//                    System.out.print(cell+ " ");
                    rows.add(cell);
                }
                lines.add(rows);
                rows = new ArrayList<>();
                System.out.println();
                count++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Integer> map = new HashMap<String, Integer>();
        List<String> host = lines.getFirst();
        map.put("host_url", host.indexOf("host_url"));
        map.put("host_name", host.indexOf("host_name"));
        map.put("host_since", host.indexOf("host_since"));
        map.put("host_location", host.indexOf("host_location"));
        map.put("host_about", host.indexOf("host_about"));
        map.put("host_response_time", host.indexOf("host_response_time"));
        map.put("host_is_superhost", host.indexOf("host_is_superhost"));
        map.put("host_neighbourhood", host.indexOf("host_neighbourhood"));

        for (List<String> row: lines) {
            try {
                indexEntry(map, row);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        return writer.getDocStats().numDocs;
        // eso no funciona :(
//        Scanner inputStream;

//        try {
//            inputStream = new Scanner(file);
//            int count = 0
//            while (inputSteam.hasNext() && count < 101) {
//                String line = inputStream.next();
//                String[] values = line.split(",");
//                lines.add(Arrays.asList(values));
//                count++;
//            }
//            inputStream.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        // the following code lets you iterate through the 2-dimensional array
//        int lineNo = 1;
//        for(List<String> line: lines) {
//            int columnNo = 1;
//            for (String value: line) {
//                System.out.println("Line " + lineNo + " Column " + columnNo + ": " + value);
//                columnNo++;
//            }
//            lineNo++;
//        }

    }

    public void close() throws CorruptIndexException, IOException{
        try {
            writer.commit();
            writer.close();
        } catch (IOException e) {
            System.out.println("Error closing the index.");
        }
    }
}