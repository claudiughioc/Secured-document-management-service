package common;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.StringTokenizer;

import server.Server;

public class DepartmentPriorities {
	public static final String PRIORITIES	= "config/server/priorities.txt";
	
	/**
	 * Initiates the Hashtable with departments - priority
	 * Will be called by both the server and the Authorization Server
	 */
	public static Hashtable<String, Integer> initPriorities() {
		Hashtable<String, Integer> priorities = new Hashtable<String, Integer>();
		String dept = "";
		int priority;
		try {
			FileInputStream fstream = new FileInputStream(Server.PRIORITIES);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;

			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(strLine, " \t,");
				dept = st.nextToken();
				priority = Integer.parseInt(st.nextToken());
				priorities.put(dept, priority);
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return priorities;
	}
}
