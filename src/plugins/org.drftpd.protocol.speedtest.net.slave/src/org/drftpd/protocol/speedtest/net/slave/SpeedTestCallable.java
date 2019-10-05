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
package org.drftpd.protocol.speedtest.net.slave;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author scitz0
 */
public class SpeedTestCallable implements Callable<Long> {

	private static final Logger logger = LogManager.getLogger(SpeedTestCallable.class);

	private CloseableHttpResponse response;
	private CloseableHttpClient httpClient;
	private HttpPost httpPost;
	private HttpGet httpGet;

	public SpeedTestCallable() {
		httpClient = HttpClients.createDefault();
	}

	public void setHttpPost(HttpPost httpPost) {
		this.httpPost = httpPost;
	}

	public void setHttpGet(HttpGet httpGet) {
		this.httpGet = httpGet;
	}

	@Override
	public Long call() throws Exception {
		Long bytes = 0L;
		try {
			if (httpPost != null) {
				response = httpClient.execute(httpPost);
				final int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					throw new Exception("Error code " + statusCode + " while running upload test.");
				}

				HttpEntity entity = response.getEntity();
				String data = EntityUtils.toString(entity);
				EntityUtils.consume(entity);
				if (!data.startsWith("size=")) {
					throw new Exception("Wrong return result from upload messurement from test server.\nReceived: " + data);
				}
				bytes = Long.parseLong(data.replaceAll("\\D", ""));
			} else if (httpGet != null) {
				response = httpClient.execute(httpGet);
				final int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					throw new Exception("Error code " + statusCode + " while running upload test.");
				}
				HttpEntity entity = response.getEntity();
				InputStream instream = entity.getContent();
				int bufferSize = 10240;
				byte[] buffer = new byte[bufferSize];
				int len;
				while ((len = instream.read(buffer)) != -1) {
					bytes = bytes + len;
				}
				EntityUtils.consume(entity);
			}
		} catch (Exception e) {
			throw new ExecutionException(e);
		} finally {
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
				// Must already be closed, ignore.
			}
		}

		return bytes;
	}

	public void close() {
		if (httpClient != null) {
			try {
				httpClient.close();
			} catch (IOException e) {
				// Must already be closed, ignore.
			}
		}
	}
}
