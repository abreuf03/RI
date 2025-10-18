
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.commongrams.CommonGramsFilter;
import org.apache.lucene.analysis.core.*;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.analysis.miscellaneous.*;
import org.apache.lucene.analysis.snowball.*;
import org.apache.lucene.analysis.shingle.*;
import org.apache.lucene.analysis.ngram.*;
import org.apache.lucene.analysis.synonym.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.SpanishStemmer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class tokenFilters {

    public static void main(String[] args) throws Exception {
        //String texto = "Los gatos felices juegan con pelotas azules.";
        String texto = "Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota en que su padre lo llevó a conocer el hielo.";

         Analyzer analyzer = CustomAnalyzer.builder()
                .withTokenizer("standard")
                .addTokenFilter("lowercase")
                // .addTokenFilter("stop", "words", "spanish")
                // .addTokenFilter("snowballPorter", "language", "Spanish")
                //.addTokenFilter("commongrams", "words", "spanish")
                .addTokenFilter("synonymGraph", "synonyms", "synonyms.txt", "format", "solr")
                .build();

        TokenStream tokenStream = analyzer.tokenStream("campo", new StringReader(texto));
        CharTermAttribute termAttr = tokenStream.addAttribute(CharTermAttribute.class);

        tokenStream.reset();
        System.out.println("Tokens generados:");
        while (tokenStream.incrementToken()) {
            System.out.println(" - " + termAttr.toString());
        }
        tokenStream.end();
        tokenStream.close();
        analyzer.close();
    }
}

