/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.event;

import org.bushe.swing.event.ThreadSafeEventService;

import java.lang.reflect.Type;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author djb61
 * @version $Id$
 */
public final class AsyncThreadSafeEventService extends ThreadSafeEventService {

	private LinkedBlockingQueue<QueuedAsyncEvent> _eventQueue = new LinkedBlockingQueue<>();

	public AsyncThreadSafeEventService() {
		super();
		new Thread(new EventHandler()).start();
	}

	public void publishAsync(Object event) {
		_eventQueue.add(new QueuedAsyncEvent(event));
	}

	public void publishAsync(Type genericType, Object event) {
		_eventQueue.add(new QueuedAsyncEvent(genericType,event));
	}

	public void publishAsync(String topicName, Object eventObj) {
		_eventQueue.add(new QueuedAsyncEvent(topicName,eventObj));
	}

	public int getQueueSize() {
		return _eventQueue.size();
	}

	private static class QueuedAsyncEvent {

		private Object _event;
		private String _topic;
		private Type _genericType;

		private QueuedAsyncEvent(Object event) {
			_event = event;
		}

		private QueuedAsyncEvent(String topic, Object event) {
			_topic = topic;
			_event = event;
		}

		private QueuedAsyncEvent(Type genericType, Object event) {
			_genericType = genericType;
			_event = event;
		}

		private Object getEvent() {
			return _event;
		}

		private String getTopic() {
			return _topic;
		}

		private Type getGenericType() {
			return _genericType;
		}
	}

	private class EventHandler implements Runnable {

		public void run() {
			Thread.currentThread().setName("AsyncEventHandler");
			while (true) {
				try {
					QueuedAsyncEvent queuedEvent = _eventQueue.take();
					if (queuedEvent.getTopic() != null) {
						publish(queuedEvent.getTopic(),queuedEvent.getEvent());
					} else if (queuedEvent.getGenericType() != null) {
						publish(queuedEvent.getGenericType(),queuedEvent.getEvent());
					} else {
						publish(queuedEvent.getEvent());
					}
				} catch (InterruptedException e) {
					// Do nothing just loop and try again
				}
			}
		}
	}
}
