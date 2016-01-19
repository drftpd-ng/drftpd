package org.drftpd.plugins.nukefilter;

/**
 * @author phew
 */
public class NukeFilterConfigElement {

	private Integer nukex;
	private String element;
	
	public NukeFilterConfigElement() {
		nukex = null;
		element = null;
	}
	
	public Integer getNukex() {
		return nukex;
	}
	
	public String getElement() {
		return element;
	}
	
	public void setNukex(Integer nukex) {
		this.nukex = nukex;
	}
	
	public void setElement(String element) {
		this.element = element;
	}
	
}
