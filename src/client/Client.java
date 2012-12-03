package client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class Client extends Thread{
	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger(Client.class.getName());

	private BufferedReader br;
	private PrintWriter pw;
	private SSLSocket s;
	
	public void createSSLConnection (String address, int port) throws Exception{

		// set up key manager to do server authentication
		String store=System.getProperty("KeyStore");
		String passwd =System.getProperty("KeyStorePass");

		SSLContext ctx;
		KeyManagerFactory kmf;
		KeyStore ks;
		char[] storepswd = passwd.toCharArray(); 
		ctx = SSLContext.getInstance("TLS");

		/* IBM or Sun vm ? */
		if ( System.getProperty("java.vm.vendor").toLowerCase().indexOf("ibm") != -1 ){
			kmf = KeyManagerFactory.getInstance("IBMX509","IBMJSSE");
		} else {
			kmf = KeyManagerFactory.getInstance("SunX509");
		}

		ks = KeyStore.getInstance("JKS");

		ks.load(new FileInputStream(store), storepswd);
		kmf.init(ks,storepswd);
		ctx.init(kmf.getKeyManagers(), new TrustManager[] {new CustomTrustManager()}, null);
		SSLSocketFactory ssf = ctx.getSocketFactory();
		s = (SSLSocket)ssf.createSocket();

		s.connect(new InetSocketAddress(address, port));

		pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		br = new BufferedReader(new InputStreamReader(s.getInputStream()));

	} //createSSLConnection

	
	
	public Client(String hostname, int port) {
		try {
		createSSLConnection(hostname, port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean parseCommand(String command) {
		if (command.equals("quit") || command.equals("exit")) {
			return false;
		}
		if (command.equals("list"))
			listCommand();
		if (command.equals("download"))
			downloadCommand();
		if (command.equals("upload"))
			uploadCommand();
		if (command.equals("help"))
			help();
		return true;
	}

	public void listCommand() {
		System.out.println("Listing");
		pw.println("list");
		pw.flush();
		
		try {
			String resp = br.readLine();
			logger.info("Resp from server " + resp);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void downloadCommand() {
		System.out.println("Downloading...");
	}

	public void uploadCommand() {
		System.out.println("Uploading...");
	}

	public void help() {
		System.out.println("Available commands:");
		System.out.println("list - lists all the available documents on the server");
		System.out.println("upload <name> - uploads a file on the server");
		System.out.println("download <name> - downloads a file from the server");
		System.out.println("quit/exit - close the application");
	}

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
