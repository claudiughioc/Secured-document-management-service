package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public final class ClientThread implements Runnable {
	private static Logger logger = Logger.getLogger(ClientThread.class.getName());
	private BufferedReader br;
	private PrintWriter pw;
	private Socket s;
	private ClientDetails clientDetails;

	public ClientThread(Socket s, String clientDN) {
		try {
			pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
			br = new BufferedReader(new InputStreamReader(s.getInputStream()));
			this.s = s;
		} catch (Exception e) { }
		clientDetails = new ClientDetails(clientDN);
		logger.info("New connection from " + clientDetails);
	}

	public void processCommandFromClient(String s) {
		if (s.equals("list"))
			serverList();
		if (s.equals("download"))
			serverDownload();
		if (s.equals("upload"))
			serverUpload();
	}

	private void serverUpload() {
	}

	private void serverDownload() {
	}

	private void serverList() {
	}
	
	public void close() {
		if (br != null){
            try {
                br.close();
            }catch(Throwable tt){
            }
        }
        if (pw != null){
            try {
                pw.close();
            }catch(Throwable tt){
            }
        }
        if ( s != null){
            try {
                s.close();
            } catch (Throwable t){
            }
        }
	}
	
	public void run() {
		// run indefinetely until exception
		while (true) {
			try {
				String s = br.readLine();
				if (s == null)
					throw new NullPointerException();
				
				processCommandFromClient(s);
				logger.info("Process cmd " + s);
				pw.println("executed ok "+s);
				pw.flush();
			} catch (Exception e) {
				break;
			}
		}
		System.out.println("Server thread for client is dying");
		close();
	}
}
