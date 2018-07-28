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
package org.drftpd.vfs.index.lucene.extensions.zipscript;

import org.drftpd.dynamicdata.Key;

/**
 * @author scitz0
 * @version $Id$
 */
public class ZipscriptQueryParams {

	public static final Key<ZipscriptQueryParams> ZIPSCRIPTQUERYPARAMS =
            new Key<>(ZipscriptQueryParams.class, "zipscriptqueryparams");
	
	private Integer _minPresent;
	private Integer _maxPresent;
	private Integer _minMissing;
	private Integer _maxMissing;
	private Integer _minPercent;
	private Integer _maxPercent;
	
	public Integer getMinPresent() {
		return _minPresent;
	}

	public Integer getMaxPresent() {
		return _maxPresent;
	}

	public Integer getMinMissing() {
		return _minMissing;
	}

	public Integer getMaxMissing() {
		return _maxMissing;
	}

	public Integer getMinPercent() {
		return _minPercent;
	}

	public Integer getMaxPercent() {
		return _maxPercent;
	}
	
	public void setMinPresent(Integer present) {
		_minPresent = present;
	}

	public void setMaxPresent(Integer present) {
		_maxPresent = present;
	}

	public void setMinMissing(Integer missing) {
		_minMissing = missing;
	}

	public void setMaxMissing(Integer missing) {
		_maxMissing = missing;
	}

	public void setMinPercent(Integer percent) {
		_minPercent = percent;
	}

	public void setMaxPercent(Integer percent) {
		_maxPercent = percent;
	}
}
