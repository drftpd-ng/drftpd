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
package org.drftpd.commands.speedtest.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.RemoteSlave;
import org.drftpd.util.HttpUtils;
import org.tanesha.replacer.ReplacerEnvironment;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author scitz0
 */
public class SpeedTestUtils {
	private static final Logger logger = LogManager.getLogger(SpeedTestUtils.class);

	private static final String[] _speedTestURLS = {
			"http://www.speedtest.net/speedtest-servers-static.php",
			"http://c.speedtest.net/speedtest-servers-static.php",
			"http://www.speedtest.net/speedtest-servers.php",
			"http://c.speedtest.net/speedtest-servers.php"};

	/**
	 * Determine the 5 closest speedtest.net servers based on geographic distance
	 */
	public static HashSet<SpeedTestServer> getClosetsServers() {

		HashSet<SpeedTestServer> serverList = new HashSet<>();

		// Get servers from speedtest.net
		for (String url : _speedTestURLS) {
			try {
				String data = HttpUtils.retrieveHttpAsString(url);
				serverList.addAll(parseXML(data));
			} catch (UnsupportedEncodingException e) {
                logger.warn("UnsupportedEncodingException parsing {} :: {}", url, e.getMessage());
			} catch (XMLStreamException e) {
                logger.warn("XMLStreamException parsing {} :: {}", url, e.getMessage());
			} catch (Exception e) {
                logger.warn("Failed to get data from {} :: {}", url, e.getMessage());
			}
		}
		return serverList;
	}

	private static HashSet<SpeedTestServer> parseXML(String xmlString)
			throws UnsupportedEncodingException, XMLStreamException {
		HashSet<SpeedTestServer> serverList = new HashSet<>();
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(
				new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8)));
		while(xmlEventReader.hasNext()) {
			//Get next event.
			XMLEvent xmlEvent = xmlEventReader.nextEvent();
			//Check if event is the start element.
			if (xmlEvent.isStartElement()) {
				//Get event as start element.
				StartElement startElement = xmlEvent.asStartElement();
				if (startElement.getName().getLocalPart().equals("server")) {
					SpeedTestServer server = new SpeedTestServer();
					//Iterate and process attributes.
					Iterator iterator = startElement.getAttributes();
					while (iterator.hasNext()) {
						Attribute attribute = (Attribute) iterator.next();
						String name = attribute.getName().getLocalPart();
						String value = attribute.getValue();
                        switch (name) {
                            case "url":
                                server.setUrl(value);
                                break;
                            case "url2":
                                server.setUrl2(value);
                                break;
                            case "lat":
                                server.setLatitude(Double.parseDouble(value));
                                break;
                            case "lon":
                                server.setLongitude(Double.parseDouble(value));
                                break;
                            case "name":
                                server.setName(value);
                                break;
                            case "country":
                                server.setCountry(value);
                                break;
                            case "cc":
                                server.setCc(value);
                                break;
                            case "sponsor":
                                server.setSponsor(value);
                                break;
                            case "id":
                                server.setId(Integer.parseInt(value));
                                break;
                            case "host":
                                server.setHost(value);
                                break;
                        }
					}
					serverList.add(server);
				}
			}
		}
		return serverList;
	}

	public static SlaveLocation getSlaveLocation(RemoteSlave rslave) {
		SlaveLocation slaveLocation = new SlaveLocation();
		try {
			String ip = InetAddress.getByName(rslave.getPASVIP()).getHostAddress();
			String data = HttpUtils.retrieveHttpAsString("http://ipinfo.io/" + ip + "/json");
			JsonElement root = JsonParser.parseString(data);
			JsonObject rootobj = root.getAsJsonObject();
			String[] loc = rootobj.get("loc").getAsString().split(",");
			slaveLocation.setLatitude(Double.parseDouble(loc[0]));
			slaveLocation.setLongitude(Double.parseDouble(loc[1]));
		} catch (Exception e) {
            logger.error("Something went wrong getting slave location: {}", e.getMessage());
		}
		return slaveLocation;
	}

	public static double getDistance(double lat1, double lon1, double lat2, double lon2, char unit) {
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		dist = dist * 60 * 1.1515;
		if (unit == 'K') {
			dist = dist * 1.609344;
		} else if (unit == 'N') {
			dist = dist * 0.8684;
		}

		return (dist);
	}
	private static double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}
	private static double rad2deg(double rad) {
		return (rad * 180 / Math.PI);
	}

	public static void addServerEnvVariables(SpeedTestServer server, ReplacerEnvironment env) {
		env.add("server.url", server.getUrl());
		env.add("server.name", server.getName());
		env.add("server.country", server.getCountry());
		env.add("server.cc", server.getCc());
		env.add("server.sponsor", server.getSponsor());
		env.add("server.id", server.getId());
		env.add("server.host", server.getHost());
		env.add("server.latency", server.getLatency());
		env.add("server.latitude", server.getLatitude());
		env.add("server.longitude", server.getLongitude());
	}
}
