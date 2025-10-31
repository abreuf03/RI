//import src.LukeIndex;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.*;

public class LuceneTester {
    String indexPath = "./index";
    String docPath = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/practica3/listings.csv";
    LukeIndex indexer;
    Analyzer analyzer = new StandardAnalyzer();
    Similarity sim = new ClassicSimilarity();

//    public static void main(String[] args) {
//        LuceneTester tester;
//        try {
//            tester = new LuceneTester();
//            tester.createIndex();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    // todavia no esta completo
    private void createIndex() throws IOException {
        indexer = new LukeIndex(indexPath, analyzer, sim);
        int numIndexed = 0;
        long startTime = System.currentTimeMillis();
        try {
            numIndexed = indexer.createIndex(docPath,  100);
        } catch (Exception e) {
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        indexer.close();
        System.out.println(numIndexed+" file indexed, time taken: "+(endTime-startTime)+" ms");
    }
}