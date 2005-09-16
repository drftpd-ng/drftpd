/**
 * 
 */
package org.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;

import org.drftpd.GlobalContext;
import org.drftpd.SFVFile;
import org.drftpd.dynamicdata.Key;
import org.drftpd.id3.MP3File;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.RemoteIOException;

/**
 * @author zubov
 * @version $Id$
 */
class File extends Inode {
	public static final Key CHECKSUM = new Key(File.class, "checksum",
			Long.class);

	public static final Key MP3FILE = new Key(File.class, "mp3file",
			MP3File.class);

	public static final Key SFVFILE = new Key(File.class, "sfv", SFVFile.class);

	public static final Key SLAVELIST = new Key(File.class, "slavelist",
			ArrayList.class);

	public static final Key XFERTIME = new Key(File.class, "xfertime",
			Long.class);

	private ArrayList<String> _slaves = null;

	/**
	 * @param path
	 */
	public File(String path, String user, String group, RemoteSlave rslave) {
		super(path, user, group);
		ArrayList<RemoteSlave> slavelist = new ArrayList<RemoteSlave>();
		slavelist.add(rslave);
		setObject(SLAVELIST, slavelist);
	}

	public Collection<RemoteSlave> getAvailableSlaves()
			throws NoAvailableSlaveException {
		return GlobalContext.getGlobalContext().getSlaveManager()
				.getOnlineSlavesFromList(_slaves);
	}

	public long getCheckSum() {
		// TODO Auto-generated method stub
		return 0;
	}

	public long getCheckSumFromSlave() throws NoAvailableSlaveException,
			IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * Will return the SFVFile if the SFVFile exists
	 * 
	 * @return
	 * @throws NoAvailableSlaveException
	 * @throws FileNotFoundException
	 */
	public synchronized SFVFile getSFVFile() throws NoAvailableSlaveException,
			FileNotFoundException {
		try {
			return (SFVFile) getObject(SFVFILE);
		} catch (ObjectNotFoundException e) {
			SFVFile sfvFile = null;
			for (RemoteSlave rslave : getSlaveList()) {
				try {
					String index = rslave.issueSFVFileToSlave(getPath());
					sfvFile = new SFVFile(rslave.fetchSFVFileFromIndex(index));
					setObject(SFVFILE, sfvFile);
					return sfvFile;
				} catch (SlaveUnavailableException e1) {
					// that one didn't have it
				} catch (RemoteIOException e1) {
					if (e1.getCause() instanceof FileNotFoundException) {
						removeSlave(rslave);
					}
				}
			}
		}
		throw new NoAvailableSlaveException();
	}

	private ArrayList<RemoteSlave> getSlaveList() {
		try {
			return (ArrayList<RemoteSlave>) getObject(SLAVELIST);
		} catch (ObjectNotFoundException e) {
			throw new RuntimeException(
					"Cannot have a file without a slavelist", e);
		}
	}

	private synchronized void removeSlave(RemoteSlave rslave)
			throws FileNotFoundException {
		ArrayList<RemoteSlave> slavelist = getSlaveList();
		slavelist.remove(rslave);
		if (slavelist.isEmpty()) {
			throw new FileNotFoundException("File does not exist on any slaves");
		}
	}

}
