package net.sf.drftpd;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class SFVFile implements Serializable {
	static final long serialVersionUID = 5381510163578487722L;
	
	private transient LinkedRemoteFile companion; 
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
			if(separator == -1) continue;

			String fileName = line.substring(0, separator);
			String checkSumString = line.substring(separator + 1);
			Long checkSum;
			try {
				checkSum = Long.valueOf(checkSumString, 16);
			} catch(NumberFormatException e) {
				continue;
			}
			entries.put(fileName, checkSum);
		}
	}
	
	public Map getEntries() {
		return entries;
	}
	
	public long getChecksum(String fileName) throws ObjectNotFoundException {
		Long checksum = (Long)entries.get(fileName);
		if(checksum == null) throw new ObjectNotFoundException();
		return checksum.longValue();
	}
	
	public void setCompanion(LinkedRemoteFile companion) {
		if(this.companion != null) throw new IllegalStateException("Can't overwrite companion");
		this.companion = companion;
	}
	
	public int finishedFiles() {
		int good = 0;

		for (Iterator iter = getFiles().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if(file.length() != 0) good++;
		}
		return good;
	}
	
	public Map getEntriesFiles() {
		LinkedRemoteFile dir;
		try {
			dir = companion.getParentFile();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}

		Map sfventries = getEntries();
		Map ret = new Hashtable();
		
		for (Iterator iter = sfventries.entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry element = (Map.Entry) iter.next();
			String fileName = (String) element.getKey();

			LinkedRemoteFile file;
			try {
				file = (LinkedRemoteFile) dir.getFile(fileName);
			} catch (FileNotFoundException e1) {
				continue;
			}
			ret.put(file, (Long) element.getValue());
		}
		return ret;
	}
	public Collection getFiles() {
		return getEntriesFiles().keySet();
	}
	public boolean hasFile(String name) {
		return getEntries().containsKey(name);
	}

	/**
	 * @return Number of file entries in the .sfv
	 */
	public int size() {
		return entries.size();
	}

	public long getTotalBytes() {
		long totalBytes=0;
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalBytes += ((LinkedRemoteFile) iter.next()).length();
		}
		return totalBytes;
	}

	public long getTotalXfertime() {
		long totalXfertime=0;
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalXfertime += ((LinkedRemoteFile) iter.next()).getXfertime();
		}
		return totalXfertime;
	}

	public int filesLeft() {
		return size()-finishedFiles();
	}

	public long getXferspeed() {
		if(getTotalXfertime() == 0) return 0;
		return getTotalBytes() / (getTotalXfertime() / 1000);
	}

}
