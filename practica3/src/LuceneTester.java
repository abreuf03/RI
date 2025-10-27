//package src;
import  src.LukeIndex;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.*;

public class LuceneTester {
    String indexPath = "./index";
    String docPath = "../listings.csv";
    LukeIndex indexer;
    Analyzer analyzer = new StandardAnalyzer();
    Similarity sim = new ClassicSimilarity();

    public static void main(String[] args) {
        LuceneTester tester;
        try {
            tester = new LuceneTester();
            tester.createIndex();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // todavia no esta completo
    private void createIndex() throws IOException {
        indexer = new LukeIndex(indexPath, analyzer, sim);
        int numIndexed;
        long startTime = System.currentTimeMillis();
        numIndexed = indexer.createIndex(docPath,  100);
        long endTime = System.currentTimeMillis();
        indexer.close();
        System.out.println(numIndexed+" file indexed, time taken: "+(endTime-startTime)+" ms");
    }
}