package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import server.Server;

import common.FileTransport;

public class Client extends Thread{
	/** Logger used by this class */
	private static Logger logger = Logger.getLogger(Client.class.getName());

	private static final String CLIENT_DIRECTORY = "resources/files/";
	private BufferedReader br;
	private DataOutputStream dos;
	private DataInputStream dis;
	private String name;
	private PrintWriter pw;
	private SSLSocket s;
	private Certificate CACertificate = null;
	
	/**
	 * Constructor for Client
	 * Initialize connection to the server
	 * @param hostname
	 * @param port
	 */
	public Client(String hostname, int port) {
		this.name = "Claudiu";
		try {
		createSSLConnection(hostname, port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Create the SSL connection to the server
	 * @param address
	 * @param port
	 * @throws Exception
	 */
	public void createSSLConnection (String address, int port) throws Exception{
		// set up key manager to do server authentication
		String store=System.getProperty("KeyStore");
		String passwd =System.getProperty("KeyStorePass");
		SSLContext ctx;
		KeyManagerFactory kmf;
		KeyStore ks;
		char[] storepswd = passwd.toCharArray(); 
		
		// Load the keystore
		ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(store), storepswd);
		
		// Create the KeyManagerFactory
		/* IBM or Sun vm ? */
		if ( System.getProperty("java.vm.vendor").toLowerCase().indexOf("ibm") != -1 ){
			kmf = KeyManagerFactory.getInstance("IBMX509","IBMJSSE");
		} else {
			kmf = KeyManagerFactory.getInstance("SunX509");
		}
		kmf.init(ks,storepswd);
		
		// Get a Trust Manager Factory
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
		trustManagerFactory.init(ks);

		// Create the SSL context
		ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
		this.CACertificate = ks.getCertificate("certification_authority");
		
		// Create the SSL Socket
		SSLSocketFactory ssf = ctx.getSocketFactory();
		s = (SSLSocket)ssf.createSocket();
		s.connect(new InetSocketAddress(address, port));
		
		try {
			s.startHandshake();	
		} catch (IOException e) {
			logger.severe("[" + this.name + "] Failed to complete the handshake");
			e.printStackTrace();
			return;
		}
		logger.info("[" + this.name + "] Successful handshake");
		X509Certificate[] peerCertificates = null;
		try {
			peerCertificates = (X509Certificate[])((s).getSession()).getPeerCertificates();
			if (peerCertificates.length < 2) {
				logger.severe("'peerCertificates' should contain at least two certificates");
				return;
			}
		} catch (Exception e) {
			logger.severe("[" + this.name + "] Failed to get peer certificates");
			e.printStackTrace();
			return;
		}
		if (CACertificate.equals(peerCertificates[1])) {
			logger.info("[" + this.name + "] The peer's CA certificate matches this client's CA certificate");
		}
		else {
			logger.severe("[" + this.name + "] The peer's CA certificate doesn't match this client's CA certificate");
			return;
		}
		
		dos = new DataOutputStream(s.getOutputStream());
		dis = new DataInputStream(s.getInputStream());
		pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		br = new BufferedReader(new InputStreamReader(s.getInputStream()));

	} //createSSLConnection

	/**
	 * Analyzes a command from the client
	 * @param command
	 * @return
	 */
	public boolean parseCommand(String command) {
		if (command.equals("quit") || command.equals("exit")) {
			return false;
		}
		if (command.equals("list")) {
			listCommand();
			return true;
		}
		if (command.startsWith("download")) {
			downloadCommand(command);
			return true;
		}
		if (command.startsWith("upload")) {
			uploadCommand(command);
			return true;
		}
		if (command.equals("help")) {
			help();
			return true;
		}
		if (command.length() == 0)
			return true;
		System.out.println("Unknown command");
		return true;
	}
	
	/**
	 * Wait a response from server
	 */
	public void waitResponse() {
		try {
			String message;
			while (!(message = br.readLine()).equals(Server.END_OF_MESSAGE))
				System.out.println(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Processes the "list" command
	 */
	public void listCommand() {
		pw.println("list");
		pw.flush();
		System.out.println("-- listing files on server");
		waitResponse();
	}

	/**
	 * Processes the "download" command
	 * @param command
	 */
	public void downloadCommand(String command) {
		// Get the file to download
		String fileName = CLIENT_DIRECTORY + command.substring(9);
		
		// Send the command
		pw.println(command);
		pw.flush();
		
		// Receive the file if OK from server
		try {
			String message = br.readLine();
			if (message.equals(FileTransport.OK))
				FileTransport.receiveFile(fileName, dis);
			else {
				System.out.println("-- the download has been rejected by the server");
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("-- downloaded to " + fileName);	
	}

	/**
	 * Processes the "upload" command
	 * @param command
	 */
	public void uploadCommand(String command) {
		// Get the file to upload
		String fileName = CLIENT_DIRECTORY + command.substring(7);
		
		// Send the command
		pw.println(command);
		pw.flush();
		
		// Send the file
		FileTransport.sendFile(fileName, dos);
		System.out.println("-- uploaded " + fileName);
	}

	/**
	 * Display the help messages
	 */
	public void help() {
		System.out.println("Available commands:");
		System.out.println("list - lists all the available documents on the server");
		System.out.println("upload <name> - uploads a file on the server");
		System.out.println("download <name> - downloads a file from the server");
		System.out.println("quit/exit - close the application");
	}

	/**
	 * Main loop to parse commands
	 */
	public void run() {
		String command = "";
		Scanner scanner = new Scanner(System.in);
		try {
			while (true) {
				System.out.flush();
				command = scanner.nextLine();
				if (!parseCommand(command))
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		close();
	}
	
	/**
	 * Close all the streams and the socket to the server
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
        if (dos != null){
            try {
                dos.close();
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
        System.out.println("From client everything is closed now");
	}
	
	public static void main (String [] args) {
		String hostname;
		int port;
		Client client;

		if(args == null || args.length < 2) {
			System.out.println("Introduce the server's address and port");
			return;
		}

		hostname = args[0];
		port = Integer.parseInt(args[1]);
		client = new Client(hostname, port);
		client.start();
	}
}
