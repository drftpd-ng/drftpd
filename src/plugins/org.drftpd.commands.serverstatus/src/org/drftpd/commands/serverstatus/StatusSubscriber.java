package org.drftpd.commands.serverstatus;

import org.bushe.swing.event.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.event.SlaveEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.java.plugin.PluginManager;

public class StatusSubscriber implements EventSubscriber {
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
		GlobalContext.getEventService().subscribe(SlaveEvent.class, this);
		GlobalContext.getEventService().subscribe(UnloadPluginEvent.class, this);
	}
	
	public void onEvent(Object event) {
		if (event instanceof SlaveEvent) {
			SlaveEvent slaveEvent = (SlaveEvent) event;			
			KeyedMap<Key, Object> keyedMap = slaveEvent.getRSlave().getTransientKeyedMap();

			if (slaveEvent.getCommand().equals("ADDSLAVE")) {
				keyedMap.setObject(ServerStatus.CONNECTTIME, System.currentTimeMillis());
			} else if (slaveEvent.getCommand().equals("DELSLAVE")) {
				keyedMap.remove(ServerStatus.CONNECTTIME);
			}
		} else if (event instanceof UnloadPluginEvent) {
			UnloadPluginEvent pluginEvent = (UnloadPluginEvent) event;
			PluginManager manager = PluginManager.lookup(this);
			String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
			for (String pluginExtension : pluginEvent.getParentPlugins()) {
				int pointIndex = pluginExtension.lastIndexOf("@");
				String pluginName = pluginExtension.substring(0, pointIndex);
				if (pluginName.equals(currentPlugin)) {
					GlobalContext.getEventService().unsubscribe(SlaveEvent.class, this);
					GlobalContext.getEventService().unsubscribe(UnloadPluginEvent.class, this);
					nullify();
				}
			}
		}
	}
}
