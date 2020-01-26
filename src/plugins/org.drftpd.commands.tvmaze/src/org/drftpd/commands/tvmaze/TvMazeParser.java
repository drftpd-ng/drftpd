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
package org.drftpd.commands.tvmaze;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.util.HttpUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lh
 */
public class TvMazeParser {
	private static final Logger logger = LogManager.getLogger(TvMazeParser.class); 

	private static final String _searchUrl = "http://api.tvmaze.com/search/shows?q=";
	private static final String _showUrl = "http://api.tvmaze.com/shows/";
	
	public TvMazeParser() {
	}

	// For Info / Dir
	private TvMazeInfo _TvShow;
	private String _error = "";

	public TvMazeInfo getTvShow()	{ return _TvShow; }
	public String getError()	{ return _error; }
	
	public void doTV(String searchString) {
		_TvShow = getInfo(searchString);
	}
	
	private TvMazeInfo getInfo(String searchString) {
		try {
			String newSearchString = searchString;

			int season = -1;
			int number = -1;
			Pattern p1 = Pattern.compile(".*[\\s|\\.](s(\\d+)\\.?(e(\\d+))?).*");
			Matcher m1 = p1.matcher(newSearchString.toLowerCase());
			Pattern p2 = Pattern.compile(".*[\\s|\\.]((\\d+)x(\\d+)).*");
			Matcher m2 = p2.matcher(newSearchString.toLowerCase());
			if (m1.find()) {
				season = Integer.parseInt(m1.group(2));
				if (m1.group(4) != null) {
					number = Integer.parseInt(m1.group(4));
				}
				// Remove season/episode from search string
				newSearchString = newSearchString.substring(0,newSearchString.toLowerCase().indexOf(m1.group(1))).trim();
			} else if (m2.find()) {
				season = Integer.parseInt(m2.group(2));
				if (m2.group(3) != null) {
					number = Integer.parseInt(m2.group(3));
				}
				// Remove season/episode from search string
				newSearchString = newSearchString.substring(0,newSearchString.toLowerCase().indexOf(m2.group(1))).trim();
			}
			int index = -1;
			// Match year
			String year = "";
			Pattern p3 = Pattern.compile(".*[\\s|\\.](\\d{4}).*");
			Matcher m3 = p3.matcher(newSearchString.toLowerCase());
			if (m3.find()) {
				year = m3.group(1);
				index = newSearchString.toLowerCase().indexOf(m3.group(1));
			}
			// Match common country codes
			//TODO: Make this configurable
			String countrycode = "";
			Pattern p4 = Pattern.compile(".*[\\s|\\.](uk|gb|us|ca|au)([\\s|\\.].*)?$");
			Matcher m4 = p4.matcher(newSearchString.toLowerCase());
			if (m4.find()) {
				countrycode = m4.group(1).toUpperCase();
				// TvMaze use GB instead of UK
				if (countrycode.equals("UK")) countrycode = "GB";
				int countrycodeindex = newSearchString.toLowerCase().indexOf(m4.group(1));
				if (index == -1 || countrycodeindex < index) index = countrycodeindex;
			}
			if (index >= 0) {
				newSearchString = newSearchString.substring(0,index).trim();
			}

			newSearchString = TvMazeUtils.filterTitle(newSearchString);
			newSearchString = _searchUrl + newSearchString;

			String data = HttpUtils.retrieveHttpAsString(newSearchString);

			JsonElement body = JsonParser.parseString(data);
			if (!body.isJsonArray()) {
				_error = "No Show Results Were Found For \"" + searchString + "\"";
				logger.info(_error);
				return null;
			}

			String id = TvMazeUtils.getBestMatch(body.getAsJsonArray(), year, countrycode);

			if (id == null) {
				_error = "No show matched search criteria [show=" + searchString + ",year="+ year + ",country=" + countrycode + "]";
				logger.info(_error);
				return null;
			}

			newSearchString = _showUrl + id;

			if (season >= 0) {
				newSearchString += "?embed=episodes";
			}

			data = HttpUtils.retrieveHttpAsString(newSearchString);
			JsonElement body2 = JsonParser.parseString(data);
			JsonObject jsonobj = body2.getAsJsonObject();

			return TvMazeUtils.createTvMazeInfo(jsonobj, season, number);

		} catch (HttpException e) {
			// Ignore stack trace for HttpException and just log error message as an info
			_error = e.getMessage() + " [" + searchString + "]";
			logger.info(_error);
		} catch (Exception e) {
			_error = e.getMessage() + " [" + searchString + "]";
			logger.error(_error,e);
		}
		return null;
	}
}
