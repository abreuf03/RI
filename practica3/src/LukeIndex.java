
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

import java.util.HashMap;
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
                        } catch (Exception e) {
                            System.err.println("Error parsing lat/lon: " + e.getMessage());
                        }
                        break;

                    

                    case "price":
                    case "review_scores_rating":
                        try {
                            double value;
                            if (val.isEmpty()) {
                                value = 0.0;
                            } else {
                                value = Double.parseDouble(val.replaceAll("[^0-9.]", ""));
                                doc.add(new DoublePoint(attr, value));
                            }
                            doc.add(new StoredField(attr, value));
                        } catch (Exception e) {
                            System.err.println("Error parsing " + attr + ": " + e.getMessage());
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
                            doc.add(new StoredField("host_since", date.getTime()));
                        } catch (Exception e) {
                            System.err.println("Error parsing date: " + e.getMessage());
                        }
                        break;

                    case "host_is_superhost":
                        if ("t".equals(val)) {
                            doc.add(new TextField(attr, "yes", Field.Store.YES));
                        } else if ("f".equals(val)) {
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Integer> map = new HashMap<String, Integer>();
        List<String> host = lines.getFirst();

        //atributos elena
        map.put("id", host.indexOf("id"));
        map.put("listing_url", host.indexOf("listing_url"));
        map.put("name", host.indexOf("name"));
        map.put("description", host.indexOf("description"));
        map.put("neighborhood_overview", host.indexOf("neighborhood_overview"));
        map.put("neighbourhood_cleansed", host.indexOf("neighbourhood_cleansed"));
        map.put("latitude", host.indexOf("latitude"));
        map.put("longitude", host.indexOf("longitude"));
        map.put("property_type", host.indexOf("property_type"));
        map.put("bathrooms", host.indexOf("bathrooms"));
        map.put("bathrooms_text", host.indexOf("bathrooms_text"));
        map.put("bedrooms", host.indexOf("bedrooms"));
        map.put("amenities", host.indexOf("amenities"));
        map.put("price", host.indexOf("price"));
        map.put("number_of_reviews", host.indexOf("number_of_reviews"));
        map.put("review_scores_rating", host.indexOf("review_scores_rating"));

        for (List<String> row: lines) {
            try {
                indexEntry(map, row);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        return writer.getDocStats().numDocs;
        // eso no funciona :(
//        Scanner inputStream;

//        try {
//            inputStream = new Scanner(file);
//            int count = 0
//            while (inputSteam.hasNext() && count < 101) {
//                String line = inputStream.next();
//                String[] values = line.split(",");
//                lines.add(Arrays.asList(values));
//                count++;
//            }
//            inputStream.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        // the following code lets you iterate through the 2-dimensional array
//        int lineNo = 1;
//        for(List<String> line: lines) {
//            int columnNo = 1;
//            for (String value: line) {
//                System.out.println("Line " + lineNo + " Column " + columnNo + ": " + value);
//                columnNo++;
//            }
//            lineNo++;
//        }

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

//        String csvPath = args[0];      // Ruta al CSV
//        String propIndexPath = args[1];    // Ruta donde se creará el índice de propiedad
//        String hostIndexPath = args[2];    // Ruta donde se creará el índice de anfitrion
//        int limit = Integer.parseInt(args[3]); // Número máximo de filas a indexar (0 = todas)
//        String mode = args[4];
        String csvPath = "/Users/tsan-yuwu/Library/CloudStorage/OneDrive-StudentsRWTHAachenUniversity/Erasmus/RI/practica3/listings.csv";
        String propIndexPath = "./propIndex";
        String hostIndexPath = "./hostIndex";
        int limit = 1000;
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
