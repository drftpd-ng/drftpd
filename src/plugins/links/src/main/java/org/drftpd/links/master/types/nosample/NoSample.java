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
package org.drftpd.links.master.types.nosample;

import org.drftpd.links.master.LinkType;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Link type for directories that have files but no Sample subdirectory.
 * <p>
 * Optional config key {@code x.samplename}: regex matched against the whole
 * name of a subdirectory to decide whether it is a Sample dir
 * (default {@code (?i)sample}).
 */
public class NoSample extends LinkType {

    static final String DEFAULT_SAMPLE_NAME = "(?i)sample";

    private final Pattern _samplePattern;

    public NoSample(Properties p, int confnum, String type) {
        super(p, confnum, type);
        String sampleName = p.getProperty(confnum + ".samplename", DEFAULT_SAMPLE_NAME).trim();
        if (sampleName.isEmpty()) {
            sampleName = DEFAULT_SAMPLE_NAME;
        }
        _samplePattern = Pattern.compile(sampleName);
    }

    /** True if the given directory name is a Sample dir name. */
    public boolean isSampleDirName(String name) {
        return isSampleDirName(name, _samplePattern);
    }

    static boolean isSampleDirName(String name, Pattern samplePattern) {
        return samplePattern.matcher(name).matches();
    }

    static boolean hasSampleDir(Collection<String> subDirNames, Pattern samplePattern) {
        for (String name : subDirNames) {
            if (isSampleDirName(name, samplePattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decision logic: a link is wanted only for a non-Sample dir that
     * contains files and has no Sample subdirectory.
     */
    static boolean wantsLink(String dirName, Collection<String> subDirNames, int fileCount, Pattern samplePattern) {
        return !isSampleDirName(dirName, samplePattern)
                && fileCount > 0
                && !hasSampleDir(subDirNames, samplePattern);
    }

    private List<String> subDirNames(DirectoryHandle dir) throws FileNotFoundException {
        List<String> names = new ArrayList<>();
        for (DirectoryHandle sub : dir.getDirectoriesUnchecked()) {
            names.add(sub.getName());
        }
        return names;
    }

    /*
     * Creates the link for targetDir (called when files arrive in it), unless
     * it is itself a Sample dir or already has a Sample subdir.
     */
    @Override
    public void doCreateLink(DirectoryHandle targetDir) {
        if (isSampleDirName(targetDir.getName())) {
            return;
        }
        try {
            if (hasSampleDir(subDirNames(targetDir), _samplePattern)) {
                doDeleteLink(targetDir);
                return;
            }
        } catch (FileNotFoundException e) {
            // targetDir no longer exists
            return;
        }
        createLink(targetDir, targetDir.getPath(), targetDir.getName());
    }

    @Override
    public void doDeleteLink(DirectoryHandle targetDir) {
        deleteLink(targetDir, targetDir.getPath(), targetDir.getName());
    }

    /*
     * Creates the link if the dir has files and no Sample subdir, otherwise
     * makes sure no link exists.
     */
    @Override
    public void doFixLink(DirectoryHandle targetDir) {
        try {
            int fileCount = 0;
            for (FileHandle ignored : targetDir.getFilesUnchecked()) {
                fileCount++;
            }
            if (wantsLink(targetDir.getName(), subDirNames(targetDir), fileCount, _samplePattern)) {
                createLink(targetDir, targetDir.getPath(), targetDir.getName());
            } else {
                doDeleteLink(targetDir);
            }
        } catch (FileNotFoundException e) {
            // targetDir no longer exists - Ignore
        }
    }
}
