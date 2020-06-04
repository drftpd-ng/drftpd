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
package org.drftpd.nukefilter.master;

import java.util.ArrayList;

/**
 * @author phew
 */
public interface NukeFilterConfigInterface {

    Integer getNukeDelay();

    void setNukeDelay(Integer delay);

    ArrayList<NukeFilterConfigElement> getFilterStringList();

    ArrayList<NukeFilterConfigElement> getEnforceStringList();

    ArrayList<NukeFilterConfigElement> getFilterRegexList();

    ArrayList<NukeFilterConfigElement> getEnforceRegexList();

    ArrayList<NukeFilterConfigElement> getFilterYearList();

    ArrayList<NukeFilterConfigElement> getEnforceYearList();

    ArrayList<NukeFilterConfigElement> getFilterGroupList();

    ArrayList<NukeFilterConfigElement> getEnforceGroupList();

    void addFilterStringElement(NukeFilterConfigElement element);

    void addEnforceStringElement(NukeFilterConfigElement element);

    void addFilterRegexElement(NukeFilterConfigElement element);

    void addEnforceRegexElement(NukeFilterConfigElement element);

    void addFilterYearElement(NukeFilterConfigElement element);

    void addEnforceYearElement(NukeFilterConfigElement element);

    void addFilterGroupElement(NukeFilterConfigElement element);

    void addEnforceGroupElement(NukeFilterConfigElement element);

    boolean hasFilterStrings();

    boolean hasEnforceStrings();

    boolean hasFilterRegex();

    boolean hasEnforceRegex();

    boolean hasFilterYears();

    boolean hasEnforceYears();

    boolean hasFilterGroups();

    boolean hasEnforceGroups();

}
