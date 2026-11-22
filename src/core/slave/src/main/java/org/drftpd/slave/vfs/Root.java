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
package org.drftpd.slave.vfs;

import org.drftpd.common.io.PhysicalFile;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/**
 * @author mog
 * @version $Id$
 */
public class Root {
    private static final Logger logger = LogManager.getLogger(Root.class);

    private static final String separator = "/";
    private final PhysicalFile _rootFile;
    private long _lastModified;

    public Root(String root) throws IOException {
        _rootFile = new PhysicalFile(new PhysicalFile(root).getCanonicalFile());
        _lastModified = getFile().lastModified();
    }

    public PhysicalFile getFile() {
        return _rootFile;
    }

    public String getPath() {
        return _rootFile.getPath();
    }

    public long lastModified() {
        return _lastModified;
    }

    public void touch() {
        getFile().setLastModified(_lastModified = System.currentTimeMillis());
    }

    public String toString() {
        return "[root=" + getPath() + "]";
    }

    public long getDiskSpaceAvailable() {
        return getFile().getUsableSpace();
    }

    public long getDiskSpaceCapacity() {
        return getFile().getTotalSpace();
    }

    public PhysicalFile getFile(String path) {
        return new PhysicalFile(getPath() + separator + path);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    //@Override
    public boolean equals(Object arg0) {
        if (!(arg0 instanceof Root)) {
            return false;
        }
        Root r = (Root) arg0;
        return r.getPath().equals(getPath());
    }

    public void getAllInodes(Map<String, DirectoryContent> inodeTree, BooleanSupplier cancelled)
        throws IllegalArgumentException, IOException
    {
        if (inodeTree == null) {
            throw new IllegalArgumentException();
        }
        var walker = new FileTreeWalker(inodeTree, cancelled);
        walker.Walk(getPath());
    }

    public class DirectoryContent {
        public BasicFileAttributes dirAttributes;
        public Map<String, BasicFileAttributes> inodes;

        public DirectoryContent(BasicFileAttributes dirAttributes, Map<String, BasicFileAttributes> inodes) {
            this.dirAttributes = dirAttributes;
            this.inodes = inodes;
        }
    }

    private class FileTreeWalker extends SimpleFileVisitor<Path> {
        private final Map<String, DirectoryContent> _inodeTree;
        private final BooleanSupplier _cancelled;

        public FileTreeWalker(Map<String, DirectoryContent> inodeTree, BooleanSupplier cancelled) {
            _inodeTree = inodeTree;
            _cancelled = cancelled;
        }

        private Path rootPath = null;
        private String rootPathString = null;

        public void Walk(String path) throws IOException {
            rootPath = Paths.get(path).toRealPath();
            rootPathString = rootPath.toString();
            if (!rootPathString.endsWith(File.separator)) {
                rootPathString = rootPathString + File.separator;
            }

            Files.walkFileTree(rootPath, this);
        }

        public String GetRootRelativePathString(Path path) throws IllegalArgumentException {
            Path normalizedPath = path.normalize();
            if (!normalizedPath.startsWith(rootPath)) {
                throw new IllegalArgumentException(String.format("Path {} is not part of rootPath {}", path, rootPath));
            }
            return normalizedPath.toString().substring(rootPathString.length() - File.separator.length());
        }

        private void AddDir(Path dir, BasicFileAttributes attrs) throws IOException {
            String rootRelativePath = "";
            try {
                rootRelativePath = GetRootRelativePathString(dir);
            }
            catch (IllegalArgumentException e) {
                return;
            }

            // Add parent first
            AddDir(dir.getParent(), null);

            if ((rootRelativePath != "") && (rootRelativePath != "/")) {
                // Master expects subdirectories to appear in file list
                AddFile(dir, null);
            }

            if (attrs == null) {
                try {
                    attrs = Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                }
                catch (IOException e) {
                    logger.fatal("Could not read attributes for directory {}", dir, e);
                    throw e;
                }
            }

            var value = _inodeTree.get(rootRelativePath);
            if (value == null) {
                _inodeTree.put(rootRelativePath, new DirectoryContent(attrs, new TreeMap<String, BasicFileAttributes>()));
            }
            else if (attrs.lastModifiedTime().compareTo(value.dirAttributes.lastModifiedTime()) > 0) {
                // keep newest modified time in case directory exists in multiple roots
                value.dirAttributes = attrs;
                _inodeTree.put(rootRelativePath, value);
            }
        }

        private void AddFile(Path path, BasicFileAttributes attrs) throws IOException {
            if (attrs == null) {
                try {
                    attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                }
                catch (IOException e) {
                    logger.fatal("Could not read attributes for directory {}", path, e);
                    throw e;
                }
            }

            String rootRelativeParentPath = GetRootRelativePathString(path.getParent());

            AddDir(path.getParent(), null);

            var dirContents = _inodeTree.get(rootRelativeParentPath);
            if (dirContents == null) {
                throw new IOException("parent path should exist in hashmap");
            }
            else {
                var inodeAttributes = dirContents.inodes.get(path.getFileName().toString());
                if ((inodeAttributes == null) || (attrs.isDirectory() && attrs.lastModifiedTime().compareTo(inodeAttributes.lastModifiedTime()) > 0)) {
                    // keep newest modified time in case directory exists in multiple roots
                    dirContents.inodes.put(path.getFileName().toString(), attrs);
                    _inodeTree.put(rootRelativeParentPath, dirContents);
                } else if (attrs.isRegularFile()) {
                    String errorMessage = String.format("File found in multiple roots: {}", path.toString());
                    logger.fatal(errorMessage);
                    throw new IOException(errorMessage);
                }
            }
        }


        @Override
        public FileVisitResult preVisitDirectory(
            Path dir,
            BasicFileAttributes attrs
        ) throws IOException
        {
            try {
                if (_cancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }

                AddDir(dir, attrs);

                return FileVisitResult.CONTINUE;
            }
            catch (IOException e) {
                logger.fatal("Error processing {}", dir, e);
                throw e;
                //return FileVisitResult.TERMINATE;

            }
            catch (IllegalArgumentException e) {
                logger.fatal("Error getting root relative path for {}", dir, e);
                throw e;
                //return FileVisitResult.TERMINATE;
            }
        }

        @Override
        public FileVisitResult visitFile(
            Path file,
            BasicFileAttributes attrs) throws IOException
        {
            try {
                if (_cancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }

                if (attrs.isSymbolicLink()) {
                    logger.warn("You have a symbolic link {} -- these are ignored by drftpd", file);
                }
                else if (attrs.isRegularFile()) {
                    AddFile(file, attrs);
                }
                else if (attrs.isDirectory()) {
                    // directory should have been added in preVisitDirectory, adding in case preVisitDirectory missed attributes
                    AddDir(file, attrs);
                }

                return FileVisitResult.CONTINUE;
            }
            catch (IOException e) {
                logger.fatal("Error processing {}", file, e);
                throw e;
            }
            catch (IllegalArgumentException e) {
                logger.fatal("Error getting root relative path for {}", file, e);
                throw e;
                //return FileVisitResult.TERMINATE;
            }
        }

        @Override
        public FileVisitResult postVisitDirectory(
            Path path,
            IOException exc
        ) throws IOException
        {
            if (exc != null) {
                logger.fatal("Failed to visit directory: {}", path, exc);
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(
            Path path,
            IOException exc
        ) throws IOException
        {
            logger.fatal("Failed to visit file: {}", path, exc);
            throw exc;
            //return FileVisitResult.CONTINUE;
        }
    }
}
