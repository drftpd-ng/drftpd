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
package org.drftpd.speedtestnet.slave;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author scitz0
 */
public class SpeedTestCallable implements Callable<SpeedTestAnswer> {

    private static final Logger logger = LogManager.getLogger(SpeedTestCallable.class);

    private HttpPost _httpPost;
    private HttpGet _httpGet;

    public SpeedTestCallable() {}

    public void setHttpPost(HttpPost httpPost) {
        logger.debug("Setting httpPost");
        _httpPost = httpPost;
        _httpGet = null;
    }

    public void setHttpGet(HttpGet httpGet) {
        logger.debug("Setting httpGet");
        _httpGet = httpGet;
        _httpPost = null;
    }

    @Override
    public SpeedTestAnswer call() throws Exception {
        logger.debug("We were called");
        long bytes = 0L;
        long timeStart = 0L;
        long timeStop = 0L;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        try {
            timeStart = Instant.now().toEpochMilli();
            timeStop = timeStart;
            if (_httpPost != null) {
                logger.debug("executing httpPost");
                response = httpClient.execute(_httpPost);
                final int statusCode = response.getCode();
                if (statusCode != HttpStatus.SC_OK) {
                    throw new Exception("Error code " + statusCode + " while running upload test.");
                }

                HttpEntity entity = response.getEntity();
                String data = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                if (!data.startsWith("size=")) {
                    throw new Exception("Wrong return result from upload messurement from test server.\nReceived: " + data);
                }
                timeStop = Instant.now().toEpochMilli();
                bytes = Long.parseLong(data.replaceAll("\\D", ""));
            } else if (_httpGet != null) {
                logger.debug("executing httpGet");
                response = httpClient.execute(_httpGet);
                final int statusCode = response.getCode();
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
                timeStop = Instant.now().toEpochMilli();
            } else
            {
                logger.error("Called without httpget or httppost set...");
            }
        } catch (Exception e) {
            logger.error("Received exception while being called. {}", e.getMessage());
            throw new ExecutionException(e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
                httpClient.close();
            } catch (IOException e) {
                // Must already be closed, ignore.
            }
        }

        long time = timeStop - timeStart;
        logger.debug("Returning [" + bytes + "] bytes and [" + time + "] time");
        return new SpeedTestAnswer(bytes, time);
    }

}
