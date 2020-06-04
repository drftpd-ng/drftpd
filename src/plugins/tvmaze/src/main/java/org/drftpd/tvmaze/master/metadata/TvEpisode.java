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
package org.drftpd.tvmaze.master.metadata;

import java.io.Serializable;

/**
 * @author _LH_
 */
@SuppressWarnings("serial")
public class TvEpisode implements Serializable {
    int _id;
    String _url = "";
    String _name = "";
    int _season;
    int _number;
    String _airdate;
    int _runtime;
    String _summary = "";

    public TvEpisode() {
    }

    public int getID() {
        return _id;
    }

    public void setID(int id) {
        _id = id;
    }

    public String getURL() {
        return _url;
    }

    public void setURL(String url) {
        _url = url;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public int getSeason() {
        return _season;
    }

    public void setSeason(int season) {
        _season = season;
    }

    public int getNumber() { return _number; }

    public void setNumber(int number) {
        _number = number;
    }

    public String getAirDate() {
        return _airdate;
    }

    public void setAirDate(String airdate) { _airdate = airdate; }

    public int getRuntime() {
        return _runtime;
    }

    public void setRuntime(int runtime) {
        _runtime = runtime;
    }

    public String getSummary() {
        return _summary;
    }

    public void setSummary(String summary) {
        _summary = summary;
    }

}
