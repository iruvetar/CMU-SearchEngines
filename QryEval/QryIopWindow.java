import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
public class QryIopWindow extends QryIop {
    private int distance;
    public int docPointer = 0;
    public int docIdPointer;
    
    //default
    public QryIopWindow(){}
    
    public QryIopWindow (int dist) {
        distance = dist;
    }
    
    
    
    @Override
    protected void evaluate () throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.
        
        this.invertedList = new InvList (this.getField());

        if (args.size () == 0) {
          return;
        }
        //  Each pass of the loop adds 1 document to result inverted list
        //  until all of the argument inverted lists are depleted.

        while (true) {
           int minDocid = Qry.INVALID_DOCID;
           boolean matchfound = false;
          //  Find the minimum next document id shared by all the arguments.  If there is none, the while loop will break at row 63
           while(! matchfound){
               Qry q_0 = this.args.get (0);

               if (! q_0.docIteratorHasMatch (null)) {
                   break;
               }

               int docid_0 = q_0.docIteratorGetMatch ();
               matchfound = true;
               int count = 1; 
               for (int i=1; i<this.args.size(); i++) {
                   Qry q_i = this.args.get(i);
                   q_i.docIteratorAdvanceTo(docid_0);
                   if (q_i.docIteratorHasMatch (null)) {
                     int q_iDocid = q_i.docIteratorGetMatch ();
                     if(docid_0 == q_iDocid){
                         count++;
                        if(count == this.args.size()) {
                            //match found
                             minDocid = q_iDocid;
                             break;
                        }
                     } //docid_0 != q_iDocid
                     else{//no match between q_i and q0 found
                         q_0.docIteratorAdvanceTo (q_iDocid);
                         matchfound = false;
                         break;
                     }
                   }
                   else{ //q_i has no match anymore
                       minDocid = Qry.INVALID_DOCID;
                       matchfound = true; //actually not found
                       break; //break for loop
                   }
           }//for loop end
          
          }//while loop ends  
       

          if (minDocid == Qry.INVALID_DOCID)
            break;              // All docids have been processed.  Done.
          
          //  Create a new posting that is the INTERSECTION of the posting lists
          //  that match the minDocid.  Save it.
          //  

          List<Integer> positions = new ArrayList<Integer>();
          List<Vector<Integer>> locCollection = new ArrayList<Vector<Integer>>(); //store all the location lists for document (minDocid)
          
          // store location pointer of all the arguments to be matched
          int[] pointerList = new int[this.args.size()];
          
          for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                Vector<Integer> locations_i = ((QryIop) q_i).docIteratorGetMatchPosting().positions;
                locCollection.add(locations_i);
              q_i.docIteratorAdvancePast (minDocid);
          }
          //locCollection is a collective list that each element stores a list of position
          
          //create pointerList
          for (int p = 0; p < this.args.size(); p++) {
              pointerList[p] = 0;
          }
          
          boolean match = false;
          boolean breakForLoop = false;
          int matchCount = 0;
          
          
          //while loop for finding matches----------------------------------------
          while(!match){
        	  
        	  //check whether the pointers are still within appropriate ranges
              for(int j = 0; j < this.args.size(); j++) {
                  if(pointerList[j] >= locCollection.get(j).size()) {
                      match = true;//no more possible matches so ends the while loop
                      breakForLoop = true;
                      break;
                  }
              }
        	 
              if (breakForLoop == true) {
                      break;
              }
                  
                  //search for maximum and minimum among the compared locations between terms 
                  int max = 0;
                  int maxIndex = 0;
                  int min = Integer.MAX_VALUE;
                  int minIndex = Integer.MAX_VALUE;
                  for(int searchMax = 0; searchMax < this.args.size(); searchMax++) {
                	  if(locCollection.get(searchMax).get(pointerList[searchMax]) > max) {
                		  max = locCollection.get(searchMax).get(pointerList[searchMax]);
                		  maxIndex = searchMax;
                	  }
                	  
                	  if(locCollection.get(searchMax).get(pointerList[searchMax]) < min) {
                		  min = locCollection.get(searchMax).get(pointerList[searchMax]);
                		  minIndex = searchMax;
                	  }
                  }
                  
                  if(1 + max - min <= this.distance) {
                	  //matchFound, pointer advance by 1
                	  matchCount++;
                      positions.add(max);
                	  for(int matchPlus = 0; matchPlus < this.args.size(); matchPlus++) {
                		  pointerList[matchPlus]++;
                	  }
                  } else {
                	  //match not found, smallest pointer advance by 1
                	  pointerList[minIndex]++;
                  }  


          }//while loop ends here
          
         
        
          if(matchCount != 0) {      
              Collections.sort (positions);
              this.invertedList.appendPosting (minDocid, positions);
          }
        }
        
      }
    
    
    /*
     * Return the Id of the matched document
     */
    public int docIteratorGetMatch () {
        return this.invertedList.getDocid(this.docIteratorIndex);
      }
    
    @Override
    public double getScore (RetrievalModel r){
        if (r instanceof RetrievalModelUnrankedBoolean) {
            //unranked score
            return 1.0;
        } else if (r instanceof RetrievalModelRankedBoolean) {
            //ranked score
            return ((double)this.invertedList.postings.get(this.docIteratorIndex).tf);
        } 
        //else if (r instanceof RetrievalModelBM25) {
//            try {
//                return getScoreBM25(r);
//            } catch (IOException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//                return 0.0;
//            }
//        } 
        else {
            return 0.0;
        }
    }
    @Override
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (this.docIteratorIndex < this.invertedList.df) {
            return true;
        }
        return false;
    }
    
    @Override
    public int getTf (RetrievalModel r, int docindex){
        if (r instanceof RetrievalModelUnrankedBoolean) {
            //unranked score
            return 1;
        } else {
            //ranked score
            return this.invertedList.postings.get(this.docIteratorIndex).tf;
        }
    }
    
//    /**
//     * 
//     * @param r the RetrievalModel(BM25)
//     * @return the score of the sum operator
//     * @throws IOException 
//     */
//    public double getScoreBM25(RetrievalModel r) throws IOException {
//        if(! this.docIteratorHasMatchCache()) {
//            return 0.0;
//        } else {
//            double RSJWeight = Math.max(0, Math.log((Idx.getNumDocs() - this.invertedList.postings.size() + 0.5) / (this.invertedList.postings.size() + 0.5)));
//            //To call BM25 model r ---> ((RetrievalModelBM25)r)
//            String tempField = this.field;
//            double tfWeight = this.invertedList.postings.get(this.docIteratorIndex).tf/
//                    ( this.invertedList.postings.get(this.docIteratorIndex).tf + (((RetrievalModelBM25)r).getk1()*( (1-((RetrievalModelBM25)r).getb() ) + ( ((RetrievalModelBM25)r).getb() * Idx.getFieldLength(tempField, this.docIteratorGetMatch())/ (Idx.getSumOfFieldLengths(tempField) /(float) Idx.getDocCount (tempField)) ) )   ));
//              //    (                     tf                                   + (          k1                  *(  (1-      b                        ) + (      b                         *       doclen                                             /       avg-doclen                                                           ) )   ))
//            double userWeight = ((((RetrievalModelBM25)r).getk3() + 1) * 1 )/ (((RetrievalModelBM25)r).getk3() + 1);
////            System.out.println(RSJWeight + "  " + tfWeight + " " + userWeight);
//            return RSJWeight*tfWeight*userWeight;
//        }
//    }
    
    public String displayName() {
        return "QryIopWindow";
    }
}
