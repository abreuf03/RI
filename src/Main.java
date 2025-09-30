import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;

import java.util.*;
import java.lang.*;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import java.io.InputStream ;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection ;
import java.net.URL;
import org.apache.tika.metadata.Metadata ;
import org.apache.tika.parser.ParseContext ;
import org.apache.tika.sax.BodyContentHandler ;
import org.apache.tika.sax.LinkContentHandler ;
import org.apache.tika.sax.TeeContentHandler ;
import org.apache.tika.sax.ToHTMLContentHandler ;
import org.xml.sax.ContentHandler ;
import org.apache.tika.language.detect.LanguageDetector ;
import org.apache.tika.language.detect.LanguageResult ;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;

public class Main{
    public static void main(String[] args) throws Exception {

        String path = args[0];
        String option = args[1];
        File dir = new File(path);

        for(File file : dir.listFiles()){
            if(option.equals("-d")){
                optionD(file);
            }
            else if(option.equals("-t")){
                optionT(file);
            }
            else if(option.equals("-l")){
                optionL(file);
            }


        }
        

    }

    //Primera parte : "-d Realizar de forma automática una tabla que contenga el nombre del fichero, tipo, codificación e idioma."
    private static void optionD( File file ) throws Exception{

        Tika tika = new Tika();
        Metadata metadata = new Metadata();
        InputStream is = new FileInputStream(file);
        ParseContext parsecontext = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); 

        //con autodetectParser obtenemos los metadatos y contenido del doc
        parser.parse(is,handler,metadata,parsecontext);

        //obtenemos nombre
        String name = file.getName();

        //obtenemos tipo
        String type = tika.detect(is);

        //obtenemos codificación
        String encoding = metadata.get("Content-Encoding"); //mal solo saca null 

        //obtenemos el idioma
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        LanguageResult result = detector.detect(handler.toString());
        String language = result.getLanguage();

        //imprimimos en forma de tabla
        System.out.println(name + "\t" + type + "\t" + encoding + "\t" + language);



    }

    //Segunda parte : "-l Todos los enlaces que se pueden extraer de cada documento"
    private static void optionL(File file) throws Exception{
        LinkContentHandler linkhandler = new LinkContentHandler();
        ContentHandler texthandler = new BodyContentHandler(-1);
        TeeContentHandler teehandler = new TeeContentHandler(linkhandler,texthandler);
        FileInputStream is = new FileInputStream(file);

        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();

        parser.parse(is,teehandler,metadata,parseContext);

        System.out.println("links: \n" + linkhandler.getLinks());

    }

    // Metodo para ordenar el HashMap por valor
    static HashMap<String, Integer> sortByValue(HashMap<String, Integer> hm) {
        List<Map.Entry<String, Integer> > list =
            new LinkedList<Map.Entry<String,Integer> >(hm.entrySet());

            // Ordenamos decreciente por valor
            Collections.sort(list, new Comparator<Map.Entry<String, Integer> >() {
                public int compare(Map.Entry<String,Integer> o1, Map.Entry<String, Integer> o2) {
                    return (o2.getValue()).compareTo(o1.getValue());
                
                }
            });

            // Recreamos un HashMap con los elementos ordenados
            HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Integer> aa: list) {
                temp.put(aa.getKey(), aa.getValue());
            }
            return temp;
    }

    
    // Tercera parte: "-t Generar un fichero CSV con las palabras y sus frecuencias en order decreciete de frecuencia, si el fichero de Yerma es txt."
    private static void optionT(File file) throws Exception {

        // Creamos una instancia de Tika con la configuracion por defecto
        Tika tika = new Tika();

        // Se parsean todos los ficheros pasados como arguemnto y se extrae el contenido
        for (String file: args) {
            File f = new File(file);

            // Obtenemos el tipo de fichero
            String type = tika.detect(f);

            // Chequeamos si el fichero es de texto plano
            if (type == "text/plain") {
                // Creamos un HashMap para almacenar las palabras y sus frecuencias (sin orden)
                HashMap<String, Integer> hm = new HashMap<String, Integer>();
                InputStream is = new FileInputStream(f);
                String text = tika.parseToString(is);

                // Seperamos el texto en palabras, contamos sus frecuencias y almacenamos en el HashMap
                for (String word: text.split("\\W+")){
                    word = word.toLowerCase();
                    if (hm.containsKey(word)) {
                        hm.put(word, hm.get(word) + 1);
                    } else {
                        hm.put(word, 1);
                    }
                }
                // Preparamos el HashMap ordenado por frecuencia
                Map<String, Integer> hm1 = sortByValue(hm);

                String filename = "frecuencia.csv";
                // Creamos el fichero csv, chequeando si ya existe
                try {
                    File output = new File(filename);
                    if (output.createNewFile()) {
                        System.out.println("File created: " + output.getName());
                    } else {
                        System.out.println("File already exists.");
                    }
                } catch (IOException e) {
                    System.out.println("An error occurred.");
                    e.printStackTrace(); // Print error details
                }
                // Luego, escribimos el HashMap ordenado en el fichero creado
                try {
                    FileWriter writer = new FileWriter(filename);
                    // Escribimos la primera fila en el fichero
                    writer.write("Text;Size\n");
                    for (String word: hm1.keySet()) {
                        // Escribimos cada palabra y su frecuencia en el fichero
                        writer.write(word + ";" + hm1.get(word) + "\n");
                    }
                    writer.close();
                    System.out.println("Successsfully wrote to the file.");
                } catch (IOException e) {
                    System.out.println("Error occurred while writng.");
                    e.printStackTrace();
                }

            } else {
                System.out.println("El fichero " + file + " no es de texto plano");
            }
            
            
        }
    }
        
}

