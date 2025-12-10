
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
// practica 5
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.FacetsCollectorManager.FacetsResult;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.ParallelCompositeReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.Query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opencsv.*;

import org.apache.lucene.facet.range.DoubleRangeFacetCounts;
//import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.facet.range.DoubleRange;
//import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.util.IOUtils;


public class Facetas {

    //boolean createIndex = true;
    private IndexWriter writer;
    private String indexPath;
    private Analyzer analyzer;
    private Similarity similarity;

    // practica 5
    private static FacetsConfig facetsConfig;
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
        facetsConfig.setMultiValued("amenities", true);
        facetsConfig.setHierarchical("host_since", true);
        facetsConfig.setHierarchical("neighbourhood_hier", true);
        //facetsConfig.setHierarchical("bedrooms", true);
        //facetsConfig.setHierarchical("bathrooms", true);


//        System.out.println("FACET CONFIG: " + facetsConfig.getDimConfigs());
    }

    //solo lecturas -> intento de solucionar problema de bathrooms y bedrooms
    public Facetas(String indexPath, boolean forSearchOnly) throws IOException {
        this.indexPath = indexPath;
        facetsConfig = new FacetsConfig();
        facetsConfig.setMultiValued("amenities", true);
        facetsConfig.setHierarchical("host_since", true);
        facetsConfig.setHierarchical("neighbourhood_hier", true);
        //facetsConfig.setHierarchical("bedrooms", true);
        //facetsConfig.setHierarchical("bathrooms", true);
    }


    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();

        for (String attr : map.keySet()) {
            String val = values.get(map.get(attr));
             if (attr.equals(val)) { // No añadir los nombres de campos
                ;
            } else {
//                System.out.println("Attribute: " + attr + ", Value: " + val);

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
                        double value;
                        try {
                            if (!val.isEmpty()) {
                                value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                                doc.add(new DoublePoint(attr, value));
                                // Limpiar comillas, signo $ y comas
                                String cleanVal = val.replace("\"", "")
                                                    .replace("$", "")
                                                    .replace(",", "")
                                                    .trim();

                                value = Double.parseDouble(cleanVal);
                                                                // Indexar correctamente
                                doc.add(new DoublePoint("price", value));                             // para búsquedas
                                doc.add(new StoredField("price", value));                             // para recuperar con document.getField()
                                doc.add(new DoubleDocValuesField("price", value));  // para ordenar
                            }

                                //debugging : System.out.println("Indexando price: '" + val + "' → " + value);
                                
                                // practica5
                                
                                //doc.add(new NumericDocValuesField("price", value));

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing price: '" + val + "'");
                        }
                        break;

                    case "review_scores_rating":
                        try {
                            if (!val.isEmpty()) {
                                value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                                doc.add(new DoublePoint(attr, value));
                                String cleanVal = val.replace("\"", "").trim();

                                value = Double.parseDouble(cleanVal);
                                                                // Indexar correctamente
                                doc.add(new DoublePoint("review_scores_rating", value));
                                doc.add(new StoredField("review_scores_rating", value));
                                doc.add(new DoubleDocValuesField("review_scores_rating", value));
                            }

                                //debugging : System.out.println("Indexando review_scores_rating: '" + val + "' → " + value);

                                //practica 5
                                //doc.add(new NumericDocValuesField("review_scores_rating", Double.doubleToRawLongBits(value)));

                            
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing review_scores_rating: '" + val + "'");
                        }
                        break;
                    case "number_of_reviews":
                        try {
                             String cleaned = val.replaceAll("[^0-9.]", "").trim();
                            // Ignorar si el valor está vacío
                            if (val != null && !val.trim().isEmpty()) {
                                int num = (int) Double.parseDouble(cleaned);
//                                System.out.println("DEBUG " + attr + " cleaned = " + num);

                                // Campos para búsquedas y recuperación
                                doc.add(new IntPoint(attr, num));
                                doc.add(new StoredField(attr, num));
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG bathrooms EMPTY: " + val);
                            System.err.println("Error parsing int field " + attr + ": " + e.getMessage());
                        }
                        break;
                    case "bedrooms":
                    case "bathrooms":
                    
                        try {
                             String cleaned = val.replaceAll("[^0-9.]", "").trim();
                            // Ignorar si el valor está vacío
                            if (val != null && !val.trim().isEmpty()) {
                                int num = (int) Double.parseDouble(cleaned);
//                                System.out.println("DEBUG " + attr + " cleaned = " + num);

                                // Campos para búsquedas y recuperación
                                doc.add(new IntPoint(attr, num));
                                doc.add(new StoredField(attr, num));

                                // Para ordenar y doc values — usar un nombre distinto para evitar conflictos
                                doc.add(new NumericDocValuesField(attr + "_dv", num));

                                // Faceta (usar la dimensión tal cual con el valor string)
                                doc.add(new FacetField(attr, Integer.toString(num)));
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG bathrooms EMPTY: " + val);
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
                    case "amenities":
                        if(val != null && !val.isEmpty()) {
                            cleanData = val
                                .replaceAll("[\\[\\]\"]", "")
                                .replaceAll("u2019", "'")
                                .trim();

                            if(!cleanData.isEmpty()) {   // <-- validar después de limpiar
                                doc.add(new TextField(attr, cleanData, Field.Store.YES));
                                //doc.add(new FacetField(attr, cleanData));
                            }

                            // Dividir cada amenity individualmente y añadir como faceta
                            String[] amenities = cleanData.split(",\\s*"); // separa por coma y posibles espacios
                            for (String amenity : amenities) {
                                if (!amenity.isEmpty()) {
                                    doc.add(new FacetField(attr, amenity.trim()));
                                }
                            }
                        }
                        break;


                    // practica5
                    case "host_location":
                    case "host_neighbourhood":
                        if (!val.isEmpty()) {
                            cleanData = val.replaceAll("<[^>]+>", "");
                            doc.add(new StringField(attr, cleanData, Field.Store.YES));
                            doc.add(new FacetField(attr, cleanData));
                        } 

                        break;
                    case "host_name":
                        cleanData = val.replaceAll("<[^>]+>", "");
                        doc.add(new StringField(attr, cleanData, Field.Store.YES));
//                        doc.add(new FacetField(attr, cleanData));
                        break;

                    case "host_is_superhost":
                        if ("t".equals(val)) {
                            doc.add(new StringField(attr, "yes", Field.Store.YES));
                            doc.add(new FacetField("host_is_superhost", "superhost"));

                        } else if ("f".equals(val)) {
                            doc.add(new StringField(attr, "no", Field.Store.YES));
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
                        //if(!val.isEmpty())
                        //    doc.add(new FacetField("neighbourhood_cleansed", val));

                        break;
                    case "property_type":
                        doc.add(new StringField(attr, val, Field.Store.YES));

                        // NUEVO: faceta de propiedad
                        if(!val.isEmpty())
                            doc.add(new FacetField("property_type", val));

                        break;
                    case "neighbourhood_group_cleansed":
                        doc.add(new StringField(attr, val, Field.Store.YES));

                        String barrio = null;
                        try {
                            Integer idxBarrio = map.get("neighbourhood_cleansed");
                            if (idxBarrio != null) {
                                barrio = values.get(idxBarrio);
                            }
                        } catch (Exception e) {
                            // por si acaso
                        }

                        if (val != null && !val.isEmpty() &&
                            barrio != null && !barrio.isEmpty()) {

                            // Faceta jerárquica
                            doc.add(new FacetField("neighbourhood_hier", val, barrio));
                        }

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

    public int createBothIndices(String docPath, int limit, String cat) throws Exception { //voy a volver a ponerlo para usar 1 índice
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
        if (cat.equals("host") || cat.equals("all")) {
            map.put("host_url", header.indexOf("host_url"));
            map.put("host_name", header.indexOf("host_name"));
            map.put("host_since", header.indexOf("host_since"));
            map.put("host_location", header.indexOf("host_location"));
            map.put("host_about", header.indexOf("host_about"));
            map.put("host_response_time", header.indexOf("host_response_time"));
            map.put("host_is_superhost", header.indexOf("host_is_superhost"));
            map.put("host_neighbourhood", header.indexOf("host_neighbourhood"));

        }
        if (cat.equals("prop") || cat.equals("all")) {
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
            map.put("neighbourhood_group_cleansed",header.indexOf("neighbourhood_group_cleansed"));

        }


        // Iterar filas y crear ambos documentos
        for (int i = 1; i < lines.size(); i++) { // saltar header
            List<String> row = lines.get(i);
            indexEntry(map, row);
        }

        return writer.getDocStats().numDocs;
    }

    /** User runs a query and counts facets. */
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
            new DoubleRange("0-100", 0, true, 100, false),
            new DoubleRange("100-200", 100, true, 200, false),
            new DoubleRange("200-500", 200, true, 500, false),
            new DoubleRange("500+", 500, true, Double.MAX_VALUE, true)
        };


        Facets facets = new DoubleRangeFacetCounts("price", fc, priceRanges);
        //Facets facets = new LongValueFacetCounts("price", fc);
        FacetResult resultado = facets.getAllChildren("price");
        results.add(resultado);

        DoubleRange[] reviewRanges = new DoubleRange[] {
            new DoubleRange("0-1", 0, true, 1, false),
            new DoubleRange("1-2", 1, true, 2, false),
            new DoubleRange("2-3", 2, true, 3, false),
            new DoubleRange("3-4", 3, true, 4, false),
            new DoubleRange("4-5", 4, true, Double.MAX_VALUE, true)
        };

        Facets facets2 = new DoubleRangeFacetCounts("review_scores_rating", fc, reviewRanges);
        FacetResult resultado2 = facets2.getAllChildren("review_scores_rating");
        results.add(resultado2);

        results.add(counts.getTopChildren(10, "bathrooms"));
        results.add(counts.getTopChildren(10, "bedrooms"));
        
        

        IOUtils.close(indexReader, taxoReader);

        return results;
    }

    public Map<Integer, String> mostrarFacetas(IndexSearcher searcher, Query query) throws IOException {

        DirectoryReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        TaxonomyReader taxoReader = new DirectoryTaxonomyReader(
                FSDirectory.open(Paths.get(indexPath + "_taxo"))
        );

        FacetsCollectorManager fcm = new FacetsCollectorManager();
        FacetsCollector fc = FacetsCollectorManager.search(searcher, query, 100, fcm).facetsCollector();

        Facets facets = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, fc);

        List<FacetResult> all = facets.getAllDims(20);
        if (all.isEmpty()) {
            return null;
        }
        System.out.println("\n--- FACETAS DISPONIBLES ---");
        Map<Integer, String> opciones = new HashMap<>();

        int id = 1;
        for (FacetResult fr : all) {
            System.out.println(id + ") " + fr.dim);
            opciones.put(id, fr.dim);
            id++;
        }
        //añado manualmente price
        if (!all.isEmpty()) {
            System.out.println(id + ") price");
//        System.out.println(facets);
//        System.out.println(facets.getTopChildren(10, "price"));
            opciones.put(id++, "price");
        }

        IOUtils.close(indexReader, taxoReader);
        return opciones;
    }

    public Map<Integer, String> mostrarValoresFaceta(String faceta, IndexSearcher searcher, Query query) throws IOException {
        DirectoryReader ir = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        TaxonomyReader tr = new DirectoryTaxonomyReader(FSDirectory.open(Paths.get(indexPath + "_taxo")));
        FacetsCollectorManager fcm = new FacetsCollectorManager();
        FacetsCollector fc = FacetsCollectorManager.search(searcher, query, 10, fcm).facetsCollector();
        String[] ranges = new  String[] {"0-100", "100-200", "200-500", "500+"};
        Facets facets = new DoubleRangeFacetCounts("price", fc, new DoubleRange(ranges[0], 0, true, 100, false),
                new DoubleRange(ranges[1], 100, true, 200, false),
                new DoubleRange(ranges[2], 200, true, 500, false),
                new DoubleRange(ranges[3], 500, true, Double.MAX_VALUE, true));

        // --- FACETA PRICE: valores manuales ---
        if (faceta.equals("price")) {
            System.out.println("\nValores para la faceta: price");

            FacetResult fr = facets.getTopChildren(3, "price");

            Map<Integer, String> result = new LinkedHashMap<>();

            if (fr == null || fr.labelValues == null) {
                System.out.println("(sin valores disponibles)");
                IOUtils.close(ir, tr);
                return result;
            }

            Map<String, Number> output = new HashMap<>();
            int count = 0;
            while (count < ranges.length) {
                for (LabelAndValue lav : fr.labelValues) {
                    if (lav.label.equals(ranges[count])) {
                        output.put(ranges[count], lav.value);
                        result.put(count+1, lav.label);
                        count++;
                    }
                }
                count++;
            }

            for (int i = 0; i < ranges.length; i++) {
                String key = ranges[i];
                Number num = output.get(key);
                if (num == null) {
                    System.out.println((i+1) + ") " + key + " (" + 0 + ")");
                } else {
                    System.out.println((i+1) + ") " + key + " (" + num + ")");
                }

            }
//
//            Map<Integer, String> vals = new LinkedHashMap<>();
//            vals.put(1, "0-100");
//            vals.put(2, "100-200");
//            vals.put(3, "200-500");
//            vals.put(4, "500+");
//
//            int id = 1;
//            for (Map.Entry<Integer, String> e : vals.entrySet()) {
//                System.out.println(id + ") " + e.getValue());
//                id++;
//            }

//            return vals;
            return result;
        }

        // --- RESTO DE FACETAS NORMALES ---
        fcm = new FacetsCollectorManager();
        fc = FacetsCollectorManager.search(searcher, query, 100, fcm).facetsCollector();

        facets = new FastTaxonomyFacetCounts(tr, facetsConfig, fc);

        FacetResult fr = facets.getTopChildren(3, faceta);

        Map<Integer, String> result = new LinkedHashMap<>();

        System.out.println("\nValores para la faceta: " + faceta);

        if (fr == null || fr.labelValues == null) {
            System.out.println("(sin valores disponibles)");
            IOUtils.close(ir, tr);
            return result;
        }

        int id = 1;
        for (LabelAndValue lv : fr.labelValues) {
            System.out.println(id + ") " + lv.label + " (" + lv.value + ")");
            result.put(id, lv.label);
            id++;
        }

        IOUtils.close(ir, tr);
        return result;
    }


    public TopDocs aplicarFaceta(IndexSearcher searcher, Query baseQuery, String faceta, String valor) throws IOException {
        DrillDownQuery ddq = new DrillDownQuery(facetsConfig, baseQuery);
        ddq.add(faceta, valor);
        return searcher.search(ddq, 3);
    }



    // método para mostrar rangos de precio
    private DoubleRange parsePriceRange(String label) {
        switch (label) {
            case "0-100":
                return new DoubleRange("0-100", 0, true, 100, false);
            case "100-200":
                return new DoubleRange("100-200", 100, true, 200, false);
            case "200-500":
                return new DoubleRange("200-500", 200, true, 500, false);
            case "500+":
                return new DoubleRange("500+", 500, true, Double.MAX_VALUE, true);
        }
        return null;
    }

    //como no es categórica necesita una implementación distinta:
    public TopDocs aplicarFacetaPrice(IndexSearcher searcher, Query original, String priceLabel) throws IOException {

        DoubleRange r = parsePriceRange(priceLabel);

        FacetsCollectorManager fcm = new FacetsCollectorManager();
        FacetsCollector fc = FacetsCollectorManager.search(searcher, original, 10, fcm).facetsCollector();


        Facets facets = new DoubleRangeFacetCounts("price", fc, new DoubleRange[]{ r });

        Query priceQuery = DoublePoint.newRangeQuery(
                "price",
                r.min, 
                r.max  
        );

        BooleanQuery filtered = new BooleanQuery.Builder()
                .add(original, BooleanClause.Occur.MUST)
                .add(priceQuery, BooleanClause.Occur.MUST)
                .build();

        return searcher.search(filtered, 10);
    }

    public Query aplicarFacetaPriceAdicional(Query baseQuery, String priceLabel) {
        DoubleRange r = parsePriceRange(priceLabel);

        Query priceQuery = DoublePoint.newRangeQuery(
                "price",
                r.min,
                r.max
        );

        return new BooleanQuery.Builder()
                .add(baseQuery, BooleanClause.Occur.MUST)   // consulta original
                .add(priceQuery, BooleanClause.Occur.MUST)  // filtro de precio
                .build();
    }

    public Query aplicarFacetaAdicional(Query baseQuery, String faceta, String valor) {
        DrillDownQuery ddq = new DrillDownQuery(facetsConfig, baseQuery);
        ddq.add(faceta, valor);
        return ddq;
    }

    public Query aplicarFacetaJerarquicaAdicional(Query baseQuery,String faceta,String nivel1,String nivel2) {
        DrillDownQuery ddq = new DrillDownQuery(facetsConfig, baseQuery);
        ddq.add(faceta, nivel1, nivel2);
        return ddq;
    }

    public Map<Integer,String> mostrarBarriosDeGrupo(String dim,String grupo,IndexSearcher searcher,Query query) throws IOException {
        DirectoryReader ir = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        TaxonomyReader tr = new DirectoryTaxonomyReader(FSDirectory.open(Paths.get(indexPath + "_taxo")));

        FacetsCollectorManager fcm = new FacetsCollectorManager();
        FacetsCollector fc = FacetsCollectorManager.search(searcher, query, 100, fcm).facetsCollector();

        Facets facets = new FastTaxonomyFacetCounts(tr, facetsConfig, fc);

        FacetResult fr = facets.getTopChildren(20, dim, grupo);

        Map<Integer, String> result = new LinkedHashMap<>();
        if (fr != null && fr.labelValues != null) {
            int id = 1;
            for (LabelAndValue lv : fr.labelValues) {
                System.out.println(id + ") " + lv.label + " (" + lv.value + ")");
                result.put(id, lv.label);
                id++;
            }
        }

        IOUtils.close(ir, tr);
        return result;
    }



    public static void indexSearch(String indexHost, String indexProp, Analyzer analyzer, Integer top)
            throws IOException, ParseException {

        //DirectoryReader readerH = DirectoryReader.open(FSDirectory.open(Paths.get(indexHost)));
        //DirectoryReader readerP = DirectoryReader.open(FSDirectory.open(Paths.get(indexProp)));
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexProp)));
        IndexSearcher searcher = new IndexSearcher(reader);
        //ParallelCompositeReader parallelReader = new ParallelCompositeReader(readerH, readerP);
        //IndexSearcher searcher = new IndexSearcher(parallelReader);
        //IndexSearcher searcher = new IndexSearcher(readerP);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        List<QueryParser> parsers = new ArrayList<>();
        String[] columns = prepareColumns();

        for (String s : columns) {
            parsers.add(new QueryParser(s, analyzer));
        }

        String line = null;

        consultaLoop:
        do {
            System.out.println("Elegir la búsqueda para empezar: ");
            System.out.println("1) Búsqueda básica");
            System.out.println("2) Búsqueda avanzada");
            line = in.readLine();
            if (line.equals("2")) {
                consultaAvanzada(indexProp, analyzer);
            } else {
                System.out.println("Introducir una búsqueda: ");

                line = in.readLine();
                if (line == null || line.length() == -1 || line.equalsIgnoreCase("terminar")) {
                    break;
                }

                // Eliminamos caracteres blancos al inicio y al final
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }

                Query query = new MatchAllDocsQuery();
                //Query originalquery =  new MultiFieldQueryParser(columns.toArray(new String[0]), analyzer).parse(line);
                TopDocs[] hits = new TopDocs[columns.length];

                // Determine how many top hits do we want
                // try {
                for (QueryParser p : parsers) {
                    int idx = parsers.indexOf(p);
                    query = p.parse(line);
                    // originalquery = parsers.get(0).parse(line);
                    hits[idx] = searcher.search(query, top);
                    // System.out.println(hits[idx].totalHits.value() + " documentos encontrados");
                }
                //} catch (ParseException e) {
                //    System.out.println("Error en cadena consulta.");
                //    continue;
                //}

                StoredFields storedFields = searcher.storedFields();
                HashMap<ScoreDoc, Float> topScores = new HashMap<>();

                for (int i = top - 1; i >= 0; i--) {
                    for (TopDocs hit : hits) {
                        if (hit.scoreDocs.length == 0) {
                            continue;
                        } else {
                            if (hit.scoreDocs.length > i) {
                                ScoreDoc sd = hit.scoreDocs[i];
                                if (topScores.size() < top) {
                                    topScores.put(sd, sd.score);
                                    // System.out.println("Add documnet: " + sd.doc + ", Score: " + sd.score);
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
                                        // System.out.println("Remove document: " + min.doc + ", Score: " + min.score);
                                        topScores.put(sd, sd.score);
                                        // System.out.println("Add documnet: " + sd.doc + ", Score: " + sd.score);
                                    }
                                }
                            }
                        }
                    }
                }
                // System.out.println("Top " + topScores.size() + " documentos encontrados: ");

                for (ScoreDoc hit : topScores.keySet()) {
                    System.out.println(hit.doc + ", Score: " + hit.score);
                    Document doc = storedFields.document(hit.doc);
                    System.out.println("--------------------------------------------------");
                    // System.out.println("ID: " + id);
                    System.out.println("name: " + doc.get("name"));
                    System.out.println("property_type: " + doc.get("property_type"));
                    // System.out.println("description: " + doc.get("description"));
                    // System.out.println("amenities: " + doc.get("amenities"));
                    System.out.println("host_about: " + doc.get("host_about"));
                    System.out.println("host_location: " + doc.get("host_location"));
                    System.out.println("host_neighbourhood: " + doc.get("host_neighbourhood"));
                    // System.out.println("host_name " + doc.get("host_name"));
                    System.out.println("price: $" + doc.get("price"));
                    // System.out.println("information " + doc.get("information"));
                    System.out.println();
                }

                if (line.equals("")) {
                    break;
                }

                boolean seguirBusqueda = true;
                while (seguirBusqueda) {
                    Facetas facPreview = new Facetas(indexProp, true);
                    // Esto ya imprime las facetas como hasta ahora.
                    if (facPreview.mostrarFacetas(searcher, query) == null) {
                        System.out.println("NO HAY FACETAS DISPONIBLES");
                        // Mostrar facetas disponibles (para informar)
                        System.out.println("\n                           | ORDENACIONES DISPONIBLES");
                        System.out.println("-----------------------------+------------------------------");


                        System.out.println("                             | 1) Puntuación reseñas (descendente)");
                        System.out.println("                             | 2) Precio ascendente");


                        System.out.println("\nOpciones:");
                        System.out.println("1. Ordenar resultados");
                        System.out.println("2. Realizar otra búsqueda");
                        System.out.println("3. Aplicar consulta avanzada");
                        System.out.println("4. Salir");
                    } else {
                        // Mostrar facetas disponibles (para informar)
                        System.out.println("\n                           | ORDENACIONES DISPONIBLES");
                        System.out.println("-----------------------------+------------------------------");


                        System.out.println("                             | 1) Puntuación reseñas (descendente)");
                        System.out.println("                             | 2) Precio ascendente");


                        System.out.println("\nOpciones:");
                        System.out.println("1. Aplicar facetas");
                        System.out.println("2. Ordenar resultados");
                        System.out.println("3. Realizar otra búsqueda");
                        System.out.println("4. Aplicar consulta avanzada");
                        System.out.println("5. Salir");
                    }


                    String opcionMenu = in.readLine();
                    if (opcionMenu == null) {
                        break consultaLoop;
                    }

                    switch (opcionMenu) {

                        case "1": { // Aplicar facetas (como antes, con múltiples facetas)
                            Query currentQuery = query;
                            Facetas fac = new Facetas(indexProp, true);
                            boolean masFacetas = true;

                            while (masFacetas) {
                                Map<Integer, String> facetas = fac.mostrarFacetas(searcher, currentQuery);

                                if (facetas.isEmpty()) {
                                    System.out.println("No hay facetas disponibles para esta búsqueda");
                                    break;
                                }

                                System.out.println("Seleccione nº de faceta o 0 para salir:");
                                int fsel = Integer.parseInt(in.readLine());
                                if (fsel == 0) {
                                    break;
                                }

                                String facetaElegida = facetas.get(fsel);
                                if (facetaElegida == null) {
                                    System.out.println("Opción de faceta no válida.");
                                    continue;
                                }

                                Map<Integer, String> valores = fac.mostrarValoresFaceta(facetaElegida, searcher, currentQuery);
                                if (valores.isEmpty()) {
                                    System.out.println("No hay valores disponibles para la faceta seleccionada.");
                                    continue;
                                }

                                System.out.println("Seleccione un valor:");
                                int vsel = Integer.parseInt(in.readLine());
                                String valorElegido = valores.get(vsel);

                                if (valorElegido == null) {
                                    System.out.println("Opción de valor no válida.");
                                    continue;
                                }

                                if (facetaElegida.equals("neighbourhood_hier")) {
                                    // 1) mostrar grupos
                                    Map<Integer, String> grupos = fac.mostrarValoresFaceta("neighbourhood_hier", searcher, currentQuery);
                                    System.out.println("Elige un grupo:");
                                    int gSel = Integer.parseInt(in.readLine());
                                    String grupoElegido = grupos.get(gSel);

                                    // 2) mostrar barrios dentro del grupo)

                                    Map<Integer, String> barrios = fac.mostrarBarriosDeGrupo("neighbourhood_hier", grupoElegido, searcher, currentQuery);

                                    System.out.println("Elige un barrio:");
                                    int bSel = Integer.parseInt(in.readLine());
                                    String barrioElegido = barrios.get(bSel);

                                    // 3) aplicar faceta jerárquica
                                    currentQuery = fac.aplicarFacetaJerarquicaAdicional(currentQuery, "neighbourhood_hier", grupoElegido, barrioElegido);
                                } else if (facetaElegida.equals("price")) {
                                    currentQuery = fac.aplicarFacetaPriceAdicional(currentQuery, valorElegido);
                                } else {
                                    currentQuery = fac.aplicarFacetaAdicional(currentQuery, facetaElegida, valorElegido);
                                }

                                TopDocs filtrados = searcher.search(currentQuery, top);
                                System.out.println("\n--- RESULTADOS FILTRADOS ---");

                                for (ScoreDoc sd : filtrados.scoreDocs) {
                                    StoredFields sf = searcher.storedFields();
                                    Document d = sf.document(sd.doc);

                                    System.out.println("Doc " + sd.doc + " score=" + sd.score);
                                    System.out.println("property_type: " + d.get("property_type"));
                                    System.out.println("price: $" + d.get("price"));
                                    System.out.println("description: " + d.get("description"));
                                    // System.out.println("host_location: " + d.get("host_location"));
                                    System.out.println("neighbourhood: " + d.get("neighbourhood_cleansed"));
                                    // System.out.println("amenities: " + d.get("amenities"));
                                    System.out.println("----------------------------------");
                                }

                                System.out.println("¿Quieres añadir otra faceta? (si/no)");
                                String otra = in.readLine();
                                if (!"si".equalsIgnoreCase(otra)) {
                                    masFacetas = false;
                                }
                            }

                            // Después de aplicar facetas, volvemos al menú de opciones
                            break;
                        }

                        case "2": { // Ordenar resultados
                            System.out.println("Elige tipo de ordenación:");
                            System.out.println("1) Puntuación reseñas descendente");
                            System.out.println("2) Precio ascendente");
                            String ord = in.readLine();
                            switch (ord) {
                                case "1":
                                    // Ordenar por review_scores_rating descendente
                                    SortField sf = new SortField("review_scores_rating", SortField.Type.DOUBLE, true); // true = desc
                                    sf.setMissingValue(Double.NEGATIVE_INFINITY); // sin score al final al ordenar desc
                                    Sort orden = new Sort(sf);

                                    TopFieldDocs resultsOrdenados = searcher.search(query, top, orden);
                                    System.out.println("\n--- Resultados ordenados por puntuación de reseña (descendente) ---");
                                    for (ScoreDoc sd : resultsOrdenados.scoreDocs) {
                                        Document d = storedFields.document(sd.doc);
                                        String name = d.get("name");
                                        IndexableField scoreField = d.getField("review_scores_rating");
                                        if (scoreField == null) {
                                            System.out.println((name != null ? name : ("Doc " + sd.doc)) + " - (sin puntuación)");
                                        } else {
                                            double score = scoreField.numericValue().doubleValue();
                                            System.out.println((name != null ? name : ("Doc " + sd.doc)) + " - " + score + " puntos");
                                        }
                                    }
                                    break;

                                case "2":
                                    // Ordenar por precio ascendente
                                    SortField sf2 = new SortField("price", SortField.Type.DOUBLE, false); // false = asc
                                    sf2.setMissingValue(Double.POSITIVE_INFINITY); // sin precio al final
                                    Sort orden2 = new Sort(sf2);

                                    TopFieldDocs resultsOrdenados2 = searcher.search(query, top, orden2);
                                    System.out.println("\n--- Resultados ordenados por precio (ascendente) ---");
                                    for (ScoreDoc sd : resultsOrdenados2.scoreDocs) {
                                        Document d = storedFields.document(sd.doc);
                                        String name = d.get("name");
                                        IndexableField priceField = d.getField("price");
                                        if (priceField == null) {
                                            System.out.println((name != null ? name : ("Doc " + sd.doc)) + " - (sin precio)");
                                        } else {
                                            double price = priceField.numericValue().doubleValue();
                                            System.out.println((name != null ? name : ("Doc " + sd.doc)) + " - " + price + " $");
                                        }
                                    }
                                    break;

                                default:
                                    System.out.println("Opción de ordenación no válida.");
                            }

                            break;
                        }

                        case "3": // Realizar otra búsqueda
                            seguirBusqueda = false;
                            break;

                        case "4": // consulta avanzada
                            seguirBusqueda = false;
                            consultaAvanzada(indexProp, analyzer);
                        case "5": // Salir
                            break consultaLoop;

                        default:
                            System.out.println("Opción no válida.");
                    }
                }
            }

        } while (true) ;

        try {
            //readerP.close();
            //readerH.close();
            //parallelReader.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static String[] prepareColumns() {
        String[] columns = new String[9];
        columns[0] = "host_about";
        columns[1] = "host_location";
        columns[2] = "host_neighbourhood";
        columns[3] = "property_type";
        columns[4] = "bathrooms_text";
        columns[5] = "description";
        columns[6] = "neighbourhood_overview";
        columns[7] = "neighbourhood_cleansed";
        columns[8] = "amenities";

        return columns;
    }

    private static void consultaAvanzada(String index, Analyzer analyzer) throws IOException, ParseException {
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        boolean finishedTyping = false;
        Map<Query, String> filters = new HashMap<>();
        Map<String, String> inputs = new HashMap<>();

        System.out.println("Búsqueda avanzada");
        Map<String, String> options = new HashMap<>();
        options.put("1", "Cualquier lugar");
        options.put("2", "En amenities");
        options.put("3", "En description");
        options.put("4", "En host_about");

        while (!finishedTyping) {

            System.out.println("Elegir uno de los campos que quiere usar: ");
            for (String s: options.keySet()) {
                System.out.println(s + ") " +options.get(s));
            }

            QueryParser parser;
            String chosen = in.readLine();
            String input;
            String key = options.get(chosen);

            switch (key) {
                case "Cualquier lugar" -> {
                    if (inputs.containsKey(key)) {
                        System.out.println("El valor en este campo: ");
                        System.out.println(inputs.get(key));
                    }
                    System.out.println("Cualquier lugar: ");
                    String[] columns = prepareColumns();

                    parser = new MultiFieldQueryParser(columns, analyzer);
                    input = in.readLine();
                    filters.put(parser.parse(input), "1");
                    if (inputs.containsKey(key)) {
                        inputs.put(key, inputs.get(key) + " " + input);
                    } else {
                        inputs.put(key, input);
                    }
                    break;
                }
                case "En amenities" -> {
                    if (inputs.containsKey(key)) {
                        System.out.println("El valor en este campo: ");
                        System.out.println(inputs.get(key));
                    }

                    System.out.println("En amenities: ");
                    input = in.readLine();
                    parser = new QueryParser("amenities", analyzer);
                    filters.put(parser.parse(input), "2");
                    if (inputs.containsKey(key)) {
                        inputs.put(key, inputs.get(key) + " " + input);
                    } else {
                        inputs.put(key, input);
                    }
                    break;
                }
                case "En description" -> {
                    if (inputs.containsKey(key)) {
                        System.out.println("El valor en este campo: ");
                        System.out.println(inputs.get(key));
                    }
                    System.out.println("En description: ");
                    input = in.readLine();
                    parser = new QueryParser("description", analyzer);
                    filters.put(parser.parse(input), "3");
                    if (inputs.containsKey(key)) {
                        inputs.put(key, inputs.get(key) + " " + input);
                    } else {
                        inputs.put(key, input);
                    }
                    break;
                }
                case "En host_about" -> {
                    if (inputs.containsKey(key)) {
                        System.out.println("El valor en este campo: ");
                        System.out.println(inputs.get(key));
                    }

                    System.out.println("En host_about: ");
                    input = in.readLine();
                    parser = new QueryParser("host_about", analyzer);
                    filters.put(parser.parse(input), "4");
                    if (inputs.containsKey(key)) {
                        inputs.put(key, inputs.get(key) + " " + input);
                    } else {
                        inputs.put(key, input);
                    }
                    break;
                }
                default -> {
                    System.out.println("Opción inválida.");
                }
            }
            System.out.println("Quiere usar los otros campos también? (si/no)");

            input = in.readLine();
            if (!input.equalsIgnoreCase("si")) {
                finishedTyping = true;
            }

        }
        System.out.println("Búsqueda avanzada: ");
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        for (Query query : filters.keySet()) {
            String num = filters.get(query);
            switch (num) {
                case "1" ->
                    queryBuilder.add(query, BooleanClause.Occur.SHOULD);
                case "2" ->
                    queryBuilder.add(query, BooleanClause.Occur.MUST);
                case "3" ->
                    queryBuilder.add(query, BooleanClause.Occur.MUST);
                case "4" ->
                    queryBuilder.add(query, BooleanClause.Occur.MUST);
                default -> throw new IllegalStateException("Unexpected value: " + num);
            }

            String campo = options.get(num);
            if (inputs.get(campo) != null) {
                System.out.println(campo + ": ");
                System.out.println(inputs.get(campo));
                inputs.remove(campo);
            }

        }
        BooleanQuery bq = queryBuilder.build();

        TopDocs filtered =  searcher.search(bq, 10);
        System.out.println("\n--- RESULTADOS FILTRADOS ---");

        for (ScoreDoc sd : filtered.scoreDocs) {
            StoredFields sf = searcher.storedFields();
            Document d = sf.document(sd.doc);

            System.out.println("Doc " + sd.doc + " score=" + sd.score);
            System.out.println("property_type: " + d.get("property_type"));
            System.out.println("price: $" + d.get("price"));
            System.out.println("description: " + d.get("description"));
            System.out.println("host_about: " + d.get("host_about"));
            System.out.println("neighbourhood: " + d.get("neighbourhood_cleansed"));
            System.out.println("amenities: " + d.get("amenities"));
            System.out.println("----------------------------------");
        }
    }


    public static void main(String[] args) throws Exception {
        /*
        PARA EJECUTAR:
        java -jar buscador-facetas.jar
        */


//        String csvPath = "doc/listings.csv";        // Ruta al CSV
//        String indexPath = "index/IndexUnico"; //ruta del ÚNICO índice
//        //String propIndexPath = args[1];    // Ruta donde se creará el índice de propiedad
////       // String hostIndexPath = args[2];    // Ruta donde se creará el índice de anfitrión
//        int limit = 500; // Número máximo de filas a indexar (0 = todas)
//        String modo = "otro";

        String modo = "indexar"; // "indexar", "facetas_p", "facetas_h"
        String csvPath = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/practica3/listings.csv";
        String indexPath = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/index/IndexUnico";
        int limit = 500;

        indexSearch(indexPath, indexPath, new StandardAnalyzer(), 3);

        switch (modo) {
            case "indexar" -> {

                // Analizador y similitud de Lucene
                Analyzer analyzer = new StandardAnalyzer();
                Similarity similarity = new ClassicSimilarity();

                // Crear indexadores
                Facetas facetas = new Facetas(indexPath);
                System.out.println("Indexing...");
                int numDocs = facetas.createBothIndices(csvPath, limit, "all");
                System.out.println("Número de documentos indexados : " + numDocs);
                facetas.close();
                //indexSearch(indexPath, indexPath, new StandardAnalyzer(), 10);

                // Indexar ambos índices simultáneamente
                //int numDocsProp = propFacetas.createBothIndices(csvPath, limit, "prop");
                //System.out.println("Número de documentos indexados de propiedad: " + numDocsProp);

                //int numDocsHost = hostFacets.createBothIndices(csvPath, limit, "host");
                //System.out.println("Número de documentos indexados de anfitrión: " + numDocsHost);

                // Cerrar indexadores
                //propFacetas.close();
                //hostFacets.close();
            }
            case "facetas_p" -> {

                String indexP = args[1];
                //String indexPath = args[2];
                String taxoPath = indexP + "_taxo";

                Facetas f = new Facetas(indexP, true);
//                Facetas f = new Facetas(indexP);
                List<FacetResult> results = f.searchProp();

                System.out.println("Neighbourhood: " + results.get(0));
                System.out.println("Amenities: " + results.get(1));
                System.out.println("PropertyType: " + results.get(2));
                System.out.println("Price ranges: " + results.get(3));
                System.out.println("Review scores: " + results.get(4));
                System.out.println("Bathrooms: " + results.get(5));
                System.out.println("Bedrooms: " + results.get(6));

                //indexSearch(hostIndexPath, propIndexPath, new StandardAnalyzer(), 5);


            }
            case "facetas_h" -> {
                String indexP = args[2];
//            String taxoPath = indexPath + "_taxo";
                // String indexPath = hostIndexPath;
                // String taxoPath = hostIndexPath + "_taxo";

                Facetas f = new Facetas(indexP, true);
                List<FacetResult> results = f.searchHost();

                System.out.println("Host neighbourhood: " + results.get(0));
                System.out.println("Host since: " + results.get(1));
                System.out.println("Host name: " + results.get(2));
                System.out.println("Superhost: " + results.get(3));
                System.out.println("Location: " + results.get(4));

            }
        }


    }


}
