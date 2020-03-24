package org.drftpd.plugins.nukefilter;

import java.util.ArrayList;

/**
 * @author phew
 */
public interface NukeFilterConfigInterface {
	
	void setNukeDelay(Integer delay);
	Integer getNukeDelay();
	
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
