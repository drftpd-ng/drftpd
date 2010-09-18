package org.drftpd.commands.serverstatus;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.event.SlaveEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.util.CommonPluginUtils;

public class StatusSubscriber {
	private static StatusSubscriber _subscriber = null;
	
	/**
	 * Checks if this subscriber is already listening to events, otherwise, initialize it.
	 */
	public static void checkSubscription() {
		if (_subscriber == null) {
			_subscriber = new StatusSubscriber();
		}
	}
	
	/**
	 * Remove the reference to the current subscriber so that it can be GC'ed.
	 */
	private static void nullify() {
		_subscriber = null;
	}
	
	private StatusSubscriber() {
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	@EventSubscriber
	public void onSlaveEvent(SlaveEvent event) {
		KeyedMap<Key<?>, Object> keyedMap = event.getRSlave().getTransientKeyedMap();
		if (event.getCommand().equals("ADDSLAVE")) {
			keyedMap.setObject(ServerStatus.CONNECTTIME, System.currentTimeMillis());
		} else if (event.getCommand().equals("DELSLAVE")) {
			keyedMap.remove(ServerStatus.CONNECTTIME);
		}
	}

	@EventSubscriber
	public void onUnloadPluginEvent(UnloadPluginEvent event) {
		String currentPlugin = CommonPluginUtils.getPluginIdForObject(this);
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			if (pluginName.equals(currentPlugin)) {
				AnnotationProcessor.unprocess(this);
				nullify();
				return;
			}
		}
	}
}
