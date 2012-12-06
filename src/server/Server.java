package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import common.DESEncrypter;

/**
 * O clasa server ce accepta conexiuni TLS.
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 *
 */
public class Server implements Runnable {
	public static final String END_OF_MESSAGE		 = "###";
	public static final String STORAGE_DIRECTORY	 = "resources/storage/";
	public static final String FILE_STORAGE_DETAILS	 = "config/server/file_details";
	public static final String SECRET_KEY			 = "config/server/SecretKey.ser";
	
	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger("capV.example3.Server");

	// variabila ce este folosita pentru testarea conditiei de oprire
	protected volatile boolean hasToRun = true;

	// socketul server
	protected ServerSocket ss = null;

	// un pool de threaduri ce este folosit pentru executia
	// secventelor de operatii corespunzatoare
	// conextiunilor cu fiecare client
	final protected ExecutorService pool;
	final private ThreadFactory tfactory;
	
	private Certificate CACertificate = null;
	private String name;
	private File storage;
	public static StorageDetails storageDetails = null;

	/**
	 * Constructor.
	 * @param port Portul pe care va asculta serverul
	 * @throws Exception
	 */
	public Server(int port) throws Exception {
		this.name = Server.class.getName();
		ss = createServerSocket(port, System.getProperty("KeyStore"), System.getProperty("KeyStorePass"));
		tfactory = new DaemonThreadFactory();
		pool = Executors.newCachedThreadPool(tfactory);

		// Open the directory of files
		storage = new File(STORAGE_DIRECTORY);
		
		// Create an object to save information about the stored files
		storageDetails = new StorageDetails(FILE_STORAGE_DETAILS);
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
			ss.setNeedClientAuth(true);
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
	 * Metoda run: accepta conexiuni si initiaza noi threaduri pentru fiecare conexiune in parte
	 */
	public void run() {
		if (logger.isLoggable(Level.INFO))
			logger.log(Level.INFO, "TLSServerSocket entering main loop ... ");
		while (hasToRun) {
			try {
				Socket s = ss.accept();	
				s.setTcpNoDelay(true);
				try {
					((SSLSocket) s).startHandshake();
				} catch (IOException e) {
					logger.severe("[" + this.name + "] Failed to complete a handshake");
					e.printStackTrace();
					continue;	
				}
				logger.info("[" + this.name + "] Successful handshake");

				// Get the client's certificate
				X509Certificate[] peerCertificates = null;
				try {
					peerCertificates = (X509Certificate[])(((SSLSocket) s).getSession()).getPeerCertificates();
					if (peerCertificates.length < 2) {
						logger.severe("'peerCertificates' should contain at least two certificates");
						continue;
					}
				} catch (Exception e) {
					logger.severe("[" + this.name + "] Failed to get peer certificates");
					e.printStackTrace();
					continue;
				}

				// Check if the client's certificate has the same CA
				if (CACertificate.equals(peerCertificates[1])) {
					logger.info("[" + this.name + "] The peer's CA certificate matches this server's CA certificate");
				}
				else {
					logger.severe("[" + this.name + "] The peer's CA certificate doesn't match this server's CA certificate");
					continue;
				}

				// Add the client connection to connection pool
				String clientDN = peerCertificates[0].getSubjectX500Principal().getName();
				pool.execute(new ClientThread(s, clientDN, storage));
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
