import java.io.IOException;

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

    private void createIndex() throws IOException {
        indexer = new LukeIndex(indexPath, analyzer, sim);
        int numIndexed;
        long startTime = System.currentTimeMillis();
        numIndexed = indexer.createIndex(docPath, new ...);
        long endTime = System.currentTimeMillis();
        indexer.close();
        System.out.println(numberIndexed+" file indexed, time taken: "+(endTime-startTime)+" ms");
    }
}