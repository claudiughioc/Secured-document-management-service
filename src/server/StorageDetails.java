package server;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * Contains information about the files saved on the server
 * @author claudiu
 *
 */
public class StorageDetails {
	private Cipher cipher;
	private Key key = null;
	private String fileDetailsName;

	public StorageDetails(String fileDetailsName) {
		this.fileDetailsName = fileDetailsName;
		//  Get or create key.
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream("SecretKey.ser"));
			key = (Key)in.readObject();
			in.close();
		} catch (Exception fnfe) {
			try {
				KeyGenerator generator = KeyGenerator.getInstance("DES");
				generator.init(new SecureRandom());
				key = generator.generateKey();
				ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("SecretKey.ser"));
				out.writeObject(key);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		File fileDetails = new File(fileDetailsName);
		// Get a cipher object.
		try {
			// Read the bytes from file
			byte[] mybytearray = new byte[(int) fileDetails.length()]; 
			FileInputStream fis = new FileInputStream(fileDetails);  
			BufferedInputStream bis = new BufferedInputStream(fis);  
			bis.read(mybytearray, 0, mybytearray.length);

			// Decrypt
			cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key);
			BASE64Decoder decoder = new BASE64Decoder();
			byte[] raw = decoder.decodeBuffer(new String(mybytearray));
			byte[] stringBytes = cipher.doFinal(raw);
			String result = new String(stringBytes, "UTF8");

			System.out.println("Decriprion result " + result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void encrypt(String text) {
		try {
			cipher.init(Cipher.ENCRYPT_MODE, key);
			byte[] stringBytes = text.getBytes("UTF8");
			byte[] raw = cipher.doFinal(stringBytes);
			BASE64Encoder encoder = new BASE64Encoder();
			String base64 = encoder.encode(raw);
			System.out.println(base64);

			// Create file 
			FileWriter fstream = new FileWriter(fileDetailsName, true);
			PrintWriter pw = new PrintWriter(fstream);
			pw.println(base64);
			//Close the output stream
			pw.close();
			
			System.out.println("Wrote encrypted text to file");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
