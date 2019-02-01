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
package org.drftpd.vfs.index.lucene.extensions.tvmaze;

import org.drftpd.dynamicdata.Key;

/**
 * @author scitz0
 * @version $Id: MP3QueryParams.java 2484 2011-07-09 10:25:43Z scitz0 $
 */
public class TvMazeQueryParams {

	public static final Key<TvMazeQueryParams> TvMazeQUERYPARAMS = new Key<>(TvMazeQueryParams.class, "tvmazequeryparams");
	
	private String _name;
	private String _genre;
	private Integer _season;
	private Integer _number;
	private String _type;
	private String _status;
	private String _language;
	private String _country;
	private String _network;
	
	public String getName() {
		return _name;
	}
	
	public String getGenre() {
		return _genre;
	}

	public Integer getSeason() {
		return _season;
	}

	public Integer getNumber() {
		return _number;
	}

	public String getType() {
		return _type;
	}

	public String getStatus() {
		return _status;
	}

	public String getLanguage() {
		return _language;
	}

	public String getCountry() {
		return _country;
	}

	public String getNetwork() {
		return _network;
	}

	public void setName(String name) {
		_name = name;
	}
	
	public void setGenre(String genre) {
		_genre = genre;
	}

	public void setSeason(Integer season) {
		_season = season;
	}

	public void setNumber(Integer number) {
		_number = number;
	}

	public void setType(String type) {
		_type = type;
	}

	public void setStatus(String status) {
		_status = status;
	}

	public void setLanguage(String language) {
		_language = language;
	}

	public void setCountry(String country) { _country = country; }

	public void setNetwork(String network) {
		_network = network;
	}
}
