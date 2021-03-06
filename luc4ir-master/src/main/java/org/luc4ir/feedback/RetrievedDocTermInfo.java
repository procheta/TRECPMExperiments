/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.luc4ir.feedback;

import org.luc4ir.wvec.WordVec;

/**
 *
 * @author Debasis
 */
public class RetrievedDocTermInfo implements Comparable<RetrievedDocTermInfo> {
    String term;
    int tf;
    int df;
    float wt;   // weight of this term, e.g. the P(w|R) value 
    WordVec wvec;

    public RetrievedDocTermInfo(String term) {
        this.term = term;
    }
    
     public RetrievedDocTermInfo(WordVec wvec) {
        this.wvec = wvec;
    }
     
    public RetrievedDocTermInfo(WordVec wvec, int tf) {
        this.wvec = wvec;
        this.tf = tf;
    }
    
    public RetrievedDocTermInfo(String term, int tf) {
        this.term = term;
        this.tf = tf;
    }

    @Override
    public int compareTo(RetrievedDocTermInfo that) { // descending order
        return this.wt < that.wt? 1 : this.wt == that.wt? 0 : -1;
    }
    
    public float getWeight() { return wt; }
    public void setWeight(float wt) { this.wt = wt; }
    
    public String getTerm() { return term; }    
}

