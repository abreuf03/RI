
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.Query;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opencsv.*;

import org.apache.lucene.facet.range.DoubleRangeFacetCounts;
//import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.facet.range.DoubleRange;
//import org.apache.lucene.facet.range.LongRange;

// practica 5
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.util.IOUtils;


public class Facetas {

    //boolean createIndex = true;
    private IndexWriter writer;
    private String indexPath;
    private Analyzer analyzer;
    private Similarity similarity;

    // practica 5
    private FacetsConfig facetsConfig;
    private DirectoryTaxonomyWriter taxoWriter;


    public Facetas(String indexPath) throws IOException {
        this.indexPath = indexPath;
        this.analyzer = new StandardAnalyzer();
        this.similarity =  new ClassicSimilarity();
        FSDirectory idxDir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setSimilarity(similarity);
        writer = new IndexWriter(idxDir, config);

        // practica 5
        FSDirectory taxoDir = FSDirectory.open(Paths.get(indexPath + "_taxo"));
        taxoWriter = new DirectoryTaxonomyWriter(taxoDir);

        facetsConfig = new FacetsConfig();

        // Configurar facetas normales
        facetsConfig.setMultiValued("neighbourhood", false);
        facetsConfig.setMultiValued("property_type", false);
        facetsConfig.setMultiValued("host_name", true);
        facetsConfig.setMultiValued("host_location", true);
        facetsConfig.setMultiValued("host_neighbourhood", true);
        facetsConfig.setMultiValued("host_is_superhost", true);
        facetsConfig.setHierarchical("host_since", true);

    }

    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();
        doc.add(new TextField("information", values.toString(), Field.Store.YES));
        for (String attr : map.keySet()) {
            String val = values.get(map.get(attr));
             if (attr.equals(val)) { // No añadir los nombres de campos
                ;
            } else {
                System.out.println("Attribute: " + attr + ", Value: " + val);

                switch (attr) {
                    case "latitude":
                    case "longitude":
                        // Para crear LatLonPoint necesitamos ambos valores
                        try {
                            double lat = Double.parseDouble(values.get(map.get("latitude")));
                            double lon = Double.parseDouble(values.get(map.get("longitude")));
                            doc.add(new LatLonPoint("location", lat, lon));
                            doc.add(new StoredField("latitude", lat));
                            doc.add(new StoredField("longitude", lon));
                            doc.add(new StoredField("location", lat + "," + lon));
                            doc.add(new LatLonDocValuesField("location", lat, lon));
                        } catch (Exception e) {
                            System.err.println("Error parsing lat/lon: " + e.getMessage());
                        }
                        break;

                    

                    case "price":
                        try {
                            double value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                            doc.add(new DoublePoint(attr, value));
                            if (val.isEmpty()) {
                                value = 0.0;
                            } else {
                                // Limpiar comillas, signo $ y comas
                                String cleanVal = val.replace("\"", "")
                                                    .replace("$", "")
                                                    .replace(",", "")
                                                    .trim();

                                value = Double.parseDouble(cleanVal);
                            }
                                // Indexar correctamente
                                doc.add(new DoublePoint("price", value));                             // para búsquedas
                                doc.add(new StoredField("price", value));                             // para recuperar con document.getField()
                                doc.add(new DoubleDocValuesField("price", Double.doubleToRawLongBits(value)));  // para ordenar
                                //debugging : System.out.println("Indexando price: '" + val + "' → " + value);
                                
                                // practica5
                               // doc.add(new NumericDocValuesField("price", Double.doubleToRawLongBits(value)));

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing price: '" + val + "'");
                        }
                        break;

                    case "review_scores_rating":
                        try {
                            double value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                            doc.add(new DoublePoint(attr, value));
                            if (val.isEmpty()) {
                                value = 0.0;
                            } else {
                              
                                String cleanVal = val.replace("\"", "").trim();

                                value = Double.parseDouble(cleanVal);
                            }
                                // Indexar correctamente
                                doc.add(new DoublePoint("review_scores_rating", value));
                                doc.add(new StoredField("review_scores_rating", value));
                                doc.add(new DoubleDocValuesField("review_scores_rating", Double.doubleToRawLongBits(value)));
                                //debugging : System.out.println("Indexando review_scores_rating: '" + val + "' → " + value);

                                //practica 5
                                //doc.add(new NumericDocValuesField("review_scores_rating", Double.doubleToRawLongBits(value)));

                            
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing review_scores_rating: '" + val + "'");
                        }
                        break;

                    case "number_of_reviews":
                    case "bedrooms":
                    case "bathrooms":
                        try {
                            int num = Integer.parseInt(val.replaceAll("[^0-9]", ""));
                            if (val.isEmpty()) {
                               num = 0;
                            } else {
                                double parsed = Double.parseDouble(val);
                                num = (int) parsed;
                            }
                            doc.add(new IntPoint(attr, num));
                            doc.add(new StoredField(attr, num));

                            //practica 5
                            doc.add(new NumericDocValuesField(attr, num));

                        } catch (Exception e) {
                            System.err.println("Error parsing int field " + attr + ": " + e.getMessage());
                        }
                        break;

                    case "description":
                    case "name":
                    case "neighborhood_overview":
                    case "bathrooms_text":
                    case "host_about":
                    case "host_response_time":
                        String cleanData = val
                        .replaceAll("<[^>]+>", ""); //eliminar etiquetas de HTML
                        doc.add(new TextField(attr, cleanData, Field.Store.YES));
                        break;
                    
                    case "listing_url":
                    case "id":
                    case "host_url":
                        doc.add(new StoredField(attr, val));
                        break;
                    case "amenities": //limpiar datos 
                        String cleanAmenities = val
                            .replaceAll("[\\[\\]\"]", "") //eliminar corchetes y comillas
                            .replaceAll("u2019", "'") //pack u2019n play == pack 'n play
                            .trim(); //eliminar espacios iniciales o finales 
                        
                        doc.add(new TextField(attr, cleanAmenities, Field.Store.YES));
                        break;

                    // TODO: add facets here
                    // practica5
                    case "host_location":
                    case "host_neighbourhood":
                        if (!val.isEmpty()) {
                            cleanData = val.replaceAll("<[^>]+>", "");
                        } else {
                            cleanData = "No data";
                        }
                        doc.add(new TextField(attr, cleanData, Field.Store.YES));
                        doc.add(new FacetField(attr, cleanData));
                        break;
                    case "host_name":
                        cleanData = val.replaceAll("<[^>]+>", "");
                        doc.add(new TextField(attr, cleanData, Field.Store.YES));
                        doc.add(new FacetField(attr, cleanData));
                        break;

                    case "host_is_superhost":
                        if ("t".equals(val)) {
                            doc.add(new TextField(attr, "yes", Field.Store.YES));
                            doc.add(new FacetField("host_is_superhost", "superhost"));

                        } else if ("f".equals(val)) {
                            doc.add(new TextField(attr, "no", Field.Store.YES));
                            doc.add(new FacetField("host_is_superhost", "not superhost"));

                        } else {
                        System.out.println("Invalid value for host_is_superhost: " + val);
                        }
                        break;

                    case "host_since":
                        String[] date = (val.split("-"));
                        try {
                            String year = date[0];
                            String month = date[1];
                            String day = date[2];
                            doc.add(new FacetField(attr, year, month, day));
                        } catch (Exception e) {
                            System.err.println("Error parsing date: " + e.getMessage());
                        }
                        break;

                    //practica5
                    case "neighbourhood_cleansed":
                        doc.add(new StringField(attr, val, Field.Store.YES));

                        //añadir faceta
                        if(!val.isEmpty())
                            doc.add(new FacetField("neighbourhood_cleansed", val));

                        break;
                    case "property_type":
                        doc.add(new StringField(attr, val, Field.Store.YES));

                        // NUEVO: faceta de propiedad
                        if(!val.isEmpty())
                            doc.add(new FacetField("property_type", val));

                        break;
                    default:
                        // Todos los demás atributos como StringField
                        doc.add(new StringField(attr, val, Field.Store.YES));
                        break;
                }
            }
        }
        return doc;
    }

    private void indexEntry(Map<String, Integer> map, List<String> values) {
        System.out.println("Indexing...");
        Document doc = getDocument(map, values);
        try {
            //writer.addDocument(doc);
            //practica 5
            writer.addDocument(facetsConfig.build(taxoWriter, doc));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws CorruptIndexException, IOException{
        try {
            writer.commit();
            writer.close();
            //practica 5
            taxoWriter.commit();
            taxoWriter.close(); 
        } catch (IOException e) {
            System.out.println("Error closing the index.");
        }
    }

    public int createBothIndices(String docPath, int limit, String cat) throws Exception {
        File file = new File(docPath);
        List<List<String>> lines = new ArrayList<>();

        try {
            FileReader filereader = new FileReader(file);
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            List<String> rows = new ArrayList<>();
            int count = 0;

            while ((nextRecord = csvReader.readNext()) != null && (limit == 0 || count <= limit)) {
                for (String cell : nextRecord) {
                    rows.add(cell);
                }
                lines.add(rows);
                rows = new ArrayList<>();
                count++;
            }
            csvReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (lines.isEmpty()) return 0;

        List<String> header = lines.getFirst();

        // Mapas para host y propiedad
        Map<String, Integer> map = new HashMap<>();
        if (cat.equals("host")) {
            map.put("host_url", header.indexOf("host_url"));
            map.put("host_name", header.indexOf("host_name"));
            map.put("host_since", header.indexOf("host_since"));
            map.put("host_location", header.indexOf("host_location"));
            map.put("host_about", header.indexOf("host_about"));
            map.put("host_response_time", header.indexOf("host_response_time"));
            map.put("host_is_superhost", header.indexOf("host_is_superhost"));
            map.put("host_neighbourhood", header.indexOf("host_neighbourhood"));

        } else if (cat.equals("prop")) {
            map.put("id", header.indexOf("id"));
            map.put("listing_url", header.indexOf("listing_url"));
            map.put("name", header.indexOf("name"));
            map.put("description", header.indexOf("description"));
            map.put("neighborhood_overview", header.indexOf("neighborhood_overview"));
            map.put("neighbourhood_cleansed", header.indexOf("neighbourhood_cleansed"));
            map.put("latitude", header.indexOf("latitude"));
            map.put("longitude", header.indexOf("longitude"));
            map.put("property_type", header.indexOf("property_type"));
            map.put("bathrooms", header.indexOf("bathrooms"));
            map.put("bathrooms_text", header.indexOf("bathrooms_text"));
            map.put("bedrooms", header.indexOf("bedrooms"));
            map.put("amenities", header.indexOf("amenities"));
            map.put("price", header.indexOf("price"));
            map.put("number_of_reviews", header.indexOf("number_of_reviews"));
            map.put("review_scores_rating", header.indexOf("review_scores_rating"));

        }


        // Iterar filas y crear ambos documentos
        for (int i = 1; i < lines.size(); i++) { // saltar header
            List<String> row = lines.get(i);
            indexEntry(map, row);
        }

        return writer.getDocStats().numDocs;
    }

    public static void indexSearch(String indexHost, String indexProp, Analyzer analyzer, Integer top) throws IOException {
        DirectoryReader readerH = DirectoryReader.open(FSDirectory.open(Paths.get(indexHost)));
        DirectoryReader readerP = DirectoryReader.open(FSDirectory.open(Paths.get(indexProp)));
        ParallelCompositeReader parallelReader = new ParallelCompositeReader(readerH, readerP);
        IndexSearcher searcher = new IndexSearcher(parallelReader);

        BufferedReader in = null;
        in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        List<QueryParser> parsers = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        columns.add("host_about");
        columns.add("host_location");
        columns.add("host_name");
        columns.add("host_neighbourhood");
        columns.add("information");
        columns.add("property_type");
        columns.add("bathrooms_text");
        columns.add("description");
        columns.add("neighbourhood_overview");
        columns.add("neighbourhood_cleansed");
        columns.add("amenities");
        for (String s: columns) {
            parsers.add(new QueryParser(s, analyzer));
        }

//        while (true) {
        String line = null;
        do {
            System.out.println("Consulta?: ");

            line = in.readLine();
            if (line == null || line.length() == -1) {
                break;
            }
            // Eliminamos caracteres blancos al inicio y al final
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            Query query;
            TopDocs[] hits = new TopDocs[columns.size()];
            // Determine how many top hits do we want
            try {
                for (QueryParser p: parsers) {
                    int idx = parsers.indexOf(p);
                    query = p.parse(line);
                    hits[idx] = searcher.search(query, top);
//                    System.out.println(hits[idx].totalHits.value() + " documentos encontrados");
                }
            } catch (ParseException e) {
                System.out.println("Error en cadena consulta.");
                continue;
            }

            StoredFields storedFields = searcher.storedFields();
            HashMap<ScoreDoc, Float> topScores;
            topScores = new HashMap<>();

            for (int i = top-1; i >= 0; i--) {
                for (int j = 0; j < hits.length; j++) {
                    if (hits[j].scoreDocs.length == 0) {
                        ;
                    } else {
                        if (hits[j].scoreDocs.length > i) {
                            ScoreDoc sd = hits[j].scoreDocs[i];
                            if (topScores.size() < top) {
                             topScores.put(sd, sd.score);
//                            System.out.println("Add documnet: " + sd.doc + ", Score: " + sd.score);
                            } else {
                                ScoreDoc min = null;
                                for (ScoreDoc ksd : topScores.keySet()) {
                                    if (ksd.score < sd.score) {
                                        if (min == null || ksd.score < min.score) {
                                            min = ksd;
                                        }
                                    }
                                }
                                if (min != null) {
                                    topScores.remove(min);
//                                System.out.println("Remove document: " + min.doc + ", Score: " + min.score);
                                    topScores.put(sd, sd.score);
//                                System.out.println("Add documnet: " + sd.doc + ", Score: " + sd.score);
                                }
                            }
                        }
                    }
                }
            }
//            System.out.println("Top " + topScores.size() + " documentos encontrados: ");

//
            for (ScoreDoc hit: topScores.keySet()) {
                System.out.println(hit.doc + ", Score: " + hit.score);
                Document doc = storedFields.document(hit.doc);
                System.out.println("--------------------------------------------------");
//                    System.out.println("ID: " + id);
                System.out.println("property_type: " + doc.get("property_type"));
//                System.out.println("description: " + doc.get("description"));
                System.out.println("amenities: " + doc.get("amenities"));
                System.out.println("host_about " + doc.get("host_about"));
                System.out.println("host_location " + doc.get("host_location"));
                System.out.println("host_neighbourhood " + doc.get("host_neighbourhood"));
                System.out.println("host_name " + doc.get("host_name"));
//                System.out.println("information " + doc.get("information"));
                System.out.println();
            }

//                ScoreDoc[] hits = results.scoreDocs;


//                int numTotalHits = hits.totalHits.value();

            if (line.equals("")) {
                break;
            }
            System.out.println("Si quiere poner algunas facetas, entrar 'Facetas' para cambiar el modo");
            line = in.readLine();
        } while (!line.equals("Facetas"));
        try {
            readerP.close();
            readerH.close();
            parallelReader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);

        }
    }

    private List<FacetResult> searchHost() throws IOException {
        DirectoryReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        IndexSearcher searcher = new IndexSearcher(indexReader);
        String taxoDir = indexPath + "_taxo";
        TaxonomyReader taxoReader = new DirectoryTaxonomyReader(FSDirectory.open(Paths.get(taxoDir)));
        FacetsCollectorManager fcm = new FacetsCollectorManager();
        // MatchAllDocsQuery is for "browsing" (counts facets
        // for all non-deleted docs in the index); normally
        // you'd use a "normal" query:
        FacetsCollector fc =
            FacetsCollectorManager.search(searcher, new MatchAllDocsQuery(), 10, fcm).facetsCollector();

        // Retrieve results
        List<FacetResult> results = new ArrayList<>();
        // Count both "Publish Date" and "Author" dimensions
        Facets counts = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, fc);
        results.add(counts.getTopChildren(10, "host_neighbourhood"));
        results.add(counts.getTopChildren(10, "host_since"));
        results.add(counts.getTopChildren(10, "host_name"));
        results.add(counts.getTopChildren(10, "host_is_superhost"));
        results.add(counts.getTopChildren(10, "host_location"));

        IOUtils.close(indexReader, taxoReader);

        return results;
    }

        private List<FacetResult> searchProp() throws IOException {
            DirectoryReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            IndexSearcher searcher = new IndexSearcher(indexReader);
            String taxoDir = indexPath + "_taxo";
            TaxonomyReader taxoReader = new DirectoryTaxonomyReader(FSDirectory.open(Paths.get(taxoDir)));
            FacetsCollectorManager fcm = new FacetsCollectorManager();
            // MatchAllDocsQuery is for "browsing" (counts facets
            // for all non-deleted docs in the index); normally
            // you'd use a "normal" query:
            FacetsCollector fc =
                FacetsCollectorManager.search(searcher, new MatchAllDocsQuery(), 10, fcm).facetsCollector();
    
            // Retrieve results
            List<FacetResult> results = new ArrayList<>();
            // Count both "Publish Date" and "Author" dimensions
            Facets counts = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, fc);
            results.add(counts.getTopChildren(10, "neighbourhood_cleansed"));
            results.add(counts.getTopChildren(10, "amenities"));
            results.add(counts.getTopChildren(10, "property_type"));
    
            
            // FACETAS NUMÉRICAS POR RANGO
            DoubleRange[] priceRanges = new DoubleRange[] {
                new DoubleRange("0-100", 0, true, 100, true),
                new DoubleRange("101-200", 101, true, 200, true),
                new DoubleRange("201-500", 201, true, 500, true),
                new DoubleRange("500+", 500, false, Double.MAX_VALUE, true)
            };
    
    
            Facets facets = new DoubleRangeFacetCounts("price", fc, priceRanges);
            //Facets facets = new LongValueFacetCounts("price", fc);
            FacetResult resultado = facets.getAllChildren("price");
            results.add(resultado);
    
            DoubleRange[] reviewRanges = new DoubleRange[] {
                new DoubleRange("0-1", 0, true, 1, true),
                new DoubleRange("2-3", 2, true, 3, true),
                new DoubleRange("3-4", 3, true, 4, true),
                new DoubleRange("5", 5, true, Double.MAX_VALUE, true)
            };
    
            Facets facets2 = new DoubleRangeFacetCounts("review_scores_rating", fc, reviewRanges);
            FacetResult resultado2 = facets2.getAllChildren("review_scores_rating");
            results.add(resultado2);
    
            IOUtils.close(indexReader, taxoReader);
    
            return results;
        }

    public static void main(String[] args) throws Exception {
        //"Uso: java LukeIndex <ruta_csv> <ruta_indice_propiedad> <ruta_indice_anfitrion> <límite_filas> <modo>");
//        if (args.length < 5) {
//            System.out.println("Uso: java LukeIndex <ruta_csv> <ruta_indice_propiedad> <ruta_indice_anfitrion> <límite_filas> <modo>");
//            return;
//        }
//
//        String csvPath = args[1];         // Ruta al CSV
//        String propIndexPath = args[2];    // Ruta donde se creará el índice de propiedad
//        String hostIndexPath = args[3];    // Ruta donde se creará el índice de anfitrión
//        int limit = Integer.parseInt(args[4]); // Número máximo de filas a indexar (0 = todas)
//        String modo = args[5];

        String modo = "indexar"; // "indexar", "facetas_p", "facetas_h"
        String csvPath = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/practica3/listings.csv";
        String propIndexPath = "./propFacet";
        String hostIndexPath = "./hostFacet";
        int limit = 1000;
        indexSearch(hostIndexPath, propIndexPath, new StandardAnalyzer(), 5);

        if(modo.equals("indexar")){

            // Analizador y similitud de Lucene
            Analyzer analyzer = new StandardAnalyzer();
            Similarity similarity = new ClassicSimilarity();

            // Crear indexadores
            Facetas propFacetas = new Facetas(propIndexPath);
            Facetas hostFacets = new Facetas(hostIndexPath);

            // Indexar ambos índices simultáneamente
            int numDocsProp = propFacetas.createBothIndices(csvPath, limit, "prop");
            System.out.println("Número de documentos indexados de propiedad: " + numDocsProp);

            int numDocsHost = hostFacets.createBothIndices(csvPath, limit, "host");
            System.out.println("Número de documentos indexados de anfitrión: " + numDocsHost);

            // Cerrar indexadores
            propFacetas.close();
            hostFacets.close();
        }
        else if(modo.equals("facetas_p")){
            String indexP = args[1];
            String indexPath = args[2];
            String taxoPath = indexP + "_taxo";

            List<FacetResult> results = new Facetas(indexP).searchProp();
            System.out.println("Neighbourhood: " + results.get(0));
            System.out.println("Amenities: " + results.get(1));
            System.out.println("PropertyType: " + results.get(2));
            System.out.println("Price ranges: " + results.get(3));
            System.out.println("Review scores: " + results.get(4));

        }
        else if (modo.equals("facetas_h")) {
//            String indexPath = args[2];
//            String taxoPath = indexPath + "_taxo";
            String indexPath = hostIndexPath;
            String taxoPath = hostIndexPath + "_taxo";
//            FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
//            IndexReader reader = DirectoryReader.open(dir);
//            IndexSearcher searcher = new IndexSearcher(reader);
//
//            // Abrir taxonomía
//            FSDirectory taxoDir = FSDirectory.open(Paths.get(taxoPath));
//            TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);
//
//            FacetsCollectorManager fcm = new FacetsCollectorManager();
//            FacetsCollector fc = FacetsCollectorManager.search(searcher, new MatchAllDocsQuery(), 10, fcm).facetsCollector();
//
//
//           List<FacetResult> results = new ArrayList<>();

            List<FacetResult> results = new Facetas(indexPath).searchHost();
            System.out.println("Host neighbourhood: " + results.get(0));
            System.out.println("Host since: " + results.get(1));
            System.out.println("Host name: " + results.get(2));
            System.out.println("Superhost: " + results.get(3));
            System.out.println("Location: " + results.get(4));

        }


    }


}
