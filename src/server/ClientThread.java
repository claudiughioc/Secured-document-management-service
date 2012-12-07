package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;

import authorization_server.AuthorizationServer;

import common.FileTransport;

/**
 * A server thread that deals with connections from clients
 * 
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 *
 */
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

			String response = communicateWithAuth("Hello");
			System.out.println("Sent hello to auth, got " + response);
			
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
	 * send a message to the authorization server and wait for a response
	 * @param message
	 * @return
	 */
	String communicateWithAuth (String message) {
		// connect to the authorization server
		SSLSocket auth = null;
		try {
			System.out.println("Server connecting to auth: " + Server.authServer + " port " + AuthorizationServer.SERVER_PORT);
			auth = (SSLSocket) Server.ctx.getSocketFactory().createSocket(Server.authServer, AuthorizationServer.SERVER_PORT);
		} catch (Exception e) {
			logger.severe("[" + Server.name + "] Failed to create a SSL socket to communicate with the authentication server");
			e.printStackTrace();
			return null;
		}
		logger.info("[" + Server.name + "] Successfully created a SSL socket to communicate with the authentication server");
		BufferedReader authReader = null;
		BufferedWriter authWriter = null;
		// send the information about the client to the authentication server
		try {
			authReader = new BufferedReader(new InputStreamReader(auth.getInputStream()));
			authWriter = new BufferedWriter(new OutputStreamWriter(auth.getOutputStream()));
		} catch (Exception e) {
			logger.severe("[" + Server.name + "] Failed to properly handle the communication with the authentication server");
			e.printStackTrace();
			return null;
		}

		try {
			authWriter.write(message);
			authWriter.newLine();
			authWriter.flush();
		} catch (IOException e) {
			logger.severe("[" + Server.name + "] Failed to send message to the authentication server");
			e.printStackTrace();
			try {
				auth.close();
			} catch (IOException ioe) {
				logger.severe("[" + Server.name + "] Failed to close the socket");
				ioe.printStackTrace();	
			}
			return null;
		}
		// wait for the response from the authentication server
		String response = null;
		try {
			response = authReader.readLine();
		} catch (IOException e) {
			logger.severe("[" + Server.name + "] Failed to read the response the authentication server has sent");
			e.printStackTrace();
			try {
				auth.close();
			} catch (IOException ioe) {
				logger.severe("[" + Server.name + "] Failed to close the socket");
				ioe.printStackTrace();	
			}
			return null;
		}
		if (response == null) {
			try {
				auth.close();
			} catch (IOException ioe) {
				logger.severe("[" + Server.name + "] Failed to close the socket");
				ioe.printStackTrace();	
			}
		}

		return response;
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
