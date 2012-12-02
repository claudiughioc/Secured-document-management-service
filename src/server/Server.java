package server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class Server implements Runnable{
	private static final transient Logger logger = Logger.getLogger("server.Server");
	protected volatile boolean hasToRun = true;
	// The Server socket
	protected ServerSocket ss = null;
	final protected ExecutorService pool;
	final private ThreadFactory tfactory;

	public Server(int port) throws IOException {
		String store = System.getProperty("KeyStore");
		String passwd = System.getProperty("KeyStorePass");
		ss = createServerSocket(port, store, passwd);
		tfactory = new DaemonThreadFactory();
		pool = Executors.newCachedThreadPool(tfactory);
	}

	/**
	 * Metoda run ... accepta conexiuni si initiaza noi threaduri pentru fiecare conexiune in parte
	 */
	public void run() {
		if (logger.isLoggable(Level.INFO))
			logger.log(Level.INFO, "TLSServerSocket entering main loop ... ");
		while (hasToRun) {
			try {
				Socket s = ss.accept();
				s.setTcpNoDelay(true);
				//add the client connection to connection pool
				pool.execute(new ClientThread(s));
				if (logger.isLoggable(Level.INFO))
					logger.log(Level.INFO, "New client connection added to connection-pool",s);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	/**
	 * Metoda poate fi folosita pentru oprirea serverului
	 */
	public void stop() {
		hasToRun = false;
		try {
			ss.close();
		} catch (Exception ex) {}
		ss = null;
	}

	/**
	 * Metoda ce creaza un nou server socket folosind un anumit keystore si parola
	 * @param port: port to listen on
	 * @param store: the path to keystore file containing server key pair (private/public key); if <code>null</code> is passed 
	 * @param passwd: password needed to access keystore file
	 * @return a SSL Socket bound on port specified
	 * @throws IOException
	 */
	public static SSLServerSocket createServerSocket(int port, String keystore, String password) throws IOException {
		SSLServerSocketFactory ssf = null;
		SSLServerSocket ss = null;
		try {
			SSLContext ctx;
			KeyManagerFactory kmf;
			KeyStore ks;
			ctx = SSLContext.getInstance("TLS");
			kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			FileInputStream is = new FileInputStream(keystore);
			ks.load(is, password.toCharArray());
			kmf.init(ks, password.toCharArray());
			if (logger.isLoggable(Level.FINER))
				logger.log(Level.FINER, "Server keys loaded");

			ctx.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());
			ssf = ctx.getServerSocketFactory();
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Creating SSocket");
			}
			ss = (SSLServerSocket) ssf.createServerSocket();

			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "SSocket created!");
			}
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "SSocket binding on port " + port);
			}
			ss.bind(new InetSocketAddress(port));
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "SSocket bounded on port " + port);
			}
			// this socket will not try to authenticate clients based on X.509 Certificates         
			ss.setNeedClientAuth(false);
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "SSocket FINISHED ok! Bounded on " + port);
			}
			System.out.println("SSocket FINISHED ok! Bounded on " + port);

		} catch (Throwable t) {
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Got Exception", t);
			}
			t.printStackTrace();
			throw new IOException(t.getMessage());
		}
		return ss;
	}


	/** 
	 * Custom thread factory used in connection pool
	 */
	private final class DaemonThreadFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			return thread;
		}   
	}

	/**
	 * Clasa ce implementeaza functionalitatea conexiunii cu un anumit client
	 *
	 */
	private final class ClientThread implements Runnable {

		private BufferedReader br;
		private PrintWriter pw;
		private Socket s;

		public ClientThread(Socket s) {
			try {
				pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
				br = new BufferedReader(new InputStreamReader(s.getInputStream()));
				this.s = s;
			} catch (Exception e) { }
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
			System.out.println("Running the thread for new client");
			while (true) {
				try {
					String s = br.readLine();
					System.out.println("Got from client " + s);
					// simulate processing
					pw.println("executed ok "+s);
					pw.flush();
				} catch (Exception e) {
					break;
				}
			}
			close();
		}
	}




	public static void main(String [] args) {
		System.out.println("Starting the server");
		if (args == null || args.length < 1) {
			System.out.println("Introduce the server's port");
			return;
		}

		int port = Integer.parseInt(args[0]);
		try {
			(new Thread(new Server(port))).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
