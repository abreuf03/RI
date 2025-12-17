import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.index.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import org.apache.lucene.classification.Classifier;
import org.apache.lucene.classification.KNearestFuzzyClassifier;
import org.apache.lucene.classification.SimpleNaiveBayesClassifier;
import org.apache.lucene.classification.BM25NBClassifier;
import org.apache.lucene.classification.KNearestNeighborClassifier;
import org.apache.lucene.classification.utils.DatasetSplitter;
import org.apache.lucene.classification.utils.ConfusionMatrixGenerator;
import org.apache.lucene.classification.utils.ConfusionMatrixGenerator.ConfusionMatrix;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.opencsv.*;
public class Clasificadores {

    private IndexWriter writer;
    private String indexPath;
    private Analyzer analyzer;
    private Similarity similarity;
    private static FacetsConfig facetsConfig;
    private DirectoryTaxonomyWriter taxoWriter;

    public Clasificadores(String indexPath) throws IOException {
        this.indexPath = indexPath;
        Directory indexDir = FSDirectory.open(Paths.get(indexPath));
        this.analyzer = new EnglishAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        this.writer = new IndexWriter(indexDir, config);
        Directory taxoDir = FSDirectory.open(Paths.get(indexPath + "_taxo"));
        this.taxoWriter = new DirectoryTaxonomyWriter(taxoDir);

        facetsConfig = new FacetsConfig();
        facetsConfig.setMultiValued("amenities", true);
        facetsConfig.setMultiValued("host_is_superhost", false);
        facetsConfig.setHierarchical("host_since", true);
        facetsConfig.setHierarchical("neighbourhood_hier", true);
        facetsConfig.setMultiValued("property_type", false);
        facetsConfig.setMultiValued("bathrooms", false);
        facetsConfig.setMultiValued("bedrooms", false);

    }


    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();

        for (String attr : map.keySet()) {
            String val = values.get(map.get(attr));
             if (attr.equals(val)) { // No añadir los nombres de campos
                ;
            } else {

                switch (attr) {
                    case "latitude":
                    case "longitude":
                        
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
                                                            
                                doc.add(new DoublePoint("price", value));                             // para búsquedas
                                doc.add(new StoredField("price", value));                             // para recuperar con document.getField()
                                doc.add(new DoubleDocValuesField("price", value));  // para ordenar
                            }

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing price: '" + val + "'");
                        }
                        break;


                    case "number_of_reviews":
                        try {
                             String cleaned = val.replaceAll("[^0-9.]", "").trim();
                            // Ignorar si el valor está vacío
                            if (val != null && !val.trim().isEmpty()) {
                                int num = (int) Double.parseDouble(cleaned);
                                // Campos para búsquedas y recuperación
                                doc.add(new IntPoint(attr, num));
                                doc.add(new StoredField(attr, num));
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG bathrooms EMPTY: " + val);
                            System.err.println("Error parsing int field " + attr + ": " + e.getMessage());
                        }
                        break;
                    
                    case "bathrooms":
                    
                        try {
                             String cleaned = val.replaceAll("[^0-9.]", "").trim();
                            // Ignorar si el valor está vacío
                            if (val != null && !val.trim().isEmpty()) {
                                int num = (int) Double.parseDouble(cleaned);
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
                        String cleanData = val.replaceAll("<[^>]+>", ""); //eliminar etiquetas de HTML
                        if(!val.isEmpty() && val != null){
                            cleanData = val
                                        .replaceAll("<[^>]+>", "") // eliminar HTML
                                        .replaceAll("[^a-zA-Z ]", " ") // quitar símbolos
                                        .replaceAll("\\s+", " ") // espacios duplicados
                                        .trim()
                                        .toLowerCase();
                             // Campo de texto CON term vectors
                            FieldType tvType = new FieldType(TextField.TYPE_STORED);
                            tvType.setStoreTermVectors(true);
                            tvType.setStoreTermVectorPositions(true);
                            tvType.setStoreTermVectorOffsets(true);
                            doc.add(new Field(attr, cleanData, tvType));
                            //System.out.println("DEBUG: Añadiendo description = " + cleanData.substring(0, Math.min(60, cleanData.length())));

                        }

                        break;
                    case "neighborhood_overview":
                        String cleanNeigh = val.replaceAll("<[^>]+>", "");
                        if (val != null && !val.isEmpty()) {
                            FieldType tvType = new FieldType(TextField.TYPE_STORED);
                            tvType.setStoreTermVectors(true);
                            tvType.setStoreTermVectorPositions(true);
                            tvType.setStoreTermVectorOffsets(true);
                            doc.add(new Field("neighborhood_overview", cleanNeigh, tvType));
                        }
                        break;
                    case "name":
                    case "bathrooms_text":
                    case "host_about":
                    case "host_response_time":
                        String clean_Data = val
                        .replaceAll("<[^>]+>", ""); //eliminar etiquetas de HTML
                        doc.add(new TextField(attr, clean_Data, Field.Store.YES));
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

                            if(!cleanData.isEmpty()) { 
                                doc.add(new TextField(attr, cleanData, Field.Store.YES));
        
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
                        break;


                    //PRACTICA 6

                    case "room_type":
                        String rt = (val == null || val.trim().isEmpty()) ? "UNKNOWN" : val.trim().toLowerCase().replaceAll("[\\s/]+", "_");

                        //System.out.println("DEBUG: Añadiendo room_type_class = " + rt);
                        doc.add(new StringField("room_type_class", rt, Field.Store.YES));
                        //lo pide el data set splitter
                        doc.add(new SortedDocValuesField("room_type_class", new BytesRef(rt)));
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
                            
                            String classVal = val.trim().replaceAll("\\s+", "_").toLowerCase();
                            doc.add(new StringField("neighbourhood_group_class", classVal, Field.Store.YES));
                            doc.add(new SortedDocValuesField("neighbourhood_group_class", new BytesRef(classVal)));
   
                        }
                        break;

                    case "property_type":
                        if(!val.isEmpty()) {
                            doc.add(new FacetField("property_type", val));
                            
                            String classval = classifyPropType(val);
                    
                            doc.add(new StringField("property_type_class", classval, Field.Store.YES));
                            doc.add(new SortedDocValuesField("property_type_class", new BytesRef(classval)));
                            
                            
                            doc.add(new StringField(attr, val, Field.Store.YES));
                        }
                        break;

                    case "bedrooms":
                        try {
                            String cleaned = val.replaceAll("[^0-9.]", "").trim();
                            if (val != null && !cleaned.isEmpty()) { // Usar cleaned aquí
                                int num = (int) Double.parseDouble(cleaned);
                                
                                doc.add(new IntPoint(attr, num));
                                doc.add(new StoredField(attr, num));
                                doc.add(new NumericDocValuesField(attr, num));
                                doc.add(new FacetField(attr, Integer.toString(num)));

                                String class_val = classifyBedrooms(num);
                                
                                doc.add(new StringField("bedrooms_class",class_val , Field.Store.YES));
                                doc.add(new SortedDocValuesField("bedrooms_class", new BytesRef(class_val)));
                            } /*else {
                                
                                String class_val = classifyBedrooms(0); // O manejar como UNKNOWN
                                doc.add(new StringField("bedrooms_class",class_val , Field.Store.YES));
                                doc.add(new SortedDocValuesField("bedrooms_class", new BytesRef(class_val)));
                            }*/
                        } catch (Exception e) {
                            // Esto captura la excepción si el valor no es un número limpio
                        }
                        break;
                    
                    case "review_scores_rating":
                        try {
                            if (!val.isEmpty()) {
                                value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                                
                                doc.add(new DoublePoint("review_scores_rating", value));
                                doc.add(new StoredField("review_scores_rating", value));
                                doc.add(new DoubleDocValuesField("review_scores_rating", value));

                                String class_val = classifyReviewScore(value);
                                
                                doc.add(new StringField("rating_class", class_val, Field.Store.YES));
                                
                                doc.add(new SortedDocValuesField("rating_class", new BytesRef(class_val)));
                            }

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing review_scores_rating: '" + val + "'");
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
            map.put("room_type", header.indexOf("room_type"));


        }


        // Iterar filas y crear ambos documentos
        for (int i = 1; i < lines.size(); i++) { // saltar header
            List<String> row = lines.get(i);
            indexEntry(map, row);
        }

        writer.commit();
        taxoWriter.commit();

        return writer.getDocStats().numDocs;
    }

    private String classifyPropType(String value){
        if (value == null) {
            return "other";
        }

        String valueLower = value.toLowerCase();

        if (valueLower.contains("rental unit")) {
            return "rental unit";
        } else if (valueLower.contains("condo")) {
            return "condo";
        } else if (valueLower.contains("guesthouse")) {
            return "guesthouse";
        } else if (valueLower.contains("guest suite")) {
            return "guest suite";
        } else if (valueLower.contains("home")) {
            return "home";
        } else if (valueLower.contains("hotel")) {
            return "hotel";
        } else if (valueLower.contains("bungalow")) {
            return "bungalow";
        } else if (valueLower.contains("villa")) {
            return "villa";
        } else if (valueLower.contains("townhouse")) {
            return "townhouse";
        } else if (valueLower.contains("loft")) {
            return "loft";
        } else if (valueLower.contains("serviced apartment")) {
            return "serviced apartment";
        } else {
            return "other";
        }
    }

    private String classifyBedrooms(int numBedrooms) {
        if (numBedrooms <= 0) {
            return "0";
        } else if (numBedrooms == 1) {
            return "1";
        } else if (numBedrooms == 2) {
            return "2";
        } else if (numBedrooms == 3) {
            return "3";
        } else if (numBedrooms == 4) {
            return "4";
        } else {
            return "5+";
        }
    }

    private String classifyReviewScore(double rating) {
        if (rating <= 4.0) {
            return "low";
        } else if (rating < 4.3) {
            return "medium";
        } else if (rating < 4.7) {
            return "high";
        } else {
            return "excellent";
        }
    }

    public void splitIndexForTask_roomType(String originalIndexPath) throws Exception {
        Directory originalDir = FSDirectory.open(Paths.get(originalIndexPath));
        DirectoryReader originalReader = DirectoryReader.open(originalDir);

        Directory trainDir = FSDirectory.open(Paths.get(originalIndexPath + "_roomtype_train"));
        Directory testDir  = FSDirectory.open(Paths.get(originalIndexPath + "_roomtype_test"));
        Directory cvDir    = FSDirectory.open(Paths.get(originalIndexPath + "_roomtype_cv"));

        DatasetSplitter splitter = new DatasetSplitter(0.2, 0.0); 
        Analyzer classificationAnalyzer = new EnglishAnalyzer();

        splitter.split(
            originalReader,
            trainDir,
            testDir,
            cvDir,
            classificationAnalyzer,
            true,                   // usingTermVectors
            "room_type_class",      // classFieldName
            "description",          // textFieldName
            // ** CAMBIO CRUCIAL: Pasar la cadena directamente **
            "room_type_class"       // Campo almacenado adicional
        );

        originalReader.close();
        originalDir.close();
        trainDir.close();
        testDir.close();
        cvDir.close();
    }


    public void splitIndexForTask_Bedrooms(String originalIndexPath) throws Exception {
        Directory originalDir = FSDirectory.open(Paths.get(originalIndexPath));
        DirectoryReader originalReader = DirectoryReader.open(originalDir);

        Directory trainDir = FSDirectory.open(Paths.get(originalIndexPath + "_bedrooms_train"));
        Directory testDir  = FSDirectory.open(Paths.get(originalIndexPath + "_bedrooms_test"));
        Directory cvDir    = FSDirectory.open(Paths.get(originalIndexPath + "_bedrooms_cv"));

        DatasetSplitter splitter = new DatasetSplitter(0.3, 0.0);
        Analyzer classificationAnalyzer = new EnglishAnalyzer();

        splitter.split(
            originalReader,
            trainDir,
            testDir,
            cvDir,
            classificationAnalyzer,
            true,
            "bedrooms_class",  // classFieldName
            "description",     // textFieldName
            // ** CAMBIO: Campo de clase ALMACENADO **
            "bedrooms_class"
        );

        originalReader.close();
        originalDir.close();
        trainDir.close();
        testDir.close();
        cvDir.close();
    }

    public void splitIndexForTask_Neighbourhood(String originalIndexPath) throws Exception {
        Directory originalDir = FSDirectory.open(Paths.get(originalIndexPath));
        DirectoryReader originalReader = DirectoryReader.open(originalDir);

        Directory trainDir = FSDirectory.open(Paths.get(originalIndexPath + "_neighbourhood_train"));
        Directory testDir  = FSDirectory.open(Paths.get(originalIndexPath + "_neighbourhood_test"));
        Directory cvDir    = FSDirectory.open(Paths.get(originalIndexPath + "_neighbourhood_cv"));

        DatasetSplitter splitter = new DatasetSplitter(0.3, 0.0);
        //Analyzer classificationAnalyzer = new EnglishAnalyzer();
        Analyzer textAnalyzer = new StandardAnalyzer();
        Map<String, Analyzer> perField = new HashMap<>();
        perField.put("neighbourhood_group_class", new KeywordAnalyzer());
        Analyzer classificationAnalyzer = new PerFieldAnalyzerWrapper(textAnalyzer, perField);

        splitter.split(
            originalReader,
            trainDir,
            testDir,
            cvDir,
            classificationAnalyzer,
            true,
            "neighbourhood_group_class", // classFieldName
            "neighborhood_overview",               // textFieldName
            // ** CAMBIO: Campo de clase ALMACENADO **
            "neighbourhood_group_class"
        );

        originalReader.close();
        originalDir.close();
        trainDir.close();
        testDir.close();
        cvDir.close();
    }


    public void splitIndexForTask_PropertyType(String originalIndexPath) throws Exception {
        Directory originalDir = FSDirectory.open(Paths.get(originalIndexPath));
        DirectoryReader originalReader = DirectoryReader.open(originalDir);

        Directory trainDir = FSDirectory.open(Paths.get(originalIndexPath + "_proptype_train"));
        Directory testDir  = FSDirectory.open(Paths.get(originalIndexPath + "_proptype_test"));
        Directory cvDir    = FSDirectory.open(Paths.get(originalIndexPath + "_proptype_cv"));

        DatasetSplitter splitter = new DatasetSplitter(0.3, 0.0);
        Analyzer classificationAnalyzer = new EnglishAnalyzer();

        splitter.split(
            originalReader,
            trainDir,
            testDir,
            cvDir,
            classificationAnalyzer,
            true,
            "property_type_class", // classFieldName
            "description",         // textFieldName
            // ** CAMBIO: Campo de clase ALMACENADO **
            "property_type_class"
        );

        originalReader.close();
        originalDir.close();
        trainDir.close();
        testDir.close();
        cvDir.close();
    }

    public void splitIndexForTask_Rating(String originalIndexPath) throws Exception {
        Directory originalDir = FSDirectory.open(Paths.get(originalIndexPath));
        DirectoryReader originalReader = DirectoryReader.open(originalDir);

        Directory trainDir = FSDirectory.open(Paths.get(originalIndexPath + "_rating_train"));
        Directory testDir  = FSDirectory.open(Paths.get(originalIndexPath + "_rating_test"));
        Directory cvDir    = FSDirectory.open(Paths.get(originalIndexPath + "_rating_cv"));

        DatasetSplitter splitter = new DatasetSplitter(0.3, 0.0);
        Analyzer classificationAnalyzer = new StandardAnalyzer();

        splitter.split(
            originalReader,
            trainDir,
            testDir,
            cvDir,
            classificationAnalyzer,
            true,
            "rating_class", // classFieldName
            "description",  // textFieldName
            // ** CAMBIO: Campo de clase ALMACENADO **
            "rating_class"
        );

        originalReader.close();
        originalDir.close();
        trainDir.close();
        testDir.close();
        cvDir.close();
    }


    private void imprimirMatriz(ConfusionMatrix confusionMatrix) {
        
        System.out.println("Confusion Matrix:");
        System.out.println(confusionMatrix); 

        Map<String, Map<String, Long>> matrix = confusionMatrix.getLinearizedMatrix();
        Set<String> labels = new LinkedHashSet<>();
        labels.addAll(matrix.keySet());
 
        for (Map<String, Long> row : matrix.values()) {
            labels.addAll(row.keySet());
        }

        for (String label : labels) {
            double precision = confusionMatrix.getPrecision(label);
            double recall    = confusionMatrix.getRecall(label);
            double f1        = confusionMatrix.getF1Measure(label);

            System.out.println("Clase: " + label);
            System.out.println("  Precision(" + label + ") = " + precision);
            System.out.println("  Recall(" + label + ")    = " + recall);
            System.out.println("  F1(" + label + ")        = " + f1);
        }

        System.out.println("Docs evaluados = " + confusionMatrix.getNumberOfEvaluatedDocs());
        System.out.println("---------------------------------------------------------");
    }

    public void probarClasificadores(String baseIndexPath,String taskSuffix,String classFieldName,String textFieldName) throws Exception {

        String trainPath = baseIndexPath + "_" + taskSuffix + "_train";
        String testPath  = baseIndexPath + "_" + taskSuffix + "_test";

        Directory trainDir = FSDirectory.open(Paths.get(trainPath));
        Directory testDir  = FSDirectory.open(Paths.get(testPath));

        DirectoryReader trainReader = DirectoryReader.open(trainDir);
        DirectoryReader testReader  = DirectoryReader.open(testDir);

        // DEBUG
        /* 
        for (int i = 0; i < Math.min(5, testReader.maxDoc()); i++) {
            Document d = testReader.storedFields().document(i);
            System.out.println("TEST DOC " + i + ": " +
                d.get(classFieldName) + " / " + d.get("description"));
        }


        // === DEBUG: INFO BÁSICA DE LOS ÍNDICES ===
        System.out.println("------ DEBUG " + taskSuffix + " ------");
        System.out.println("Train docs: " + trainReader.numDocs());
        System.out.println("Test docs : " + testReader.numDocs());

        int docsConClaseYTexto = 0;

        for (LeafReaderContext leafCtx : testReader.leaves()) {
            LeafReader leaf = leafCtx.reader();
            Terms classTerms = leaf.terms(classFieldName);
            Terms textTerms = leaf.terms(textFieldName);

            if (classTerms != null && textTerms != null) {
                docsConClaseYTexto += leaf.maxDoc(); // hay docs con ambos campos indexados
            }
        }


        System.out.println("Docs test con [" + classFieldName + " + " + textFieldName + "]: " + docsConClaseYTexto);
        System.out.println("--------------------------------------");
        // === FIN DEBUG ===*/
        //Analyzer classificationAnalyzer = new StandardAnalyzer(); //probar otros

        Analyzer textAnalyzer = new EnglishAnalyzer();
        Map<String, Analyzer> perField = new HashMap<>();
        perField.put(classFieldName, new KeywordAnalyzer());
        Analyzer classificationAnalyzer = new PerFieldAnalyzerWrapper(textAnalyzer, perField);
        

        //SimpleNaiveBayesClassifier 
        Classifier<BytesRef> nbClassifier = new SimpleNaiveBayesClassifier(trainReader,classificationAnalyzer, null,classFieldName,textFieldName);

        System.out.println("=== TAREA: " + taskSuffix + " :: SimpleNaiveBayesClassifier ===");
        ConfusionMatrix cmNB = ConfusionMatrixGenerator.getConfusionMatrix(testReader,nbClassifier,classFieldName,textFieldName,100000);
        imprimirMatriz(cmNB);

        //BM25NBClassifier
        Classifier<BytesRef> bm25Classifier = new BM25NBClassifier(trainReader,classificationAnalyzer,null,classFieldName,textFieldName);

        System.out.println("=== TAREA: " + taskSuffix + " :: BM25NBClassifier ===");
        ConfusionMatrix cmBM25 = ConfusionMatrixGenerator.getConfusionMatrix(testReader,bm25Classifier,classFieldName,textFieldName,100000);
        imprimirMatriz(cmBM25);

        //KNearestNeighborClassifier
        int k = 5; //ajustar he puesto 10 por poner
        int minDocsFreq = 1;
        int maxDocs = 1000;

        Classifier<BytesRef> knnClassifier =new KNearestNeighborClassifier(trainReader,null,classificationAnalyzer,
                        null,k,minDocsFreq,maxDocs,classFieldName,textFieldName);

       // System.out.println("=== TAREA: " + taskSuffix + " :: KNearestNeighborClassifier ===");
       // ConfusionMatrix cmKNN = ConfusionMatrixGenerator.getConfusionMatrix(testReader,knnClassifier,classFieldName,textFieldName,100000);
       // imprimirMatriz(cmKNN);

        Classifier<BytesRef> kFuzzyClassifier = new KNearestFuzzyClassifier(trainReader, null, classificationAnalyzer, 
            null, k,classFieldName,textFieldName);
        
        System.out.println("=== TAREA: " + taskSuffix + " :: KFuzzyClassifier ===");
        ConfusionMatrix cmKfuzzy = ConfusionMatrixGenerator.getConfusionMatrix(testReader,kFuzzyClassifier,classFieldName,textFieldName,100000);
        imprimirMatriz(cmKfuzzy);
        
        trainReader.close();
        testReader.close();
        trainDir.close();
        testDir.close();
    }

    //borrar luego es para comprobar
    /* 
    public void checkFieldCoverage(String indexPath) throws Exception {
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        DirectoryReader reader = DirectoryReader.open(dir);

        int total = reader.numDocs();
        int conDesc = 0, conRoomType = 0, conAmbos = 0;

        for (int i = 0; i < total; i++) {
            Document d = reader.storedFields().document(i);
            boolean desc = d.get("description") != null && !d.get("description").isEmpty();
            boolean cls = d.get("room_type_class") != null && !d.get("room_type_class").isEmpty();

            if (desc) conDesc++;
            if (cls) conRoomType++;
            if (desc && cls) conAmbos++;
        }

        System.out.println("Docs totales: " + total);
        System.out.println("Con description: " + conDesc);
        System.out.println("Con room_type_class: " + conRoomType);
        System.out.println("Con ambos: " + conAmbos);

        reader.close();
        dir.close();
    }*/


    // Borra una carpeta y todo su contenido (subcarpetas y ficheros)
    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        // Primero borramos contenido, luego la carpeta raíz
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    System.err.println("No se pudo borrar: " + p + " -> " + e.getMessage());
                }
            });
    }

    public static void borrarIndicesGenerados(String baseIndexPath) throws IOException {
        Path base = Paths.get(baseIndexPath);
        Path parent = base.getParent();           // "index"
        String prefix = base.getFileName().toString(); // "IndexUnico"

        if (parent == null || !Files.exists(parent)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, prefix + "*")) {
            for (Path p : stream) {
                System.out.println("Borrando índice: " + p.toString());
                deleteDirectoryRecursively(p);
            }
        }
    }



    public static void main(String[] args) throws Exception {
        
        if (args.length < 2) {
            System.err.println("Uso: java Clasificadores <rutaIndex> <rutaCSV>");
            System.err.println("Ejemplo: java Clasificadores index/IndexUnico doc/listings.csv");
            return;
        }

        String indexPath = args[0]; // "index/IndexUnico"
        String csvPath   = args[1]; // "doc/listings.csv"

        
        int limit = 1000;
       
        borrarIndicesGenerados(indexPath);

        Clasificadores c = new Clasificadores(indexPath);
        c.createBothIndices(csvPath, limit, "prop");
        c.close(); 

        Clasificadores splitter = new Clasificadores(indexPath);
        splitter.splitIndexForTask_roomType(indexPath);
        splitter.splitIndexForTask_Bedrooms(indexPath);
        splitter.splitIndexForTask_PropertyType(indexPath);
        splitter.splitIndexForTask_Neighbourhood(indexPath);
        splitter.splitIndexForTask_Rating(indexPath);
        splitter.close();

        Clasificadores eval = new Clasificadores(indexPath);
        //eval.probarClasificadores(indexPath, "roomtype", "room_type_class", "description");
        //eval.probarClasificadores(indexPath, "bedrooms", "bedrooms_class", "description");
        eval.probarClasificadores(indexPath, "proptype", "property_type_class", "description");
        //eval.probarClasificadores(indexPath, "neighbourhood", "neighbourhood_group_class", "neighborhood_overview");
        //eval.probarClasificadores(indexPath, "rating", "rating_class", "description");
        eval.close();
    }




}
