/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands.nuke;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.vfs.VirtualFileSystem;

import java.beans.XMLDecoder;
import java.io.*;
import java.util.*;

/**
 * NukeBeans handles the logging of nukes. Using a TreeMap, it sorts all nukes
 * in alphabetical order using the path. To save/load the current nukelog, we
 * are using JavaBeans XMLEncoder/XMLDecoder.
 * 
 * @author fr0w
 * @version $Id$
 */
public class NukeBeans {

	/**
	 * Singleton.
	 */
	private NukeBeans() {
	}

	protected static final Logger logger = LogManager.getLogger(NukeBeans.class);

	private static final String _nukebeansPath = "userdata";

	private static NukeBeans _nukeBeans = null;

	private Map<String, NukeData> _nukes = null;

	private ClassLoader _prevCL;

	/**
	 * Get the NukeData Object of the given path.
	 * 
	 * @param path
	 * @throws ObjectNotFoundException,
	 *             if not object is found.
	 */
	public synchronized NukeData get(String path)
			throws ObjectNotFoundException {
		NukeData ne = _nukes.get(path);
		if (ne == null)
			throw new ObjectNotFoundException("No nukelog for: " + path);
		return ne;
	}

	/**
	 * See add(String, NukeData).
	 * 
	 * @param nd
	 */
	public void add(NukeData nd) {
		add(nd.getPath(), nd);
	}

	/**
	 * Adds the given NukeData Object to the TreeMap and then serializes the
	 * TreeMap.
	 * 
	 * @param path
	 * @param nd
	 */
	public synchronized void add(String path, NukeData nd) {
		_nukes.put(path, nd);
		try {
			commit();
		} catch (IOException e) {
            logger.error("Couldn't save the nukelog due to: {}", e.getMessage(), e);
		}
	}

	/**
	 * This method will try to remove the given path from the nukelog.
	 * 
	 * @param path
	 * @throws ObjectNotFoundException,
	 *             if this path is not on the nukelog.
	 */
	public synchronized void remove(String path) throws ObjectNotFoundException {
		NukeData ne = _nukes.remove(path);
		if (ne == null)
			throw new ObjectNotFoundException("No nukelog for: " + path);
		try {
			commit();
		} catch (IOException e) {
            logger.error("Couldn't save the nukelog deu to: {}", e.getMessage(), e);
		}
	}

	/**
	 * @return all NukeData Objects stored on the TreeMap.
	 */
	public synchronized Collection<NukeData> getAll() {
		return _nukes.values();
	}

	/**
	 * @return nukes map.
	 */
	public synchronized Map<String, NukeData> getNukes() {
		return _nukes;
	}

	/**
	 * This method iterate through the Map of the users which have been nuked on
	 * the NukeData.getPath(), and create a List<Nukee> Object. See:
	 * net.sf.drftpd.Nukee for more info.
	 * 
	 * @param nd
	 * @return
	 */
	public static List<NukedUser> getNukeeList(NukeData nd) {
		ArrayList<NukedUser> list = new ArrayList<>();
		for (Map.Entry<String,Long> entry : nd.getNukees().entrySet()) {
			String user = entry.getKey();
			Long l = entry.getValue();
			list.add(new NukedUser(user, l));
		}
		return list;
	}

	/**
	 * @param path
	 * @return true if the given path is on the nukelog or false if it isn't.
	 */
	public synchronized NukeData findPath(String path) {
		try {
			return get(path);
		} catch (ObjectNotFoundException e) {
			return null;
		}
	}

	/**
	 * @param name
	 * @return true if the given name is in the nukelog or false if it isn't.
	 */
	public synchronized NukeData findName(String name) {
		for (NukeData nd : getAll()) {
			if (VirtualFileSystem.getLast(nd.getPath()).equals(name)) {
				return nd;
			}
		}
		return null;
	}

	/**
	 * Serializes the TreeMap.
	 * 
	 * @throws IOException
	 */
	public void commit() throws IOException {
		Map<String,Object> params = new HashMap<>();
		params.put(JsonWriter.PRETTY_PRINT, true);
		try (OutputStream out = new SafeFileOutputStream(_nukebeansPath + VirtualFileSystem.separator + "nukebeans.json");
			 JsonWriter writer = new JsonWriter(out, params)) {
			writer.write(_nukes);
		} catch (IOException | JsonIoException e) {
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * Singleton method.
	 * 
	 * @return NukeBeans.
	 */
	public static NukeBeans getNukeBeans() {
		if (_nukeBeans == null) {
			logger.debug("Instantiating NukeBeans.");
			newInstance();
		}

		return _nukeBeans;
	}

	/**
	 * Creates a new instance of NukeBeans. De-serialize the .json/xml.
	 */

	public static void newInstance() {
		_nukeBeans = new NukeBeans();
		_nukeBeans.loadLRUMap();
	}

	/**
	 * @param nukes
	 */
	public void setLRUMap(Map<String, NukeData> nukes) {
		_nukes = nukes;
	}

	/**
	 * Deserializes the Nukelog Map.
	 */
	@SuppressWarnings("unchecked")
	private void loadLRUMap() {
		Map<String, NukeData> nukees = new LRUMap(200);
		try (InputStream in = new FileInputStream(_nukebeansPath + VirtualFileSystem.separator + "nukebeans.json");
			 JsonReader reader = new JsonReader(in)) {
			nukees.putAll((Map<String, NukeData>)reader.readObject());
            logger.debug("Loaded log from .json, size: {}", nukees.size());
		} catch (FileNotFoundException e) {
			// Lets see if there is a legacy xml nuke log to load
			loadXMLLRUMap(nukees);
		} catch (IOException e) {
			logger.error("IOException reading json nuke log file", e);
		}
		_nukeBeans.setLRUMap(nukees);
	}

	/**
	 * Legacy XML loader
	 * Deserializes the Nukelog Map.
	 */
	@SuppressWarnings("unchecked")
	private void loadXMLLRUMap(Map<String, NukeData> nukees) {
		saveClassLoader();
		// de-serializing the Hashtable.
		try (XMLDecoder xd = new XMLDecoder(new FileInputStream(
				_nukebeansPath + VirtualFileSystem.separator + "nukebeans.xml"))) {
			switchClassLoaders();
			nukees.putAll((Map<String, NukeData>)xd.readObject());
            logger.debug("Loaded log from .xml, size: {}", nukees.size());
		} catch (FileNotFoundException e) {
			// nukelog does not exists yet.
		}
		setPreviousClassLoader();
	}

	private void saveClassLoader() {
		_prevCL = Thread.currentThread().getContextClassLoader();
	}

	private void switchClassLoaders() {
		Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
	}

	private void setPreviousClassLoader() {
		Thread.currentThread().setContextClassLoader(_prevCL);
	}

	/**
	 * Testing purposes.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("DEBUG: Testing NukeBeans");

		for (int i = 0; i < 100; i++) {
			// settings...
			String user = "test" + String.valueOf(i);
			String path = "/APPS/A" + String.valueOf(i);
			long size = Bytes.parseBytes("350MB");
			int multiplier = 3;
			long nukedAmount = multiplier * size;
			String reason = "Testing";
			Map<String, Long> nukees = new Hashtable<>();
			nukees.put("test"+i, nukedAmount);

			// actual NukeEvent
			NukeData nd = new NukeData();
			nd.setUser(user);
			nd.setPath(path);
			nd.setReason(reason);
			nd.setNukees(nukees);
			nd.setMultiplier(multiplier);
			nd.setAmount(nukedAmount);
			nd.setSize(size);

			// System.out.println(nd.toString());
			// adding
			getNukeBeans().add(nd);
		}

		// commiting.
		try {
			getNukeBeans().commit();
		} catch (IOException e) {
			System.out.println("ERROR: " + e.getMessage());
		}

		// finished!
		System.out.println("DEBUG: Test ran successfully");
	}
}
