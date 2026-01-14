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

    // Factory for creating IsoFile instances (for testing)
    public interface IsoFileFactory {
        IsoFile create(String path) throws IOException;
    }

    // Default implementation uses the real constructor
    private static IsoFileFactory _isoFileFactory = IsoFile::new;

    // Setter for testing
    protected static void setIsoFileFactory(IsoFileFactory factory) {
        _isoFileFactory = factory;
    }

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
    public MediaInfo() {
    }

    public static boolean hasWorkingMediaInfo() {
        boolean mediainfo_works = false;
        try {
            ProcessBuilder builder = new ProcessBuilder(MEDIAINFO_COMMAND, "--version");
            Process proc = builder.start();
            int status = proc.waitFor();
            if (status != 0) {
                throw new RuntimeException(
                        "Exist code of " + MEDIAINFO_COMMAND + " --version yielded exit code " + status);
            }
            mediainfo_works = true;
        } catch (Exception e) {
            logger.fatal("Something went wrong trying to see if " + MEDIAINFO_COMMAND + " binary exists and works", e);
        }
        return mediainfo_works;
    }

    public static MediaInfo getMediaInfoFromFile(File file) throws IOException {
        MediaInfo mediaInfo = new MediaInfo();

        String filePath = file.getAbsolutePath();
        mediaInfo.setActFileSize(file.length());

        Pattern pSection = Pattern.compile("^(General|Video|Audio|Text|Chapters|Conformance errors)( #\\d+)?$",
                Pattern.CASE_INSENSITIVE);
        Pattern pValue = Pattern.compile("^(.*?)\\s+: (.*)$", Pattern.CASE_INSENSITIVE);
        Pattern pExpectedSize = Pattern.compile("expected size at least (\\d+)");

        logger.debug("Running mediainfo on file: {} (size: {})", filePath, file.length());
        ProcessBuilder builder = new ProcessBuilder(MEDIAINFO_COMMAND, filePath);
        Process pDD = builder.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(pDD.getInputStream()));

        HashMap<String, String> props = new HashMap<>();
        String section = "";
        String line;
        ArrayList<String> generalComplianceList = new ArrayList<>();

        while ((line = stdout.readLine()) != null) {
            if (!line.trim().equals("")) {
                Matcher m = pSection.matcher(line);
                if (m.find() && !line.equalsIgnoreCase(section)) {
                    logger.debug("Section change: '{}' -> '{}'", section, line);
                    if (section.toLowerCase().startsWith("video")) {
                        logger.debug("Adding video info: {}", props);
                        mediaInfo.addVideoInfo(props);
                    } else if (section.toLowerCase().startsWith("audio")) {
                        logger.debug("Adding audio info: {}", props);
                        mediaInfo.addAudioInfo(props);
                    } else if (section.toLowerCase().startsWith("text")) {
                        logger.debug("Adding sub info: {}", props);
                        mediaInfo.addSubInfo(props);
                    } else if (section.toLowerCase().startsWith("general")) {
                        logger.debug("Setting general info: {}", props);
                        mediaInfo.setGeneralInfo(props);
                    }
                    section = line;
                    props = new HashMap<>();
                }
                m = pValue.matcher(line);
                if (m.find()) {
                    String key = m.group(1);
                    String value = m.group(2);
                    props.put(key, value);
                    if (section.toLowerCase().startsWith("general") && key.equals("General compliance")) {
                        logger.debug("Found General compliance: {}", value);
                        generalComplianceList.add(value);
                    }
                }
            }
        }

        // Last one
        if (section.toLowerCase().startsWith("video")) {
            logger.debug("Adding video info (last): {}", props);
            mediaInfo.addVideoInfo(props);
        } else if (section.toLowerCase().startsWith("audio")) {
            logger.debug("Adding audio info (last): {}", props);
            mediaInfo.addAudioInfo(props);
        } else if (section.toLowerCase().startsWith("text")) {
            logger.debug("Adding sub info (last): {}", props);
            mediaInfo.addSubInfo(props);
        } else if (section.toLowerCase().startsWith("general")) {
            logger.debug("Setting general info (last): {}", props);
            mediaInfo.setGeneralInfo(props);
        }

        stdout.close();
        int exitValue;
        try {
            exitValue = pDD.waitFor();
            if (exitValue != 0) {
                logger.error("ERROR: mediainfo process failed with exit code {} for file {}", exitValue, filePath);
                return null;
            }
        } catch (InterruptedException e) {
            logger.error("ERROR: mediainfo process interrupted for file {}", filePath);
        }
        pDD.destroy();

        String realFormat;
        if (mediaInfo.getGeneralInfo() != null && mediaInfo.getGeneralInfo().get("Format") != null) {
            realFormat = getRealFormat(mediaInfo.getGeneralInfo().get("Format"));
        } else {
            realFormat = getFileExtension(filePath);
        }
        logger.debug("Detected real format: {} for file {}", realFormat, filePath);

        // Calculate valid filesize for mp4, mkv and avi
        switch (realFormat) {
            case "MP4" -> {
                try {
                    try (IsoFile isoFile = _isoFileFactory.create(filePath)) {
                        if (isoFile.getSize() != mediaInfo.getActFileSize()) {
                            logger.warn("MP4: IsoFile size {} != actual file size {} for file {}", isoFile.getSize(),
                                    mediaInfo.getActFileSize(), filePath);
                            mediaInfo.setSampleOk(false);
                            mediaInfo.setCalFileSize(isoFile.getSize());
                        }
                    }
                } catch (RuntimeException | IOException e) {
                    logger.warn("MP4: Failed to parse IsoFile structure for file {}: {}", filePath, e.getMessage());
                    mediaInfo.setSampleOk(false);
                }
            }
            case "MKV" -> {
                HashMap<String, String> generalInfo = mediaInfo.getGeneralInfo();
                boolean incomplete = false;
                // Check all 'General compliance' entries from parsed output
                for (String compliance : generalComplianceList) {
                    if (compliance.contains("File size") && compliance.contains("less than expected size")) {
                        logger.warn("MKV: General compliance indicates file is incomplete: {}", compliance);
                        incomplete = true;
                        Matcher m = pExpectedSize.matcher(compliance);
                        if (m.find()) {
                            try {
                                mediaInfo.setCalFileSize(Long.parseLong(m.group(1)));
                                logger.warn("MKV: Set calculated file size to {} for file {}", m.group(1), filePath);
                            } catch (NumberFormatException ignore) {
                                logger.error("MKV: Failed to parse expected size from compliance string: {}",
                                        compliance);
                            }
                        }
                    }
                }
                if (generalInfo != null) {
                    // Only check conformance errors if the field is present
                    String conformanceErrors = generalInfo.get("Conformance errors");
                    if (conformanceErrors != null && !conformanceErrors.equals("0")) {
                        logger.warn("MKV: Conformance errors present: {}", conformanceErrors);
                        incomplete = true;
                    }
                }
                if (incomplete) {
                    logger.warn("MKV: Marking sample as not OK for file {}", filePath);
                    mediaInfo.setSampleOk(false);
                    // Delete the invalid file immediately on the slave
                    if (file.delete()) {
                        logger.warn("MKV: Deleted invalid file on slave: {}", filePath);
                    } else {
                        logger.error("MKV: Failed to delete invalid file on slave: {}", filePath);
                    }
                }
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
                    long fileSizeFromMediainfo = Bytes
                            .parseBytes(mediaInfo.getGeneralInfo().get("File size").replaceAll("\\s", ""));
                    if (videoStream.length >= 2) {
                        videoStreamSize = Bytes.parseBytes(videoStream[0] + videoStream[1]);
                    }
                    if (audioStream.length >= 2) {
                        audioStreamSize = Bytes.parseBytes(audioStream[0] + audioStream[1]);
                    }
                    if (videoStreamSize + audioStreamSize > fileSizeFromMediainfo) {
                        logger.warn("AVI: Video+Audio stream size {} > file size {} for file {}",
                                (videoStreamSize + audioStreamSize), fileSizeFromMediainfo, filePath);
                        mediaInfo.setSampleOk(false);
                        mediaInfo.setCalFileSize(videoStreamSize + audioStreamSize);
                    }
                } else {
                    // No audio or video stream available/readable
                    logger.warn("AVI: No audio or video stream available/readable for file {}", filePath);
                    mediaInfo.setSampleOk(false);
                    mediaInfo.setCalFileSize(0L);
                }
            }
        }

        // Check container format type
        if (filePath.toUpperCase().endsWith(".MP4")) {
            if (!realFormat.equals("MP4")) {
                logger.warn("Container extension is MP4 but detected format is {} for file {}", realFormat, filePath);
                mediaInfo.setRealFormat(realFormat);
                mediaInfo.setUploadedFormat("MP4");
                mediaInfo.setSampleOk(false);
            }
        } else if (filePath.toUpperCase().endsWith(".MKV")) {
            if (!realFormat.equals("MKV")) {
                logger.warn("Container extension is MKV but detected format is {} for file {}", realFormat, filePath);
                mediaInfo.setRealFormat(realFormat);
                mediaInfo.setUploadedFormat("MKV");
                mediaInfo.setSampleOk(false);
            }
        } else if (filePath.toUpperCase().endsWith(".AVI")) {
            if (!realFormat.equals("AVI")) {
                logger.warn("Container extension is AVI but detected format is {} for file {}", realFormat, filePath);
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
