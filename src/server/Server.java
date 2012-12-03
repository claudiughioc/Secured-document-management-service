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
import java.security.cert.Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * O clasa server ce accepta conexiuni TLS.
 * @author Dobre Ciprian
 *
 */
public class Server implements Runnable {

	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger("capV.example3.Server");
	
	// variabila ce este folosita pentru testarea conditiei de oprire
	protected volatile boolean hasToRun = true;
	// socketul server
	protected ServerSocket ss = null;
	
	// un pool de threaduri ce este folosit pentru executia secventelor de operatii corespunzatoare
	// conextiunilor cu fiecare client
	final protected ExecutorService pool;
	final private ThreadFactory tfactory;
	private Certificate CACertificate = null;
	
	/**
	 * Constructor.
	 * @param port Portul pe care va asculta serverul
	 * @throws Exception
	 */
	public Server(int port) throws Exception {
		// set up key manager to do server authentication
		String store=System.getProperty("KeyStore");
		String passwd =System.getProperty("KeyStorePass");
		System.out.println("Password = " + passwd);
		ss = createServerSocket(port, store, passwd);
		tfactory = new DaemonThreadFactory();
		pool = Executors.newCachedThreadPool(tfactory);		
	}
	
	/**
	 * Metoda ce creaza un nou server socket folosind un anumit keystore si parola
	 * @param port: port to listen on
	 * @param store: the path to keystore file containing server key pair (private/public key); if <code>null</code> is passed 
	 * @param passwd: password needed to access keystore file
	 * @return a SSL Socket bound on port specified
	 * @throws IOException
	 */
	public SSLServerSocket createServerSocket(int port, String keystore, String password) throws IOException {
		SSLServerSocketFactory ssf = null;
		SSLServerSocket ss = null;
		try {
			SSLContext ctx;
			KeyManagerFactory kmf;
			KeyStore ks;
			
			// Load the keystore
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			FileInputStream is = new FileInputStream(keystore);
			ks.load(is, password.toCharArray());
			if (logger.isLoggable(Level.INFO))
				logger.log(Level.INFO, "Server keys loaded");

			// Obtain the key manager factory
			kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, password.toCharArray());

			// Get a Trust Manager
			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
			trustManagerFactory.init(ks);

			// Initialize the SSL Context
			ctx = SSLContext.getInstance("TLS");
			ctx.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());

			// get the certificate of this server's certification authority
			this.CACertificate = ks.getCertificate("certification_authority");
			
			//Create the SSL Socket
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "Creating SSocket");
			}
			ssf = ctx.getServerSocketFactory();
			ss = (SSLServerSocket) ssf.createServerSocket();

			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SSocket created!");
			}
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SSocket binding on port " + port);
			}
			ss.bind(new InetSocketAddress(port));
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SSocket bounded on port " + port);
			}
			// this socket will not try to authenticate clients based on X.509 Certificates			
			ss.setNeedClientAuth(false);
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SSocket FINISHED ok! Bounded on " + port);
			}

		} catch (Throwable t) {
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "Got Exception", t);
			}
			t.printStackTrace();
			throw new IOException(t.getMessage());
		}
		return ss;
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
	 * @author Dobre Ciprian
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

	public void processCommandFromClient(String s) {
		
	}
	
	public static void main(String args[]) {
		if (args == null || args.length < 1) {
			System.out.println("Nu a fost furnizat ca argument portul");
			return;
		}
		try {
			int port = Integer.parseInt(args[0]);
			(new Thread(new Server(port))).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
