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
package org.drftpd.commands.tvmaze.metadata;

import org.drftpd.dynamicdata.Key;

import java.io.Serializable;

/**
 * @author lh
 */
@SuppressWarnings("serial")
public class TvMazeInfo implements Serializable {

    public static final Key<TvMazeInfo> TVMAZEINFO = new Key<>(TvMazeInfo.class, "tvmaze");

	int _id;
	String _url = "";
	String _name = "";
	String _type = "";
	String _language = "";
	String[] _genres = new String[0];
	String _status = "";
	int _runtime;
	String _premiered;
	String _network = "";
	String _country = "";
	String _summary = "";
	TvEpisode _prevEP;
	TvEpisode _nextEP;
	TvEpisode[] _epList = new TvEpisode[0];

	/**
	 * Constructor for TvMazeInfo
	 */
	public TvMazeInfo() {	}

	public int getID () { return _id; }
	public String getURL () { return _url; }
	public String getName () { return _name; }
	public String getType () { return _type; }
	public String getLanguage () { return _language; }
	public String[] getGenres () { return _genres; }
	public String getStatus () { return _status; }
	public int getRuntime () { return _runtime; }
	public String getPremiered () { return _premiered; }
	public String getNetwork () { return _network; }
	public String getCountry () { return _country; }
	public String getSummary () { return _summary; }
	public TvEpisode getPreviousEP () { return _prevEP; }
	public TvEpisode getNextEP () { return _nextEP; }
	public TvEpisode[] getEPList () { return _epList; }

	public void setID (int id) { _id = id; }
	public void setURL (String url) { _url = url; }
	public void setName (String name) { _name = name; }
	public void setType (String type) { _type = type; }
	public void setLanguage (String language) { _language = language; }
	public void setGenres (String[] genres) { _genres = genres; }
	public void setStatus (String status) { _status = status; }
	public void setRuntime (int runtime) { _runtime = runtime; }
	public void setPremiered (String premiered) { _premiered = premiered; }
	public void setNetwork (String network) { _network = network; }
	public void setCountry (String country) { _country = country; }
	public void setSummary (String summary) { _summary = summary; }
	public void setPreviousEP (TvEpisode prevEP) { _prevEP = prevEP; }
	public void setNextEP (TvEpisode nextEP) { _nextEP = nextEP; }
	public void setEPList (TvEpisode[] epList) { _epList = epList; }

}
