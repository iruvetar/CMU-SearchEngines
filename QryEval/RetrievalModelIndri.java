/**
 *  Copyright (c) 2016, Carnegie Mellon University, yuntiehc.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the Indri
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
	private double lambda;
	private double mu;
	
	public RetrievalModelIndri(){}
	
	public RetrievalModelIndri(double newLambda, double newMu) {
		this.lambda = newLambda;
		this.mu = newMu;
	}
	
	public double getLambda() {
		return this.lambda;
	}
	
	public double getMu() {
		return this.mu;
	}
	
	@Override
	public String defaultQrySopName() {
		return new String ("#and");
	}

}
