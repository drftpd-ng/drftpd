package net.sf.drftpd;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class SFVFile {
	/**
	 * String fileName as key
	 * Long checkSum as value
	 */
	Map entries = new Hashtable();
	/**
	 * Constructor for SFVFile.
	 */
	public SFVFile(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if(line.length() == 0) continue;
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
	
	public Map getEntries() {
		return entries;
	}
	
	public long get(String fileName) throws ObjectNotFoundException {
		Long checksum = (Long)entries.get(fileName);
		if(checksum == null) throw new ObjectNotFoundException();
		return checksum.longValue();
	}

}
