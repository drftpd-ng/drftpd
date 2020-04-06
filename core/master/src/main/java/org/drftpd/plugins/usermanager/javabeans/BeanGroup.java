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
package org.drftpd.plugins.usermanager.javabeans;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.io.SafeFileOutputStream;
import org.drftpd.master.master.CommitManager;
import org.drftpd.master.usermanager.AbstractGroup;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mikevg
 * @version $Id$
 */
public class BeanGroup extends AbstractGroup {

	private static final Logger logger = LogManager.getLogger(BeanGroup.class);

	private transient BeanUserManager _um;

	private transient boolean _purged;

	public BeanGroup(String groupname) {
		super(groupname);
	}

	public BeanGroup(BeanUserManager manager, String groupname) {
		super(groupname);
		_um = manager;
	}

	public AbstractUserManager getAbstractUserManager() {
		return _um;
	}

	public UserManager getUserManager() {
		return _um;
	}

	public void commit() {
		CommitManager.getCommitManager().add(this);
	}

	public void purge() {
		_purged = true;
		_um.deleteGroup(getName());
	}

	public void setUserManager(BeanUserManager manager) {
		_um = manager;
	}

	public void writeToDisk() throws IOException {
		if (_purged)
			return;

		Map<String,Object> params = new HashMap<>();
		params.put(JsonWriter.PRETTY_PRINT, true);
		try (OutputStream out = new SafeFileOutputStream(_um.getGroupFile(getName()));
			 JsonWriter writer = new JsonWriter(out, params)) {
			writer.write(this);
		logger.debug("Wrote groupfile for {}", this.getName());
		} catch (IOException | JsonIoException e) {
			throw new IOException("Unable to write " + _um.getGroupFile(getName()) + " to disk", e);
		}
	}

	public String descriptiveName() {
		return getName();
	}
}
