package src;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class Parser {
    static String indexPath = "./host";
//    QueryParser parser = new QueryParser("text",  new StandardAnalyzer());
//    Query q1, q2, q3;
////    q1 = parser.parse(...)
//
//    int cuantos = 20;
//    TopDocs topDocs = indexSearcher.search(q1, cuantos);
//    System.out.println("Documentos encontrados" + topDocs.totalHits);



    public static void main(String[] args) throws IOException {
        Analyzer analyzer = new StandardAnalyzer();
        Similarity similarity = new ClassicSimilarity();
        try {
            indexSearch(analyzer, similarity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void indexSearch(Analyzer analyzer, Similarity similarity) throws IOException {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        BufferedReader in = null;
        in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));


        // El campo cuerpo sera analizado utilizando el analyzer
        QueryParser parser = new QueryParser("host_about", analyzer);
        while (true) {
            System.out.println("Consulta?: ");

            String line = in.readLine();
            if (line == null || line.length() == -1) {
                break;
            }
            // Eliminamos caracteres blancos al inicio y al final
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            Query query;
            try {
                query = parser.parse(line);
            } catch (ParseException e) {
                System.out.println("Error en cadena consulta.");
                continue;
            }

            TopDocs hits = searcher.search(query, 100);
//                ScoreDoc[] hits = results.scoreDocs;

            StoredFields storedFields = searcher.storedFields();

//                int numTotalHits = hits.totalHits.value();
            System.out.println(hits.totalHits.value() + " documentos encontrados");

            for (ScoreDoc hit: hits.scoreDocs) {
                Document doc = storedFields.document(hit.doc);
                String cuerpo = doc.get("host_about");
//                    Integer id = doc.getField("ID").numericValue().intValue();
                System.out.println("--------------------------------------------------");
//                    System.out.println("ID: " + id);
                System.out.println("Curepo: " + cuerpo);
                System.out.println();
            }
            if (line.equals("")) {
                break;
            }
        }
        try {
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);

        }
    }
}
