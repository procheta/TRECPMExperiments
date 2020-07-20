/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.trec;

/**
 *
 * @author Debasis
 */
import java.io.FileReader;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.util.*;
import java.io.*;
import java.io.FileNotFoundException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.luc4ir.indexing.TrecDocIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class TRECQueryParser extends DefaultHandler {
    StringBuffer        buff;      // Accumulation buffer for storing the current topic
    String              fileName;
    TRECQuery           query;
    Analyzer            analyzer;
    StandardQueryParser queryParser;
    
    public List<TRECQuery>  queries;
    final static String[] tags = {"id", "title", "desc", "narr"};

    public TRECQueryParser(String fileName, Analyzer analyzer) throws SAXException {
        this.fileName = fileName;
        this.analyzer = analyzer;
        buff = new StringBuffer();
        queries = new LinkedList<>();
        queryParser = new StandardQueryParser(analyzer);
    }

    public StandardQueryParser getQueryParser() { return queryParser; }
    
    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(false);
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(fileName, this);
    }

    public List<TRECQuery> getQueries() { return queries; }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("top")) {
                query = new TRECQuery();
                queries.add(query);
            }
            else
                buff = new StringBuffer();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    /*public Query constructLuceneQueryObj_v1(TRECQuery trecQuery) throws QueryNodeException {    
	String S= trecQuery.title+" "+trecQuery.desc+" "+trecQuery.narr;    
        Query luceneQuery = queryParser.parse(S, TrecDocIndexer.ABSTRACT_TEXT,TrecDocIndexer.ARTICLE_TITLE,TrecDocIndexer.CHEMICAL_LIST,TrecDocIndexer.MESH_HEADING);
        return luceneQuery;
    }*/
    public Query constructLuceneQueryObj(TRECQuery trecQuery) throws QueryNodeException {    

		
	String st[] = trecQuery.title.split("\\s+");
        BooleanQuery query = new BooleanQuery();
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.ARTICLE_TITLE, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
/*	Term term1 = new Term(TrecDocIndexer.ARTICLE_TITLE, trecQuery.title);
        //create the term query object
        Query query1 = new TermQuery(term1);*/
	st = trecQuery.narr.split("\\s+");
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.ABSTRACT_TEXT, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
	st = trecQuery.desc.split("\\s+");
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.MESH_HEADING, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
		st = trecQuery.desc.split("\\s+");
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.ABSTRACT_TEXT, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
			st = trecQuery.title.split("\\s+");
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.ABSTRACT_TEXT, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }	
			st = trecQuery.desc.split("\\s+");
        for (String s : st) {

            Term term1 = new Term(TrecDocIndexer.CHEMICAL_LIST, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
	

	//System.out.println("The query is "+query);
	return query;
    }
	
	String analyze(String query, String stopFileName) throws Exception {
        StringBuffer buff = new StringBuffer();
        TokenStream stream = new EnglishAnalyzer(StopFilter.makeStopSet(buildStopwordList(stopFileName))).tokenStream("dummy", new StringReader(query));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            buff.append(term).append(" ");
        }
        stream.end();
        stream.close();
       return buff.toString().trim();
    }
	public List<String> buildStopwordList(String stopwordFileName) throws FileNotFoundException, IOException {
        List<String> stopwords = new ArrayList();
        String stopFile = stopwordFileName;
        String line;

        FileReader fr = new FileReader(stopFile);
        BufferedReader br = new BufferedReader(fr);
        while ((line = br.readLine()) != null) {
            stopwords.add(line.trim());
        }
        br.close();
        return stopwords;
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("title"))
                query.title = analyze(buff.toString(),"/home/irlab/Documents/share/sonal/luc4ir-master/stop.txt");            
            else if (qName.equalsIgnoreCase("desc"))
                query.desc = analyze(buff.toString(),"/home/irlab/Documents/share/sonal/luc4ir-master/stop.txt");
            else if (qName.equalsIgnoreCase("narr"))
                query.narr = analyze(buff.toString(),"/home/irlab/Documents/share/sonal/luc4ir-master/stop.txt");
            else if (qName.equalsIgnoreCase("num"))
                query.id = buff.toString();
            else if (qName.equalsIgnoreCase("top"))
	
                query.luceneQuery = constructLuceneQueryObj(query);            
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }

        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            String queryFile = prop.getProperty("query.file");
            
            TRECQueryParser parser = new TRECQueryParser(queryFile, new EnglishAnalyzer());
            parser.parse();
            for (TRECQuery q : parser.queries) {
                System.out.println(q);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}    
