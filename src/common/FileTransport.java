package common;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileTransport {
	public static final String OK 		= "OK";
	public static final String NO		= "NO";
	public static final String DENIED 	= "You don't have the rights";
	public static final String BAN		= "This file name is forbidden. You are Banned!";
	public static final String STILL_BANNED = "You are still banned. Try again later";

	/**
	 * Send a file to a DataOutputStream
	 * @param fileName
	 * @param dos
	 */
	public static void sendFile(String fileName, DataOutputStream dos) {
		// Send the file
		File file = new File(fileName);
		try {
			byte[] mybytearray = new byte[(int) file.length()]; 
			FileInputStream fis = new FileInputStream(file);  
	        BufferedInputStream bis = new BufferedInputStream(fis);  
	        bis.read(mybytearray, 0, mybytearray.length);
	        
	        dos.writeLong(mybytearray.length);
	        dos.write(mybytearray, 0, mybytearray.length);  
	        dos.flush();
	        
	        fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Receive a file from a DataInputStream
	 * @param fileName
	 * @param dis
	 */
	public static void receiveFile(String fileName, DataInputStream dis) {
		try {
			OutputStream newFile = new FileOutputStream(fileName);
			
			// Get the length of the file and the bytes
			long len = dis.readLong();
			byte[] mybytearray = new byte[(int) len];  
			dis.read(mybytearray, 0, (int)len);
			
			// Write the file and close
			newFile.write(mybytearray, 0, (int)len);
			newFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
