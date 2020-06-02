/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.speedtestnet.slave;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import org.apache.hc.core5.http.io.entity.EntityUtils;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;

import org.apache.hc.core5.http.message.BasicNameValuePair;

import org.apache.hc.client5.http.config.RequestConfig;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.drftpd.speedtestnet.common.AsyncResponseSpeedTestInfo;
import org.drftpd.speedtestnet.common.SpeedTestInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler for SpeedTest requests.
 *
 * @author Scitz0
 */
public class SpeedTestHandler extends AbstractHandler {
    private static final Logger logger = LogManager.getLogger(SpeedTestHandler.class);

    private int[] _sizes = { 350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000 };
    private int _sizeLoop = 4;
    private int _downTime = 10000;
    private int _upTime = 5000;
    private String _payload = "";
    private int _payloadLoop = 20;
    private int _upThreads = 3;
    private int _downThreads = 3;

    public SpeedTestHandler(SlaveProtocolCentral central) {
        super(central);
        try {
            readConf();
        } catch (Exception e) {
            logger.error("Error loading config/plugins/speedtest.net.slave.conf :: {}", e.getMessage());
        }
    }

    @Override
    public String getProtocolName() {
        return "SpeedTestProtocol";
    }

    /**
     * Load config/plugins/speedtest.net.slave.conf
     */
    private void readConf() {
        logger.info("Loading speedtest.net slave configuration...");
        Properties p = ConfigLoader.loadPluginConfig("speedtest.net.slave.conf");
        if (p.getProperty("sizes") != null) {
            String[] strArray = p.getProperty("sizes").split(",");
            _sizes = new int[strArray.length];
            for (int i = 0; i < strArray.length; i++) {
                _sizes[i] = Integer.parseInt(strArray[i]);
            }
        }
        if (p.getProperty("size.loop") != null) {
            _sizeLoop = Integer.parseInt(p.getProperty("size.loop"));
        }
        if (p.getProperty("max.down.time") != null) {
            _downTime = Integer.parseInt(p.getProperty("max.down.time")) * 1000;
        }
        if (p.getProperty("max.up.time") != null) {
            _upTime = Integer.parseInt(p.getProperty("max.up.time")) * 1000;
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
    }

    public AsyncResponse handleSpeedTest(AsyncCommandArgument ac) {
        return new AsyncResponseSpeedTestInfo(ac.getIndex(), doSpeedTest(ac.getArgs()));
    }

    private SpeedTestInfo doSpeedTest(String urls) {
        SpeedTestInfo result = new SpeedTestInfo();
        try {
            String[] testServerURLs = urls.split(" ");
            String url = getBestServer(testServerURLs, result);
            if (url == null) {
                logger.warn("Unable to measure latency for server(s), returning empty speedtest information");
            } else {
                result.setURL(url);
                result.setDown(getDownloadSpeed(url));
                result.setUp(getUploadSpeed(url));
            }
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
        logger.debug("Getting upload speed for [" + url + "]");

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(60000, TimeUnit.MILLISECONDS)
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(5000, TimeUnit.MILLISECONDS)
                .build();

        logger.debug("Initializing " + _upThreads + " speedtest upload callables");
        SpeedTestCallable[] speedTestCallables = new SpeedTestCallable[_upThreads];
        for (int i = 0; i < _upThreads; i++) {
            speedTestCallables[i] = new SpeedTestCallable();
        }

        ExecutorService executor = Executors.newFixedThreadPool(_upThreads);
        List<Future<SpeedTestAnswer>> threadList;
        Set<Callable<SpeedTestAnswer>> callables = new HashSet<>();

        String payload = _payload; // Initial payload

        boolean limitReached = false;

        int i = 2;
        while ((System.currentTimeMillis() - startTime) <= _upTime) {

            List<NameValuePair> nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("content1", payload));

            callables.clear();
            for (int k = 0; k < _upThreads; k++) {
                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("content-type", "application/x-www-form-urlencoded");
                httpPost.setConfig(requestConfig);
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                speedTestCallables[k].setHttpPost(httpPost);
                callables.add(speedTestCallables[k]);
            }

            logger.debug("iterating from 0 to " + _payloadLoop);
            for (int j = 0; j < _payloadLoop; j++) {
                try {
                    long time = 0L;
                    threadList = executor.invokeAll(callables);
                    for (Future<SpeedTestAnswer> fut : threadList) {
                        totalBytes += fut.get().getBytes();
                        time += fut.get().getTime();
                    }
                    // we execute parallel processes in the same time, so we need to divide by the concurrency
                    totalTime += time / threadList.size();
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                    close(executor);
                    return 0;
                } catch (ExecutionException e) {
                    if (e.getMessage().contains("Error code 413")) {
                        limitReached = true;
                        payload = StringUtils.repeat(_payload, i - 2);
                    } else {
                        logger.error(e.getMessage());
                        close(executor);
                        return 0;
                    }
                }
                if ((System.currentTimeMillis() - startTime) > _upTime) {
                    logger.debug("uptime " + _upTime + " reached, stopping");
                    break;
                }
            }

            if (!limitReached) { // Increase payload size if not too big
                payload = StringUtils.repeat(_payload, i);
                i++;
            }
        }

        close(executor);

        if (totalBytes == 0L || totalTime == 0L) {
            return 0;
        }

        long totalBits = totalBytes * 8;
        float totalTimeSec = (float) totalTime / 1000L;
        float bitsperms = (float) totalBits / totalTimeSec;
        float mbitspers = bitsperms / 1000000;
        logger.debug("totalTime (milli): {}, totalTime (sec): {}, totalBytes: {}, totalBits: {}, bitsperms: {}, mbitspers: {}", totalTime, totalTimeSec, totalBytes, totalBits, bitsperms, mbitspers);
        return mbitspers;
    }

    private void close(ExecutorService executor) {
        executor.shutdown();
    }

    private float getDownloadSpeed(String url) {
        long totalTime = 0L;
        long totalBytes = 0L;

        logger.debug("Getting download speed for [" + url + "]");

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(60000, TimeUnit.MILLISECONDS)
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(5000, TimeUnit.MILLISECONDS)
                .build();

        logger.debug("Initializing " + _downThreads + " speedtest download callables");
        SpeedTestCallable[] speedTestCallables = new SpeedTestCallable[_downThreads];
        for (int i = 0; i < _downThreads; i++) {
            speedTestCallables[i] = new SpeedTestCallable();
        }

        ExecutorService executor = Executors.newFixedThreadPool(_downThreads);
        List<Future<SpeedTestAnswer>> threadList;
        Set<Callable<SpeedTestAnswer>> callables = new HashSet<>();

        url = url.substring(0, url.lastIndexOf('/') + 1) + "random";

        URI downloadUrl;

        for (int size : _sizes) { // Measure dl speed for each size in _sizes
            // We have _downTime for every size
            long startTime = System.currentTimeMillis();
            logger.debug("Testing size [" + size + "] for url [" + url +"]");
            if ((System.currentTimeMillis() - startTime) > _downTime)
            {
                logger.debug("downtime " + _downTime + " reached inside sizes, stopping");
                break;
            }

            String tmpURL = url + size + "x" + size + ".jpg";
            logger.debug("test url: [" + tmpURL + "]");
            try {
                downloadUrl = new URI(tmpURL);
            } catch (URISyntaxException e) {
                logger.error("URI syntax error for {} :: {}", tmpURL, e.getMessage());
                close(executor);
                return 0;
            }

            callables.clear();
            for (int k = 0; k < _downThreads; k++) {
                HttpGet httpget = new HttpGet(downloadUrl);
                httpget.setConfig(requestConfig);
                speedTestCallables[k].setHttpGet(httpget);
                callables.add(speedTestCallables[k]);
            }

            logger.debug("iterating from 0 to " + _sizeLoop);
            for (int j = 0; j < _sizeLoop; j++) {
                try {
                    long time = 0L;
                    threadList = executor.invokeAll(callables);
                    for (Future<SpeedTestAnswer> fut : threadList) {
                        totalBytes += fut.get().getBytes();
                        time += fut.get().getTime();
                    }
                    // we execute parallel processes in the same time, so we need to divide by the concurrency
                    totalTime += time / threadList.size();
                    logger.debug("totalTime: {}, time for this run: {}", totalTime, time);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error(e.getMessage());
                    close(executor);
                    return 0;
                }
                if ((System.currentTimeMillis() - startTime) > _downTime)
                {
                    logger.debug("downtime " + _downTime + " reached inside sizeLoop, stopping");
                    break;
                }
            }
        }

        close(executor);

        if (totalBytes == 0L || totalTime == 0L) {
            return 0;
        }

        long totalBits = totalBytes * 8;
        float totalTimeSec = (float) totalTime / 1000L;
        float bitsperms = (float) totalBits / totalTimeSec;
        float mbitspers = bitsperms / 1000000;
        logger.debug("totalTime (milli): {}, totalTime (sec): {}, totalBytes: {}, totalBits: {}, bitsperms: {}, mbitspers: {}", totalTime, totalTimeSec, totalBytes, totalBits, bitsperms, mbitspers);
        return mbitspers;
    }

    private String getBestServer(String[] urls, SpeedTestInfo result) {
        String url = null;
        // Measure latency for each test server
        int lowestLatency = Integer.MAX_VALUE;
        for (String testURL : urls) {
            String latencyURL = testURL.substring(0, testURL.lastIndexOf('/') + 1) + "latency.txt";
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
        logger.debug("Measuring latency of url [" + url + "]");
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(5000, TimeUnit.MILLISECONDS)
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
                final int statusCode = response.getCode();
                if (statusCode != HttpStatus.SC_OK) {
                    logger.error("Error {} for URL {}", statusCode, url);
                    break;
                }
                HttpEntity entity = response.getEntity();
                String data = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                if (!data.startsWith("test=test")) {
                    logger.error("Wrong return result from latency measurement from test server, {}\nReceived: {}", url, data);
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
            int time = (int) watch.getTime();
            logger.debug("Iteration[" + i + "] bestTime: " + bestTime + ", new time: " + time);
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
