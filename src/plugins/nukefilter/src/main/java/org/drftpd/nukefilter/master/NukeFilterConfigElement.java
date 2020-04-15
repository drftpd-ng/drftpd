package org.drftpd.nukefilter.master;

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

    public void setNukex(Integer nukex) {
        this.nukex = nukex;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

}
