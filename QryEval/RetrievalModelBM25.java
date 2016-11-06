/**
 *  Copyright (c) 2016, Carnegie Mellon University, yuntiehc.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM 25
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    private double k1;
    private double b;
    private double k3;
    
    public RetrievalModelBM25() {}
    
    public RetrievalModelBM25(double kk1, double bb, double kk3) {
        this.k1 = kk1;
        this.b = bb;
        this.k3 = kk3;
    }
    
    public double getk1() {
        return k1;
    }
    
    public double getb() {
        return b;
    }
    
    public double getk3() {
        return k3;
    }
    
  public String defaultQrySopName () {
    return new String ("#sum");
  }

}
