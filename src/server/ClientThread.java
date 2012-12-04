package server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClientThread implements Runnable {
	private static Logger logger = Logger.getLogger(ClientThread.class.getName());
	private BufferedReader br;
	private PrintWriter pw;
	private DataInputStream dis;
	private Socket s;
	private ClientDetails clientDetails;
	private File storage;

	public ClientThread(Socket s, String clientDN, File storage) {
		try {
			pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
			br = new BufferedReader(new InputStreamReader(s.getInputStream()));
			dis = new DataInputStream(s.getInputStream());
			this.s = s;
		} catch (Exception e) { }
		clientDetails = new ClientDetails(clientDN);
		this.storage = storage;
		logger.info("New connection from " + clientDetails);
	}

	public void processCommandFromClient(String s) {
		if (s.equals("list"))
			serverList();
		if (s.startsWith("download"))
			serverDownload(s);
		if (s.startsWith("upload"))
			serverUpload(s);
	}

	private void serverDownload(String command) {
		String fileName = Server.STORAGE_DIRECTORY + command.substring(9);
	}

	private void serverUpload(String command) {
		String fileName = Server.STORAGE_DIRECTORY + command.substring(7);
		
		// Get a lock to secure writing
		synchronized(Server.writeLock) {
			try {
				OutputStream newFile = new FileOutputStream(fileName);
				
				// Get the length of the file and the bytes
				long len = dis.readLong();
				byte[] mybytearray = new byte[(int) len];  
				dis.read(mybytearray, 0, (int)len);
				
				// Write the file and close
				newFile.write(mybytearray, 0, (int)len);
				newFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (logger.isLoggable(Level.INFO))
			logger.log(Level.INFO, "Successfully uploaded " + fileName);
	}

	/**
	 * Send the list of file to the client
	 */
	private void serverList() {
		String[] chld = storage.list();
		if(chld == null) {
			if (logger.isLoggable(Level.INFO))
				logger.log(Level.INFO, "Specified directory does not exist or is not a directory.");
		} else
			for (int i = 0; i < chld.length; i++){
				pw.println(chld[i]);
				pw.flush();
			}
		pw.println(Server.END_OF_MESSAGE);
		pw.flush();
	}
	
	/**
	 * Closes the streams and the socket
	 */
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
        if (dis != null){
            try {
                dis.close();
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
	
	/**
	 * Main loop waiting for commands from client
	 */
	public void run() {
		// run indefinetely until exception
		while (true) {
			try {
				String s = br.readLine();
				if (s == null)
					throw new NullPointerException();
				
				processCommandFromClient(s);
				logger.info("Received command " + s);
			} catch (Exception e) {
				break;
			}
		}
		System.out.println("Server thread for client is dying");
		close();
	}
}
