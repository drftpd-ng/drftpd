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
 * @version $Id: SFVFile.java,v 1.21 2003/12/04 04:48:25 zubov Exp $
 */
public class SFVFile implements Serializable {

	public class SFVStatus {
		private int _missing;
		private int _offline;

		public SFVStatus(int offline, int missing) {
			_offline = offline;
			_missing = missing;
		}

		public int getMissing() {
			return _missing;
		}

		public int getOffline() {
			return _offline;
		}

		public boolean isFinished() {
			return _missing == 0;
		}
	}

	static final long serialVersionUID = 5381510163578487722L;

	private transient LinkedRemoteFile _companion;
	
	/**
	 * String fileName as key.
	 * Long checkSum as value.
	 */
	private Map _entries = new Hashtable();
	/**
	 * Constructor for SFVFile.
	 */
	public SFVFile(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.length() == 0)
				continue;
			if (line.charAt(0) == ';')
				continue;
			int separator = line.indexOf(" ");
			if (separator == -1)
				continue;

			String fileName = line.substring(0, separator);
			String checkSumString = line.substring(separator + 1);
			Long checkSum;
			try {
				checkSum = Long.valueOf(checkSumString, 16);
			} catch (NumberFormatException e) {
				continue;
			}
			_entries.put(fileName, checkSum);
		}
	}

	/**
	 * @deprecated use getStatus().getMissing()
	 */
	public int filesLeft() {
		return getStatus().getMissing();
	}

	/**
	 * @return the number of files in the dir that are in the .sfv and aren't 0 bytes
	 * @deprecated use getStatus()
	 */
	public int finishedFiles() {
		return size() - getStatus().getMissing();
		//		int good = 0;
		//		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
		//			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
		//			if (file.length() != 0)
		//				good++;
		//		}
		//		return good;
	}

	public SFVStatus getStatus() {
		int good = 0;
		int offline = 0;
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.length() != 0)
				good++;
			if (!file.isAvailable())
				offline++;
		}
		return new SFVStatus(offline, size()-good);
	}

	public long getChecksum(String fileName) throws ObjectNotFoundException {
		Long checksum = (Long) _entries.get(fileName);
		if (checksum == null)
			throw new ObjectNotFoundException();
		return checksum.longValue();
	}

	public Map getEntries() {
		return _entries;
	}

	public Map getEntriesFiles() {
		LinkedRemoteFile dir;
		try {
			dir = _companion.getParentFile();
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

	public long getTotalBytes() {
		long totalBytes = 0;
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalBytes += ((LinkedRemoteFile) iter.next()).length();
		}
		return totalBytes;
	}

	public long getTotalXfertime() {
		long totalXfertime = 0;
		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalXfertime += ((LinkedRemoteFile) iter.next()).getXfertime();
		}
		return totalXfertime;
	}

	public long getXferspeed() {
		if (getTotalXfertime() == 0)
			return 0;
		return getTotalBytes() / (getTotalXfertime() / 1000);
	}
	public boolean hasFile(String name) {
		return getEntries().containsKey(name);
	}

	/**
	 * @deprecated use getStatus().getOffline()
	 */
	public int offlineFiles() {
		return getStatus().getOffline();
		//		int offlineFiles = 0;
		//		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
		//			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
		//			if (!file.isAvailable()) {
		//				offlineFiles++;
		//			}
		//		}
		//		return offlineFiles;
	}

	public void setCompanion(LinkedRemoteFile companion) {
		if (_companion != null)
			throw new IllegalStateException("Can't overwrite companion");
		_companion = companion;
	}

	/**
	 * @return Number of file entries in the .sfv
	 */
	public int size() {
		return _entries.size();
	}
}
