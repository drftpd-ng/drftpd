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
package org.drftpd.protocol.mediainfo.common;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
@SuppressWarnings("serial")
public class MediaInfo implements Serializable {
	private static final Logger logger = Logger.getLogger(MediaInfo.class);

    public static final Key<MediaInfo> MEDIAINFO = new Key<MediaInfo>(MediaInfo.class, "mediainfo");

	private String _fileName = "";
	private long _checksum;

	private ArrayList<HashMap<String,String>> _videoInfos = new ArrayList<HashMap<String,String>>();
	private ArrayList<HashMap<String,String>> _audioInfos = new ArrayList<HashMap<String,String>>();
	private ArrayList<HashMap<String,String>> _subInfos = new ArrayList<HashMap<String,String>>();

	/**
	 * Constructor for MediaInfoMKV
	 */
	public MediaInfo() {	}
	
	public void setFileName(String fileName) {
		_fileName = fileName;
	}
	public String getFileName() {
		return _fileName;
	}
	
	public void setChecksum(long value) {
		_checksum = value;
	}
	public long getChecksum() {
		return _checksum;
	}

	public void setVideoInfos(ArrayList<HashMap<String,String>> videoInfos) {
		_videoInfos = videoInfos;
	}
	public void addVideoInfo(HashMap<String,String> videoInfo) {
		_videoInfos.add(videoInfo);
	}
	public ArrayList<HashMap<String,String>> getVideoInfos() {
		return _videoInfos;
	}

	public void setAudioInfos(ArrayList<HashMap<String,String>> audioInfos) {
		_audioInfos = audioInfos;
	}
	public void addAudioInfo(HashMap<String,String> audioInfo) {
		_audioInfos.add(audioInfo);
	}
	public ArrayList<HashMap<String,String>> getAudioInfos() {
		return _audioInfos;
	}

	public void setSubInfos(ArrayList<HashMap<String,String>> subInfos) {
		_subInfos = subInfos;
	}
	public void addSubInfo(HashMap<String,String> subInfo) {
		_subInfos.add(subInfo);
	}
	public ArrayList<HashMap<String,String>> getSubInfos() {
		return _subInfos;
	}
	
	public static MediaInfo getMediaInfoFromFile(File file) throws IOException {
		MediaInfo mediaInfo = new MediaInfo();

		Pattern pSection = Pattern.compile("^(Video|Audio|Text|Chapters)( #\\d+)?$", Pattern.CASE_INSENSITIVE);
		Pattern pValue = Pattern.compile("^(.*?)\\s+: (.*)$", Pattern.CASE_INSENSITIVE);

		ProcessBuilder builder = new ProcessBuilder("mediainfo", file.getAbsolutePath());
		Process pDD = builder.start();
		BufferedReader stdout = new BufferedReader(new InputStreamReader(pDD.getInputStream()));

		HashMap<String,String> props = new HashMap<String,String>();
		String section = "";
		String line;

		while ((line = stdout.readLine()) != null) {
			if (!line.trim().equals("")) {
				Matcher m = pSection.matcher(line);
				if (m.find() && !line.equalsIgnoreCase(section)) {
					if (section.toLowerCase().startsWith("video")) {
						mediaInfo.addVideoInfo(props);
					} else if (section.toLowerCase().startsWith("audio")) {
						mediaInfo.addAudioInfo(props);
					} else if (section.toLowerCase().startsWith("text")) {
						mediaInfo.addSubInfo(props);
					}
					section = line;
					props = new HashMap<String,String>();
				}
				m = pValue.matcher(line);
				if (m.find()) {
					props.put(m.group(1), m.group(2));
				}
			}
		}

		// Last one
		if (section.toLowerCase().startsWith("video")) {
			mediaInfo.addVideoInfo(props);
		} else if (section.toLowerCase().startsWith("audio")) {
			mediaInfo.addAudioInfo(props);
		} else if (section.toLowerCase().startsWith("text")) {
			mediaInfo.addSubInfo(props);
		}

		stdout.close();
		int exitValue;
		try {
			exitValue = pDD.waitFor();
			if (exitValue != 0) {
				logger.error("ERROR: mediainfo process failed with exit code " + exitValue);
				return null;
			}
		} catch (InterruptedException e) {
			logger.error("ERROR: mediainfo process interrupted");
		}
		pDD.destroy();
		
		return mediaInfo;
	}
}
