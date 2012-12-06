package common;

import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

/**
 * Encrypter class
 * 
 * @author Claudiu Ghioc claudiu.ghioc@gmail.com
 */
public class DESEncrypter {
	
	private Cipher encryptionCipher = null;
	private Cipher decryptionCipher = null;
	private int bufferSize = 1024;
	
	public DESEncrypter(SecretKey key){
		try{
			encryptionCipher = Cipher.getInstance("DES");
			encryptionCipher.init(Cipher.ENCRYPT_MODE, key);
			decryptionCipher = Cipher.getInstance("DES");
			decryptionCipher.init(Cipher.DECRYPT_MODE, key);
		}catch (Exception e){
			System.out.println("Error getting cipher: "+e);
		}
	}
	
	/**
	 * Encrypts an InputStream using the secret key
	 * @param in
	 * @param out
	 * @return
	 */
	public boolean encrypt (InputStream in, OutputStream out){
		byte[] buffer = new byte[bufferSize];
		
		try{
			OutputStream cout = new CipherOutputStream(out, encryptionCipher);
			int bytesRead = 0;
			while((bytesRead = in.read(buffer)) >= 0)
				cout.write(buffer, 0 , bytesRead);
			
			cout.close();
			in.close();
			out.close();
		}catch(Exception e){
			System.out.println("Error at encrypt: "+e);
		}
		return true;
	}
	
	/**
	 * Decrypts an InputStream using a secret key
	 * @param in
	 * @param out
	 * @return
	 */
	public boolean decrypt (InputStream in, OutputStream out){
		byte[] buffer = new byte[bufferSize];
		
		try{
			InputStream cin = new CipherInputStream(in, decryptionCipher);
			int bytesRead =0;
			while((bytesRead = cin.read(buffer)) >=0){
				out.write(buffer, 0 , bytesRead);
			}
			
			cin.close();
			in.close();
			out.close();
		}catch(Exception e){
			System.out.println("Error at decrypt: "+e);
		}
		return true;
	}
}