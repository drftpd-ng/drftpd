/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.speedtest.slave;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.protocol.speedtest.common.SpeedTestInfo;
import org.drftpd.protocol.speedtest.common.async.AsyncResponseSpeedTestInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for SpeedTest requests.
 * @author Scitz0
 */
public class SpeedTestHandler extends AbstractHandler {
	private static final Logger logger = Logger.getLogger(SpeedTestHandler.class);

	private static final int[] _sizes = {350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000};

	private static final String _chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private static final String _payload = StringUtils.repeat(_chars, 7000);
	
	public SpeedTestHandler(SlaveProtocolCentral central) {
		super(central);
	}

	public AsyncResponse handleSpeedTest(AsyncCommandArgument ac) {
		return new AsyncResponseSpeedTestInfo(ac.getIndex(),
				doSpeedTest(getSlaveObject(), ac.getArgs()));

	}

	private SpeedTestInfo doSpeedTest(Slave slave, String urls) {
		SpeedTestInfo result = new SpeedTestInfo();
		try {
			String[] testServerURLs = urls.split(" ");
			String url = getBestServer(testServerURLs, result);
			if (url == null) {
				// Was unable to measure latency for server(s), return empty SpeedTestInfo
				return result;
			}
			result.setURL(url);
			result.setDown(getDownloadSpeed(url));
			result.setUp(getUploadSpeed(url));
		} catch (Exception e) {
			// Catch all errors to not throw slave offline in case something went wrong
			logger.error("Something went horribly wrong speedtesting slave", e);
		}
		return result;
	}

	private float getUploadSpeed(String url) {
		long totalTime = 0L;
		long totalBytes = 0L;

		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(60000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();
		CloseableHttpResponse response = null;

		CloseableHttpClient httpClient = HttpClients.createDefault();

		HttpPost httpPost = new HttpPost(url);
		httpPost.setHeader("content-type", "application/x-www-form-urlencoded");
		httpPost.setConfig(requestConfig);

		String payload = _payload; // Initial payload

		StopWatch watch = new StopWatch();

		int i = 2;
		while (true) { // ul speed for payload generated for each multiplier in _multipliers
			if (totalTime > 5000) { break; } // 5s is enough time spent on measurement

			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("content1",payload));
			try {
				httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			} catch (UnsupportedEncodingException e) {
				logger.error("Unsupported encoding of payload for speedtest upload: " + e.getMessage());
				return 0;
			}

			for (int j = 0; j < 20; j++) {
				watch.reset();
				try {
					watch.start();
					response = httpClient.execute(httpPost);
					watch.stop();
					final int statusCode = response.getStatusLine().getStatusCode();
					if (statusCode != HttpStatus.SC_OK) {
						logger.error("Error " + statusCode + " for URL " + url);
						return 0;
					}

					HttpEntity entity = response.getEntity();
					String result = EntityUtils.toString(entity);
					EntityUtils.consume(entity);
					if (!result.startsWith("size=")) {
						logger.error("Wrong return result from upload messurement from test server, " + url +
								"\nReceived: " + result);
						return 0;
					}
					totalBytes += Long.parseLong(result.replaceAll("\\D", ""));
				} catch (Exception e) {
					logger.error("Error for URL " + url, e);
					return 0;
				} finally {

					try {
						if (response != null) {
							response.close();
						}
					} catch (IOException e) {
						// Must already be closed, ignore.
					}
				}
				long time = watch.getTime();
				totalTime += time;
				if (totalTime > 5000) { break; } // 5s is enough time spent on measurement
			}

			if (payload.length() < 5000000) { // Increase payload size if not too big
				payload = StringUtils.repeat(_payload, i);
				i++;
			}
		}
		try {
			httpClient.close();
		} catch (IOException e) {
			// Must already be closed, ignore.
		}
		if (totalBytes == 0L || totalTime == 0L) {
			return 0;
		}

		return (float)(((totalBytes*8)/totalTime)*1000)/1000000;
	}

	private float getDownloadSpeed(String url) {
		long totalTime = 0L;
		long totalBytes = 0L;

		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(60000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();
		CloseableHttpResponse response = null;

		CloseableHttpClient httpClient = HttpClients.createDefault();

		HttpGet httpGet = new HttpGet();
		httpGet.setConfig(requestConfig);

		url = url.replace("upload.php","random");

		StopWatch watch = new StopWatch();

		for (int size : _sizes) { // Measure dl speed for each size in _sizes
			if (totalTime > 10000) { break; } // 10s is enough time spent on measurement
			String tmpURL = url + size+"x"+size+".jpg";
			try {
				httpGet.setURI(new URI(tmpURL));
			} catch (URISyntaxException e) {
				logger.error("URI syntax error for " + tmpURL + " :: " + e.getMessage());
				return 0;
			}
			for (int j = 0; j < 4; j++) {
				// Measure each size four times
				long bytes = 0;
				watch.reset();
				try {
					response = httpClient.execute(httpGet);
					final int statusCode = response.getStatusLine().getStatusCode();
					if (statusCode != HttpStatus.SC_OK) {
						logger.error("Error " + statusCode + " for URL " + tmpURL);
						return 0;
					}
					HttpEntity entity = response.getEntity();
					InputStream instream = entity.getContent();
					int bufferSize = 10240;
					byte[] buffer = new byte[bufferSize];
					int len;
					watch.start();
					while ((len = instream.read(buffer)) != -1) {
						bytes = bytes + len;
					}
					watch.stop();
					EntityUtils.consume(entity);
				} catch (Exception e) {
					logger.error("Error for URL " + tmpURL, e);
					return 0;
				} finally {
					try {
						if (response != null) {
							response.close();
						}
					} catch (IOException e) {
						// Must already be closed, ignore.
					}
				}
				long time = watch.getTime();
				totalTime += time;
				totalBytes += bytes;
				if (totalTime > 10000) { break; } // 10s is enough time spent on measurement
			}
		}
		try {
			httpClient.close();
		} catch (IOException e) {
			// Must already be closed, ignore.
		}
		if (totalBytes == 0L || totalTime == 0L) {
			return 0;
		}

		return (float)(((totalBytes*8)/totalTime)*1000)/1000000;
	}

	private String getBestServer(String[] urls, SpeedTestInfo result) {
		String url = null;
		// Measure latency for each test server
		int lowestLatency = Integer.MAX_VALUE;
		for (String testURL : urls) {
			String latencyURL = testURL.replace("upload.php","latency.txt").trim();
			int latency = messureLatency(latencyURL);
			if (latency < lowestLatency) {
				lowestLatency = latency;
				url = testURL;
			}
		}
		result.setLatency(lowestLatency);
		return url;
	}

	private int messureLatency(String url) {
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(5000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();
		HttpGet httpGet = new HttpGet(url);
		httpGet.setConfig(requestConfig);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		CloseableHttpResponse response = null;
		int bestTime = Integer.MAX_VALUE;
		StopWatch watch = new StopWatch();
		for (int i = 0; i < 3; i++) {
			// Do three measurements for each url to get a fair value
			watch.reset();
			try {
				watch.start();
				response = httpClient.execute(httpGet);
				final int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					logger.error("Error " + statusCode + " for URL " + url);
					break;
				}
				HttpEntity entity = response.getEntity();
				String data = EntityUtils.toString(entity);
				EntityUtils.consume(entity);
				if (!data.startsWith("test=test")) {
					logger.error("Wrong return result from latency messurement from test server, " + url +
							"\nReceived: " + data);
					break;
				}
			} catch (Exception e) {
				logger.error("Error for URL " + url, e);
				break;
			} finally {
				watch.stop();
				try {
					if (response != null) {
						response.close();
					}
				} catch (IOException e) {
					// Must already be closed, ignore.
				}
			}
			int time = (int)watch.getTime();
			if (time < bestTime) {
				bestTime = time;
			}
		}
		try {
			httpClient.close();
		} catch (IOException e) {
			// Must already be closed, ignore.
		}
		return bestTime;
	}
}
