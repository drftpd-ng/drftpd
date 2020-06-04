/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.zipscript.master.sfv.indexation;

import org.drftpd.common.dynamicdata.Key;

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

    public void setMinPresent(Integer present) {
        _minPresent = present;
    }

    public Integer getMaxPresent() {
        return _maxPresent;
    }

    public void setMaxPresent(Integer present) {
        _maxPresent = present;
    }

    public Integer getMinMissing() {
        return _minMissing;
    }

    public void setMinMissing(Integer missing) {
        _minMissing = missing;
    }

    public Integer getMaxMissing() {
        return _maxMissing;
    }

    public void setMaxMissing(Integer missing) {
        _maxMissing = missing;
    }

    public Integer getMinPercent() {
        return _minPercent;
    }

    public void setMinPercent(Integer percent) {
        _minPercent = percent;
    }

    public Integer getMaxPercent() {
        return _maxPercent;
    }

    public void setMaxPercent(Integer percent) {
        _maxPercent = percent;
    }
}
