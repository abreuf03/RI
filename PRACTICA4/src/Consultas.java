import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query ;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.TopDocs;

public class Consultas {
    //NOTA PARA LA ELENA DEL FUTURO : QUITA ID Y URL DEL INDEX

    private static final Analyzer analyzer = new StandardAnalyzer();

    //Apartado 4
    //Crear consultas donde la salida esté ordenada siguiendo un criterio distinto
    //al valor de similitud entre documento y consulta

    public static void ordenarConsulta(IndexSearcher searcher) throws Exception{
        QueryParser parser = new QueryParser("descripcion", analyzer);
        Query query = parser.parse("apartamento");

        SortField sf = new SortField("price", SortField.Type.DOUBLE, false); //false para que sea ascendente, primero los más baratos
        Sort orden = new Sort(sf);

        TopFieldDocs results = searcher.search(query, 10, orden);

        StoredFields storedFields = searcher.storedFields();

        System.out.println("\n--- Resultados ordenados por precio ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = storedFields.document(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("price") + " $");
        }

        sf = new SortField("review_scores_rating", SortField.Type.DOUBLE,true);
        orden = new Sort(sf);
        results = searcher.search(query, 10, orden);
        
        System.out.println("\n--- Resultados ordenados por puntuación de reseña ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = storedFields.document(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("review_score_rating") + " stars");
        }
        
    }

    //Apartado 5
    //Crear consultas geográficas.

    public static void consultaGeografica(IndexSearcher searcher) throws Exception{
        double lat = 33.95779;
        double lon = -118.4326;
        double radio = 2000; 

        Query geoQuery = LatLonPoint.newDistanceQuery("location", lat, lon, radio);
        TopDocs results = searcher.search(geoQuery, 10);
        StoredFields storedFields = searcher.storedFields();

        System.out.println("\n--- Resultados en un radio de 2km ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = storedFields.document(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("location"));
        }

        Query boxQuery = LatLonPoint.newBoxQuery("location", lat, lat + 1, lon, lon + 1);
        results = searcher.search(boxQuery, 10);

        System.out.println("\n--- Resultados en un cuadrado ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = storedFields.document(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("location"));
        }

        Query distQuery = LatLonPoint.newDistanceFeatureQuery("location", 2, lat, lon, radio);
        results = searcher.search(distQuery, 10);
        System.out.println("\n--- Resultados ordenados por cercanía ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = storedFields.document(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("location"));
        }
    }

     public static void main(String[] args) throws Exception {
        String indexProperties = args[0];
        String indexHosts = args[1];
        FSDirectory dir = FSDirectory.open(Paths.get(indexProperties));
        DirectoryReader reader = DirectoryReader.open(dir);
        IndexSearcher index = new IndexSearcher(reader);

        ordenarConsulta(index);

    }
}

