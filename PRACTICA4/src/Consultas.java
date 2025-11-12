import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
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
    
    private static final Analyzer analyzer = new StandardAnalyzer();

    //Apartado 4
    //Crear consultas donde la salida esté ordenada siguiendo un criterio distinto
    //al valor de similitud entre documento y consulta

    public static void ordenarConsulta(IndexSearcher index) throws Exception{
        QueryParser parser = new QueryParser("descripcion", analyzer);
        Query query = parser.parse("apartamento");

        SortField sf = new SortField("price", SortField.Type.DOUBLE, false); //false para que sea ascendente, primero los más baratos
        Sort orden = new Sort(sf);

        TopFieldDocs results = index.search(query, 10, orden);
        System.out.println("\n--- Resultados ordenados por precio ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = index.doc(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("price") + " $");
        }
        
    }

    //Apartado 5
    //Crear consultas geográficas.

    public static void consultaGeografica(IndexSearcher index){
        double lat = 33.95779;
        double lon = -118.4326;
        double radio = 2000; 

        Query geoQuery = LatLonPoint.newDistanceQuery("location", lat, lon, radio);
        TopDocs results = index.search(geoQuery, 10);

        System.out.println("\n--- Resultados en un radio de 2km ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = index.doc(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("location"));
        }

        Query boxQuery = LatLonPoint.newBoxQuery("location", lat, lat + 30, lon, lon + 30);
        results = index.search(boxQuery, 10);

        System.out.println("\n--- Resultados en un cuadrado ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = index.doc(sd.doc);
            System.out.println(d.get("name") + " - " + d.get("location"));
        }

        Query distQuery = LatLonPoint.newDistanceFeatureQuery("location", 2, lat, lon, radio);
        results = index.search(distQuery, 10);
        System.out.println("\n--- Resultados ordenados por cercanía ---");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = index.doc(sd.doc);
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
