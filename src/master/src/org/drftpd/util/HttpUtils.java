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
package org.drftpd.util;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.text.Normalizer;

/**
 * @author scitz0
 */
public class HttpUtils {
	public static final String _userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";

	public static String retrieveHttpAsString(String url) throws HttpException, IOException {
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(5000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.setCookieSpec(CookieSpecs.IGNORE_COOKIES)
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
			final int statusCode = response.getStatusLine().getStatusCode();
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
		String str = input.replaceAll("\n","");
		str = StringEscapeUtils.unescapeHtml4(str);
		str = Normalizer.normalize(str, Normalizer.Form.NFD);
		str = str.replaceAll("\\P{InBasic_Latin}", "");
		while(str.contains("<"))
		{
			int startPos = str.indexOf("<");
			int endPos = str.indexOf(">",startPos);
			if (endPos>startPos)
			{
				String beforeTag = str.substring(0,startPos);
				String afterTag = str.substring(endPos+1);
				str = beforeTag + afterTag;
			}
		}
		return str;
	}
}
