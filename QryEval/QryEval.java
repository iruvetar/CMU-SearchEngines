/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */
import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/** HW4 Debug use
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  //add "keywords" by yuntiehc.
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink", "keywords" };

  //Map of scoreList to store initial retrieval result (top n docs) used for relevance feedback, created by yuntiehc.
  //String is the query Id, scorelist contains a list of documents(with score)
  private static Map<String, ScoreList> mapQueryScoreList = new HashMap<String, ScoreList>();
  private static ArrayList<String> queryIdList = new ArrayList<String>();
  
  //For Learning to Rank model:
  private static ArrayList<String> queryTrain = new ArrayList<String>();
  //Map from ExternalId to PageRank score
  private static Map<String, Float> mapPageRank;
  //To mark how many documents were retrieved during the initial ranking for EACH of the query, this variable will help to chop the rerank process
  private static ArrayList<Integer> sizeOfInitialOutputPerQuery = new ArrayList<Integer>();
  //important variable, controll disabled features, 0=able -1=unable
  private static int[] intFeaturesDisable;
  private static boolean allfeature;
  //  ----Constants for feature BM25 and Indri---------------------
  private static double N;
  private static double k1;
  private static double b;
  private static double k3;
  
  private static double lambda;
  private static double mu;
  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {
    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.

    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));

    //  Check whether it's learning to rank algorithm
    if (parameters.get("retrievalAlgorithm").equals("letor")) {
    	
    	//generate training data
    	N = Idx.getNumDocs();
    	k1 = Double.parseDouble(parameters.get("BM25:k_1"));
    	b = Double.parseDouble(parameters.get("BM25:b"));
    	k3 = Double.parseDouble(parameters.get("BM25:k_3"));
    	lambda = Double.parseDouble(parameters.get("Indri:lambda"));;
    	mu = Double.parseDouble(parameters.get("Indri:mu"));
    	
    	generateTrainingData(parameters);
    	runSVMrankTrain(parameters);
    	BM25FeatureVector(parameters.get("queryFilePath"), parameters);
    	runSVMrankClassify(parameters);
    	rerank(parameters.get("queryFilePath"), parameters.get("letor:testingFeatureVectorsFile"), parameters.get("letor:testingDocumentScores"), parameters.get("trecEvalOutputPath"));
    } 

    else {
    	// for boolean model, language model
    	
    	RetrievalModel model = initializeRetrievalModel (parameters);

        // Add Query Expansion processing
        if (! parameters.get("fb").equals("true")) {
        	//  Perform regular experiments.
        	processQueryFile(parameters.get("queryFilePath"), model, parameters.get("trecEvalOutputPath"));
        } else {
        	// Acceptable values are integers > 0. This value determines the number of documents to use for query expansion.	
            int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
            
            if (fbDocs <= 0) {
            	throw new IllegalArgumentException("Illegal value of fbDoc, the value must be larger than 0");
            }
        	
            if (parameters.containsKey("fbInitialRankingFile") && parameters.get("fbInitialRankingFile") != null && parameters.get("fbInitialRankingFile").length() != 0) {
        		System.out.println("fbInitialRankingFile is given.");
            	processInitialRankingFile(parameters.get("queryFilePath"), model, fbDocs, parameters);
        	} else { //start from query processing, but only keeps top N documents. 
        		processQueryforPRF(parameters.get("queryFilePath"), model, fbDocs, parameters);
        	}
       
        }
    }
    
    

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }
  /**
   * 
   * @param initialRankFilePath
   * @param SVMScoreFilePath
   * @param outputFilePath
 * @throws IOException 
   */
  private static void rerank(String queryPath, String initialRankFilePath, String SVMScoreFilePath, String outputFilePath) throws IOException {
	  BufferedReader initialRanking = null;
	  BufferedReader SVMscore = null;
//	  BufferedReader queryInput = null;
	  
      File file = new File(outputFilePath);
      file.createNewFile();
      PrintWriter pwriter = new PrintWriter(new FileWriter(file));
	  
	  try {
//		  queryInput = new BufferedReader(new FileReader(queryPath));
		  
		  initialRanking = new BufferedReader(new FileReader(initialRankFilePath));
		  SVMscore = new BufferedReader(new FileReader(SVMScoreFilePath));
		  
		  for (int i = 0; i < sizeOfInitialOutputPerQuery.size(); i++) {
			  ArrayList<Integer> temp = sizeOfInitialOutputPerQuery;
			  initialRanking.mark(10);
			  String line = initialRanking.readLine();
			  String[] lineArr = line.split("\\s+");
			  String qid = lineArr[1].substring(lineArr[1].indexOf(":") + 1);
			  initialRanking.reset();
			  ScoreList r = partialRerank(sizeOfInitialOutputPerQuery.get(i), initialRanking, SVMscore);
			  r.sort();
			  
			  if (r.size() != 0) {
		            pwriter.println(printResults(qid, r));
		      }
		        //no match result for this query
		        else {
		            pwriter.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
		      }
		  }
		  pwriter.close();
		  initialRanking.close();
		  SVMscore.close();
	  } catch (IOException e) {
		  
	  }
  }
  
  private static ScoreList partialRerank (int size, BufferedReader documentReader, BufferedReader scoreReader) throws IOException {
	  ScoreList r = new ScoreList();
	  for (int i = 0; i < size; i ++) {
		  String docFeatureVector = documentReader.readLine();
		  String[] docFVarray = docFeatureVector.split("\\s+");
		  String externalid = docFVarray[docFVarray.length - 1]; //the last element is the external id
		  
		  String scoreinput = scoreReader.readLine();
		  String[] scorearray = scoreinput.split("\\s+");
		  double score = Double.parseDouble(scorearray[0]);
		  r.addExt(externalid, score);
	  }
	  return r;
  }
  /**
   *  Process the query file, use BM25 for initial ranking, and then calculate featureVector
   *  @param queryFilePath
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static void BM25FeatureVector(String queryFilePath, Map<String, String> parameters) throws IOException {
	RetrievalModel model = new RetrievalModelBM25(Double.parseDouble(parameters.get("BM25:k_1")),Double.parseDouble(parameters.get("BM25:b")),Double.parseDouble(parameters.get("BM25:k_3")));
	  
    BufferedReader input = null;
    
    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      // creates output file
      File file = new File(parameters.get("letor:testingFeatureVectorsFile"));
      file.createNewFile();
      // creates a FileWriter Object
      FileWriter writer = new FileWriter(file); 
      PrintWriter pwriter = new PrintWriter(writer);
      
      // Writes the content to the file
      
  
      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;
        
        //get the top 100 documents for the current query
        r = processQuery(query, model);
        r.sort();
        r.truncate(100);
        
        System.out.println("Initial Ranking Done");
	    //------ important vectors ---------------
        //stemmed and stopwords removed query array
  	    String[] queries = QryParser.tokenizeString(query);
	  	//store important information of the documents for the current query
	  	ArrayList<String> collectionExtDocid = new ArrayList<String>();
	  	ArrayList<double[]> collectionFeatureVector = new ArrayList<double[]>();
	  	  
	  	// row 0 (feature1) : min
	  	double[] minMemo = new double[18];
	  	double[] maxMemo = new double[18];
	  	for (int row = 0; row < 18; row++) {
	  	 minMemo[row] = Double.MAX_VALUE;
	  	 maxMemo[row] = 0;
	  	}
        
	  	//calculate feature vector
        for (int document = 0; document < r.size(); document++) {
        	double[] featureVector = new double[18];
        	boolean validFV = false;
//      	    System.out.println("...getting feature for document " + document);
        	String externalid = "N/A";
        	for (int i = 0; i < 18; i++) {
        		//get the feature score
        		try {
        			externalid = Idx.getExternalDocid(r.getDocid(document));
        			validFV = true;
        			featureVector[i] = getFeature(i, r.getDocid(document), externalid, parameters, queries);
	        		//collect max and min score of each feature at the same time
	        		if (featureVector[i] != -1 && featureVector[i] > maxMemo[i]) {
	        			maxMemo[i] = featureVector[i];
	        		}
	        		if (featureVector[i] != -1 && featureVector[i] < minMemo[i]) {
	        			minMemo[i] = featureVector[i];
	        		}
				} catch (Exception e) {
				}
        	}
        	if (validFV) {
        		collectionFeatureVector.add(featureVector);
            	collectionExtDocid.add(externalid);
        	}
        }
        System.out.println("Unnormalized Feature Vectors Done");
        
        //save the valid size of the initial ranking for this query
        sizeOfInitialOutputPerQuery.add(r.size());
        
        //normalize
        //-------- Normalization of Feature Vectors and write feature value-----------
        for (int i = 0; i < collectionFeatureVector.size(); i++) {
        	double[] fv = collectionFeatureVector.get(i);
        	
        	StringBuilder output = new StringBuilder();
	    	output.append(0).append(" qid:").append(qid).append(" ");
//        	int[] jj = intFeaturesDisable;
	    	//normalize each of the feature except "3"
	    	if (allfeature) {
		    	for (int j = 0; j < 18; j++) {
	        		if (fv[j] == -1) {
	        			//leave blank
	        		} 
//	        		else if (j == 2 || j== 6 || j== 9 || j== 12 || j== 15) {
//	        			output.append(j+1).append(":").append(fv[j]).append(" ");
//	        		} 
	        		else {
	        			//do normalization HERE
	        			if (maxMemo[j] == minMemo[j]) {
	        				output.append(j+1).append(":").append(0.0).append(" ");
	        			} else {
	        				double normalized = (fv[j] - minMemo[j]) / (maxMemo[j] - minMemo[j]);
	        				output.append(j+1).append(":").append(normalized).append(" ");
	        			}
	        		} 
	        	}
	    	} else {
		    	for (int j = 0; j < 18; j++) {
	        		if (fv[j] == -1 || intFeaturesDisable[j] == -1) {
	        			//leave blank
	        		} 
//	        		else if (j == 2 || j== 6 || j== 9 || j== 12 || j== 15) {
//	        			output.append(j+1).append(":").append(fv[j]).append(" ");
//	        		} 
	        		else {
	        			//do normalization HERE
	        			if (maxMemo[j] == minMemo[j]) {
	        				output.append(j+1).append(":").append(0.0).append(" ");
	        			} else {
	        				double normalized = (fv[j] - minMemo[j]) / (maxMemo[j] - minMemo[j]);
	        				output.append(j+1).append(":").append(normalized).append(" ");
	        			}
	        		} 
	        	}
	    	}

        	output.append("# ").append(collectionExtDocid.get(i));
        	pwriter.println(output);
        }
      }
      pwriter.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }
  
  private static void runSVMrankClassify(Map<String, String> parameters) throws Exception {
	  
	  String execPath = parameters.get("letor:svmRankClassifyPath");
	  //String qrelsFeatureOutputFile = "REF_DIR/HW4-test-FeatureOut.LeToRTest";
	  String qrelsFeatureOutputFile = parameters.get("letor:testingFeatureVectorsFile");
	  String modelOutputFile = parameters.get("letor:svmRankModelFile");
	  String prediction = parameters.get("letor:testingDocumentScores");
	  
	  // runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.

	    Process cmdProc;
		try {
			cmdProc = Runtime.getRuntime().exec(
			    new String[] { execPath, qrelsFeatureOutputFile, modelOutputFile, prediction});
			// The stdout/stderr consuming code MUST be included.
		    // It prevents the OS from running out of output buffer space and stalling.

		    // consume stdout and print it out for debugging purposes
		    BufferedReader stdoutReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getInputStream()));
		    String line;
		    while ((line = stdoutReader.readLine()) != null) {
		      System.out.println(line);
		    }
		    // consume stderr and print it for debugging purposes
		    BufferedReader stderrReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getErrorStream()));
		    while ((line = stderrReader.readLine()) != null) {
		      System.out.println(line);
		    }

		    // get the return value from the executable. 0 means success, non-zero 
		    // indicates a problem
		    int retValue = cmdProc.waitFor();
		    if (retValue != 0) {
		      throw new Exception("SVM Rank crashed.");
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}

  }
  /**
   * Execute SVM_Train
   * @param parameters
   * @throws Exception
   */
  private static void runSVMrankTrain(Map<String, String> parameters) throws Exception {
	  
	  String execPath = parameters.get("letor:svmRankLearnPath");
	  String c = parameters.get("letor:svmRankParamC");
	  String qrelsFeatureOutputFile = parameters.get("letor:trainingFeatureVectorsFile");
	  String modelOutputFile = parameters.get("letor:svmRankModelFile");
	  
	  // runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.

	    Process cmdProc;
		try {
			cmdProc = Runtime.getRuntime().exec(
			    new String[] { execPath, "-c", String.valueOf(c), qrelsFeatureOutputFile, modelOutputFile });
			// The stdout/stderr consuming code MUST be included.
		    // It prevents the OS from running out of output buffer space and stalling.

		    // consume stdout and print it out for debugging purposes
		    BufferedReader stdoutReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getInputStream()));
		    String line;
		    while ((line = stdoutReader.readLine()) != null) {
		      System.out.println(line);
		    }
		    // consume stderr and print it for debugging purposes
		    BufferedReader stderrReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getErrorStream()));
		    while ((line = stderrReader.readLine()) != null) {
		      System.out.println(line);
		    }

		    // get the return value from the executable. 0 means success, non-zero 
		    // indicates a problem
		    int retValue = cmdProc.waitFor();
		    if (retValue != 0) {
		      throw new Exception("SVM Rank crashed.");
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}

  }
  /**
   * The first function for HW4, created by yuntiehc
   * @param parameters
 * @throws Exception 
   */
  private static void generateTrainingData(Map<String, String> parameters) throws Exception {
	  BufferedReader input = null;
	  String queryTrainFilePath = parameters.get("letor:trainingQueryFile");
	  BufferedReader relevanceJudge = null;
	  String relJudgeFilePath = parameters.get("letor:trainingQrelsFile");
      File file = new File(parameters.get("letor:trainingFeatureVectorsFile"));
      file.createNewFile();
      PrintWriter pwriter = new PrintWriter(new FileWriter(file));
      
      
     allfeature = true;
      
      if (parameters.get("letor:featureDisable") == null) {
    	  allfeature = true;
      } else {
    	  allfeature = false;
    	  String featureDisable = parameters.get("letor:featureDisable");
    	  String[] featuresDisable = featureDisable.split(",");
    	  intFeaturesDisable = new int[18]; //0=able -1=unable
    	  for (int temp = 0; temp < featuresDisable.length; temp++) {
    		  intFeaturesDisable[Integer.parseInt(featuresDisable[temp]) - 1] = -1;
    	  }
      }
      
      
	    try {
	      String qLine = null;
	      input = new BufferedReader(new FileReader(queryTrainFilePath));

	      //  Each pass of the loop processes one query.
	      while ((qLine = input.readLine()) != null) {
	    	  String qid = qLine.substring(0, qLine.indexOf(":"));
	    	  String qQuery = qLine.substring(qLine.indexOf(":") + 1, qLine.length());
	    	  
	    	  //stemmed and stopwords removed query array
	    	  String[] queries = QryParser.tokenizeString(qQuery);
	    	  
	    	  
	    	  //------ important vectors ---------------
	    	  //store important information of the documents for the current query
	    	  ArrayList<Integer> collectionRelScore = new ArrayList<Integer>();
	    	  ArrayList<String> collectionExtDocid = new ArrayList<String>();
	    	  ArrayList<Integer> collectionIntDocid = new ArrayList<Integer>();
	    	  ArrayList<double[]> collectionFeatureVector = new ArrayList<double[]>();
	    	  
	    	  // row 0 (feature1) : min
	    	  double[] minMemo = new double[18];
	    	  double[] maxMemo = new double[18];
	    	  for (int row = 0; row < 18; row++) {
	    		  minMemo[row] = Double.MAX_VALUE;
	    		  maxMemo[row] = 0;
	    	  }
	    	  
	    	  //process relevance judgement documents for the current query------
	    	  
	    	  //Each pass of the loop processes one document record.
	    	    relevanceJudge = new BufferedReader(new FileReader(relJudgeFilePath));
	    	    String line = null;
		        while ((line = relevanceJudge.readLine()) != null) {
		          String[] relJudgeInput = line.split("\\s+");
		          
		          if (qid.equals(relJudgeInput[0])) { //qid and current id matches
		        	  double[] featureVector = new double[18];
		        	  int internalid = 0;
		        	  try {
		        		  internalid = Idx.getInternalDocid(relJudgeInput[2]);
		        		  collectionRelScore.add(relevanceNormalize(Integer.parseInt(relJudgeInput[3])));
			        	  collectionIntDocid.add(internalid);
			        	  collectionExtDocid.add(relJudgeInput[2]);
			        	  /*
			        	          create an empty feature vector
							      read the PageRank feature from a file
							      fetch the term vector for d
							      calculate other features for <q, d>
			        	   */
				        	for (int i = 0; i < 18; i++) {
				        		//get the feature score
				        		featureVector[i] = getFeature(i, internalid, relJudgeInput[2], parameters, queries);
				        		//collect max and min score of each feature at the same time
				        		if (featureVector[i] != -1 && featureVector[i] > maxMemo[i]) {
				        			maxMemo[i] = featureVector[i];
				        		}
				        		if (featureVector[i] != -1 && featureVector[i] < minMemo[i]) {
				        			minMemo[i] = featureVector[i];
				        		}
				        	}
				        	
				        	//NOTICE: The only issue is that if a document does not have a url field, you should flag it as having no value for feature (not 0).  
				        	//As you can see in the test cases, it is OK to leave out features. flag it as having no value for feature (not 0)
				        	
				        	collectionFeatureVector.add(featureVector);
				        	relevanceJudge.mark(0);
		        	  } catch (Exception e) {
//		        		  System.out.println("Error happened during feature collection when qid = " + qid + ", input docid = " + relJudgeInput[2]);
//		        		  System.out.println(e.getStackTrace());
//		        		  System.out.println(e.getMessage());
		        	  }
		          } else if (Integer.parseInt(qid) > Integer.parseInt(relJudgeInput[0])){
		        	  //leave a mark and continue the while loop
		        	  relevanceJudge.mark(0);
		          } else {
		        	  relevanceJudge.reset();
		        	  break;
		          }
		        }
		        //the training data has been processed.
			       

		        //-------- Normalization of Feature Vectors and write feature value-----------
		        for (int i = 0; i < collectionFeatureVector.size(); i++) {
		        	double[] fv = collectionFeatureVector.get(i);
		        	
		        	StringBuilder output = new StringBuilder();
			    	output.append(collectionRelScore.get(i)).append(" qid:").append(qid).append(" ");
		        	
			    	if (allfeature) {
				    	//normalize each of the feature
			        	for (int j = 0; j < 18; j++) {
			        		if (fv[j] == -1) {
			        			//leave blank
			        		} else {
			        			//do normalizetion HERE
			        			if (maxMemo[j] == minMemo[j]) {
			        				output.append(j+1).append(":").append(0.0).append(" ");
			        			} else {
			        				double normalized = (fv[j] - minMemo[j]) / (maxMemo[j] - minMemo[j]);
			        				output.append(j+1).append(":").append(normalized).append(" ");
			        			}
			        		} 
			        	}			    		
			    	} else {
				    	//normalize each of the feature except "3 7 10 13 16"
			        	for (int j = 0; j < 18; j++) {
			        		if (fv[j] == -1 || intFeaturesDisable[j] == -1) {
			        			//leave blank
			        		} 
//			        		else if (j == 2 || j== 6 || j== 9 || j== 12 || j== 15) {
//			        			output.append(j+1).append(":").append(fv[j]).append(" ");
//			        		} 
			        		else {
			        			//do normalizetion HERE
			        			if (maxMemo[j] == minMemo[j]) {
			        				output.append(j+1).append(":").append(0.0).append(" ");
			        			} else {
			        				double normalized = (fv[j] - minMemo[j]) / (maxMemo[j] - minMemo[j]);
			        				output.append(j+1).append(":").append(normalized).append(" ");
			        			}
			        		} 
			        	}		
			    	}

		        	output.append("# ").append(collectionExtDocid.get(i));
		        	pwriter.println(output);
		        }
	      }
	      
	      
	    } catch (IOException ex) {
		      ex.printStackTrace();
	    } finally {
	      input.close();
	      pwriter.close();
	    }
  }
  /**
   * Convert relevance score from [-2, 2] to [1, 5]. Created by yuntiehc for hw4.
   * @param i
   * @return i+3
   */
  private static int relevanceNormalize(int i) {
	  return i + 3;
  }
  /**
   * A collective function that will call specific getFeature function according to the given number. Created by yuntiehc.
   * @param i, the given index that specify the corresponding index of feature.
   * @return a float feature score.
 * @throws Exception 
   */
  private static double getFeature(int i, int docid, String exdocid, Map<String, String> parameters, String[] queries) throws Exception {
	  switch(i + 1) {
	  	case 1 : //spamscore
	  		String spamscore = Idx.getAttribute ("score", docid);
	  		if (spamscore == null) {
	  			return -1;
	  		}
	  		return Double.parseDouble(spamscore);
	  		
	  	case 2 : //url depth, number of '/' in the rawUrl field
	  		String rawUrl = Idx.getAttribute ("rawUrl", docid);
	  		if (rawUrl == null) {
	  			return -1.0;
	  		}
	  		int index = rawUrl.indexOf("//");
	  		if (index != -1) {
	  			String replaceUrl = rawUrl.substring(index + 2);
	  			String newreplaceUrl = replaceUrl.replace("/","");
	  			return replaceUrl.length() - newreplaceUrl.length();
	  		} else {
	  			String replaceUrl = rawUrl.replace("/", "");
	  			return rawUrl.length() - replaceUrl.length();
	  		}
	  	case 3 : //FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
	  		String Url = Idx.getAttribute ("rawUrl", docid);
	  		if (Url == null) {
	  			return -1.0;
	  		}
	  		if (Url.contains("wikipedia.org")) {
	  			return 1.0;
	  		} else {
	  			return 0.0;
	  		}
	  		
	  	case 4 : //PageRank score for d (read from file)
	  		return getPageRank(exdocid, parameters);
	  		
	  	case 5 : //BM25 score for <q, dbody>.
	  		return getFeatureBM25("body", queries, docid);
	  		
	  	case 6 : // Indri score for <q, dbody>
	  		return getFeatureIndri("body", queries, docid);
	  		
	  	case 7 : //Term overlap score for <q, dbody>.
	  		return getTermOverlap("body", queries, docid);
	  		
	  	case 8 : //BM25 score for <q, dtitle>.
	  		return getFeatureBM25("title", queries, docid);
	  		
	  	case 9 : //Indri score for <q, dtitle>.
	  		return getFeatureIndri("title", queries, docid);
	  		
	  	case 10 : //Term overlap score for <q, dtitle>.
	  		return getTermOverlap("title", queries, docid);
	  		
	  	case 11 : //BM25 score for <q, durl>.
	  		return getFeatureBM25("url", queries, docid);
	  		
	  	case 12 : //Indri score for <q, durl>.
	  		return getFeatureIndri("url", queries, docid);
	  		
	  	case 13 : //Term overlap score for <q, durl>.
	  		return getTermOverlap("url", queries, docid);
	  		
	  	case 14 : //BM25 score for <q, dinlink>.
	  		return getFeatureBM25("inlink", queries, docid);
	  		
	  	case 15 : //Indri score for <q, dinlink>.
	  		return getFeatureIndri("inlink", queries, docid);
	  		
	  	case 16 : //Term overlap score for <q, dinlink>.
	  		return getTermOverlap("inlink", queries, docid);
//	  	case 17 : //SVM for body
//	  		return getCosineSim("body", queries, docid);
//	  	case 18 : //SVM for title
//	  		return getCosineSim("title", queries, docid);
	  	default :
	  		return 0;
	  }
  }
  
  private static double getCosineSim(String field, String[] queries, int docid) throws IOException {
	  double[] tfArray = new double[queries.length]; //save tf, -->di
	  double[] dfArray = new double[queries.length]; //dave idf --> qi
	  
	  int queryMatch = 0;
	  TermVector termvector = new TermVector(docid, field);	
	  
	  if (termvector.positionsLength() > 0) { //valid termvector (non-null)
		  
		  //for each query term
		  for (int q = 0; q < queries.length; q++) {
			  int index = termvector.indexOfStem(queries[q]);

			  if (index != -1) { //the query term exists in the document
				  tfArray[q] = termvector.stemFreq(index);
				  dfArray[q] = termvector.stemDf(index);
				  queryMatch++;
			  } else {
				  tfArray[q] = 0;
				  dfArray[q] = Idx.INDEXREADER.docFreq(new Term (field, new BytesRef (queries[q])));
			  }
		  }
		  
		  // if a field does not match any term of a query, documents that have no terms in common with the query were not given a score.
		  if (queryMatch == 0) {
			  return -1;
		  } else {
			  double numerator = 0;
			  double denominator_1Sq = 0;
			  double denominator_2Sq = 0;
		      //calculate Cosine Similarity (lnc.ltc)
			  for (int i = 0; i < tfArray.length; i++) {
				  numerator += (Math.max(Math.log(tfArray[i]), 0) + 1) * Math.log(N / dfArray[i]);
				  denominator_1Sq += Math.pow(Math.max(Math.log(tfArray[i]), 0) + 1, 2);
				  denominator_2Sq += Math.pow(Math.log(N / dfArray[i]), 2);
			  }
			  double denominator = Math.sqrt(denominator_1Sq) * Math.sqrt(denominator_2Sq);
			  return numerator / denominator ;
		  }
	  } else {
		  return -1;
	  }
  }
  /**
   * Return the percentage of query terms that match the document field. Created by yuntiehc.
   * @param field
   * @param queries
   * @param docid
   * @return Overlap score, the percentage of query terms that match the document field.
   * @throws IOException
   */
  private static double getTermOverlap(String field, String[] queries, int docid) throws IOException {
	  double score = 1;
	  double queryMatch = 0;
	  TermVector termvector = new TermVector(docid, field);	
	  
	  if (termvector.positionsLength() > 0) {
		  //loop through termvector the calculate the percentage
		  for (int q = 0; q < queries.length; q++) {
			  if (termvector.indexOfStem(queries[q]) != -1) {
				  queryMatch++;
			  }
		  }
		  
		  return queryMatch / queries.length;
		  
	  } else {
		  return -1;
	  }
  }
  
  /**
   * Return a Indri score for the document, -1 if the document doesn't have the field. 0 if none of the query exists in the document. Created by yuntiehc.
   * @param field the field name
   * @param queries a String array of queries
   * @param docid internal document id
   * @return Indri feature score.
   * @throws IOException
   */
  private static double getFeatureIndri(String field, String[] queries, int docid) throws IOException {
	  double score = 1;
	  int queryMatch = 0;
	  TermVector termvector = new TermVector(docid, field);	
	  
	  if (termvector.positionsLength() > 0) { //valid termvector (non-null)
		  
		  for (int q = 0; q < queries.length; q++) {
			  int index = termvector.indexOfStem(queries[q]);
			  double tf = 0;
			  double ctf = 0;
			  if (index != -1) { //the query term exists in the document
				  tf = termvector.stemFreq(index);
				  ctf = termvector.totalStemFreq(index);
				  queryMatch++;
			  } else {
				  ctf = Idx.getTotalTermFreq(field, queries[q]);
			  }
			  //calculate score
			  double docLengthC = Idx.getSumOfFieldLengths(field);
			  double docLengthD = Idx.getFieldLength(field, docid);
			  double pMLE =  ctf / docLengthC ; 
			  double firstPart = (1 - lambda) * ( tf + mu * ctf / docLengthC ) / (docLengthD + mu);
			  double secondPart = lambda * ctf / docLengthC;
			  double indriScore = Math.pow(firstPart + secondPart, 1.0 / queries.length);
			  
		      score *= indriScore;
		  }
		  
		  // if a field does not match any term of a query, the score for the field is 0. documents that have no terms in common with the query were not given a score.
		  if (queryMatch == 0) {
			  return 0;
		  } else {
		      return score; 
		  }
	  } else {
		  return -1;
	  }

  }
  
  /**
   * Return BM 25 score for the current document
   * @param field
   * @param queries
   * @param docid, internal document id
   * @return return BM25 score for this pair of document and field
 * @throws IOException 
   */
  private static double getFeatureBM25(String field, String[] queries, int docid) throws IOException {
	  double score = 0;
	  TermVector termvector = new TermVector(docid, field);
	  
	  if (termvector.positionsLength() > 0) { //valid termvector (non-null)
		  
		  for (int i = 1; i < termvector.stemsLength(); i++) {
			  try {
				  String term = termvector.stemString(i);
				  
				  //if stem is a queryStem
				  for (int j = 0; j < queries.length; j++) {
					  if (term.equals(queries[j])) {
						  //calculate score
						  double dft = termvector.stemDf(i);
						  double RSJWeight = Math.max(0, Math.log(( N - dft + 0.5) / ( dft + 0.5)));
						  double tf = termvector.stemFreq(i);
						  double doclen = termvector.positionsLength();
					      double ave_doclen = Idx.getSumOfFieldLengths(field) /(float) Idx.getDocCount (field);
					      double qtf = 1; //let's assume the teacher doesn't treat us at all. No duplicate query term.
					      double tfWeight = tf / (tf + k1* ((1-b) + b*doclen/ave_doclen));
					      double userWeight = ((k3 + 1) * qtf )/ (k3 + qtf);
					      double result = RSJWeight*tfWeight*userWeight;
					      score += result;
					      break;
					  }
				  }
			  } catch (Exception e) {
        		  System.out.println("Error happened at i = " + i);
        	  }

		  }
	      return score;
	  } else {
		  return -1;
	  }
	  
  }
  /**
   * Process the pagerank file and return the pagerank score of the document. -1 if the document is not listed in the file. Created by yuntiehc.
   * @param externalid, the external id of the document
   * @param parameters
   * @return the page rank score.
   * @throws IOException
   */
  private static float getPageRank(String externalid, Map<String, String> parameters) throws IOException {
	  //process the file if the map hasn't been initialized yet
	  if (mapPageRank == null) {
		  mapPageRank = new HashMap<String, Float>();
		  
		  //process the pagerank file
		  BufferedReader input = null;
		  String filePath = parameters.get("letor:pageRankFile");
//		  String actualPath = filePath + ".txt";
		  try {
		      String qLine = null;
		      input = new BufferedReader(new FileReader(filePath));

		      //  Each pass of the loop processes one line.
		      while ((qLine = input.readLine()) != null) {
		    	  String[] arr = qLine.split("\t");
		    	  mapPageRank.put(arr[0], Float.parseFloat(arr[1]));
		      }
		  }catch (IOException ex) {
		      ex.printStackTrace();
	     } finally {
	       input.close();
	     }
	  } 
	  
	  if (mapPageRank.get(externalid) == null) {
		  return -1;
	  } else {
		  return mapPageRank.get(externalid);
	  }
  }
  
  /**
   * Sort the expansion term map based on the value(score)
   * @param unsortMap
   * @param order
   * @return a list with pair of value Entry<String, Double>
   */
  private static List<Entry<String, Double>> sortByComparator(Map<String, Double> unsortMap, final boolean order) {

      List<Entry<String, Double>> list = new LinkedList<Entry<String, Double>>(unsortMap.entrySet());

      // Sorting the list based on values
      Collections.sort(list, new Comparator<Entry<String, Double>>()
      {
          public int compare(Entry<String, Double> o1,
                  Entry<String, Double> o2)
          {
              if (order)
              {
                  return o1.getValue().compareTo(o2.getValue());
              }
              else
              {
                  return o2.getValue().compareTo(o1.getValue());

              }
          }
      });
      
      return list;
       
  }
  
  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }
    else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    }
    else if (modelString.equals("bm25")) {
      model = new RetrievalModelBM25(Double.parseDouble(parameters.get("BM25:k_1")),Double.parseDouble(parameters.get("BM25:b")),Double.parseDouble(parameters.get("BM25:k_3")));
    }
    else if (modelString.equals("indri")) {
      model = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:lambda")), Double.parseDouble(parameters.get("Indri:mu")));
    }
    else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";    
    //q will end up being a query tree
    Qry q = QryParser.getQuery (qString, model);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries
        q.initialize (model);
            //scan through document and store matched result in r
            while (q.docIteratorHasMatch (model)) {
              int docid = q.docIteratorGetMatch ();
//              double score = ((QrySop) q).getScore (model);
              double score = q.getScore (model);
              r.add (docid, score); //add some info of the doc into scorelist r            
              q.docIteratorAdvancePast (docid);
            }
            return r;
       }
      return null;
      }   
    else{
        return null;
    }  
  }

  /**
   *  Process the query file.
   *  @param queryFilePath
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath,
                               RetrievalModel model, String resultFilePath)
      throws IOException {

    BufferedReader input = null;
    
    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      // creates output file
      File file = new File(resultFilePath);
      file.createNewFile();
      // creates a FileWriter Object
      FileWriter writer = new FileWriter(file); 
      PrintWriter pwriter = new PrintWriter(writer);
      
      // Writes the content to the file
      
  
      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;
        
        //enter query after :
        r = processQuery(query, model);

        
        //there is result
        if (r.size() != 0) {
            pwriter.println(printResults(qid, r));
        }
        //no match result for this query
        else{
            pwriter.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
        }
      }
      pwriter.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   *  Process the query file for Pseudo Relevance Feedback. Created by yuntiehc. 
   *  @param queryFilePath
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryforPRF(String queryFilePath, RetrievalModel model, int fbDocs, Map<String, String> parameters)
      throws IOException {

    BufferedReader input = null;
    // creates output file for expansion query
    //The value is a string that contains the name of a file where your software must write its expansion query. The file format is described below.
	String fbExpansionQueryFilePath = parameters.get("fbExpansionQueryFile");
	
	File expansionFile = new File(fbExpansionQueryFilePath);
	expansionFile.createNewFile();
	PrintWriter expansionpwriter = new PrintWriter(new FileWriter(expansionFile));
	
	//creates output file for final result
	String resultFilePath = parameters.get("trecEvalOutputPath");
	File file = new File(resultFilePath);
	file.createNewFile();
	PrintWriter pwriter = new PrintWriter(new FileWriter(file));
    
    try {
      
          			
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

  
      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;
        
        //enter query after :
        r = processQuery(query, model);

        
        //there is result
        if (r.size() != 0) {
        	r.sort();
            r.truncate(fbDocs);
            //change here
            
            //calculate p(t|I) for each expansion term
            
            //-----some constants--------------------
            // Acceptable values are integers >= 0. This value determines the amount of smoothing used to calculate p(r|d).
            int fbMu = Integer.parseInt(parameters.get("fbMu"));
            // Acceptable values are integers > 0. This value determines the number of terms that are added to the query.
            int fbTerms = Integer.parseInt(parameters.get("fbTerms"));     
            //Acceptable values are between 0.0 and 1.0. This value determines the weight on the original query. The weight on the expanded query is (1-fbOrigWeight).
            double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
            //----------------------------------------

            
            //each loop process a expansion query calculation for each query
     	
            	//store a map with distinct string (term) as the key
                Map<String, Double> mapExpansionTerms = new HashMap<String, Double>();
              //store a map with distinct string (term) as the key to its ctf
                Map<String, Double> ctfExpansionTerms = new HashMap<String, Double>();
                
            	//each loop retrieve a document id and get a forward index
            	for (int i = 0; i < r.size(); i++) {
            		int internalId = r.getDocid(i);
            		TermVector forwardIndex = new TermVector(internalId, "body");
            		
            		//the loop goes through the distinct term index of every document a keep a distinct term dictionary
            		for (int k = 1; k < forwardIndex.positionsLength(); k++) {
            			String term = forwardIndex.stemString(k);
            			
            			if (term != null && ! term.contains(".") && ! term.contains(",")) {
            				if ( ! mapExpansionTerms.containsKey(term)) {
            					mapExpansionTerms.put(term, 0.0);
            					ctfExpansionTerms.put(term, (double)forwardIndex.totalStemFreq(k));
            				} 
            			} 
            		}
            	}
            	
            	//calculate score for each of the expansion term
            	for (Map.Entry<String, Double> expansionTerm : mapExpansionTerms.entrySet()) {
            		
            		String targetTerm = expansionTerm.getKey();
            		double docLengthC = Idx.getSumOfFieldLengths("body");
            		
            		//calculate the score of this document for this term
            		for (int doc = 0; doc < r.size(); doc++) {
            			int id = r.getDocid(doc);
                		TermVector index = new TermVector(id, "body");
                		int indexPosition = index.indexOfStem(targetTerm);
                		double tf;
                		if (indexPosition != -1) {
                			//this term exist in the termVector
                			tf = index.stemFreq(indexPosition);
                		} else {
                			tf = 0;
                		}

        				double ctf = ctfExpansionTerms.get(targetTerm);
        				//index.totalStemFreq(indexPosition);
        				double PMLE = ctf / docLengthC;
        				double ptd = ( tf + fbMu * PMLE )/ (Idx.getFieldLength("body", id)+ fbMu );
        				double initialScoreDoc = r.getDocidScore(doc);
        				double idf = Math.log(docLengthC / ctf);
        						
        				double score = ptd * initialScoreDoc * idf;
        				
        				double accumulativeScore = score + mapExpansionTerms.get(targetTerm);
        				mapExpansionTerms.put(targetTerm, accumulativeScore);
            		} 	
            	}
            	
            	        	
            	//create new query
                
            	StringBuilder newQuery = new StringBuilder();
            	newQuery.append(qid).append(": #WAND (");
            	
            	
            	//get a sorted list
            	List<Entry<String, Double>> sortedMapExpansionTerms = sortByComparator(mapExpansionTerms, false);
            	
            	//collect the top N (fbTerms) terms
            	//if the term candidates are smaller than required amount
            	if (sortedMapExpansionTerms.size() < fbTerms) {
            		for (int index = sortedMapExpansionTerms.size() - 1; index >= 0; index--) {
            			newQuery.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
            			newQuery.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");            			
            		}
            	} else {
            		for (int index = fbTerms - 1; index >= 0 ; index--) {
            			newQuery.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
            			newQuery.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");
                	}
            	}
            	newQuery.delete(newQuery.length() - 1, newQuery.length());
            	newQuery.append(")");
            	
            	
            	//write to the expanded query file------------------
            	expansionpwriter.println(newQuery);
                
            
            
               //combine original and new query and run the final query search
               // parameters.get("trecEvalOutputPath"), fbOrigWeight, model


        	   
        	        
        	   System.out.println("**Start the expanded query search**");
        	        
        	   String newQueryWithoutId = newQuery.substring(newQuery.indexOf(":") + 1);
        	   //combine two queries
        	   StringBuilder combinedQuery = new StringBuilder();
        	   combinedQuery.append("#wand (").append(fbOrigWeight).append("  #and(").append(query).append(") ");
        	   combinedQuery.append(1 - fbOrigWeight).append(" ").append(newQueryWithoutId).append(")");
        	           	          
        	   System.out.println("Query " + qid + ":" + combinedQuery.toString());

        	   ScoreList finalr = null;
        	          
        	    //enter query after :
        	   finalr = processQuery(combinedQuery.toString(), model);

        	          
        	    //there is result
        	    if (finalr.size() != 0) {
        	    	pwriter.println(printResults(qid, finalr));
        	    }
        	          //no match result for this query
        	     else{
        	    	 pwriter.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
        	    }
        }
      }//query loop ends
      
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      expansionpwriter.close();
      pwriter.close();//close the expansion query writer
      input.close();
    }
  }
  
  /**
   * Process Initial Ranking File and following term expansion operations.
   * @param queryFilePath
   * @param model
   * @param fbDocs
   * @param parameters
   * @throws IOException
   */
  private static void processInitialRankingFile(String queryFilePath, RetrievalModel model, int fbDocs, Map<String, String> parameters)
	      throws IOException {

	    BufferedReader input = null;
	    String initialRankingFilePath = parameters.get("fbInitialRankingFile");
	    BufferedReader initialRanking = null;
	    // creates output file for expansion query
	    //The value is a string that contains the name of a file where your software must write its expansion query. The file format is described below.
		String fbExpansionQueryFilePath = parameters.get("fbExpansionQueryFile");
		
		File expansionFile = new File(fbExpansionQueryFilePath);
		expansionFile.createNewFile();
		FileWriter expansionwriter = new FileWriter(expansionFile); 
		PrintWriter expansionpwriter = new PrintWriter(expansionwriter);
		
		//creates output file for final result
		String resultFilePath = parameters.get("trecEvalOutputPath");
		File file = new File(resultFilePath);
		file.createNewFile();
		FileWriter writer = new FileWriter(file); 
		PrintWriter pwriter = new PrintWriter(writer);
	    
	    try {
	      
	          			
	      String qLine = null;

	      input = new BufferedReader(new FileReader(queryFilePath));
	      initialRanking = new BufferedReader(new FileReader(initialRankingFilePath));
	  
	      //  Each pass of the loop processes one query.
	      while ((qLine = input.readLine()) != null) {
	        int d = qLine.indexOf(':');

	        if (d < 0) {
	          throw new IllegalArgumentException
	            ("Syntax error:  Missing ':' in query line.");
	        }

	        printMemoryUsage(false);

	        String qid = qLine.substring(0, d);
	        String query = qLine.substring(d + 1);

	        System.out.println("Query " + qLine);

	        //process initial ranking tein file to rebuild a scoreList
	        
	        ScoreList r = new ScoreList();
	        
	        //  Each pass of the loop processes one document record.
	        String line = null;
	        while ((line = initialRanking.readLine()) != null) {
	          
	          String[] rankingInput = line.split("\\s+");
	          
	          if (qid.equals(rankingInput[0])) {
	          	int ranking = Integer.parseInt(rankingInput[3]);
	          	  if (ranking <= fbDocs) {
	  				try {
	  					int internalId = Idx.getInternalDocid(rankingInput[2]);
	  					double score = Double.parseDouble(rankingInput[4]);
	  	        		r.add(internalId, score);
	  				} catch (Exception e) {
	  					e.printStackTrace();
	  				}
	          	  } else {
	          		  break;
	          	  }
	        	  initialRanking.mark(0);
	          } else if (Integer.parseInt(qid) > Integer.parseInt(rankingInput[0])){
	        	  initialRanking.mark(0);
	          } else {
	        	  initialRanking.reset();
	        	  break;
	          }
	        }

	        
	        
	        //if there is result
	        if (r.size() != 0) {
	            //change here
	            
	            //calculate p(t|I) for each expansion term
	            
	            //-----some constants--------------------
	            // Acceptable values are integers >= 0. This value determines the amount of smoothing used to calculate p(r|d).
	            int fbMu = Integer.parseInt(parameters.get("fbMu"));
	            // Acceptable values are integers > 0. This value determines the number of terms that are added to the query.
	            int fbTerms = Integer.parseInt(parameters.get("fbTerms"));     
	            //Acceptable values are between 0.0 and 1.0. This value determines the weight on the original query. The weight on the expanded query is (1-fbOrigWeight).
	            double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
	            //----------------------------------------

	            
	            //each loop process a expansion query calculation for each query
	     	
	            	//store a map with distinct string (term) as the key
	                Map<String, Double> mapExpansionTerms = new HashMap<String, Double>();
	                
	            	//each loop retrieve a document id and get a forward index
	            	for (int i = 0; i < r.size(); i++) {
	            		int internalId = r.getDocid(i);
	            		TermVector forwardIndex = new TermVector(internalId, "body");
	            		
	            		//the loop goes through the distinct term index of every document a keep a distinct term dictionary
	            		for (int k = 1; k < forwardIndex.positionsLength(); k++) {
	            			String term = forwardIndex.stemString(k);
	            			
	            			if (term != null && ! term.contains(".") && ! term.contains(",")) {
	            				if ( ! mapExpansionTerms.containsKey(term)) {
	            					mapExpansionTerms.put(term, 0.0);
	            				} 
	            			} 
	            		}
	            	}
	            	
	            	//calculate score for each of the expansion term
	            	for (Map.Entry<String, Double> expansionTerm : mapExpansionTerms.entrySet()) {
	            		
	            		String targetTerm = expansionTerm.getKey();
	            		
	            		//calculate the score of this document for this term
	            		for (int doc = 0; doc < r.size(); doc++) {
	            			int id = r.getDocid(doc);
	                		TermVector index = new TermVector(id, "body");
	                		int indexPosition = index.indexOfStem(targetTerm);
	                		double tf;
	                		if (indexPosition != -1) {
	                			//this term exist in the termVector
	                			tf = index.stemFreq(indexPosition);
	                		} else {
	                			tf = 0;
	                		}
	                		double docLengthC = Idx.getSumOfFieldLengths("body");
	        				double ctf = Idx.getTotalTermFreq("body", targetTerm);
	        				//index.totalStemFreq(indexPosition);
	        				double PMLE = ctf / docLengthC;
	        				double ptd = ( tf + fbMu * PMLE )/ (Idx.getFieldLength("body", id)+ fbMu );
	        				double initialScoreDoc = r.getDocidScore(doc);
	        				double idf = Math.log(docLengthC / ctf);
	        						
	        				double score = ptd * initialScoreDoc * idf;
	        				
	        				double accumulativeScore = score + mapExpansionTerms.get(targetTerm);
	        				mapExpansionTerms.put(targetTerm, accumulativeScore);
	            		} 	
	            	}
	            	
	            	        	
	            	//create new query
	                
	            	StringBuilder newQuery = new StringBuilder();
	            	StringBuilder newQueryWithoutId = new StringBuilder();
	            	newQuery.append(qid).append(": #WAND (");
	            	newQueryWithoutId.append("#WAND (");
	            	
	            	//get a sorted list
	            	List<Entry<String, Double>> sortedMapExpansionTerms = sortByComparator(mapExpansionTerms, false);
	            	
	            	//collect the top N (fbTerms) terms
	            	//if the term candidates are smaller than required amount
	            	if (sortedMapExpansionTerms.size() < fbTerms) {
	            		for (int index = sortedMapExpansionTerms.size() - 1; index >= 0; index--) {
	            			newQuery.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
	            			newQuery.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");
	            			newQueryWithoutId.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
	            			newQueryWithoutId.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");
	            		}
	            	} else {
	            		for (int index = fbTerms - 1; index >= 0 ; index--) {
	            			newQuery.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
	            			newQuery.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");
	            			newQueryWithoutId.append(String.format("%.4f", sortedMapExpansionTerms.get(index).getValue())).append(" ");
	            			newQueryWithoutId.append(sortedMapExpansionTerms.get(index).getKey()).append(" ");
	                	}
	            	}
	            	newQuery.delete(newQuery.length() - 1, newQuery.length());
	            	newQuery.append(")");
	            	newQueryWithoutId.delete(newQueryWithoutId.length() - 1, newQueryWithoutId.length());
	            	newQueryWithoutId.append(")");
	            	
	            	
	            	//write to the expanded query file------------------
	            	expansionpwriter.println(newQuery);
	                
	            
	            
	               //combine original and new query and run the final query search
	               // parameters.get("trecEvalOutputPath"), fbOrigWeight, model


	        	   
	        	        
	        	   System.out.println("**Start the expanded query search**");
	        	        
	 
	        	   //combine two queries
	        	   StringBuilder combinedQuery = new StringBuilder();
	        	   combinedQuery.append("#wand (").append(fbOrigWeight).append("  #and(").append(query).append(") ");
	        	   combinedQuery.append(1 - fbOrigWeight).append(" ").append(newQueryWithoutId).append(")");
	        	           	          
	        	   System.out.println("Query " + qid + ":" + combinedQuery.toString());

	        	   ScoreList finalr = null;
	        	          
	        	    //enter query after :
	        	   finalr = processQuery(combinedQuery.toString(), model);

	        	          
	        	    //there is result
	        	    if (finalr.size() != 0) {
	        	    	pwriter.println(printResults(qid, finalr));
	        	    }
	        	          //no match result for this query
	        	     else{
	        	    	 pwriter.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
	        	    }
	        }
	      }//query loop ends
	      
	    } catch (IOException ex) {
	      ex.printStackTrace();
	    } finally {
	      expansionpwriter.close();
	      pwriter.close();//close the expansion query writer
	      input.close();
	      initialRanking.close();
	    }
	  }
  
  /** 
   * Print the query results in the trec_eval form. Edited by yuntiehc.
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryId The id of the query.
   * @param result A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static String printResults(String queryId, ScoreList result) throws IOException {

//    System.out.println(queryName + ":  ");
//    if (result.size() < 1) {
//      System.out.println("\tNo results.");
//    } else {
//      for (int i = 0; i < result.size(); i++) {
//        System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
//            + result.getDocidScore(i));
//      }
//    }
      
      result.sort();
      result.truncate(100);
      //test only

       
      String s = queryId + " Q0 " + Idx.getExternalDocid(result.getDocid(0)) + " 1 " + String.format("%.12f", result.getDocidScore(0)) + " run-1";
      for(int i = 1; i < result.size() && i < 100; i ++){
//          String debug = Idx.getExternalDocid(result.getDocid(i));
//          if(debug.equals("clueweb09-enwp03-57-00556")) {
//        	  System.out.println("You are looking for docid = " + result.getDocid(i));
//          }
          String scoreformat = String.format("%.12f", result.getDocidScore(i));
          s = s + "\n" + queryId + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i+1) + " " + scoreformat + " run-1";
      }
      
    return s;
      

  }
  
  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();
            
    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    } else if (parameters.get("retrievalAlgorithm").equals("BM25")) {
        if(! (parameters.containsKey ("BM25:k_1") &&
                parameters.containsKey ("BM25:b") &&
                parameters.containsKey ("BM25:k_3"))) {
            throw new IllegalArgumentException
            ("Required parameters for BM25 were missing from the parameter file.");
        }
    } else if (parameters.get("retrievalAlgorithm").equals("Indri")) {
        if(! (parameters.containsKey ("Indri:mu") &&
                parameters.containsKey ("Indri:lambda"))) {
            throw new IllegalArgumentException
            ("Required parameters for Indri were missing from the parameter file.");
        }
    }
    return parameters;
  }

}
