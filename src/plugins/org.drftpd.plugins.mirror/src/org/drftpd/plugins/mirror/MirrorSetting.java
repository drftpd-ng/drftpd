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
package org.drftpd.plugins.mirror;

import org.drftpd.master.RemoteSlave;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author lh
 */
public class MirrorSetting {
	private int nbrOfMirrors;
	private int priority;
	private ArrayList<String> paths;
	private ArrayList<String> excludedPaths;
	private HashSet<RemoteSlave> slaves;
	private HashSet<RemoteSlave> excludedSlaves;

	public MirrorSetting() {
	}

	public int getNbrOfMirrors() {
		return nbrOfMirrors;
	}

	public void setNbrOfMirrors(int nbrOfMirrors) {
		this.nbrOfMirrors = nbrOfMirrors;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public ArrayList<String> getPaths() {
		return paths;
	}

	public void setPaths(ArrayList<String> paths) {
		this.paths = paths;
	}

	public ArrayList<String> getExcludedPaths() {
		return excludedPaths;
	}

	public void setExcludedPaths(ArrayList<String> excludedPaths) {
		this.excludedPaths = excludedPaths;
	}

	public HashSet<RemoteSlave> getSlaves() {
		return slaves;
	}

	public void setSlaves(HashSet<RemoteSlave> slaves) {
		this.slaves = slaves;
	}

	public HashSet<RemoteSlave> getExcludedSlaves() {
		return excludedSlaves;
	}

	public void setExcludedSlaves(HashSet<RemoteSlave> excludedSlaves) {
		this.excludedSlaves = excludedSlaves;
	}
}
