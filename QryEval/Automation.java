
public class Automation {

	public static void main(String[] args) throws Exception {
		

		String[] trial4_8 = {"input8.txt"};
		String[] trial4_10 = {"input10.txt"};
        String[] trial5_4 = {"inputsdmex.txt"};
        
		QryEval.main(trial4_8);
		System.out.println("---------------------0.8 done");
		QryEval.main(trial4_10);
		System.out.println("---------------------1.0 done");
		QryEval.main(trial5_4);
	}

}

