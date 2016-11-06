import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *  The WAND operator for only Indri model. Created by yuntiehc
 */
public class QrySopWAnd extends QrySop {
	/**
	 * The double list stores the arguments weight in the WAND operator.
	 */
	private List<Double> arg_weights = new ArrayList<Double>();
	
	/**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determinTes what is a match
     *  @return True if the query matches, otherwise false.
     */
	@Override
	public boolean docIteratorHasMatch(RetrievalModel r) {
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

      if (r instanceof RetrievalModelIndri){
    	  return this.getScoreIndri (r);
      } else {
    	  throw new IllegalArgumentException
          (r.getClass().getName() + " doesn't support the WAND operator.");
      }
    }
    
    /**
     * 
     * @param r Retrieval model, Indri model
     * @return collective score of the document with the smallest id
     * @throws IOException
     */
    private double getScoreIndri (RetrievalModel r) throws IOException {
    	//normalize the weight
    	double totalWeight = 0;
    	for (double weight : this.arg_weights) {
    		totalWeight += weight;
    	}

        double score = 1.0;
        int index = 0;
    	for(Qry q_i : this.args) {
        	double currentScore = 1.0;
    		if(! q_i.docIteratorHasMatchCache() || q_i.docIteratorGetMatch() != this.docIteratorGetMatch()) {
        		currentScore = ((QrySop)q_i).getDefaultScore(r, this.docIteratorGetMatch());
        	} else {
        		currentScore = q_i.getScore(r);
        	}
    		double geometricScore = Math.pow(currentScore, this.arg_weights.get(index)/totalWeight);
    		score = score * geometricScore;
    		index++;
        }
    	return score;
    }
    
	@Override
	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
    	//normalize the weight
    	double totalWeight = 0;
    	for(double weight : this.arg_weights) {
    		totalWeight += weight;
    	}
    	
		double score = 1.0;
    	int index = 0;
    	for (Qry q_i : this.args) {
    		score = score * Math.pow(((QrySop)q_i).getDefaultScore(r, docid), this.arg_weights.get(index)/totalWeight);
    		index++;
    	}
    	return score;
	}

	/**
	 * Add a new weight into the list.
	 * @param weight of the argument.
	 */
	public void appendWeights (double weight) {
		arg_weights.add(weight);
	}

	@Override
	public int getTf(RetrievalModel r, int n) {
		// TODO Auto-generated method stub
		return 0;
	}

}
