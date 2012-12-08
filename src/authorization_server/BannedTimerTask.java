package authorization_server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.TimerTask;

/**
 * Timer Task for banning clients
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 *
 */
public class BannedTimerTask extends TimerTask {
	private AuthorizationServer server = null;
	private String clientName = null;
	
	public BannedTimerTask (AuthorizationServer server, String clientName) {
		this.server = server;
		this.clientName = clientName;	
	}

	public void run () {
		AuthorizationServer.logger.info("REMOVING THE BAN FOR CLIENT '" + this.clientName);
		try {
			if (!this.server.crypt.decrypt(new FileInputStream(AuthorizationServer.BANNED_ENCRYPTED),
					new FileOutputStream(AuthorizationServer.BANNED_DECRYPTED))) {
				return;
			}
			
			removeBannedClient(clientName);
			
			// delete the old encryption
			File file = new File(AuthorizationServer.BANNED_ENCRYPTED);
			file.delete();
			
			// encrypt the new information
			File newEncryption = new File(AuthorizationServer.BANNED_ENCRYPTED);
			if (newEncryption.createNewFile() == false) {
				AuthorizationServer.logger.severe("Failed to re-create the 'banned_encrypted' file when trying to remove the banning of the client '" + clientName + "'");
				return;	
			}
			
			if (!this.server.crypt.encrypt(new FileInputStream(AuthorizationServer.BANNED_DECRYPTED), new FileOutputStream(newEncryption))) {
				AuthorizationServer.logger.severe("Failed to ban the client '" + clientName + "'");
				return;
			}
		} catch (Exception e) {
			AuthorizationServer.logger.warning("An exception was caught while trying to remove '" + clientName + "' from the banned list");
			e.printStackTrace();
			return;
		}
		// delete the decryption
		File temp = new File(AuthorizationServer.BANNED_DECRYPTED);
		temp.delete();
		return;
	}

	/**
	 * Removes a client from the list with banned clients
	 * @param clientName
	 */
	public void removeBannedClient(String clientName) {
		try{
			// Open the decrypted file
			FileInputStream fstream = new FileInputStream(AuthorizationServer.BANNED_DECRYPTED);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			// Open a new temporary file
			PrintWriter prw = new PrintWriter(new FileOutputStream(new File(AuthorizationServer.BANNED_DECRYPTED + ".cpy")));

			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(strLine, " \t,");
				String currClient = st.nextToken();
				String time = st.nextToken();
				if (currClient.equals(clientName))
					continue;
					
				String line = currClient + " " + time;
				prw.println(line);
			}
			prw.close();
			br.close();
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Delete the old configuration file
		File file = new File(AuthorizationServer.BANNED_DECRYPTED);
		file.delete();
		
		// Replace it with the new one
		file = new File(AuthorizationServer.BANNED_DECRYPTED + ".cpy");
		file.renameTo(new File(AuthorizationServer.BANNED_DECRYPTED));
	}
}