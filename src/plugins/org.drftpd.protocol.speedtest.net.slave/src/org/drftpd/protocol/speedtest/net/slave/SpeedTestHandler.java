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
package org.drftpd.protocol.speedtest.net.slave;

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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.protocol.speedtest.net.common.SpeedTestInfo;
import org.drftpd.protocol.speedtest.net.common.async.AsyncResponseSpeedTestInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Handler for SpeedTest requests.
 * @author Scitz0
 */
public class SpeedTestHandler extends AbstractHandler {
	private static final Logger logger = LogManager.getLogger(SpeedTestHandler.class);

	private int[] _sizes = {350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000};
	private int _sizeLoop = 4;
	private int _downTime = 10000;
	private int _upTime = 5000;
	private String _payload = "";
	private int _payloadLoop = 20;
	private int _upThreads = 3;
	private int _downThreads = 3;
	private int _sleep = 100;
	
	public SpeedTestHandler(SlaveProtocolCentral central) {
		super(central);
		try {
			readConf();
		} catch (Exception e) {
            logger.error("Error loading conf/plugins/speedtest.net.slave.conf :: {}", e.getMessage());
		}
	}

	/**
	 * Load conf/plugins/speedtest.net.slave.conf
	 * @throws Exception
	 */
	private void readConf() throws Exception {
		logger.info("Loading speedtest.net slave configuration...");
		Properties p = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream("conf/plugins/speedtest.net.slave.conf");
			p.load(fis);
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
		if (p.getProperty("sizes") != null) {
			String[] strArray = p.getProperty("sizes").split(",");
			_sizes = new int[strArray.length];
			for(int i = 0; i < strArray.length; i++) {
				_sizes[i] = Integer.parseInt(strArray[i]);
			}
		}
		if (p.getProperty("size.loop") != null) {
			_sizeLoop = Integer.parseInt(p.getProperty("size.loop"));
		}
		if (p.getProperty("max.down.time") != null) {
			_downTime = Integer.parseInt(p.getProperty("max.down.time"))*1000;
		}
		if (p.getProperty("max.up.time") != null) {
			_upTime = Integer.parseInt(p.getProperty("max.up.time"))*1000;
		}
		String payloadString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		if (p.getProperty("payload.string") != null) {
			payloadString = p.getProperty("payload.string");
		}
		int payloadRepeat = 7000;
		if (p.getProperty("payload.repeat") != null) {
			payloadRepeat = Integer.parseInt(p.getProperty("payload.repeat"));
		}
		_payload = StringUtils.repeat(payloadString, payloadRepeat);
		if (p.getProperty("payload.loop") != null) {
			_payloadLoop = Integer.parseInt(p.getProperty("payload.loop"));
		}
		if (p.getProperty("threads.up") != null) {
			_upThreads = Integer.parseInt(p.getProperty("threads.up"));
		}
		if (p.getProperty("threads.down") != null) {
			_downThreads = Integer.parseInt(p.getProperty("threads.down"));
		}
		if (p.getProperty("sleep") != null) {
			_sleep = Integer.parseInt(p.getProperty("sleep"));
		}
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

		long startTime = System.currentTimeMillis();

		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(60000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();

		HttpPost httpPost = new HttpPost(url);
		httpPost.setHeader("content-type", "application/x-www-form-urlencoded");
		httpPost.setConfig(requestConfig);

		String payload = _payload; // Initial payload

		StopWatch watch = new StopWatch();

		SpeedTestCallable[] speedTestCallables = new SpeedTestCallable[_upThreads];
		for (int i = 0; i < _upThreads; i++) {
			speedTestCallables[i] = new SpeedTestCallable();
		}

		ExecutorService executor = Executors.newFixedThreadPool(_upThreads);
		List<Future<Long>> threadList;
		Set<Callable<Long>> callables = new HashSet<>();

		boolean limitReached = false;

		int i = 2;
        while ((System.currentTimeMillis() - startTime) <= _upTime) {

            List<NameValuePair> nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("content1", payload));
            try {
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            } catch (UnsupportedEncodingException e) {
                logger.error("Unsupported encoding of payload for speedtest upload: {}", e.getMessage());
                close(executor, callables);
                return 0;
            }

            callables.clear();
            for (int k = 0; k < _upThreads; k++) {
                speedTestCallables[k].setHttpPost(httpPost);
                callables.add(speedTestCallables[k]);
            }

            for (int j = 0; j < _payloadLoop; j++) {
                try {
                    watch.reset();
                    Thread.sleep(_sleep);
                    watch.start();
                    threadList = executor.invokeAll(callables);
                    for (Future<Long> fut : threadList) {
                        Long bytes = fut.get();
                        totalBytes += bytes;
                    }
                    watch.stop();
                    totalTime += watch.getTime();
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                    close(executor, callables);
                    return 0;
                } catch (ExecutionException e) {
                    if (e.getMessage().contains("Error code 413")) {
                        limitReached = true;
                        payload = StringUtils.repeat(_payload, i - 2);
                    } else {
                        logger.error(e.getMessage());
                        close(executor, callables);
                        return 0;
                    }
                }
                if ((System.currentTimeMillis() - startTime) > _upTime) {
                    break;
                }
            }

            if (!limitReached) { // Increase payload size if not too big
                payload = StringUtils.repeat(_payload, i);
                i++;
            }
        }

		if (totalBytes == 0L || totalTime == 0L) {
			close(executor, callables);
			return 0;
		}

		close(executor, callables);

		return (float)(((totalBytes*8)/totalTime)*1000)/1000000;
	}

	private void close(ExecutorService executor, Set<Callable<Long>> callables) {
		for (Callable<Long> callable : callables) {
			((SpeedTestCallable)callable).close();
		}
		executor.shutdown();
	}

	private float getDownloadSpeed(String url) {
		long totalTime = 0L;
		long totalBytes = 0L;

		long startTime = System.currentTimeMillis();

		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(60000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();

		HttpGet httpGet = new HttpGet();
		httpGet.setConfig(requestConfig);

		SpeedTestCallable[] speedTestCallables = new SpeedTestCallable[_downThreads];
		for (int i = 0; i < _downThreads; i++) {
			speedTestCallables[i] = new SpeedTestCallable();
		}

		ExecutorService executor = Executors.newFixedThreadPool(_downThreads);
		List<Future<Long>> threadList;
		Set<Callable<Long>> callables = new HashSet<>();

		url = url.substring(0,url.lastIndexOf('/')+1) + "random";

		StopWatch watch = new StopWatch();

		for (int size : _sizes) { // Measure dl speed for each size in _sizes
			if ((System.currentTimeMillis()-startTime) > _downTime) { break; }

			String tmpURL = url + size+"x"+size+".jpg";
			try {
				httpGet.setURI(new URI(tmpURL));
			} catch (URISyntaxException e) {
                logger.error("URI syntax error for {} :: {}", tmpURL, e.getMessage());
				close(executor, callables);
				return 0;
			}

			callables.clear();
			for (int k = 0; k < _downThreads; k++) {
				speedTestCallables[k].setHttpGet(httpGet);
				callables.add(speedTestCallables[k]);
			}

			for (int j = 0; j < _sizeLoop; j++) {
				try {
					watch.reset();
					Thread.sleep(_sleep);
					watch.start();
					threadList = executor.invokeAll(callables);
					for(Future<Long> fut : threadList){
						Long bytes = fut.get();
						totalBytes += bytes;
					}
					watch.stop();
					totalTime += watch.getTime();
				} catch (InterruptedException e) {
					logger.error(e.getMessage());
					close(executor, callables);
					return 0;
				} catch (ExecutionException e) {
					logger.error(e.getMessage());
					close(executor, callables);
					return 0;
				}
				if ((System.currentTimeMillis()-startTime) > _downTime) { break; }
			}
		}

		if (totalBytes == 0L || totalTime == 0L) {
			close(executor, callables);
			return 0;
		}

		close(executor, callables);

		return (float)(((totalBytes*8)/totalTime)*1000)/1000000;
	}

	private String getBestServer(String[] urls, SpeedTestInfo result) {
		String url = null;
		// Measure latency for each test server
		int lowestLatency = Integer.MAX_VALUE;
		for (String testURL : urls) {
			String latencyURL = testURL.substring(0,testURL.lastIndexOf('/')+1) + "latency.txt";
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
                    logger.error("Error {} for URL {}", statusCode, url);
					break;
				}
				HttpEntity entity = response.getEntity();
				String data = EntityUtils.toString(entity);
				EntityUtils.consume(entity);
				if (!data.startsWith("test=test")) {
                    logger.error("Wrong return result from latency messurement from test server, {}\nReceived: {}", url, data);
					break;
				}
			} catch (Exception e) {
                logger.error("Error for URL {}", url, e);
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
