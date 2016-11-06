import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Test {

	public static void main(String[] args) throws IOException {
		  //process the pagerank file
		  BufferedReader input = null;
		  String filePath = "PageRankInIndex.txt";
		  try {
		      String qLine = null;
		      input = new BufferedReader(new FileReader(filePath));

		      //  Each pass of the loop processes one line.
		      while ((qLine = input.readLine()) != null) {
		    	  String[] test = qLine.split("\\s+");
		    	  for (int i = 0; i < test.length; i++) {
		    		  System.out.println(test[i]);
		    	  }
		    	  break;
		      }
		  }catch (IOException ex) {
		      ex.printStackTrace();
	     } finally {
	       input.close();
	     }
	}

}
