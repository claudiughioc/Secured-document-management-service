package authorization_server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.logging.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import common.DESEncrypter;

/**
 * The Authorization Server 
 * Accepts connections from the server
 * Bans, reject / allow downloads
 * 
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 *
 */
public class AuthorizationServer implements Runnable {
	public static final int SERVER_PORT					= 778;
	private static final int BAN_TIME					= 30000; // mili seconds
	private static final String SECRET_KEY				= "config/auth/AuthSecretKey.ser";
	static final String BANNED_ENCRYPTED				= "config/auth/banned";
	static final String BANNED_DECRYPTED				= "config/auth/banned.tmp";

	static Logger logger = Logger.getLogger(AuthorizationServer.class.getName());

	private Map<String, Integer> priorities;
	private KeyStore serverKeyStore = null;
	private KeyManagerFactory keyManagerFactory = null;
	private TrustManagerFactory trustManagerFactory = null;
	private SSLContext sslContext = null;
	private SSLServerSocket serverSocket = null;
	private Certificate CACertificate = null;
	private String authKeystore;
	private String authKeystorePass;
	// key used for encrypting / decrypting the file containing information about the banned clients
	DESEncrypter crypt = null;


	public AuthorizationServer () {
		this.authKeystore 	  = System.getProperty("KeyStore");
		this.authKeystorePass = System.getProperty("KeyStorePass");
		this.priorities = new LinkedHashMap<String, Integer>();
		this.priorities.put("HUMAN_RESOURCES", 1);
		this.priorities.put("ACCOUNTING", 1);
		this.priorities.put("IT", 2);
		this.priorities.put("MANAGEMENT", 3);
	}

	/**
	 * Initializes the Authorization Server, opens SSL socket, creates decrypter
	 * @return
	 */
	private int initialize () {
		try {
			// get a reference to the authorization server's keystore
			this.serverKeyStore = KeyStore.getInstance("JKS");
			this.serverKeyStore.load(new FileInputStream(authKeystore), authKeystorePass.toCharArray());
		} catch (Exception e) {
			logger.severe("Failed to get a reference to the authorization server's keystore: keyStoreName = "
					+ authKeystore + ", password = " + authKeystorePass);
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully obtained a reference to the authorization server's keystore");

		try {
			// get a key manager factory
			this.keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		} catch (Exception e) {
			logger.severe("Failed to obtain a key manager factory");
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully obtained a key manager factory");

		try {
			// initialize the key manager factory with a source of key material
			this.keyManagerFactory.init(this.serverKeyStore, authKeystorePass.toCharArray());
		} catch (Exception e) {
			logger.severe("Failed to initialize the key manager factory with the key material coming from the server's keystore");
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully initialized the key manager factory");

		try {
			// get a trust manager factory
			this.trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
		} catch (Exception e) {
			logger.severe("Failed to obtain a trust manager factory");
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully obtained a trust manager factory");

		try {
			// initialize the trust manager factory with a source of key material
			this.trustManagerFactory.init(this.serverKeyStore);
		} catch (Exception e) {
			logger.severe("Failed to initialize the trust manager factory with the key material coming from the server's keystore");
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully initialized the trust manager factory");

		try {
			// set the SSL context
			this.sslContext = SSLContext.getInstance("TLS");
			this.sslContext.init(this.keyManagerFactory.getKeyManagers(), this.trustManagerFactory.getTrustManagers(), null);
		} catch (Exception e) {
			logger.severe("Failed to initialize the SSl context");
			e.printStackTrace();
			return 1;
		}
		logger.info("Successfully initialized a SSL context");

		try {
			// get the certificate of this server's certification authority
			this.CACertificate = this.serverKeyStore.getCertificate("certification_authority");
		} catch (Exception e) {
			logger.severe("Failed to retrive the certificate of this server's certificate authority");
			e.printStackTrace();
			return 1;
		}

		// Try to open the Authorization server's secret key
		SecretKey key = null;
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(AuthorizationServer.SECRET_KEY));
			key = (SecretKey)in.readObject();
			in.close();
		} catch (FileNotFoundException fnfe) {
			// generate a cryptographic key
			try {
				key = KeyGenerator.getInstance("DES").generateKey();
				ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(AuthorizationServer.SECRET_KEY));
				out.writeObject(key);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
				return 1;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 1;
		}
		crypt = new DESEncrypter(key);
		return 0;
	}

	public void run () {
		if (initialize() != 0) {
			return;
		}
		try {
			this.serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(AuthorizationServer.SERVER_PORT);
		} catch (Exception e) {
			logger.severe("Failed to create a SSL server socket");
			e.printStackTrace();
			return;
		}
		logger.info("Successfully created a SSL server socket");

		serverSocket.setNeedClientAuth(true);
		logger.info("Listening for incoming connections... ");

		while (true) {
			SSLSocket socket = null;
			try {
				socket = (SSLSocket) this.serverSocket.accept();
			} catch (Exception e) {
				logger.severe("Failed to accept an incoming connection");
				e.printStackTrace();
				return;	
			}
			logger.info("Just accepted a connection");
			try {
				socket.startHandshake();
			} catch (IOException e) {
				logger.severe("Failed to complete a handshake");
				e.printStackTrace();
				continue;	
			}
			logger.info("Successful handshake");
			X509Certificate[] peerCertificates = null;
			try {
				peerCertificates = (X509Certificate[])(socket.getSession()).getPeerCertificates();
				if (peerCertificates.length < 2) {
					logger.severe("'peerCertificates' should contain at least two certificates");
					continue;
				}
			} catch (Exception e) {
				logger.severe("Failed to get peer certificates");
				e.printStackTrace();
				continue;
			}

			if (CACertificate.equals(peerCertificates[1])) {
				logger.info("The peer's CA certificate matches the authorization server's CA certificate");
			}
			else {
				logger.severe("The peer's CA certificate doesn't match the authorization server's CA certificate");
				continue;
			}
			BufferedReader reader = null;
			BufferedWriter writer = null;
			try {
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));	
			} catch (Exception e) {
				logger.severe("Failed to properly handle the communication with a peer");
				e.printStackTrace();
				continue;
			}
			String request = null;
			try {
				request = reader.readLine();	
			} catch (IOException e) {
				logger.severe("Failed to read a message from the socket");
				e.printStackTrace();
				try {
					socket.close();
				} catch (IOException ioe) {
					logger.severe("Failed to close the socket");
					ioe.printStackTrace();	
				}
				continue;
			}
			if (request == null) {
				logger.severe("The end of the input stream has been reached");
				try {
					socket.close();
				} catch (IOException ioe) {
					logger.severe("Failed to close the socket");
					ioe.printStackTrace();	
				}
				continue;
			}
			System.out.println("Received request: <" + request + ">");
			String response = processRequest(request);
			try {
				writer.write(response);
				writer.newLine();
				writer.flush();	
			} catch (IOException e) {
				logger.severe("Failed to write a message to the socket");
				e.printStackTrace();
				try {
					socket.close();
				} catch (IOException ioe) {
					logger.severe("Failed to close the socket");
					ioe.printStackTrace();	
				}
				continue;
			}
		}
	}

	/**
	 * Processes a request from the server
	 * @param request
	 * @return
	 */
	public String processRequest(String request) {
		StringTokenizer st = new StringTokenizer(request, " ");
		if (request.startsWith("BAN")) {
			st.nextToken();
			ban(st.nextToken());
		}
		return "mama";
	}

	private void ban (String clientName) {
		System.out.println("Banning " + clientName);
		try {
			if (!crypt.decrypt(new FileInputStream(BANNED_ENCRYPTED), new FileOutputStream(BANNED_DECRYPTED))) {
				return;	
			}
			// add this client to the list of those banned
			FileOutputStream out = new FileOutputStream(BANNED_DECRYPTED, true);
			String line = clientName + " " + System.currentTimeMillis() + "\n";
			out.write(line.getBytes());
			out.close();
			
			// delete the old encryption
			File file = new File(BANNED_ENCRYPTED);
			file.delete();
			
			// encrypt the new information
			File newEncryption = new File(BANNED_ENCRYPTED);
			if (newEncryption.createNewFile() == false) {
				logger.severe("Failed to re-create the 'banned_encrypted' file when banning the client '" + clientName + "'");
				return;	
			}
			if (!crypt.encrypt(new FileInputStream(BANNED_DECRYPTED), new FileOutputStream(BANNED_ENCRYPTED))) {
				logger.severe("Failed to ban the client '" + clientName + "'");
				return;
			}
			
			// set the timer so that the client is allowed back after the timeout
			Date timeToRun = new Date(System.currentTimeMillis() + BAN_TIME);
			Timer timer = new Timer();
			timer.schedule(new BannedTimerTask(this, clientName), timeToRun);

		} catch (Exception e) {
			logger.severe("Failed to ban the client '" + clientName + "'");
			e.printStackTrace();
			return;
		}
		// delete the decryption
		File temp = new File(BANNED_DECRYPTED);
		temp.delete();
		return;
	}

	public static void main (String args[]) {
		(new Thread(new AuthorizationServer())).start();
	}
}
