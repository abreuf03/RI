import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.morph.Token;
import org.apache.lucene.analysis.pattern.PatternTokenizer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.FrenchStemmer;
import org.tartarus.snowball.ext.SpanishStemmer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.analysis.pattern.PatternCaptureGroupTokenFilter;

public class XAnalyzer extends Analyzer {
    private String language;
    private CharArraySet stopwords;

    public XAnalyzer(String language) { 
        this.language = language.toLowerCase();

        switch (this.language) {
            case "spanish":
                stopwords = SpanishAnalyzer.getDefaultStopSet();
                break;

            case "french":
                stopwords = FrenchAnalyzer.getDefaultStopSet();
                break;

            case "english":
            default:
                stopwords = EnglishAnalyzer.getDefaultStopSet();
                break;
        }
    }


    @Override
    protected TokenStreamComponents createComponents(String fieldName){
        
        //incluimos @ y # como tokens
        Tokenizer source = new WhitespaceTokenizer();
        //TokenStream filter = new PatternCaptureGroupTokenFilter(source, true, Pattern.compile("(@\\w+)|(#\\w+)"));

        TokenStream filter = new StopFilter(source, stopwords); //eliminamos palabras vacías 
        filter = new LowerCaseFilter(filter);

        switch (this.language) { //aplicamos stem distinguiendo por idioma igual que con las palabras vacías
            case "spanish":
                filter = new SnowballFilter(filter, new SpanishStemmer());
                break;

            case "french":
                filter = new SnowballFilter(filter, new FrenchStemmer());
                break;

            case "english":
            default:
                filter = new SnowballFilter(filter, new EnglishStemmer());
                break;
        }

        //incluimos sinonimos de emojis
        /*:)=feliz,contento,alegre
            :(=triste,desanimado
            T_T=llorar,llorando,deprimido
            XD=reir,risa,divertido
            >:(=enfadado,molesto
            :D=euforico,entusiasmado
            .3.=beso
            <3=corazon,amor
            </3=desamor,decepcion
            :O=sorpresa */
        SynonymMap.Builder builder = new SynonymMap.Builder(true);

        builder.add(new CharsRef(":)"), new CharsRef("feliz"), true);
        builder.add(new CharsRef(":)"), new CharsRef("contento"), true);
        builder.add(new CharsRef(":)"), new CharsRef("alegre"), true);

        builder.add(new CharsRef(":("), new CharsRef("triste"), true);
        builder.add(new CharsRef(":("), new CharsRef("desanimado"), true);

        builder.add(new CharsRef("xd"), new CharsRef("reir"), true);
        builder.add(new CharsRef("xd"), new CharsRef("divertido"), true);

        builder.add(new CharsRef("t_t"), new CharsRef("llorar"), true);
        builder.add(new CharsRef("t_t"), new CharsRef("deprimido"), true);

        builder.add(new CharsRef(">:("), new CharsRef("enfadado"), true);
        builder.add(new CharsRef(">:("), new CharsRef("molesto"), true);     

        builder.add(new CharsRef(":d"), new CharsRef("euforico"), true); 
        builder.add(new CharsRef(":d"), new CharsRef("entusiasmado"), true); 

        builder.add(new CharsRef(".3."), new CharsRef("beso"), true); 

        builder.add(new CharsRef("<3"), new CharsRef("corazon"), true);
        builder.add(new CharsRef("<3"), new CharsRef("amor"), true);  

        builder.add(new CharsRef("</3"), new CharsRef("desamor"), true); 
        builder.add(new CharsRef("<3"), new CharsRef("decepcion"), true); 

        builder.add(new CharsRef(":o"), new CharsRef("sorpresa"), true); 

        SynonymMap emojis;
        try {
           emojis = builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Error al construir SynonymMap", e);
        }

        filter = new SynonymGraphFilter(filter, emojis, true);

        return new TokenStreamComponents(source, filter);
    }

    public static void main(String[] args) throws IOException {
        // Idioma a usar (puede ser "english", "spanish" o "french")
        XAnalyzer analyzer = new XAnalyzer("spanish");

        // Texto de prueba
        String texto = "@usuario hoy me siento :( porque es #domingo";

        // Analizar y mostrar tokens
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
