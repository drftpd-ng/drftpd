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
package org.drftpd.imdb.master.index;

import org.drftpd.common.dynamicdata.Key;

/**
 * @author scitz0
 * @version $Id: MP3QueryParams.java 2484 2011-07-09 10:25:43Z scitz0 $
 */
public class IMDBQueryParams {

    public static final Key<IMDBQueryParams> IMDBQUERYPARAMS = new Key<>(IMDBQueryParams.class, "imdbqueryparams");

    private String _title;
    private String _director;
    private String _genres;
    private Integer _minVotes;
    private Integer _maxVotes;
    private Integer _minRating;
    private Integer _maxRating;
    private Integer _minYear;
    private Integer _maxYear;
    private Integer _minRuntime;
    private Integer _maxRuntime;

    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        _title = title;
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

    public Integer getMinVotes() {
        return _minVotes;
    }

    public void setMinVotes(Integer votes) {
        _minVotes = votes;
    }

    public Integer getMaxVotes() {
        return _maxVotes;
    }

    public void setMaxVotes(Integer votes) {
        _maxVotes = votes;
    }

    public Integer getMinRating() {
        return _minRating;
    }

    public void setMinRating(Integer rating) {
        _minRating = rating;
    }

    public Integer getMaxRating() {
        return _maxRating;
    }

    public void setMaxRating(Integer rating) {
        _maxRating = rating;
    }

    public Integer getMinYear() {
        return _minYear;
    }

    public void setMinYear(Integer year) {
        _minYear = year;
    }

    public Integer getMaxYear() {
        return _maxYear;
    }

    public void setMaxYear(Integer year) {
        _maxYear = year;
    }

    public Integer getMinRuntime() {
        return _minRuntime;
    }

    public void setMinRuntime(Integer runtime) {
        _minRuntime = runtime;
    }

    public Integer getMaxRuntime() {
        return _maxRuntime;
    }

    public void setMaxRuntime(Integer runtime) {
        _maxRuntime = runtime;
    }

}
