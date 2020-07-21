/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.indexing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author Debasis
 */
public class TrecDocIndexer {

    Properties prop;
    File indexDir;
    IndexWriter writer;
    Analyzer analyzer;
    List<String> stopwords;
    int pass;

    static final public String FIELD_ID = "id_info";
    static final public String FIELD_ANALYZED_CONTENT = "words";  // Standard analyzer w/o stopwords.
    static final public String DATE_CREATED = "datecreated";
    static final public String DATE_COMPLETED = "datecompleted";
    static final public String DATE_REVISED = "revised";
    static final public String ARTICLE_TITLE = "articletitle";
    static final public String PAGE_NUM = "pagenum";
    static final public String AUTHOR_LIST = "authlist";
   static final public String MEDLINE_JOURN = "medlinejourm";
   static final public String CHEMICAL_LIST = "chemlist";
    static final public String MESH_HEADING = "meshheading";
    static final public String INTERVENTION = "intervention";
    static final public String ELIGIBILITY = "eligibilty";


    static final public String PUBMED_DATA = "pubmeddata";
    static final public String ABSTRACT_TEXT = "abstract";
    static final public String ALL_STR = "all_str";

    protected List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);
        String line;

        try (FileReader fr = new FileReader(stopFile);
                BufferedReader br = new BufferedReader(fr)) {
            while ((line = br.readLine()) != null) {
                stopwords.add(line.trim());
            }
            br.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    Analyzer constructAnalyzer() {
        Analyzer eanalyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(buildStopwordList("stopfile"))); // default analyzer
        return eanalyzer;
    }

    public TrecDocIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        analyzer = constructAnalyzer();
        String indexPath = prop.getProperty("index");
        indexDir = new File(indexPath);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public Properties getProperties() {
        return prop;
    }

    void processAll() throws Exception {
        System.out.println("Indexing TREC collection...");

        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);

        indexAll();

        writer.close();
    }

    public File getIndexDir() {
        return indexDir;
    }

    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }

        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            } else {
                indexFile(f);
            }
        }
    }

    Document constructDoc(String fname,String PMID,String abst,String art_title,String meshs) throws IOException {
        Document doc = new Document();
        doc.add(new Field(FIELD_ID,fname, Field.Store.YES, Field.Index.NOT_ANALYZED));
        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(ARTICLE_TITLE, art_title, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        doc.add(new Field(ABSTRACT_TEXT, abst, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        doc.add(new Field(MESH_HEADING, meshs, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
	
  //      System.out.println("Indexing " + id);

        return doc;
    }

    void indexFile(File file) throws Exception {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        Document doc;

        System.out.println("Indexing file: " + file.getName());
	String fname = file.getName();
        StringBuffer txtbuff = new StringBuffer();
        while ((line = br.readLine()) != null) {
            txtbuff.append(line).append("\n");
        }
        String content = txtbuff.toString();

        org.jsoup.nodes.Document jdoc = Jsoup.parse(content);
        Elements Articles = jdoc.select("clinical_study");
   

        for (int i = 0; i < Articles.size(); i++) {
            String text_b = "";
            String delim = ",";
            String meshs = "";
            String abst = "";
            ArrayList<String> eligiList = new ArrayList<String>();
            ArrayList<String> meshlis = new ArrayList<String>();

            String PMID = Articles.get(i).select("id_info").first().text();
	    try{
     //       System.out.println("The id number is " +PMID);
            Elements authorsil = Articles.get(i).select("eligibility");
	    if(authorsil != null)
	{
	     Elements authorsi =authorsil.select("criteria");
            for (Element text_block : authorsi) {
                text_b = text_block.text();
                eligiList.add(text_b);
                text_b = "";
            }
            text_b = String.join(delim, eligiList);
	//    System.out.println("The value of text_block of  eligibility "+text_b);
	  
	}
            Element abstarct = Articles.get(i).select("detailed_description").first();
            if (abstarct != null) {
		abst=abstarct.text();
	    }

            Elements MeshHeadingElements = Articles.get(i).select("mesh_term");
            if (MeshHeadingElements != null) {
                for (Element element : MeshHeadingElements) {
                    meshs = element.text();
                    meshlis.add(meshs);
                    meshs = "";
                }
                meshs = String.join(delim, meshlis);
            }

            Element intervent = Articles.get(i).select("intervention").first();
            Element brief_summary = Articles.get(i).select("brief_summary").first();
            Element title = Articles.get(i).select("brief_title").first();
            String brief_summ="";
	    String Inte="";
	    String art_title="";	
            if(title != null)
		art_title= title.text();
	    if(brief_summary != null)
		brief_summ=brief_summary.text();
	    if(intervent != null)
		Inte=intervent.text();
	    abst=abst+" "+brief_summ+" "+Inte+" "+text_b;
	  doc = constructDoc(fname,PMID,abst,art_title,meshs);
                  writer.addDocument(doc);
 }
catch(Exception ex)
{
	System.out.println("The document which caused error are "+PMID);
}
       } //*/
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java TrecDocIndexer <prop-file>");
            args[0] = "C:\\Users\\Procheta\\Downloads\\luc4ir-master\\src\\main\\java\\org\\luc4ir\\retriever/init.properties";
        }

        try {
            System.out.println("We are startif from hwre");
            TrecDocIndexer indexer = new TrecDocIndexer(args[0]);
            indexer.processAll();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
