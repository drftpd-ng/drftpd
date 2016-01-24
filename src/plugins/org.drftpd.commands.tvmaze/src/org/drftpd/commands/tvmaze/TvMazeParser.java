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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpException;
import org.apache.log4j.Logger;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.util.HttpUtils;

/**
 * @author lh
 */
public class TvMazeParser {
	private static final Logger logger = Logger.getLogger(TvMazeParser.class); 

	private static final String _searchUrl = "http://api.tvmaze.com/singlesearch/shows?q=";
	
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

			newSearchString = TvMazeUtils.filterTitle(newSearchString);

			newSearchString = _searchUrl + newSearchString;

			if (season >= 0) {
				newSearchString += "&embed=episodes";
			}

			String data = HttpUtils.retrieveHttpAsString(newSearchString);

			JsonParser jp = new JsonParser();
			JsonElement root = jp.parse(data);
			JsonObject rootobj = root.getAsJsonObject();

			if (rootobj == null) {
				_error = "No Show Results Were Found For \"" + searchString + "\"";
				return null;
			}

			return TvMazeUtils.createTvMazeInfo(rootobj, season, number);

		} catch (HttpException e) {
			// Ignore stack trace for HttpException and just log error message as an info
			_error = e.getMessage();
			logger.info(_error);
		} catch (Exception e) {
			_error = e.getMessage();
			logger.error(_error,e);
		}
		return null;
	}
}
