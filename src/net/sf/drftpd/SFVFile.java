package net.sf.drftpd;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class SFVFile {
	Map entries = new HashMap();
	/**
	 * Constructor for SFVFile.
	 */
	public SFVFile(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.charAt(0) == ';')
				continue;
			int separator = line.indexOf(" ");

			String fileName = line.substring(0, separator);
			String checkSumString = line.substring(separator + 1);
			//long checkSum = Long.decode("0x"+checkSumString).longValue();
			Long checkSum = Long.valueOf(checkSumString, 16);
			entries.put(fileName, checkSum);
		}
	}
	
	public Set entrySet() {
		return entries.entrySet();
	}
}
