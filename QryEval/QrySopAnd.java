import java.io.*;

/**
 *  The AND operator for all retrieval models. Edited by yuntiehc
 */
public class QrySopAnd extends QrySop {
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determinTes what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
    	if (r instanceof RetrievalModelIndri) {
    		return this.docIteratorHasMatchMin(r);
    	}
      return this.docIteratorHasMatchAll (r); //for boolean retrieval model
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
      } else if (r instanceof RetrievalModelIndri){
    	  return this.getScoreIndri (r);
      } else {
    	  throw new IllegalArgumentException
          (r.getClass().getName() + " doesn't support the SCORE operator.");
      }
    }
    
    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
      if (! this.docIteratorHasMatchCache()) {
        return 0.0;
      } else {
        return 1.0;
      }
    }
    
    private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
          return 0.0;
        } else {
          return this.getTf(r,this.docIteratorGetMatch());
        }
      }
    /**
     * 
     * @param r Retrieval model, Indri model
     * @return collective score of the document with the smallest id
     * @throws IOException
     */
    private double getScoreIndri (RetrievalModel r) throws IOException {
        double score = 1.0;
    	for(Qry q_i : this.args) {
        	double currentScore = 1.0;
    		if(! q_i.docIteratorHasMatchCache() || q_i.docIteratorGetMatch() != this.docIteratorGetMatch()) {
        		currentScore = ((QrySop)q_i).getDefaultScore(r, this.docIteratorGetMatch());
        	} else {
        		currentScore = q_i.getScore(r);
        	}
    		double geometricScore = Math.pow(currentScore, 1.0/(double)this.args.size());
    		score = score * geometricScore;
        }
    	return score;
    }
    
    /**
     * Return the default score of the nested #AND query.
     * When this function is called, it means that all terms inside the nested #AND query 
     */
    @Override
    public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
    	double score = 1.0;
    	for (Qry q_i : this.args) {
    		score = score * Math.pow(((QrySopScore)q_i).getDefaultScore(r, docid), (1.0/(double)this.args.size()));
    	}
    	return score;
    }
    @Override
    public int getTf(RetrievalModel r, int n) {
        return this.getTfMin(r, n);
    }


}
