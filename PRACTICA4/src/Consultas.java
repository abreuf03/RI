import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Date;

import com.healthmarketscience.jackcess.Index;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

public class Consultas {
    //NOTA PARA LA ELENA DEL FUTURO : QUITA ID Y URL DEL INDEX

    private static final Analyzer analyzer = new StandardAnalyzer();

    // Apartado 1
    // Crear consultas utilizando el QueryParser para cada uno de los dos índices
    // de forma independiente.
    public static void independienteConsulta(IndexSearcher indexPSearcher, IndexSearcher indexHSearcher) throws IOException {
        String column1 = "neighborhood_overview";
        String column2 = "host_about";
        QueryParser parser1 = new QueryParser(column1, analyzer);
        QueryParser parser2 = new QueryParser(column2, analyzer);

        Query query1;
        Query query2;
        try {
            query1 = parser1.parse("best");
            query2 = parser2.parse("cat");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        TopDocs hits1 = indexPSearcher.search(query1, 10);
        TopDocs hits2 = indexHSearcher.search(query2, 10);

        StoredFields storedFieldsP = indexPSearcher.storedFields();
        StoredFields storedFieldsH = indexHSearcher.storedFields();

//                int numTotalHits = hits.totalHits.value();
        System.out.println((hits1.totalHits.value() + hits2.totalHits.value()) + " documentos encontrados");

        for (ScoreDoc hit : hits1.scoreDocs) {
            Document doc = storedFieldsP.document(hit.doc);
            String cuerpo = doc.get(column1);
            int id = Integer.parseInt(doc.get("id"));
            String sth = doc.get("neighborhood_overview");
            System.out.println("--------------------------------------------------");
            System.out.println("ID: " + id);
            System.out.println("Descripción: " + cuerpo);
            System.out.println("Something: " + sth);
            System.out.println();
        }

        for (ScoreDoc hit : hits2.scoreDocs) {
            Document doc = storedFieldsH.document(hit.doc);
            String cuerpo = doc.get("host_about");
            String host_since = doc.get("host_since");
            System.out.println("--------------------------------------------------");
            System.out.println("Host since: " + host_since);
            System.out.println("Host about: " + cuerpo);
            System.out.println();
        }
    }

    // Apartado 2
    // Crear consultas que involucren a valores numéricos, exactas y por rango,
    // sobre alguno de los índices
    public static void consultaNumericos(IndexSearcher indexPSearcher) throws IOException {
        Query qe = IntPoint.newExactQuery("bathrooms", 1);
        Query qr = DoublePoint.newRangeQuery("price", 50, 300);
        Query qs = IntPoint.newSetQuery("bathrooms", 1, 4);

        TopDocs hits = indexPSearcher.search(qe, 5);
        StoredFields storedFieldsP = indexPSearcher.storedFields();
        System.out.println(hits.totalHits.value() + " documentos encontrados");

        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = storedFieldsP.document(hit.doc);
            int id = Integer.parseInt(doc.get("id"));
            String name = doc.get("name");
            System.out.println("--------------------------------------------------");
            System.out.println("Number of Bathroom exactly equals to one");
            System.out.println("ID: " + id);
            System.out.println("Name: " + name);
        }

        hits = indexPSearcher.search(qr, 5);
        System.out.println(hits.totalHits.value() + " documentos encontrados");
        storedFieldsP = indexPSearcher.storedFields();

        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = storedFieldsP.document(hit.doc);
            int id = Integer.parseInt(doc.get("id"));
            double price = Double.parseDouble(doc.get("price"));
            System.out.println("--------------------------------------------------");
            System.out.println("Price in range 50-300");
            System.out.println("ID: " + id);
            System.out.println("Price of the accommandation: "+ price);
        }

        hits = indexPSearcher.search(qs, 5);
        System.out.println(hits.totalHits.value() + " documentos encontrados");
        storedFieldsP = indexPSearcher.storedFields();

        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = storedFieldsP.document(hit.doc);
            int id = Integer.parseInt(doc.get("id"));
            int bathrooms =  Integer.parseInt(doc.get("bathrooms"));
            System.out.println("--------------------------------------------------");
            System.out.println("Bathroom number is 1 or 4");
            System.out.println("ID: " + id);
            System.out.println("Number of bathrooms: "+ bathrooms);
        }
    }

    // Apartado 3
    // Crear BooleanQuerys que involucren a distintos campos y con distintas
    // BooleanClause sobre alguno de los índices.
    public static void consultaBooleana(IndexSearcher indexHSearcher) throws IOException {

        Query q1 = new TermQuery(new Term("host_about", "cool"));
        Query q2 = new TermQuery(new Term("host_response_time", "day"));
        Query q3 = new TermQuery(new Term("host_neighbourhood", "Venice"));
        BooleanClause bc1 = new BooleanClause(q1, BooleanClause.Occur.MUST);
        BooleanClause bc2 = new BooleanClause(q2, BooleanClause.Occur.SHOULD);
        BooleanClause bc3 = new BooleanClause(q3, BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
        bqBuilder.add(bc1);
        bqBuilder.add(bc2);
        bqBuilder.add(bc3);
        BooleanQuery bq1 = bqBuilder.build();

        TopDocs hits = indexHSearcher.search(bq1, 10);
        System.out.println(hits.totalHits.value() + " documentos encontrados");
        StoredFields storedFields = indexHSearcher.storedFields();

        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = storedFields.document(hit.doc);
            String host_about = doc.get("host_about");
            System.out.println("--------------------------------------------------");
            System.out.println("About the host: "+ host_about);
            System.out.println("Response time: " + doc.get("host_response_time"));
            System.out.println("Host neighbourhood: " + doc.get("host_neighbourhood"));
//            System.out.println(indexHSearcher.explain(q1, hit.doc));
        }
    }


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
//        String indexProperties = args[0];
//        String indexHosts = args[1];
        String indexProperties = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/propIndex";
        String indexHosts = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/hostIndex";
        FSDirectory dirP = FSDirectory.open(Paths.get(indexProperties));
        DirectoryReader readerP = DirectoryReader.open(dirP);
        IndexSearcher indexP = new IndexSearcher(readerP);

        FSDirectory dirH = FSDirectory.open(Paths.get(indexHosts));
        DirectoryReader readerH = DirectoryReader.open(dirH);
        IndexSearcher indexH = new IndexSearcher(readerH);
//        ordenarConsulta(indexP);

//        independienteConsulta(indexP, indexH);
//        consultaNumericos(indexP);
        consultaBooleana(indexH);
    }
}

