
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.commongrams.CommonGramsFilter;
import org.apache.lucene.analysis.core.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.analysis.miscellaneous.*;
import org.apache.lucene.analysis.snowball.*;
import org.apache.lucene.analysis.shingle.*;
import org.apache.lucene.analysis.ngram.*;
import org.apache.lucene.analysis.synonym.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.tartarus.snowball.ext.EnglishStemmer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class tokenFilters {

    public static void main(String[] args) throws Exception {
        //String texto = "Los gatos felices juegan con pelotas azules.";
        String texto = "The nineteenth century dislike of romanticism is the rage of Caliban not seeing his own face in a glass. The moral life of man forms part of the subject- matter of the artist, but the morality of art consists in the perfect use of an imperfect medium.";


        // Crea un Tokenizer de base
        Tokenizer tokenizer = new StandardTokenizer();
        tokenizer.setReader(new StringReader(texto));

        // Aplica distintos filtros (puedes comentar/descomentar)
        TokenStream tokenStream = new LowerCaseFilter(tokenizer);
       // tokenStream = new StopFilter(tokenStream, EnglishAnalyzer.getDefaultStopSet());
       // tokenStream = new SnowballFilter(tokenStream, new EnglishStemmer());
        //tokenStream = new ShingleFilter(tokenStream, 2);
        //tokenStream = new EdgeNGramTokenFilter(tokenStream, 4);
       // tokenStream = new NGramTokenFilter(tokenStream, 2, 4, false);
        //tokenStream = new CommonGramsFilter(tokenStream, EnglishAnalyzer.getDefaultStopSet());
        // tokenStream = SynonymFilter 

        // Imprime tokens resultantes
        CharTermAttribute termAttr = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        System.out.println("Tokens generados:");
        while (tokenStream.incrementToken()) {
            System.out.println(" - " + termAttr.toString());
        }
        tokenStream.end();
        tokenStream.close();
    }
}

