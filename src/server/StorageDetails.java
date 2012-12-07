package server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import common.DESEncrypter;

/**
 * Contains information about the files saved on the server
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 *
 */
public class StorageDetails {
	private DESEncrypter desHelper = null;
	private String fileDetailsName;
	private Logger logger = Logger.getLogger(StorageDetails.class.getName());

	public StorageDetails(String fileDetailsName) {
		this.fileDetailsName = fileDetailsName;
		SecretKey key = null;
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(Server.SECRET_KEY));
            key = (SecretKey)in.readObject();
            in.close();
        } catch (FileNotFoundException fnfe) {
        	// generate a cryptographic key
        	try {
				key = KeyGenerator.getInstance("DES").generateKey();
	            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(Server.SECRET_KEY));
	            out.writeObject(key);
	            out.close();
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        } catch (Exception e) {
        	e.printStackTrace();
        }
        desHelper = new DESEncrypter(key);
	}

	/**
	 * Saves the file and client information to the encrypted file
	 * @param fileName
	 * @param clientDetails
	 * @return true if the server should accept the new file
	 * 			false if the server should reject the file
	 */
	public boolean storeUploadDetails(String fileName, ClientDetails clientDetails) {
		// Check if the client's department has a priority
		if (!Server.priorities.containsKey(clientDetails.department)) {
			logger.severe("The client's department is unknown. Adding default priority: " + Server.DEFAULT_DEPT_PRIORITY);
			Server.priorities.put(clientDetails.department, Server.DEFAULT_DEPT_PRIORITY);
		}
		
		logger.info("Storing upload info for file " + fileName + " client " + clientDetails);
		
		try {
			// Decrypt the file
			if (!desHelper.decrypt(new FileInputStream(fileDetailsName), new FileOutputStream(fileDetailsName + ".tmp")))
				return false;	
			
			// Add or replace the client and file information
			if(!updateFileInformation(fileName, clientDetails, fileDetailsName + ".tmp"))
				return false;
			
			// Delete the old encryption
			File file = new File(fileDetailsName);
			file.delete();

			// Encrypt the new information
			File newEncryption = new File(fileDetailsName);
			if (newEncryption.createNewFile() == false) {
				logger.severe("Failed to re-create the storage information file when uploading '" + fileName + "'");
				return false;	
			}
			if (!desHelper.encrypt(new FileInputStream(fileDetailsName + ".tmp"), new FileOutputStream(fileDetailsName))) {
				logger.severe("Failed encrypt storage information file '" + fileName + "'");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// TODO delete the decryption
		/*
		File temp = new File(fileDetailsName + ".tmp");
		temp.delete();
		*/
		return true;
	}

	/**
	 * Adds or replace client and new file information in the server's
	 * storage configuration file
	 * @param fileName - the name of the new file
	 * @param clientDetails - client details
	 * @param decryptedFile - decrypted configuration file
	 * @return true if the server should accept the new file
	 * 			false if the server should reject the file
	 * @throws IOException 
	 */
	public boolean updateFileInformation(String fileName, ClientDetails clientDetails, String decryptedFile) throws IOException {
		String info = fileName + " " + clientDetails.name + " " + clientDetails.department;
		String currFile, currClient, currDept;
		boolean updated = true, clientFound = false;
		try{
			// Open the decrypted file
			FileInputStream fstream = new FileInputStream(decryptedFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			// Open a new temporary file
			PrintWriter prw = new PrintWriter(new FileOutputStream(new File(decryptedFile + ".cpy")));

			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(strLine, " \t,");
				currFile = st.nextToken();
				currClient = st.nextToken();
				currDept = st.nextToken();
				// Skip different files
				if (!currFile.equals(fileName)) {
					prw.println(strLine);
					continue;
				}
				clientFound = true;
				int currPriority = Server.priorities.get(currDept);
				int newPriority = Server.priorities.get(clientDetails.department);
				if (newPriority <= currPriority) {
					System.out.println("Storage: replacing file info in config");
					prw.println(info);
				} else {
					System.out.println("Storage: the new client is dumber than the previous one");
					prw.println(strLine);
					updated = false;
				}
			}
			if (!clientFound) {
				prw.println(info);
				updated = true;
			}
			prw.close();
			br.close();
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		if (!updated) {
			System.out.println("Storage: the server should refuse");
			return false;
		}
		System.out.println("Storage: the server should accept");
		
		// Delete the old configuration file
		File file = new File(decryptedFile);
		file.delete();
		
		// Replace it with the new one
		file = new File(decryptedFile + ".cpy");
		file.renameTo(new File(decryptedFile));
		return true;
	}
}
