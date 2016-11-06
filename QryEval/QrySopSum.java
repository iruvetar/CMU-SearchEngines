import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  The SUM operator for all retrieval models. Created by yuntiehc
 */
public class QrySopSum extends QrySop {
    
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determinTes what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
      return this.docIteratorHasMatchMin (r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

      if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean) {
          throw new IllegalArgumentException ("The operator doesn't support the retrieval model");
      } else {
         return getScoreBM25(r);
      }
    }
    
      
    private double getScoreBM25 (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
          return 0.0;
        } else {
            double score = 0.0;
            //sum up here...
            for(Qry q_i : this.args) {
            	if(! q_i.docIteratorHasMatchCache() || q_i.docIteratorGetMatch() != this.docIteratorGetMatch()) {
            		score = score + 0;
            	} else {
                    score = score + q_i.getScore(r);
            	}
            }
          return score;
        }
      }
    
    @Override
    public int getTf(RetrievalModel r, int n) {
        return this.getTfMin(r, n);
    }

	@Override
	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

}

