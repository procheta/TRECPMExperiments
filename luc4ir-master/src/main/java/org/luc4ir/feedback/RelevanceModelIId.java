/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.feedback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;
import org.luc4ir.retriever.TrecDocRetriever;
import org.luc4ir.wvec.WordVec;
import org.luc4ir.wvec.WordVecs;

/**
 *
 * @author Debasis
 */
class KLDivScoreComparator implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc a, ScoreDoc b) {
        return a.score < b.score ? -1 : a.score == b.score ? 0 : 1;
    }
}

public class RelevanceModelIId {

    TrecDocRetriever retriever;
    TRECQuery trecQuery;
    TopDocs topDocs;
    Properties prop;
    float mixingLambda;
    int numTopDocs;
    RetrievedDocsTermStats retrievedDocsTermStats;
    int nterms;
    float fbweight;
    IndexReader reader;
    WordVecs wvecs;
    QueryWordVecs qwvecs;
    QueryVecComposer composer;
    static final float TERM_SEL_DF_THRESH = 0.8f;

    public RelevanceModelIId(TrecDocRetriever retriever, TRECQuery trecQuery, TopDocs topDocs) throws Exception {
        this.prop = retriever.getProperties();
        this.retriever = retriever;
        this.trecQuery = trecQuery;
        this.topDocs = topDocs;
        numTopDocs = Integer.parseInt(prop.getProperty("fdbk.numtopdocs", "5"));
        mixingLambda = Float.parseFloat(prop.getProperty("fdbk.lambda", "0.8"));

        nterms = Integer.parseInt(prop.getProperty("rlm.qe.nterms", "10"));
        fbweight = Float.parseFloat(prop.getProperty("rlm.qe.newterms.wt", "0.2"));

        if (wvecs == null) {
            wvecs = new WordVecs(prop);
        }
        composer = new QueryVecComposer(trecQuery, wvecs, prop);
    }

    public RetrievedDocsTermStats getRetrievedDocsTermStats() {
        return this.retrievedDocsTermStats;
    }

    public void buildTermStats(String retrieveMode) throws Exception {
        retrievedDocsTermStats = new RetrievedDocsTermStats(retriever.getReader(), topDocs, numTopDocs);
        retrievedDocsTermStats.buildAllStats(retrieveMode);
        reader = retrievedDocsTermStats.getReader();
    }

    float mixTfIdf(RetrievedDocTermInfo w) {
        return mixingLambda * w.tf / (float) retrievedDocsTermStats.sumTf
                + (1 - mixingLambda) * w.df / retrievedDocsTermStats.sumDf;
    }

    float mixTfIdf(RetrievedDocTermInfo w, PerDocTermVector docvec) {
        RetrievedDocTermInfo wGlobalInfo = retrievedDocsTermStats.termStats.get(w.getTerm());
        return mixingLambda * w.tf / (float) docvec.sum_tf
                + (1 - mixingLambda) * wGlobalInfo.df / retrievedDocsTermStats.sumDf;
    }

    public void computeFdbkWeights(String retrieveMode) throws Exception {
        float p_q;
        float p_w;

        buildTermStats(retrieveMode);

        /* For each w \in V (vocab of top docs),
         * compute f(w) = \sum_{q \in qwvecs} K(w,q) */
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            float total_p_q = 0;
            RetrievedDocTermInfo w = e.getValue();
            p_w = mixTfIdf(w);

            Set<Term> qTerms = this.trecQuery.getQueryTerms();
            for (Term qTerm : qTerms) {

                // Get query term frequency
                RetrievedDocTermInfo qtermInfo = retrievedDocsTermStats.getTermStats(qTerm.toString());
                if (qtermInfo == null) {
                    //System.err.println("No KDE for query term: " + qTerm.toString());
                    continue;
                }
                p_q = qtermInfo.tf / (float) retrievedDocsTermStats.sumTf; //mixTfIdf(qtermInfo); //

                total_p_q += Math.log(1 + p_q);
            }
            w.wt = p_w * (float) Math.exp(total_p_q - 1);
        }
    }

    public void prepareQueryVector() {
        if (qwvecs == null) {
            qwvecs = composer.getQueryWordVecs();
        }
    }

    public void computeKDE(String retrieveMode) throws Exception {
        float p_q;
        float p_w;

        buildTermStats(retrieveMode);
        prepareQueryVector();

        /* For each w \in V (vocab of top docs),
         * compute f(w) = \sum_{q \in qwvecs} K(w,q) */
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            float total_p_q = 0;
            RetrievedDocTermInfo w = e.getValue();
            p_w = mixTfIdf(w);

            for (WordVec qwvec : qwvecs.getVecs()) {
                if (qwvec == null) {
                    continue; // a very rare case where a query term is OOV
                } else {
                    //System.out.println("KDE for "+ qwvec.getWord());
                }
                // Get query term frequency
                RetrievedDocTermInfo qtermInfo = retrievedDocsTermStats.getTermStats(qwvec);
                if (qtermInfo == null) {
                    //System.err.println("No KDE for query term: " + qwvec.getWord());
                    continue;
                }
                p_q = qtermInfo.tf / (float) retrievedDocsTermStats.sumTf; //mixTfIdf(qtermInfo); //

                total_p_q += Math.log(1 + p_q);
            }
            w.wt = p_w * (float) Math.exp(total_p_q - 1);
        }
    }

    public TopDocs rerankDocs() {
        ScoreDoc[] klDivScoreDocs = new ScoreDoc[this.topDocs.scoreDocs.length];
        float klDiv;
        float p_w_D;    // P(w|D) for this doc D
        final float EPSILON = 0.0001f;

        // For each document
        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            klDiv = 0;
            klDivScoreDocs[i] = new ScoreDoc(topDocs.scoreDocs[i].doc, klDiv);
            PerDocTermVector docVector = this.retrievedDocsTermStats.docTermVecs.get(i);

            // For each v \in V (vocab of top ranked documents)
            for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
                RetrievedDocTermInfo w = e.getValue();

                float ntf = docVector.getNormalizedTf(w.getTerm());
                if (ntf == 0) {
                    ntf = EPSILON;
                }
                p_w_D = ntf;

                klDiv += w.wt * Math.log(w.wt / p_w_D);
            }
            klDivScoreDocs[i].score = klDiv;
        }

        // Sort the scoredocs in ascending order of the KL-Div scores
        Arrays.sort(klDivScoreDocs, new KLDivScoreComparator());

        TopDocs rerankedDocs = new TopDocs(topDocs.totalHits, klDivScoreDocs, klDivScoreDocs[klDivScoreDocs.length - 1].score);
        return rerankedDocs;
    }

    public float getQueryClarity() {
        float klDiv = 0;
        float p_w_C;
        // For each v \in V (vocab of top ranked documents)
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            p_w_C = w.df / retrievedDocsTermStats.sumDf;
            klDiv += w.wt * Math.log(w.wt / p_w_C);
        }
        return klDiv;
    }

    public float getQueryClarity(IndexReader reader) throws Exception {
        float klDiv = 0;
        float p_w_C;
        // For each v \in V (vocab of top ranked documents)
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            double sumCf = (double) reader.getSumTotalTermFreq(TrecDocIndexer.ARTICLE_TITLE);
            double cf = reader.totalTermFreq(new Term(TrecDocIndexer.ARTICLE_TITLE, w.getTerm()));
            p_w_C = (float) (cf / sumCf);
            klDiv += w.wt * Math.log(w.wt / p_w_C);
        }
        return klDiv;
    }

    // Implement post-RLM query expansion. Set the term weights
    // according to the values of f(w).
    public TRECQuery expandQuery(String retrieveMode, String weighted) throws Exception {

        // The calling sequence has to make sure that the top docs are already
        // reranked by KL-div
        // Now reestimate relevance model on the reranked docs this time
        // for QE.
        computeFdbkWeights(retrieveMode);

        TRECQuery expandedQuery = new TRECQuery(this.trecQuery);
        Set<Term> origTerms = new HashSet<Term>();
        this.trecQuery.luceneQuery.extractTerms(origTerms);
        expandedQuery.luceneQuery = new BooleanQuery();
        HashMap<String, String> origQueryWordStrings = new HashMap<>();

        float normalizationFactor = 0;

        List<RetrievedDocTermInfo> termStats = new ArrayList<>();
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            double docfreq = reader.docFreq(new Term(TrecDocIndexer.ALL_STR, w.getTerm()));
            if (retrieveMode.equals("flat")) {
                docfreq = reader.docFreq(new Term(TrecDocIndexer.ALL_STR, w.getTerm()));
            } else {
                docfreq = reader.docFreq(new Term(TrecDocIndexer.ABSTRACT_TEXT, w.getTerm()));
                if (docfreq == 0) {
                    docfreq = reader.docFreq(new Term(TrecDocIndexer.MESH_HEADING, w.getTerm()));
                }
                if (docfreq == 0) {
                    docfreq = reader.docFreq(new Term(TrecDocIndexer.ARTICLE_TITLE, w.getTerm()));
                }
            }

            w.wt = w.wt
                    * (float) Math.log(
                            reader.numDocs() / (float) docfreq);
            termStats.add(w);
            normalizationFactor += w.wt;
        }

        // Normalize the weights
        for (Map.Entry<String, RetrievedDocTermInfo> e : retrievedDocsTermStats.termStats.entrySet()) {
            RetrievedDocTermInfo w = e.getValue();
            w.wt = w.wt / normalizationFactor;
        }

        Collections.sort(termStats);
        if (retrieveMode.equals("flat") && weighted.equals("false")) {
            for (Term t : origTerms) {
                origQueryWordStrings.put(t.text(), t.text());
                TermQuery tq = new TermQuery(t);
                //+++POST_SIGIR review: Assigned weights according to RLM post QE
                tq.setBoost((1 - fbweight) / (float) origTerms.size());
                //tq.setBoost((1-fbweight));
                //---POST_SIGIR review
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);
            }
        } else {
            expandedQuery.luceneQuery = this.trecQuery.luceneQuery;
        }

        int nTermsAdded = 0;
        for (RetrievedDocTermInfo selTerm : termStats) {

            String thisTerm = selTerm.getTerm();
            if (origQueryWordStrings.get(thisTerm) != null) {
                continue;
            }

            if (retrieveMode.equals("flat")) {
                TermQuery tq = new TermQuery(new Term(TrecDocIndexer.ALL_STR, thisTerm));
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);
                tq.setBoost(fbweight);
            } else {
                TermQuery tq = new TermQuery(new Term(TrecDocIndexer.ABSTRACT_TEXT, thisTerm));
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);

                tq.setBoost(fbweight * selTerm.wt);

                tq = new TermQuery(new Term(TrecDocIndexer.MESH_HEADING, thisTerm));
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);
                tq.setBoost(fbweight * selTerm.wt);

                tq = new TermQuery(new Term(TrecDocIndexer.CHEMICAL_LIST, thisTerm));
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);
                tq.setBoost(fbweight * selTerm.wt);

                tq = new TermQuery(new Term(TrecDocIndexer.ARTICLE_TITLE, thisTerm));
                ((BooleanQuery) expandedQuery.luceneQuery).add(tq, BooleanClause.Occur.SHOULD);
                tq.setBoost(fbweight * selTerm.wt);
            }

            //+++POST_SIGIR review: Assigned weights according to RLM post QE
            //tq.setBoost(fbweight);
            //tq.setBoost(fbweight*selTerm.wt);
            //tq.setBoost(selTerm.wt);
            //---POST_SIGIR review
            nTermsAdded++;
            if (nTermsAdded >= nterms) {
                break;
            }
        }

        return expandedQuery;
    }

}
