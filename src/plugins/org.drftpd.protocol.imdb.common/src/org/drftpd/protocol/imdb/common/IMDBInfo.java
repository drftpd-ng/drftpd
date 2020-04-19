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
package org.drftpd.protocol.imdb.common;

import org.drftpd.dynamicdata.Key;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;

/**
 * @author lh
 */
@SuppressWarnings("serial")
public class IMDBInfo implements Serializable {

    public static final Key<IMDBInfo> IMDBINFO = new Key<>(IMDBInfo.class, "imdb");
	
	private String _nfoFileName = null;
	private String _nfoURL = null;
	private long _checksum = 0L;

    // IMDB DATA
    private String _title = "N|A";
    private Integer _year;
    private String _language = "N|A";
    private String _country = "N|A";
    private String _director = "N|A";
    private String _genres = "N|A";
    private String _plot = "N|A";
    private Integer _rating;
    private Integer _votes;
    private Integer _runtime;
    private boolean _foundMovie = false;

	/**
	 * Constructor for IMDBInfo
	 */
	public IMDBInfo() {	}
	
	public String getNFOFileName() {
		return _nfoFileName;
	}
	
	public void setNFOFileName(String name) {
		_nfoFileName = name;
	}
	
	public String getURL() {
		return _nfoURL;
	}

	public void setURL(String url) {
		_nfoURL = url;
	}
	
	public static IMDBInfo importNFOInfoFromFile(BufferedReader in) throws IOException {
		String line;
		String url = null;
		try {
			while ((line = in.readLine()) != null) {
				if (line.length() == 0) {
					continue;
				}
			    line = line.toLowerCase();
				if (line.contains("/title/tt")) {
					url = "https://imdb.com/title/" + line.replaceAll(".*/title/(tt\\d+).*", "$1");
					break;
				}
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
		IMDBInfo tmp = new IMDBInfo();
		tmp.setURL(url);
		return tmp;
	}
	
	public void setChecksum(long value) {
		_checksum = value;
	}
	
	public long getChecksum() {
		return _checksum;
	}

    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        _title = title;
    }

    public Integer getYear() {
        return _year;
    }

    public void setYear(Integer year) {
        _year = year;
    }

	public String getLanguage() {
        return _language;
    }

	public void setLanguage(String language) {
        _language = language;
    }

	public String getCountry() {
        return _country;
    }

    public void setCountry(String country) {
        _country = country;
    }
	
    public String getDirector() {
        return _director;
    }

    public void setDirector(String director) {
        _director = director;
    }

    public String getGenres() {
        return _genres;
    }

    public void setGenres(String genres) {
        _genres = genres;
    }

    public Integer getRating() {
        return _rating;
    }

    public void setRating(Integer rating) {
        _rating = rating;
    }

    public String getPlot() {
        return _plot;
    }

    public void setPlot(String plot) {
        _plot = plot;
    }
	
    public Integer getVotes() {
        return _votes;
    }

    public void setVotes(Integer votes) {
        _votes = votes;
    }

    public Integer getRuntime() {
        return _runtime;
    }

    public void setRuntime(Integer runtime) {
		_runtime = runtime;
    }

    public boolean getMovieFound() {
        return _foundMovie;
    }

    public void setMovieFound(boolean foundMovie) {
        _foundMovie = foundMovie;
    }

}
