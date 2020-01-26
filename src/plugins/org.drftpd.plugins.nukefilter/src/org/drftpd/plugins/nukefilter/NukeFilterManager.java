package org.drftpd.plugins.nukefilter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.event.ReloadEvent;

/**
 * @author phew
 */
public class NukeFilterManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(NukeFilterManager.class);
	
	private NukeFilterSettings _nfs;
	
	public NukeFilterManager() {
		_nfs = new NukeFilterSettings();
	}
	
	public static NukeFilterManager getNukeFilterManager() {
		for(PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if(plugin instanceof NukeFilterManager) {
				return (NukeFilterManager) plugin;
			}
		}
		throw new RuntimeException("NukeFilter plugin is not loaded.");
	}
	
	public void startPlugin() {
		AnnotationProcessor.process(this);
		_nfs.reloadConfigs();
		logger.debug("Loaded the NukeFilter plugin successfully");
	}

	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
		logger.debug("Unloaded the NukeFilter plugin successfully");
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		_nfs.reloadConfigs();
	}
	
	protected NukeFilterSettings getNukeFilterSettings() {
		return _nfs;
	}
	
	
}
