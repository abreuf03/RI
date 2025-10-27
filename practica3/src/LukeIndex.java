import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class LukeIndex {

    boolean createIndex = true;
    private IndexWriter writer;

    public void LukeIndex(String indexPath, Analyzer analyzer, Similarity similarity) throws IOException {
        FSDirecotory idxDir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setSimilarity(similarity);
        writer = new IndexWriter(idxDir, config);
    }

    private Document getDocument(File file) throws IOException {
        Document doc = new Document();

        Field idField = new StringField(...);
        ...

        doc.add(idField);
    }

    private void indexEntry(String[] values) {
        System.out.println("Indexing...");
        Document doc =  getDocument(values);
        writer.addDocument(doc);
    }

    public int createIndex(String docPath, int limit=100) throws IOException {
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

        List<String> host = lines.getFirst();
        int url = host.indexOf('host_url');
        int name = host.indexOf('host_name');
        int since = host.indexOf('host_since');
        int location = host.indexOf('host_location');
        int about = host.indexOf('host_about');
        int response_time = host.indexOf('host_response_time');
        int is_superhost = host.indexOf('host_is_superhost');
        int neighbourhood = host.indexOf('host_neighbourhood');

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