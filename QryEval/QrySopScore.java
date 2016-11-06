/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean){
      return this.getScoreRankedBoolean (r);
    } else if (r instanceof RetrievalModelBM25)  {
      return this.getScoreBM25 (r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
      (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      
      return 1.0;
    }
  }
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
      if (! this.docIteratorHasMatchCache()) {
          return 0.0;
      } 
      else {
          return this.getTfMax(r, this.docIteratorGetMatch());
      }
  }
  /**
   * Calculate the score for BM25, assuming that no duplicate query is given
   * @param r
   * @return
   * @throws IOException
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {
      if (! this.docIteratorHasMatchCache()) {
        return 0.0;
      } else {
    	double N = Idx.getNumDocs();
    	double dft = ((QryIop)this.args.get(0)).getDf();
        double RSJWeight = Math.max(0, Math.log(( N - dft + 0.5) / ( dft + 0.5)));
        String tempField = ((QryIop)this.args.get(0)).getField();
        double tf = ((QryIop)this.args.get(0)).getTf(r, 0);
        double k1 = ((RetrievalModelBM25)r).getk1();
        double b = ((RetrievalModelBM25)r).getb();
        double k3 = ((RetrievalModelBM25)r).getk3();
        double doclen = Idx.getFieldLength(tempField, this.docIteratorGetMatch());
        double ave_doclen = Idx.getSumOfFieldLengths(tempField) /(float) Idx.getDocCount (tempField);
        double qtf = 1; //let's assume the teacher doesn't treat us at all. No duplicate query term.
        //To call IopTerm ---->((QryIop)this.args.get(0))
        //To call BM25 model r ---> ((RetrievalModelBM25)r)
        double tfWeight = tf / (tf + k1* ((1-b) + b*doclen/ave_doclen));
        double userWeight = ((k3 + 1) * qtf )/ (k3 + qtf);
//        System.out.println(RSJWeight + "  " + tfWeight + " " + userWeight);
        double result = RSJWeight*tfWeight*userWeight;
        return result;
      }
    }
  
  	@Override
  	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
  		String tempField = ((QryIop)this.args.get(0)).getField();
  		double lambda = ((RetrievalModelIndri)r).getLambda();
  		double mu = ((RetrievalModelIndri)r).getMu();
  		double ctf = ((QryIop)this.args.get(0)).getCtf();
  		double tf = 0;
  	    double docLengthC = Idx.getSumOfFieldLengths(tempField);
  	    double docLengthD = Idx.getFieldLength(tempField, (int) docid);
  		double pMLE =  ctf / docLengthC ; 
  		double firstPart = (1 - lambda) * ( tf + mu*ctf/docLengthC ) / (docLengthD + mu);
  		double secondPart = lambda * ctf / docLengthC;
  		double defaultScore = firstPart + secondPart;
  		return defaultScore;
  	}
  	
  	public double getScoreIndri (RetrievalModel r) throws IOException {
  		String tempField = ((QryIop)this.args.get(0)).getField();
  		double lambda = ((RetrievalModelIndri)r).getLambda();
  		double mu = ((RetrievalModelIndri)r).getMu();
  		double ctf = ((QryIop)this.args.get(0)).getCtf();
  		double tf = ((QryIop)this.args.get(0)).getTf(r, 0);
  	    double docLengthC = Idx.getSumOfFieldLengths(tempField);
  	    double docLengthD = Idx.getFieldLength(tempField, this.docIteratorGetMatch());
  		double pMLE =  ctf / docLengthC ; 
  		double firstPart = (1 - lambda) * ( tf + mu*ctf/docLengthC ) / (docLengthD + mu);
  		double secondPart = lambda * ctf / docLengthC;
  		double Score = firstPart + secondPart;
  		return Score;
  	}
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

    @Override
    public int getTf(RetrievalModel r, int n) {
        return this.getTfFirst(r, n);
    }

}
