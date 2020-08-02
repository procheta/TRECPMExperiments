/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.sampleIndex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.checkerframework.checker.index.qual.SameLen;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.trec.TRECQueryParser;
import org.luc4ir.indexing.TrecDocIndexer;

/**
 *
 * @author Procheta
 */
public class SampleIndexer {

    IndexReader reader;
    IndexSearcher searcher;
    Properties prop;
    Analyzer analyzer;
    int numWanted;
    IndexWriter writer;

    public SampleIndexer(String propFile) throws IOException, Exception {

        TrecDocIndexer indexer = new TrecDocIndexer(propFile);
        prop = indexer.getProperties();
        Analyzer analyzer = new EnglishAnalyzer();
        numWanted = 2000;
        String indexPath = prop.getProperty("index");
        File indexDir = new File(indexPath);
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity(1, 0.5f));
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        indexPath = prop.getProperty("sampleIndex");
        indexDir = new File(indexPath);
        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);

    }

    public List<TRECQuery> constructQueries() throws Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, analyzer, false);
        parser.parse();
        return parser.getQueries();
    }

    TopDocs retrieve(TRECQuery query) throws IOException {
        return searcher.search(query.getLuceneQueryObj(), numWanted);
    }

    public void createSampleIndex() throws Exception {

        List<TRECQuery> queries = constructQueries();

        for (TRECQuery query : queries) {
            System.out.println("Executing query: " + query.getLuceneQueryObj());
            TopDocs topDocs = retrieve(query);
            int length = topDocs.scoreDocs.length;
            for (int i = 0; i < length; i++) {
                Document doc = reader.document(topDocs.scoreDocs[i].doc);
                writer.addDocument(doc);
            }
        }
        writer.close();
    }

    public void createSampleIndex_v2() throws Exception {

        HashSet<String> docIds = new HashSet<>();
        for (int i = 0; i < reader.numDocs(); i++) {
            Document doc = reader.document(i);
            if (!docIds.contains(doc.get("id"))) {
                docIds.add(doc.get("id"));
                writer.addDocument(doc);
            }
        }

        writer.close();
    }

    public static void main(String[] args) throws Exception {

        SampleIndexer si = new SampleIndexer("C:\\Users\\Procheta\\Downloads\\luc4ir-master\\src\\main\\java\\org\\luc4ir\\retriever\\init.properties");
        si.createSampleIndex();
        si.createSampleIndex_v2();
    }
}
