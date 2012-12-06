package server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.FileTransport;

public final class ClientThread implements Runnable {
	private static Logger logger = Logger.getLogger(ClientThread.class.getName());
	private BufferedReader br;
	private PrintWriter pw;
	private DataInputStream dis;
	private DataOutputStream dos;
	private Socket s;
	private ClientDetails clientDetails;
	private File storage;

	public ClientThread(Socket s, String clientDN, File storage) {
		try {
			pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
			br = new BufferedReader(new InputStreamReader(s.getInputStream()));
			dis = new DataInputStream(s.getInputStream());
			dos = new DataOutputStream(s.getOutputStream());
			this.s = s;
		} catch (Exception e) { }
		clientDetails = new ClientDetails(clientDN);
		this.storage = storage;
		logger.info("New connection from " + clientDetails);
	}

	/**
	 * Receives a string representing the command from client
	 * Processes the command
	 * @param command
	 */
	public void processCommandFromClient(String command) {
		if (command.equals("list"))
			serverList();

		if (command.startsWith("download")) {
			String fileName = Server.STORAGE_DIRECTORY + command.substring(9);
			
			// Send the file to the client
			pw.println(FileTransport.DENIED);
			pw.flush();
			FileTransport.sendFile(fileName, dos);
			if (logger.isLoggable(Level.INFO))
				logger.log(Level.INFO, "Successfully downloaded " + fileName);
		}

		if (command.startsWith("upload")) {
			String fileName = Server.STORAGE_DIRECTORY + command.substring(7);

			// Add the file information to the encrypted file
			// Also check if the server should accept the new file
			if (!Server.storageDetails.storeUploadDetails(fileName, clientDetails)) {
				pw.println(FileTransport.DENIED);
				System.out.println("Upload denied");
			} else {
				pw.println(FileTransport.OK);
				System.out.println("Upload accepted");
			}
			pw.flush();

			// Get the file from the client
			FileTransport.receiveFile(fileName, dis);
			if (logger.isLoggable(Level.INFO))
				logger.log(Level.INFO, "Successfully uploaded " + fileName);
		}
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
        if (dos != null){
            try {
                dos.close();
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
