package org.drftpd.plugins.nukefilter;

import org.drftpd.GlobalContext;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author phew
 */
public class NukeFilterNukeItem {

	private DirectoryHandle dir;
	private String reason;
	private String element;
	private int delay;
	private int nukex;
	
	public NukeFilterNukeItem(DirectoryHandle dir, String reason, String element, int delay, int nukex) {
		this.dir = dir;
		this.reason = reason;
		this.element = element;
		this.delay = delay;
		this.nukex = nukex;
	}
	
	public DirectoryHandle getDirectoryHandle() {
		return dir;
	}
	
	public String getDirectoryName() {
		return dir.getName();
	}
	
	public String getPath() {
		return dir.getParent().getPath();
	}
	
	public String getSectionName() {
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		return section.getName();
	}

	public String getSectionColor() {
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		return section.getColor();
	}
	
	public String getReason() {
		return reason;
	}
	
	public String getElement() {
		return element;
	}
	
	public int getDelay() {
		return delay;
	}
	
	public int getNukex() {
		return nukex;
	}
	
}
