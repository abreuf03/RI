
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opencsv.*;
import com.opencsv.exceptions.CsvValidationException;


public class LukeIndex {

    //boolean createIndex = true;
    private IndexWriter writer;
    private String indexPath;
    private Analyzer analyzer;
    private Similarity similarity;

    public LukeIndex(String indexPath, Analyzer analyzer, Similarity similarity, String mode) throws IOException {
        this.indexPath = indexPath;
        this.analyzer = analyzer;
        this.similarity = similarity;
        FSDirectory idxDir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        
        if(mode.equals("crear")){
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        }
        else{
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }
        
        config.setSimilarity(similarity);
        writer = new IndexWriter(idxDir, config);
    }
/* 
    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();

        for (String attr: map.keySet()) {
            doc.add(new StringField(attr, values.get(map.get(attr)), Field.Store.YES));
            System.out.println("Attribute: "+attr+", Value: "+values.get(map.get(attr)));
        }
//        Field idField = new StringField("id", "fff", Field.Store.YES);
//        ...
//        doc.add(idField);
        return doc;
    }*/
    private Document getDocument(Map<String, Integer> map, List<String> values) {
        Document doc = new Document();

        for (String attr : map.keySet()) {
            String val = values.get(map.get(attr));
             if (attr.equals(val)) { // No añadir los nombres de campos
                continue;
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
                            if (val == null || val.isBlank()) break; // ignorar valores vacíos

                                // Limpiar comillas, signo $ y comas
                                String cleanVal = val.replace("\"", "")
                                                    .replace("$", "")
                                                    .replace(",", "")
                                                    .trim();

                                double value = Double.parseDouble(cleanVal);

                                // Indexar correctamente
                                doc.add(new DoublePoint("price", value));                             // para búsquedas
                                doc.add(new StoredField("price", value));                             // para recuperar con document.getField()
                                doc.add(new DoubleDocValuesField("price", Double.doubleToRawLongBits(value)));  // para ordenar
                                //debugging : System.out.println("Indexando price: '" + val + "' → " + value);

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing price: '" + val + "'");
                        }
                        break;

                    case "review_scores_rating":
                        try {
                            if (val == null || val.isBlank()) break; // ignorar valores vacíos

                            // Limpiar comillas y espacios
                            String cleanVal = val.replace("\"", "").trim();

                            double value = Double.parseDouble(cleanVal);

                            // Indexar correctamente
                            doc.add(new DoublePoint("review_scores_rating", value));
                            doc.add(new StoredField("review_scores_rating", value));
                            doc.add(new DoubleDocValuesField("review_scores_rating", Double.doubleToRawLongBits(value)));
                            //debugging : System.out.println("Indexando review_scores_rating: '" + val + "' → " + value);

                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing review_scores_rating: '" + val + "'");
                        }
                        break;


                    case "bedrooms":
                    case "number_of_reviews":
                    case "bathrooms":
                        try {
                            int num;
                            if (val.isEmpty()) {
                                num = 0;
                            } else {
                                double parsed = Double.parseDouble(val);
                                num = (int) parsed;
                            }
                            doc.add(new IntPoint(attr, num));
                            doc.add(new StoredField(attr, num));
                        } catch (Exception e) {
                            System.err.println("Error parsing int field " + attr + ": " + e.getMessage());
                        }
                        break;

                    case "description":
                    case "name":
                    case "neighborhood_overview":
                    case "bathrooms_text":
                    case "host_neighbourhood":
                    case "host_about":
                    case "host_location":
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

                    case "host_since":
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        try {
                            Date date = sdf.parse(val);
                            doc.add(new LongPoint("host_since", date.getTime()));
                            doc.add(new StoredField("host_since", sdf.format(date)));
                        } catch (Exception e) {
                            System.err.println("Error parsing date: " + e.getMessage());
                        }
                        break;

                    case "host_is_superhost":
                        if ("t".equals(val)) {
                            doc.add(new TextField(attr, "yes", Field.Store.YES));
                        } else if ("f".equals(val) || val.isEmpty()) {
                            doc.add(new TextField(attr, "no", Field.Store.YES));
                        } else {
                        System.out.println("Invalid value for host_is_superhost: " + val);
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
        System.out.println("Indexing...");
        Document doc = getDocument(map, values);
        try {
            writer.addDocument(doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int createHostIndex(String docPath, int limit) throws Exception {
        // Read the file before starting indexing
        File file = new File(docPath);

        List<List<String>> lines = new ArrayList<>();
        try {

            // Create an object of filereader
            // class with CSV file as a parameter.
            FileReader filereader = new FileReader(file);

            // create csvReader object passing
            // file reader as a parameter
            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;
            List<String> rows = new ArrayList<>();
            int count = 0;
            // we are going to read data line by line
            while ((nextRecord = csvReader.readNext()) != null && count < limit) {
                for (String cell : nextRecord) {
//                    System.out.print(cell+ " ");
                    rows.add(cell);
                }
                lines.add(rows);
                rows = new ArrayList<>();
                System.out.println();
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Integer> map = new HashMap<String, Integer>();
        List<String> host = lines.getFirst();
        map.put("host_url", host.indexOf("host_url"));
        map.put("host_name", host.indexOf("host_name"));
        map.put("host_since", host.indexOf("host_since"));
        map.put("host_location", host.indexOf("host_location"));
        map.put("host_about", host.indexOf("host_about"));
        map.put("host_response_time", host.indexOf("host_response_time"));
        map.put("host_is_superhost", host.indexOf("host_is_superhost"));
        map.put("host_neighbourhood", host.indexOf("host_neighbourhood"));

        for (List<String> row : lines) {
            try {
                indexEntry(map, row);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        return writer.getDocStats().numDocs;
    }

    public int createPropertyIndex(String docPath, int limit) throws Exception {
        File file = new File(docPath);
        List<List<String>> lines = new ArrayList<>();

        // Leer CSV con OpenCSV
        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {
            String[] nextRecord;
            int count = 0;
            while ((nextRecord = csvReader.readNext()) != null && (limit == 0 || count < limit)) {
                lines.add(Arrays.asList(nextRecord));
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (lines.isEmpty()) {
            System.err.println("El CSV está vacío.");
            return 0;
        }

        // Cabecera y mapa de índices
        List<String> header = lines.get(0);
        Map<String, Integer> map = new HashMap<>();
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

        // Indexar solo las filas, saltando la cabecera
        for (List<String> row : lines.subList(1, lines.size())) {
            try {
                indexEntry(map, row);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return writer.getDocStats().numDocs;
    }


    public void close() throws CorruptIndexException, IOException{
        try {
            writer.commit();
            writer.close();
        } catch (IOException e) {
            System.out.println("Error closing the index.");
        }
    }

    public static void main(String[] args) throws Exception {
//        if (args.length < 4) {
//            System.out.println("Uso: java LukeIndex <ruta_csv> <ruta_indice_propiedad> <ruta_indice_anfitrion> <límite_filas>");
//            return;
//        }

        String csvPath ="/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/practica3/listings.csv";      // Ruta al CSV
        String propIndexPath = "./prop";    // Ruta donde se creará el índice de propiedad
        String hostIndexPath = "./host";    // Ruta donde se creará el índice de anfitrion
        int limit = 10; // Número máximo de filas a indexar (0 = todas)
        String mode = "crear";

        // Analizador y similaridad de Lucene
        Analyzer analyzer = new EnglishAnalyzer();
        Similarity similarity = new ClassicSimilarity();

        // Crear indexador
        LukeIndex propIndexador = new LukeIndex(propIndexPath, analyzer, similarity, mode);
        LukeIndex hostIndexador = new LukeIndex(hostIndexPath, analyzer, similarity, mode);

        // Crear índice
        int numDocs = propIndexador.createPropertyIndex(csvPath, limit);
        System.out.println("Número de documentos indexados de propiedad: " + numDocs);
        numDocs = hostIndexador.createHostIndex(csvPath, limit);
        System.out.println("Número de documentos indexados de anfitrión: " + numDocs);

        // Cerrar indexador
        propIndexador.close();
        hostIndexador.close();

    }

}
