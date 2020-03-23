package org.drftpd.plugins.nukefilter;

/**
 * @author phew
 */
public class NukeFilterNukeConfig {
	
	private String nuker;
	private String exempts;
	
	public NukeFilterNukeConfig() {
		nuker = "drftpd";
		exempts = "";
	}
	
	public void setNuker(String nuker) {
		this.nuker = nuker;
	}
	
	public void setExempts(String exempts) {
		this.exempts = exempts;
	}
	
	public String getNuker() {
		return nuker;
	}
	
	public String getExempts() {
		return exempts;
	}
	
	public String[] getExemptsArray() {
		return exempts.split(";");
	}
	
}
