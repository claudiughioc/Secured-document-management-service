package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
	 */
	public void storeUploadDetails(String fileName, ClientDetails clientDetails) {
		logger.info("Storing upload info for file " + fileName + " client " + clientDetails);
		String info = fileName + " " + clientDetails.name + " " + clientDetails.department + "\n";
		try {
			// Decrypt the file
			if (!desHelper.decrypt(new FileInputStream(fileDetailsName), new FileOutputStream(fileDetailsName + ".tmp")))
				return;	
			
			// Add the client and file information
			FileOutputStream out = new FileOutputStream(fileDetailsName + ".tmp", true);
			out.write(info.getBytes());
			out.close();
			
			// Delete the old encryption
			File file = new File(fileDetailsName);
			file.delete();

			// Encrypt the new information
			File newEncryption = new File(fileDetailsName);
			if (newEncryption.createNewFile() == false) {
				logger.severe("Failed to re-create the storage information file when uploading '" + fileName + "'");
				return;	
			}
			if (!desHelper.encrypt(new FileInputStream(fileDetailsName + ".tmp"), new FileOutputStream(fileDetailsName))) {
				logger.severe("Failed encrypt storage information file '" + fileName + "'");
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// TODO delete the decryption
		/*
		File temp = new File(fileDetailsName + ".tmp");
		temp.delete();
		*/
	}
}
