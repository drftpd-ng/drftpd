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
package org.drftpd.master.util;

import org.apache.commons.text.StringEscapeUtils;

import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;

import org.apache.hc.client5.http.config.RequestConfig;

import org.apache.hc.client5.http.cookie.StandardCookieSpec;

import org.apache.hc.client5.http.classic.methods.HttpGet;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.IOException;
import java.text.Normalizer;
import java.util.concurrent.TimeUnit;

/**
 * @author scitz0
 */
public class HttpUtils {
    public static final String _userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";

    public static String retrieveHttpAsString(String url) throws HttpException, IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(5000, TimeUnit.MILLISECONDS)
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .build();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(_userAgent)
                .build();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setConfig(requestConfig);
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpGet);
            final int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpException("Error " + statusCode + " for URL " + url);
            }
            HttpEntity entity = response.getEntity();
            String data = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            return data;
        } catch (IOException e) {
            throw new IOException("Error for URL " + url, e);
        } finally {
            if (response != null) {
                response.close();
            }
            httpclient.close();
        }
    }

    public static String htmlToString(String input) {
        String str = input.replaceAll("\n", "");
        str = StringEscapeUtils.unescapeHtml4(str);
        str = Normalizer.normalize(str, Normalizer.Form.NFD);
        str = str.replaceAll("\\P{InBasic_Latin}", "");
        while (str.contains("<")) {
            int startPos = str.indexOf("<");
            int endPos = str.indexOf(">", startPos);
            if (endPos > startPos) {
                String beforeTag = str.substring(0, startPos);
                String afterTag = str.substring(endPos + 1);
                str = beforeTag + afterTag;
            }
        }
        return str;
    }
}
