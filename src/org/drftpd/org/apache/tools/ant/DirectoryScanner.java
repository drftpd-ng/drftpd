/*
 * Copyright  2000-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.drftpd.org.apache.tools.ant;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.drftpd.org.apache.tools.ant.types.selectors.FileSelector;
import org.drftpd.org.apache.tools.ant.types.selectors.SelectorUtils;
import org.drftpd.org.apache.tools.ant.util.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * Class for scanning a directory for files/directories which match certain
 * criteria.
 * <p>
 * These criteria consist of selectors and patterns which have been specified.
 * With the selectors you can select which files you want to have included.
 * Files which are not selected are excluded. With patterns you can include
 * or exclude files based on their filename.
 * <p>
 * The idea is simple. A given directory is recursively scanned for all files
 * and directories. Each file/directory is matched against a set of selectors,
 * including special support for matching against filenames with include and
 * and exclude patterns. Only files/directories which match at least one
 * pattern of the include pattern list or other file selector, and don't match
 * any pattern of the exclude pattern list or fail to match against a required
 * selector will be placed in the list of files/directories found.
 * <p>
 * When no list of include patterns is supplied, "**" will be used, which
 * means that everything will be matched. When no list of exclude patterns is
 * supplied, an empty list is used, such that nothing will be excluded. When
 * no selectors are supplied, none are applied.
 * <p>
 * The filename pattern matching is done as follows:
 * The name to be matched is split up in path segments. A path segment is the
 * name of a directory or file, which is bounded by
 * <code>File.separator</code> ('/' under UNIX, '\' under Windows).
 * For example, "abc/def/ghi/xyz.java" is split up in the segments "abc",
 * "def","ghi" and "xyz.java".
 * The same is done for the pattern against which should be matched.
 * <p>
 * The segments of the name and the pattern are then matched against each
 * other. When '**' is used for a path segment in the pattern, it matches
 * zero or more path segments of the name.
 * <p>
 * There is a special case regarding the use of <code>File.separator</code>s
 * at the beginning of the pattern and the string to match:<br>
 * When a pattern starts with a <code>File.separator</code>, the string
 * to match must also start with a <code>File.separator</code>.
 * When a pattern does not start with a <code>File.separator</code>, the
 * string to match may not start with a <code>File.separator</code>.
 * When one of these rules is not obeyed, the string will not
 * match.
 * <p>
 * When a name path segment is matched against a pattern path segment, the
 * following special characters can be used:<br>
 * '*' matches zero or more characters<br>
 * '?' matches one character.
 * <p>
 * Examples:
 * <p>
 * "**\*.class" matches all .class files/dirs in a directory tree.
 * <p>
 * "test\a??.java" matches all files/dirs which start with an 'a', then two
 * more characters and then ".java", in a directory called test.
 * <p>
 * "**" matches everything in a directory tree.
 * <p>
 * "**\test\**\XYZ*" matches all files/dirs which start with "XYZ" and where
 * there is a parent directory called test (e.g. "abc\test\def\ghi\XYZ123").
 * <p>
 * Case sensitivity may be turned off if necessary. By default, it is
 * turned on.
 * <p>
 * Example of usage:
 * <pre>
 *   String[] includes = {"**\\*.class"};
 *   String[] excludes = {"modules\\*\\**"};
 *   ds.setIncludes(includes);
 *   ds.setExcludes(excludes);
 *   ds.setBasedir(new File("test"));
 *   ds.setCaseSensitive(true);
 *   ds.scan();
 *
 *   System.out.println("FILES:");
 *   String[] files = ds.getIncludedFiles();
 *   for (int i = 0; i < files.length; i++) {
 *     System.out.println(files[i]);
 *   }
 * </pre>
 * This will scan a directory called test for .class files, but excludes all
 * files in all proper subdirectories of a directory called "modules"
 *
 */
public class DirectoryScanner
       /*implements FileScanner, SelectorScanner, ResourceFactory*/ {

    /** Is OpenVMS the operating system we're running on? */
    //private static final boolean ON_VMS = Os.isFamily("openvms");

    /**
     * Patterns which should be excluded by default.
     *
     * <p>Note that you can now add patterns to the list of default
     * excludes.  Added patterns will not become part of this array
     * that has only been kept around for backwards compatibility
     * reasons.</p>
     *
     * @deprecated use the {@link #getDefaultExcludes
     * getDefaultExcludes} method instead.
     */
//    protected static final String[] DEFAULTEXCLUDES = {
//        // Miscellaneous typical temporary files
//        "**/*~",
//        "**/#*#",
//        "**/.#*",
//        "**/%*%",
//        "**/._*",
//
//        // CVS
//        "**/CVS",
//        "**/CVS/**",
//        "**/.cvsignore",
//
//        // SCCS
//        "**/SCCS",
//        "**/SCCS/**",
//
//        // Visual SourceSafe
//        "**/vssver.scc",
//
//        // Subversion
//        "**/.svn",
//        "**/.svn/**",
//
//        // Mac
//        "**/.DS_Store"
//    };

    /**
     * Patterns which should be excluded by default.
     *
     * @see #addDefaultExcludes()
     */
//    private static Vector defaultExcludes = new Vector();
//    static {
//        resetDefaultExcludes();
//    }

    /** The base directory to be scanned. */
    protected LinkedRemoteFileInterface _basedir;

    /** The patterns for the files to be included. */
    protected String[] _includes;

    /** The patterns for the files to be excluded. */
    protected String[] _excludes;

    /** Selectors that will filter which files are in our candidate list. */
    protected FileSelector[] _selectors = null;

    /** The files which matched at least one include and no excludes
     *  and were selected.
     */
    protected Vector<String> filesIncluded;

    /** The files which did not match any includes or selectors. */
    protected Vector<String> filesNotIncluded;

    /**
     * The files which matched at least one include and at least
     * one exclude.
     */
    protected Vector<String> filesExcluded;

    /** The directories which matched at least one include and no excludes
     *  and were selected.
     */
    protected Vector<String> dirsIncluded;

    /** The directories which were found and did not match any includes. */
    protected Vector<String> dirsNotIncluded;

    /**
     * The directories which matched at least one include and at least one
     * exclude.
     */
    protected Vector<String> dirsExcluded;

    /** The files which matched at least one include and no excludes and
     *  which a selector discarded.
     */
    protected Vector<String> filesDeselected;

    /** The directories which matched at least one include and no excludes
     *  but which a selector discarded.
     */
    protected Vector<String> dirsDeselected;

    /** Whether or not our results were built by a slow scan. */
    protected boolean haveSlowResults = false;

    /**
     * Whether or not the file system should be treated as a case sensitive
     * one.
     */
    protected static final boolean _isCaseSensitive = false;

    /**
     * Whether or not symbolic links should be followed.
     *
     * @since Ant 1.5
     */
    private boolean _followSymlinks = true;

    /** Helper. */
    private static final FileUtils FILE_UTILS = FileUtils.newFileUtils();

    /** Whether or not everything tested so far has been included. */
    protected boolean everythingIncluded = true;

	private static final Logger logger = Logger.getLogger(DirectoryScanner.class);

    /**
     * Sole constructor.
     */
    public DirectoryScanner() {
    }

    /**
     * Tests whether or not a given path matches the start of a given
     * pattern up to the first "**".
     * <p>
     * This is not a general purpose test and should only be used if you
     * can live with false positives. For example, <code>pattern=**\a</code>
     * and <code>str=b</code> will yield <code>true</code>.
     *
     * @param pattern The pattern to match against. Must not be
     *                <code>null</code>.
     * @param str     The path to match, as a String. Must not be
     *                <code>null</code>.
     *
     * @return whether or not a given path matches the start of a given
     * pattern up to the first "**".
     */
    protected static boolean matchPatternStart(String pattern, String str) {
        return SelectorUtils.matchPatternStart(pattern, str);
    }
    /**
     * Tests whether or not a given path matches the start of a given
     * pattern up to the first "**".
     * <p>
     * This is not a general purpose test and should only be used if you
     * can live with false positives. For example, <code>pattern=**\a</code>
     * and <code>str=b</code> will yield <code>true</code>.
     *
     * @param pattern The pattern to match against. Must not be
     *                <code>null</code>.
     * @param str     The path to match, as a String. Must not be
     *                <code>null</code>.
     * @param isCaseSensitive Whether or not matching should be performed
     *                        case sensitively.
     *
     * @return whether or not a given path matches the start of a given
     * pattern up to the first "**".
     */
    protected static boolean matchPatternStart(String pattern, String str,
                                               boolean isCaseSensitive) {
        return SelectorUtils.matchPatternStart(pattern, str, isCaseSensitive);
    }

    /**
     * Tests whether or not a given path matches a given pattern.
     *
     * @param pattern The pattern to match against. Must not be
     *                <code>null</code>.
     * @param str     The path to match, as a String. Must not be
     *                <code>null</code>.
     *
     * @return <code>true</code> if the pattern matches against the string,
     *         or <code>false</code> otherwise.
     */
    protected static boolean matchPath(String pattern, String str) {
        return SelectorUtils.matchPath(pattern, str);
    }

    /**
     * Tests whether or not a given path matches a given pattern.
     *
     * @param pattern The pattern to match against. Must not be
     *                <code>null</code>.
     * @param str     The path to match, as a String. Must not be
     *                <code>null</code>.
     * @param isCaseSensitive Whether or not matching should be performed
     *                        case sensitively.
     *
     * @return <code>true</code> if the pattern matches against the string,
     *         or <code>false</code> otherwise.
     */
    protected static boolean matchPath(String pattern, String str,
                                       boolean isCaseSensitive) {
        return SelectorUtils.matchPath(pattern, str, isCaseSensitive);
    }

    /**
     * Tests whether or not a string matches against a pattern.
     * The pattern may contain two special characters:<br>
     * '*' means zero or more characters<br>
     * '?' means one and only one character
     *
     * @param pattern The pattern to match against.
     *                Must not be <code>null</code>.
     * @param str     The string which must be matched against the pattern.
     *                Must not be <code>null</code>.
     *
     * @return <code>true</code> if the string matches against the pattern,
     *         or <code>false</code> otherwise.
     */
    public static boolean match(String pattern, String str) {
        return SelectorUtils.match(pattern, str);
    }

    /**
     * Tests whether or not a string matches against a pattern.
     * The pattern may contain two special characters:<br>
     * '*' means zero or more characters<br>
     * '?' means one and only one character
     *
     * @param pattern The pattern to match against.
     *                Must not be <code>null</code>.
     * @param str     The string which must be matched against the pattern.
     *                Must not be <code>null</code>.
     * @param isCaseSensitive Whether or not matching should be performed
     *                        case sensitively.
     *
     *
     * @return <code>true</code> if the string matches against the pattern,
     *         or <code>false</code> otherwise.
     */
    protected static boolean match(String pattern, String str,
                                   boolean isCaseSensitive) {
        return SelectorUtils.match(pattern, str, isCaseSensitive);
    }


    /**
     * Get the list of patterns that should be excluded by default.
     *
     * @return An array of <code>String</code> based on the current
     *         contents of the <code>defaultExcludes</code>
     *         <code>Vector</code>.
     *
     * @since Ant 1.6
     */
//    public static String[] getDefaultExcludes() {
//        return (String[]) defaultExcludes.toArray(new String[defaultExcludes
//                                                             .size()]);
//    }

    /**
     * Add a pattern to the default excludes unless it is already a
     * default exclude.
     *
     * @param s   A string to add as an exclude pattern.
     * @return    <code>true</code> if the string was added
     *            <code>false</code> if it already
     *            existed.
     *
     * @since Ant 1.6
     */
//    public static boolean addDefaultExclude(String s) {
//        if (defaultExcludes.indexOf(s) == -1) {
//            defaultExcludes.add(s);
//            return true;
//        }
//        return false;
//    }

    /**
     * Remove a string if it is a default exclude.
     *
     * @param s   The string to attempt to remove.
     * @return    <code>true</code> if <code>s</code> was a default
     *            exclude (and thus was removed),
     *            <code>false</code> if <code>s</code> was not
     *            in the default excludes list to begin with
     *
     * @since Ant 1.6
     */
//    public static boolean removeDefaultExclude(String s) {
//        return defaultExcludes.remove(s);
//    }

    /**
     *  Go back to the hard wired default exclude patterns
     *
     * @since Ant 1.6
     */
//    public static void resetDefaultExcludes() {
//    defaultExcludes = new Vector();
//
//        for (int i = 0; i < DEFAULTEXCLUDES.length; i++) {
//            defaultExcludes.add(DEFAULTEXCLUDES[i]);
//        }
//    }

    /**
     * Sets the base directory to be scanned. This is the directory which is
     * scanned recursively.
     *
     * @param basedir The base directory for scanning.
     *                Should not be <code>null</code>.
     */
    public void setBasedir(LinkedRemoteFileInterface basedir) {
        _basedir = basedir;
    }

    /**
     * Returns the base directory to be scanned.
     * This is the directory which is scanned recursively.
     *
     * @return the base directory to be scanned
     */
    public LinkedRemoteFileInterface getBasedir() {
        return _basedir;
    }

    /**
     * Find out whether include exclude patterns are matched in a
     * case sensitive way
     * @return whether or not the scanning is case sensitive
     * @since ant 1.6
     */
    public boolean isCaseSensitive() {
        return _isCaseSensitive;
    }
    /**
     * Sets whether or not include and exclude patterns are matched
     * in a case sensitive way
     *
     * @param isCaseSensitive whether or not the file system should be
     *                        regarded as a case sensitive one
     */
//    public void setCaseSensitive(boolean isCaseSensitive) {
//        this.isCaseSensitive = isCaseSensitive;
//    }

    /**
     * gets whether or not a DirectoryScanner follows symbolic links
     *
     * @return flag indicating whether symbolic links should be followed
     *
     * @since ant 1.6
     */
    public boolean isFollowSymlinks() {
        return _followSymlinks;
    }

    /**
     * Sets whether or not symbolic links should be followed.
     *
     * @param followSymlinks whether or not symbolic links should be followed
     */
    public void setFollowSymlinks(boolean followSymlinks) {
        _followSymlinks = followSymlinks;
    }

    /**
     * Sets the list of include patterns to use. All '/' and '\' characters
     * are replaced by <code>File.separatorChar</code>, so the separator used
     * need not match <code>File.separatorChar</code>.
     * <p>
     * When a pattern ends with a '/' or '\', "**" is appended.
     *
     * @param includes A list of include patterns.
     *                 May be <code>null</code>, indicating that all files
     *                 should be included. If a non-<code>null</code>
     *                 list is given, all elements must be
     * non-<code>null</code>.
     */
    public void setIncludes(String[] includes) {
        if (includes == null) {
            _includes = null;
        } else {
            _includes = new String[includes.length];
            for (int i = 0; i < includes.length; i++) {
                String pattern;
                pattern = includes[i];
                if (pattern.endsWith("/")) {
                    pattern += "**";
                }
                _includes[i] = pattern;
            }
        }
    }


    /**
     * Sets the list of exclude patterns to use. All '/' and '\' characters
     * are replaced by <code>File.separatorChar</code>, so the separator used
     * need not match <code>File.separatorChar</code>.
     * <p>
     * When a pattern ends with a '/' or '\', "**" is appended.
     *
     * @param excludes A list of exclude patterns.
     *                 May be <code>null</code>, indicating that no files
     *                 should be excluded. If a non-<code>null</code> list is
     *                 given, all elements must be non-<code>null</code>.
     */
    public void setExcludes(String[] excludes) {
        if (excludes == null) {
            _excludes = null;
        } else {
            _excludes = new String[excludes.length];
            for (int i = 0; i < excludes.length; i++) {
                String pattern;
                pattern = excludes[i];
                if (pattern.endsWith("/")) {
                    pattern += "**";
                }
                _excludes[i] = pattern;
            }
        }
    }


    /**
     * Sets the selectors that will select the filelist.
     *
     * @param selectors specifies the selectors to be invoked on a scan
     */
    public void setSelectors(FileSelector[] selectors) {
        _selectors = selectors;
    }


    /**
     * Returns whether or not the scanner has included all the files or
     * directories it has come across so far.
     *
     * @return <code>true</code> if all files and directories which have
     *         been found so far have been included.
     */
    public boolean isEverythingIncluded() {
        return everythingIncluded;
    }

    /**
     * Scans the base directory for files which match at least one include
     * pattern and don't match any exclude patterns. If there are selectors
     * then the files must pass muster there, as well.
     *
     * @exception IllegalStateException if the base directory was set
     *            incorrectly (i.e. if it is <code>null</code>, doesn't exist,
     *            or isn't a directory).
     * @throws FileNotFoundException
     */
    public void scan() throws IllegalStateException, FileNotFoundException {
        if (_basedir == null) {
            throw new IllegalStateException("No basedir set");
        }
        /*
        if (!basedir.exists()) {
            throw new IllegalStateException("basedir " + basedir
                                            + " does not exist");
        }
        */
        if (!_basedir.isDirectory()) {
            throw new IllegalStateException("basedir " + _basedir
                                            + " is not a directory");
        }

        if (_includes == null) {
            // No includes supplied, so set it to 'matches all'
            _includes = new String[1];
            _includes[0] = "**";
        }
        if (_excludes == null) {
            _excludes = new String[0];
        }

        filesIncluded    = new Vector<String>();
        filesNotIncluded = new Vector<String>();
        filesExcluded    = new Vector<String>();
        filesDeselected  = new Vector<String>();
        dirsIncluded     = new Vector<String>();
        dirsNotIncluded  = new Vector<String>();
        dirsExcluded     = new Vector<String>();
        dirsDeselected   = new Vector<String>();

        if (isIncluded("")) {
            if (!isExcluded("")) {
                if (isSelected("", _basedir)) {
                    dirsIncluded.addElement("");
                } else {
                    dirsDeselected.addElement("");
                }
            } else {
                dirsExcluded.addElement("");
            }
        } else {
            dirsNotIncluded.addElement("");
        }
        checkIncludePatterns();
        clearCaches();
    }

    /**
     * this routine is actually checking all the include patterns in
     * order to avoid scanning everything under base dir
     * @throws FileNotFoundException
     * @since ant1.6
     */
    private void checkIncludePatterns() {
        Hashtable<String, String> newroots = new Hashtable<String, String>();
        // put in the newroots vector the include patterns without
        // wildcard tokens
        for (int icounter = 0; icounter < _includes.length; icounter++) {
            String newpattern =
                SelectorUtils.rtrimWildcardTokens(_includes[icounter]);
            newroots.put(newpattern, _includes[icounter]);
        }

        if (newroots.containsKey("")) {
            // we are going to scan everything anyway
            scandir(_basedir, "", true);
        } else {
            // only scan directories that can include matched files or
            // directories
            //Enumeration enum2 = newroots.keySet().iterator();

            //LinkedRemoteFileInterface canonBase = null;
            //try {
            //    canonBase = basedir.getCanonicalFile();
            //} catch (IOException ex) {
            //    throw new BuildException(ex);
            //}
            for(Map.Entry entry : newroots.entrySet()) {
            //while (enum2.hasMoreElements()) {
            //    String currentelement = (String) enum2.nextElement();
            //    String originalpattern = (String) newroots.get(currentelement);
                String currentelement = (String) entry.getKey();
                String originalpattern = (String) entry.getValue();
                LinkedRemoteFileInterface myfile;
                try {
                	myfile = _basedir.lookupFile(currentelement);
                } catch(FileNotFoundException e) {
                	continue; // does not exist, iterate next root
                }
                currentelement = FileUtils.removeLeadingPath(_basedir, myfile);

                /*if (myfile.exists())*/ {
                    // may be on a case insensitive file system.  We want
                    // the results to show what's really on the disk, so
                    // we need to double check.
//                    try {
//                        LinkedRemoteFileInterface canonFile = myfile.getCanonicalFile();
//                        String path = FILE_UTILS.removeLeadingPath(canonBase,
//                                                                  canonFile);
//                        if (!path.equals(currentelement)/* || ON_VMS*/) {
//                            myfile = findFile(basedir, currentelement);
//                            if (myfile != null) {
//                                currentelement =
//                                    FILE_UTILS.removeLeadingPath(basedir,
//                                                                 myfile);
//                            }
//                        }
//                    } catch (IOException ex) {
//                        throw new BuildException(ex);
//                    }
                }

                if ((myfile == null/* || !myfile.exists()*/) && !_isCaseSensitive) {
                	throw new RuntimeException("DEAD CODE");
//                	LinkedRemoteFileInterface f;
//					try {
//						f = findFileCaseInsensitive(basedir, currentelement);
//					} catch (FileNotFoundException e1) {
//						throw new RuntimeException(e1);
//					}
//					/*if (f.exists())*/ {
//                        // adapt currentelement to the case we've
//                        // actually found
//                        currentelement = FILE_UTILS.removeLeadingPath(basedir,
//                                                                     f);
//                        myfile = f;
//                    }
                }

                if (myfile != null/* && myfile.exists()*/) {
                    if (!_followSymlinks
                        && /*isSymlink(basedir, currentelement)*/ myfile.isLink()) {
                        continue;
                    }

                    if (myfile.isDirectory()) {
                        if (isIncluded(currentelement)
                            && currentelement.length() > 0) {
                            accountForIncludedDir(currentelement, myfile, true);
                        }  else {
                            if (currentelement.length() > 0) {
                                if (currentelement.charAt(currentelement
                                                          .length() - 1)
                                    != '/') {
                                    currentelement =
                                        currentelement + '/';
                                }
                            }
                            scandir(myfile, currentelement, true);
                        }
                    } else {
                        if (_isCaseSensitive
                            && originalpattern.equals(currentelement)) {
                            accountForIncludedFile(currentelement, myfile);
                        } else if (!_isCaseSensitive
                                   && originalpattern
                                   .equalsIgnoreCase(currentelement)) {
                            accountForIncludedFile(currentelement, myfile);
                        }
                    }
                }
            }
        }
    }

    /**
     * Top level invocation for a slow scan. A slow scan builds up a full
     * list of excluded/included files/directories, whereas a fast scan
     * will only have full results for included files, as it ignores
     * directories which can't possibly hold any included files/directories.
     * <p>
     * Returns immediately if a slow scan has already been completed.
     * @throws FileNotFoundException
     */
    protected void slowScan() throws FileNotFoundException {
        if (haveSlowResults) {
            return;
        }

        String[] excl = new String[dirsExcluded.size()];
        dirsExcluded.copyInto(excl);

        String[] notIncl = new String[dirsNotIncluded.size()];
        dirsNotIncluded.copyInto(notIncl);

        for (int i = 0; i < excl.length; i++) {
            if (!couldHoldIncluded(excl[i])) {
                scandir(_basedir.lookupFile(excl[i]),
                        excl[i] + "/", false);
            }
        }

        for (int i = 0; i < notIncl.length; i++) {
            if (!couldHoldIncluded(notIncl[i])) {
                scandir(_basedir.lookupFile(notIncl[i]),
                        notIncl[i] + "/", false);
            }
        }

        haveSlowResults  = true;
    }

    /**
     * Scans the given directory for files and directories. Found files and
     * directories are placed in their respective collections, based on the
     * matching of includes, excludes, and the selectors.  When a directory
     * is found, it is scanned recursively.
     *
     * @param dir   The directory to scan. Must not be <code>null</code>.
     * @param vpath The path relative to the base directory (needed to
     *              prevent problems with an absolute path when using
     *              dir). Must not be <code>null</code>.
     * @param fast  Whether or not this call is part of a fast scan.
     * @throws FileNotFoundException
     *
     * @see #filesIncluded
     * @see #filesNotIncluded
     * @see #filesExcluded
     * @see #dirsIncluded
     * @see #dirsNotIncluded
     * @see #dirsExcluded
     * @see #slowScan
     */
    protected void scandir(LinkedRemoteFileInterface dir, String vpath, boolean fast) {
        if (dir == null) {
            throw new RuntimeException("dir must not be null.");
        /*
        } else if (!dir.exists()) {
            throw new BuildException(dir + " doesn't exists.");
        */
        } else if (!dir.isDirectory()) {
            throw new RuntimeException(dir + " is not a directory.");
        }

        // avoid double scanning of directories, can only happen in fast mode
        if (fast && hasBeenScanned(vpath)) {
            return;
        }
        LinkedRemoteFileInterface[] newfiles = dir.getFiles().toArray(new LinkedRemoteFileInterface[0]);

        if (!_followSymlinks) {
            Vector<LinkedRemoteFileInterface> noLinks = new Vector<LinkedRemoteFileInterface>();
            for (int i = 0; i < newfiles.length; i++) {
                //try {
                    if (newfiles[i].isLink()) {
                        String name = vpath + newfiles[i];
                        LinkedRemoteFileInterface   file = newfiles[i];
                        if (file.isDirectory()) {
                            dirsExcluded.addElement(name);
                        } else {
                            filesExcluded.addElement(name);
                        }
                    } else {
                        noLinks.addElement(newfiles[i]);
                    }
                /*} catch (IOException ioe) {
                    String msg = "IOException caught while checking "
                        + "for links, couldn't get canonical path!";
                    // will be caught and redirected to Ant's logging system
                    System.err.println(msg);
                    noLinks.addElement(newfiles[i]);
                }*/
            }
            newfiles = new LinkedRemoteFileInterface[noLinks.size()];
            noLinks.copyInto(newfiles);
        }

        for (int i = 0; i < newfiles.length; i++) {
            String name = vpath + newfiles[i].getName();
            LinkedRemoteFileInterface   file = newfiles[i];
            if (file.isDirectory()) {
                if (isIncluded(name)) {
                    accountForIncludedDir(name, file, fast);
                } else {
                    everythingIncluded = false;
                    dirsNotIncluded.addElement(name);
                    if (fast && couldHoldIncluded(name)) {
                        scandir(file, name + "/", fast);
                    }
                }
                if (!fast) {
                    scandir(file, name + "/", fast);
                }
            } else if (file.isFile()) {
                if (isIncluded(name)) {
                    accountForIncludedFile(name, file);
                } else {
                    everythingIncluded = false;
                    filesNotIncluded.addElement(name);
                }
            }
        }
    }
    /**
     * process included file
     * @param name  path of the file relative to the directory of the fileset
     * @param file  included file
     */
    private void accountForIncludedFile(String name, LinkedRemoteFileInterface file) {
        if (!filesIncluded.contains(name)
            && !filesExcluded.contains(name)
            && !filesDeselected.contains(name)) {

            if (!isExcluded(name)) {
                if (isSelected(name, file)) {
                    filesIncluded.addElement(name);
                } else {
                    everythingIncluded = false;
                    filesDeselected.addElement(name);
                }
            } else {
                everythingIncluded = false;
                filesExcluded.addElement(name);
            }
        }
    }

    /**
     *
     * @param name path of the directory relative to the directory of
     * the fileset
     * @param file directory as file
     * @param fast
     * @throws FileNotFoundException
     */
    private void accountForIncludedDir(String name, LinkedRemoteFileInterface file, boolean fast) {
        if (!dirsIncluded.contains(name)
            && !dirsExcluded.contains(name)
            && !dirsDeselected.contains(name)) {

            if (!isExcluded(name)) {
                if (isSelected(name, file)) {
                    dirsIncluded.addElement(name);
                    if (fast) {
                        scandir(file, name + "/", fast);
                    }
                } else {
                    everythingIncluded = false;
                    dirsDeselected.addElement(name);
                    if (fast && couldHoldIncluded(name)) {
                        scandir(file, name + "/", fast);
                    }
                }

            } else {
                everythingIncluded = false;
                dirsExcluded.addElement(name);
                if (fast && couldHoldIncluded(name)) {
                    scandir(file, name + "/", fast);
                }
            }
        }
    }
    /**
     * Tests whether or not a name matches against at least one include
     * pattern.
     *
     * @param name The name to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against at least one
     *         include pattern, or <code>false</code> otherwise.
     */
    protected boolean isIncluded(String name) {
        for (int i = 0; i < _includes.length; i++) {
            if (matchPath(_includes[i], name, _isCaseSensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether or not a name matches the start of at least one include
     * pattern.
     *
     * @param name The name to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against the start of at
     *         least one include pattern, or <code>false</code> otherwise.
     */
    protected boolean couldHoldIncluded(String name) {
        for (int i = 0; i < _includes.length; i++) {
            if (matchPatternStart(_includes[i], name, _isCaseSensitive)) {
                if (isMorePowerfulThanExcludes(name, _includes[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *  find out whether one particular include pattern is more powerful
     *  than all the excludes
     *  note : the power comparison is based on the length of the include pattern
     *  and of the exclude patterns without the wildcards
     *  ideally the comparison should be done based on the depth
     *  of the match, that is to say how many file separators have been matched
     *  before the first ** or the end of the pattern
     *
     *  IMPORTANT : this function should return false "with care"
     *
     *  @param name the relative path that one want to test
     *  @param includepattern  one include pattern
     *  @return true if there is no exclude pattern more powerful than this include pattern
     *  @since ant1.6
     */
    private boolean isMorePowerfulThanExcludes(String name, String includepattern) {
        String soughtexclude = name + "/" + "**";
        for (int counter = 0; counter < _excludes.length; counter++) {
            if (_excludes[counter].equals(soughtexclude))  {
                return false;
            }
        }
        return true;
    }
    /**
     * Tests whether or not a name matches against at least one exclude
     * pattern.
     *
     * @param name The name to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against at least one
     *         exclude pattern, or <code>false</code> otherwise.
     */
    protected boolean isExcluded(String name) {
        for (int i = 0; i < _excludes.length; i++) {
            if (matchPath(_excludes[i], name, _isCaseSensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether a name should be selected.
     *
     * @param name the filename to check for selecting
     * @param file the java.io.File object for this filename
     * @return <code>false</code> when the selectors says that the file
     *         should not be selected, <code>true</code> otherwise.
     */
    protected boolean isSelected(String name, LinkedRemoteFileInterface file) {
        if (_selectors != null) {
            for (int i = 0; i < _selectors.length; i++) {
                if (!_selectors[i].isSelected(_basedir, name, file)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the names of the files which matched at least one of the
     * include patterns and none of the exclude patterns.
     * The names are relative to the base directory.
     *
     * @return the names of the files which matched at least one of the
     *         include patterns and none of the exclude patterns.
     */
    public String[] getIncludedFiles() {
        String[] files = new String[filesIncluded.size()];
        filesIncluded.copyInto(files);
        Arrays.sort(files);
        return files;
    }

    /**
     * Returns the names of the files which matched none of the include
     * patterns. The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.
     *
     * @return the names of the files which matched none of the include
     *         patterns.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getNotIncludedFiles() throws FileNotFoundException {
        slowScan();
        String[] files = new String[filesNotIncluded.size()];
        filesNotIncluded.copyInto(files);
        return files;
    }

    /**
     * Returns the names of the files which matched at least one of the
     * include patterns and at least one of the exclude patterns.
     * The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.
     *
     * @return the names of the files which matched at least one of the
     *         include patterns and at least one of the exclude patterns.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getExcludedFiles() throws FileNotFoundException {
        slowScan();
        String[] files = new String[filesExcluded.size()];
        filesExcluded.copyInto(files);
        return files;
    }

    /**
     * <p>Returns the names of the files which were selected out and
     * therefore not ultimately included.</p>
     *
     * <p>The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.</p>
     *
     * @return the names of the files which were deselected.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getDeselectedFiles() throws FileNotFoundException {
        slowScan();
        String[] files = new String[filesDeselected.size()];
        filesDeselected.copyInto(files);
        return files;
    }

    /**
     * Returns the names of the directories which matched at least one of the
     * include patterns and none of the exclude patterns.
     * The names are relative to the base directory.
     *
     * @return the names of the directories which matched at least one of the
     * include patterns and none of the exclude patterns.
     */
    public String[] getIncludedDirectories() {
        String[] directories = new String[dirsIncluded.size()];
        dirsIncluded.copyInto(directories);
        Arrays.sort(directories);
        return directories;
    }

    /**
     * Returns the names of the directories which matched none of the include
     * patterns. The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.
     *
     * @return the names of the directories which matched none of the include
     * patterns.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getNotIncludedDirectories() throws FileNotFoundException {
        slowScan();
        String[] directories = new String[dirsNotIncluded.size()];
        dirsNotIncluded.copyInto(directories);
        return directories;
    }

    /**
     * Returns the names of the directories which matched at least one of the
     * include patterns and at least one of the exclude patterns.
     * The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.
     *
     * @return the names of the directories which matched at least one of the
     * include patterns and at least one of the exclude patterns.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getExcludedDirectories() throws FileNotFoundException {
        slowScan();
        String[] directories = new String[dirsExcluded.size()];
        dirsExcluded.copyInto(directories);
        return directories;
    }

    /**
     * <p>Returns the names of the directories which were selected out and
     * therefore not ultimately included.</p>
     *
     * <p>The names are relative to the base directory. This involves
     * performing a slow scan if one has not already been completed.</p>
     *
     * @return the names of the directories which were deselected.
     * @throws FileNotFoundException
     *
     * @see #slowScan
     */
    public String[] getDeselectedDirectories() throws FileNotFoundException {
        slowScan();
        String[] directories = new String[dirsDeselected.size()];
        dirsDeselected.copyInto(directories);
        return directories;
    }

    /**
     * Adds default exclusions to the current exclusions set.
     */
//    public void addDefaultExcludes() {
//        int excludesLength = excludes == null ? 0 : excludes.length;
//        String[] newExcludes;
//        newExcludes = new String[excludesLength + defaultExcludes.size()];
//        if (excludesLength > 0) {
//            System.arraycopy(excludes, 0, newExcludes, 0, excludesLength);
//        }
//        String[] defaultExcludesTemp = getDefaultExcludes();
//        for (int i = 0; i < defaultExcludesTemp.length; i++) {
//            newExcludes[i + excludesLength] =
//                defaultExcludesTemp[i]
//                ;
//        }
//        excludes = newExcludes;
//    }

    /**
     * Get the named resource
     * @param name path name of the file relative to the dir attribute.
     *
     * @return the resource with the given name.
     * @since Ant 1.5.2
     */
    /*
    public Resource getResource(String name) {
        LinkedRemoteFileInterface f = FILE_UTILS.resolveFile(basedir, name);
        return new Resource(name, f.exists(), f.lastModified(),
                            f.isDirectory());
    }
    */

    /**
     * temporary table to speed up the various scanning methods below
     *
     * @since Ant 1.6
     */
    private Map fileListMap = new HashMap();

    /**
     * List of all scanned directories.
     *
     * @since Ant 1.6
     */
    private Set<String> scannedDirs = new HashSet<String>();

    /**
     * Has the directory with the given path relative to the base
     * directory already been scanned?
     *
     * <p>Registers the given directory as scanned as a side effect.</p>
     *
     * @since Ant 1.6
     */
    private boolean hasBeenScanned(String vpath) {
        return !scannedDirs.add(vpath);
    }

    /**
     * Clear internal caches.
     *
     * @since Ant 1.6
     */
    private void clearCaches() {
        fileListMap.clear();
        scannedDirs.clear();
    }
}
