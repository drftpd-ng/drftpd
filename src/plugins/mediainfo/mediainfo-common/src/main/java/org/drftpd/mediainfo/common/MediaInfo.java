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
package org.drftpd.mediainfo.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.util.Bytes;
import org.mp4parser.IsoFile;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
@SuppressWarnings("serial")
public class MediaInfo implements Serializable {
    public static final Key<MediaInfo> MEDIAINFO = new Key<>(MediaInfo.class, "mediainfo");
    private static final Logger logger = LogManager.getLogger(MediaInfo.class);
    private static final String MEDIAINFO_COMMAND = "mediainfo";
    private static final String MKVALIDATOR_COMMAND = "mkvalidator";
    private String _fileName = "";
    private long _checksum;
    private boolean _sampleOk = true;
    private long _actFileSize = 0L;
    private long _calFileSize = 0L;
    private String _realFormat = "";
    private String _uploadedFormat = "";

    private HashMap<String, String> _generalInfo = null;

    private ArrayList<HashMap<String, String>> _videoInfos = new ArrayList<>();
    private ArrayList<HashMap<String, String>> _audioInfos = new ArrayList<>();
    private ArrayList<HashMap<String, String>> _subInfos = new ArrayList<>();

    /**
     * Constructor for MediaInfo
     */
    public MediaInfo() { }

    public static boolean hasWorkingMediaInfo() {
        boolean mediainfo_works = false;
        boolean mkvalidator_works = false;
        try {
            ProcessBuilder builder = new ProcessBuilder(MEDIAINFO_COMMAND, "--version");
            Process proc = builder.start();
            int status = proc.waitFor();
            if (status != 0) {
                throw new RuntimeException("Exist code of " + MEDIAINFO_COMMAND + " --version yielded exit code " + status);
            }
            mediainfo_works = true;
        } catch(Exception e) {
            logger.fatal("Something went wrong trying to see if " + MEDIAINFO_COMMAND + " binary exists and works", e);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(MKVALIDATOR_COMMAND, "--version");
            Process proc = builder.start();
            int status = proc.waitFor();
            if (status != 0) {
                throw new RuntimeException("Exist code of " + MKVALIDATOR_COMMAND + " --version yielded exit code " + status);
            }
            mkvalidator_works = true;
        } catch(Exception e) {
            logger.fatal("Something went wrong trying to see if " + MKVALIDATOR_COMMAND + " binary exists and works", e);
        }
        return mediainfo_works && mkvalidator_works;
    }

    public static MediaInfo getMediaInfoFromFile(File file) throws IOException {
        MediaInfo mediaInfo = new MediaInfo();

        String filePath = file.getAbsolutePath();
        mediaInfo.setActFileSize(file.length());

        Pattern pSection = Pattern.compile("^(General|Video|Audio|Text|Chapters)( #\\d+)?$", Pattern.CASE_INSENSITIVE);
        Pattern pValue = Pattern.compile("^(.*?)\\s+: (.*)$", Pattern.CASE_INSENSITIVE);

        ProcessBuilder builder = new ProcessBuilder(MEDIAINFO_COMMAND, filePath);
        Process pDD = builder.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(pDD.getInputStream()));

        HashMap<String, String> props = new HashMap<>();
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
                    } else if (section.toLowerCase().startsWith("general")) {
                        mediaInfo.setGeneralInfo(props);
                    }
                    section = line;
                    props = new HashMap<>();
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
        } else if (section.toLowerCase().startsWith("general")) {
            mediaInfo.setGeneralInfo(props);
        }

        stdout.close();
        int exitValue;
        try {
            exitValue = pDD.waitFor();
            if (exitValue != 0) {
                logger.error("ERROR: mediainfo process failed with exit code {}", exitValue);
                return null;
            }
        } catch (InterruptedException e) {
            logger.error("ERROR: mediainfo process interrupted");
        }
        pDD.destroy();

        String realFormat;
        if (mediaInfo.getGeneralInfo() != null && mediaInfo.getGeneralInfo().get("Format") != null) {
            realFormat = getRealFormat(mediaInfo.getGeneralInfo().get("Format"));
        } else {
            realFormat = getFileExtension(filePath);
        }

        // Calculate valid filesize for mp4, mkv and avi
        switch (realFormat) {
            case "MP4" -> {
                IsoFile isoFile = new IsoFile(filePath);
                if (isoFile.getSize() != mediaInfo.getActFileSize()) {
                    mediaInfo.setSampleOk(false);
                    mediaInfo.setCalFileSize(isoFile.getSize());
                }
            }
            case "MKV" -> {
                builder = new ProcessBuilder(MKVALIDATOR_COMMAND, "--quiet", "--no-warn", filePath);
                builder.redirectErrorStream(true);
                pDD = builder.start();
                stdout = new BufferedReader(new InputStreamReader(pDD.getInputStream()));
                while ((line = stdout.readLine()) != null) {
                    if (line.contains("ERR042")) {
                        mediaInfo.setSampleOk(false);
                        for (String word : line.split("\\s")) {
                            if (word.matches("^\\d+$")) {
                                mediaInfo.setCalFileSize(Long.parseLong(word));
                                break;
                            }
                        }
                    }
                }
                stdout.close();
                try {
                    pDD.waitFor();
                } catch (InterruptedException e) {
                    logger.error("ERROR: {}} process interrupted", MKVALIDATOR_COMMAND);
                }
                pDD.destroy();
            }
            case "AVI" -> {
                if (mediaInfo.getGeneralInfo() != null && mediaInfo.getGeneralInfo().get("File size") != null &&
                        !mediaInfo.getVideoInfos().isEmpty() && mediaInfo.getVideoInfos().get(0) != null &&
                        mediaInfo.getVideoInfos().get(0).containsKey("Stream size") &&
                        !mediaInfo.getAudioInfos().isEmpty() && mediaInfo.getAudioInfos().get(0) != null &&
                        mediaInfo.getAudioInfos().get(0).containsKey("Stream size")) {
                    String[] videoStream = (mediaInfo.getVideoInfos().get(0).get("Stream size")).split("\\s");
                    String[] audioStream = (mediaInfo.getAudioInfos().get(0).get("Stream size")).split("\\s");
                    long videoStreamSize = 0L;
                    long audioStreamSize = 0L;
                    long fileSizeFromMediainfo = Bytes.parseBytes(mediaInfo.getGeneralInfo().get("File size").replaceAll("\\s", ""));
                    if (videoStream.length >= 2) {
                        videoStreamSize = Bytes.parseBytes(videoStream[0] + videoStream[1]);
                    }
                    if (audioStream.length >= 2) {
                        audioStreamSize = Bytes.parseBytes(audioStream[0] + audioStream[1]);
                    }
                    if (videoStreamSize + audioStreamSize > fileSizeFromMediainfo) {
                        mediaInfo.setSampleOk(false);
                        mediaInfo.setCalFileSize(videoStreamSize + audioStreamSize);
                    }
                } else {
                    // No audio or video stream available/readable
                    mediaInfo.setSampleOk(false);
                    mediaInfo.setCalFileSize(0L);
                }
            }
        }

        // Check container format type
        if (filePath.toUpperCase().endsWith(".MP4")) {
            if (!realFormat.equals("MP4")) {
                mediaInfo.setRealFormat(realFormat);
                mediaInfo.setUploadedFormat("MP4");
                mediaInfo.setSampleOk(false);
            }
        } else if (filePath.toUpperCase().endsWith(".MKV")) {
            if (!realFormat.equals("MKV")) {
                mediaInfo.setRealFormat(realFormat);
                mediaInfo.setUploadedFormat("MKV");
                mediaInfo.setSampleOk(false);
            }
        } else if (filePath.toUpperCase().endsWith(".AVI")) {
            if (!realFormat.equals("AVI")) {
                mediaInfo.setRealFormat(realFormat);
                mediaInfo.setUploadedFormat("AVI");
                mediaInfo.setSampleOk(false);
            }
        }

        return mediaInfo;
    }

    private static String getFileExtension(String fileName) {
        if (fileName.indexOf('.') == -1) {
            // No extension on file
            return null;
        } else {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
        }
    }

    private static String getRealFormat(String format) {
        String realFormat = format;
        if (format.equals("MPEG-4")) {
            realFormat = "MP4";
        } else if (format.equals("Matroska")) {
            realFormat = "MKV";
        }
        return realFormat;
    }

    public String getFileName() {
        return _fileName;
    }

    public void setFileName(String fileName) {
        _fileName = fileName;
    }

    public long getChecksum() {
        return _checksum;
    }

    public void setChecksum(long value) {
        _checksum = value;
    }

    public boolean getSampleOk() {
        return _sampleOk;
    }

    public void setSampleOk(boolean value) {
        _sampleOk = value;
    }

    public long getActFileSize() {
        return _actFileSize;
    }

    public void setActFileSize(long value) {
        _actFileSize = value;
    }

    public long getCalFileSize() {
        return _calFileSize;
    }

    public void setCalFileSize(long value) {
        _calFileSize = value;
    }

    public String getRealFormat() {
        return _realFormat;
    }

    public void setRealFormat(String realFormat) {
        _realFormat = realFormat;
    }

    public String getUploadedFormat() {
        return _uploadedFormat;
    }

    public void setUploadedFormat(String uploadedFormat) {
        _uploadedFormat = uploadedFormat;
    }

    public HashMap<String, String> getGeneralInfo() {
        return _generalInfo;
    }

    public void setGeneralInfo(HashMap<String, String> generalInfo) {
        _generalInfo = generalInfo;
    }

    public void addVideoInfo(HashMap<String, String> videoInfo) {
        _videoInfos.add(videoInfo);
    }

    public ArrayList<HashMap<String, String>> getVideoInfos() {
        return _videoInfos;
    }

    public void setVideoInfos(ArrayList<HashMap<String, String>> videoInfos) {
        _videoInfos = videoInfos;
    }

    public void addAudioInfo(HashMap<String, String> audioInfo) {
        _audioInfos.add(audioInfo);
    }

    public ArrayList<HashMap<String, String>> getAudioInfos() {
        return _audioInfos;
    }

    public void setAudioInfos(ArrayList<HashMap<String, String>> audioInfos) {
        _audioInfos = audioInfos;
    }

    public void addSubInfo(HashMap<String, String> subInfo) {
        _subInfos.add(subInfo);
    }

    public ArrayList<HashMap<String, String>> getSubInfos() {
        return _subInfos;
    }

    public void setSubInfos(ArrayList<HashMap<String, String>> subInfos) {
        _subInfos = subInfos;
    }
}
