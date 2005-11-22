/*
 * Copyright  2001-2004 The Apache Software Foundation
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

//import org.apache.tools.ant.taskdefs.condition.Os;
import net.sf.drftpd.FileExistsException;

import org.apache.log4j.BasicConfigurator;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.StaticRemoteFile;
//import org.apache.tools.ant.util.JavaEnvUtils;

import junit.framework.TestCase;
import junit.framework.AssertionFailedError;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Iterator;

/**
 * JUnit 3 testcases for org.apache.tools.ant.DirectoryScanner
 *
 */
public class DirectoryScannerTest extends TestCase /*BuildFileTest*/ {

    private LinkedRemoteFile _root;

	public DirectoryScannerTest(String name) {super(name);}

    // keep track of what operating systems are supported here.
    //private boolean supportsSymlinks = Os.isFamily("unix")
    //    && !JavaEnvUtils.isJavaVersion(JavaEnvUtils.JAVA_1_1);

    public void setUp() throws FileExistsException {
    	BasicConfigurator.configure();
        //configureProject("src/etc/testcases/core/directoryscanner.xml");
        //getProject().executeTarget("setup");
    	_root = new LinkedRemoteFile(null);
    	LinkedRemoteFile beta = _root.createDirectories("alpha/beta");
    	beta.addFile(new StaticRemoteFile(Collections.EMPTY_LIST, "beta.xml", 0));
    	LinkedRemoteFile gamma = beta.createDirectory("gamma");
    	gamma.addFile(new StaticRemoteFile(Collections.EMPTY_LIST, "gamma.xml", 0));
    }

    public void tearDown() {
        //getProject().executeTarget("cleanup");
    }

    public void test1() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha"});
        ds.scan();
        compareFiles(ds, new String[] {} ,new String[] {"alpha"});
    }

    public void test2() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/"});
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

    public void test3() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"", "alpha", "alpha/beta",
                                   "alpha/beta/gamma"});
    }

//    public void testFullPathMatchesCaseSensitive() throws IllegalStateException, FileNotFoundException {
//        DirectoryScanner ds = new DirectoryScanner();
//        ds.setBasedir(_root);
//        ds.setIncludes(new String[] {"alpha/beta/gamma/GAMMA.XML"});
//        ds.scan();
//        compareFiles(ds, new String[] {}, new String[] {});
//    }

    public void testFullPathMatchesCaseInsensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        //ds.setCaseSensitive(false);
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/beta/gamma/GAMMA.XML"});
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/gamma/gamma.xml"},
            new String[] {});
    }

    public void test2ButCaseInsensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"ALPHA/"});
        //ds.setCaseSensitive(false);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

//    public void testAllowSymlinks() throws IllegalStateException, FileNotFoundException {
//        //if (!supportsSymlinks) {
//        //    return;
//        //}
//
//        //getProject().executeTarget("symlink-setup");
//    	setUpSymlink();
//        DirectoryScanner ds = new DirectoryScanner();
//        ds.setBasedir(_root);
//        ds.setIncludes(new String[] {"alpha/beta/gamma/"});
//        ds.scan();
//        compareFiles(ds, new String[] {"alpha/beta/gamma/gamma.xml"},
//                     new String[] {"alpha/beta/gamma"});
//    }

    public void testProhibitSymlinks() throws IllegalStateException, FileNotFoundException {
        //if (!supportsSymlinks) {
        //    return;
        //}

        //getProject().executeTarget("symlink-setup");
    	setUpSymlink();
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/beta/gamma/"});
        ds.setFollowSymlinks(false);
        ds.scan();
        compareFiles(ds, new String[] {}, new String[] {});
    }

    // father and child pattern test
    public void testOrderOfIncludePatternsIrrelevant() throws IllegalStateException, FileNotFoundException {
        String [] expectedFiles = {"alpha/beta/beta.xml",
                                   "alpha/beta/gamma/gamma.xml"};
        String [] expectedDirectories = {"alpha/beta", "alpha/beta/gamma" };
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/be?a/**", "alpha/beta/gamma/"});
        ds.scan();
        compareFiles(ds, expectedFiles, expectedDirectories);
        // redo the test, but the 2 include patterns are inverted
        ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/beta/gamma/", "alpha/be?a/**"});
        ds.scan();
        compareFiles(ds, expectedFiles, expectedDirectories);
    }

    public void testPatternsDifferInCaseScanningSensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/", "ALPHA/"});
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

    public void testPatternsDifferInCaseScanningInsensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/", "ALPHA/"});
        //ds.setCaseSensitive(false);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

    public void testFullpathDiffersInCaseScanningSensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {
            "alpha/beta/gamma/gamma.xml",
            "alpha/beta/gamma/GAMMA.XML"
        });
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/gamma/gamma.xml"},
                     new String[] {});
    }

    public void testFullpathDiffersInCaseScanningInsensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {
            "alpha/beta/gamma/gamma.xml",
            "alpha/beta/gamma/GAMMA.XML"
        });
        //ds.setCaseSensitive(false);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/gamma/gamma.xml"},
                     new String[] {});
    }

    public void testParentDiffersInCaseScanningSensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/", "ALPHA/beta/"});
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

    public void testParentDiffersInCaseScanningInsensitive() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {"alpha/", "ALPHA/beta/"});
        //ds.setCaseSensitive(false);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml"},
                     new String[] {"alpha", "alpha/beta", "alpha/beta/gamma"});
    }

    /**
     * Test case for setFollowLinks() and associated funtionality.
     * Only supports test on linux, at the moment because Java has
     * no real notion of symlinks built in, so an os-specfic call
     * to Runtime.exec() must be made to create a link to test against.
     * @throws FileNotFoundException
     * @throws IllegalStateException
     */
//    public void testSetFollowLinks() {
//        /*if (supportsSymlinks)*/ {
//            try {
//                // add conditions and more commands as soon as the need arises
//                String[] command = new String[] {
//                    "ln", "-s", "ant", "src/main/org/apache/tools/ThisIsALink"
//                };
//                try {
//                    Runtime.getRuntime().exec(command);
//                    // give ourselves some time for the system call
//                    // to execute... tweak if you have a really over
//                    // loaded system.
//                    Thread.sleep(1000);
//                } catch (IOException ioe) {
//                    fail("IOException making link "+ioe);
//                } catch (InterruptedException ie) {
//                }
//
//                File dir = new File("src/main/org/apache/tools");
//                DirectoryScanner ds = new DirectoryScanner();
//
//                // followLinks should be true by default, but if this ever
//                // changes we will need this line.
//                ds.setFollowSymlinks(true);
//
//                ds.setBasedir(dir);
//                ds.setExcludes(new String[] {"ant/**"});
//                ds.scan();
//
//                boolean haveZipPackage = false;
//                boolean haveTaskdefsPackage = false;
//
//                String[] included = ds.getIncludedDirectories();
//                for (int i=0; i<included.length; i++) {
//                    if (included[i].equals("zip")) {
//                        haveZipPackage = true;
//                    } else if (included[i].equals("ThisIsALink"
//                                                  + File.separator
//                                                  + "taskdefs")) {
//                        haveTaskdefsPackage = true;
//                    }
//                }
//
//                // if we followed the symlink we just made we should
//                // bypass the excludes.
//
//                assertTrue("(1) zip package included", haveZipPackage);
//                assertTrue("(1) taskdefs package included",
//                           haveTaskdefsPackage);
//
//
//                ds = new DirectoryScanner();
//                ds.setFollowSymlinks(false);
//
//                ds.setBasedir(dir);
//                ds.setExcludes(new String[] {"ant/**"});
//                ds.scan();
//
//                haveZipPackage = false;
//                haveTaskdefsPackage = false;
//                included = ds.getIncludedDirectories();
//                for (int i=0; i<included.length; i++) {
//                    if (included[i].equals("zip")) {
//                        haveZipPackage = true;
//                    } else if (included[i].equals("ThisIsALink"
//                                                  + File.separator
//                                                  + "taskdefs")) {
//                        haveTaskdefsPackage = true;
//                    }
//                }
//                assertTrue("(2) zip package included", haveZipPackage);
//                assertTrue("(2) taskdefs package not included",
//                           !haveTaskdefsPackage);
//
//            } finally {
//                File f = new File("src/main/org/apache/tools/ThisIsALink");
//                if (!f.delete()) {
//                    throw new RuntimeException("Failed to delete " + f);
//                }
//            }
//        }
//    }
    public void testExcludeOneFile() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {
            "**/*.xml"
        });
        ds.setExcludes(new String[] {
            "alpha/beta/b*xml"
        });
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/gamma/gamma.xml"},
                     new String[] {});
    }
    public void testExcludeHasPrecedence() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {
            "alpha/**"
        });
        ds.setExcludes(new String[] {
            "alpha/**"
        });
        ds.scan();
        compareFiles(ds, new String[] {},
                     new String[] {});

    }
    public void testAlternateIncludeExclude() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setIncludes(new String[] {
            "alpha/**",
            "alpha/beta/gamma/**"
        });
        ds.setExcludes(new String[] {
            "alpha/beta/**"
        });
        ds.scan();
        compareFiles(ds, new String[] {},
                     new String[] {"alpha"});

    }
    public void testAlternateExcludeInclude() throws IllegalStateException, FileNotFoundException {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setExcludes(new String[] {
            "alpha/**",
            "alpha/beta/gamma/**"
        });
        ds.setIncludes(new String[] {
            "alpha/beta/**"
        });
        ds.scan();
        compareFiles(ds, new String[] {},
                     new String[] {});

    }
    /**
     * Test inspired by Bug#1415.
     * @throws FileNotFoundException
     * @throws IllegalStateException
     */
    public void testChildrenOfExcludedDirectory() throws IllegalStateException, FileNotFoundException {
        //getProject().executeTarget("children-of-excluded-dir-setup");
    	setUpChildrenOfExcludedDir();
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setExcludes(new String[] {"alpha/**"});
        ds.setFollowSymlinks(false);
        ds.scan();
        compareFiles(ds, new String[] {"delta/delta.xml"},
                    new String[] {"", "delta"});

        ds = new DirectoryScanner();
        ds.setBasedir(_root);
        ds.setExcludes(new String[] {"alpha"});
        ds.setFollowSymlinks(false);
        ds.scan();
        compareFiles(ds, new String[] {"alpha/beta/beta.xml",
                                       "alpha/beta/gamma/gamma.xml",
                                        "delta/delta.xml"},
                     new String[] {"", "alpha/beta", "alpha/beta/gamma", "delta"});

    }

	private void setUpChildrenOfExcludedDir() {
		_root.createDirectories("delta").addFile(
				new StaticRemoteFile(Collections.EMPTY_LIST, "delta.xml", 0));
	}
	private void setUpSymlink() throws FileNotFoundException {
		_root.createDirectories("epsilon/gamma");
		_root.lookupFile("alpha/beta").delete();
		_root.lookupFile("alpha").addFile(new StaticRemoteFile("beta", Collections.EMPTY_LIST, "/epsilon"));
		_root.lookupFile("alpha/beta/gamma").addFile(new StaticRemoteFile("gamma.xml"));
	}

	private void compareFiles(DirectoryScanner ds, String[] expectedFiles,
                              String[] expectedDirectories) {
        String includedFiles[] = ds.getIncludedFiles();
        String includedDirectories[] = ds.getIncludedDirectories();
        assertEquals("file present: "+Arrays.asList(expectedFiles)+", "+Arrays.asList(includedFiles), expectedFiles.length,
                     includedFiles.length);
        assertEquals("directories present: ", expectedDirectories.length,
                     includedDirectories.length);

        TreeSet files = new TreeSet();
        for (int counter=0; counter < includedFiles.length; counter++) {
            files.add(includedFiles[counter].replace(File.separatorChar, '/'));
        }
        TreeSet directories = new TreeSet();
        for (int counter=0; counter < includedDirectories.length; counter++) {
            directories.add(includedDirectories[counter]
                            .replace(File.separatorChar, '/'));
        }

        String currentfile;
        Iterator i = files.iterator();
        int counter = 0;
        while (i.hasNext()) {
            currentfile = (String) i.next();
            assertEquals(expectedFiles[counter], currentfile);
            counter++;
        }
        String currentdirectory;
        Iterator dirit = directories.iterator();
        counter = 0;
        while (dirit.hasNext()) {
            currentdirectory = (String) dirit.next();
            assertEquals(expectedDirectories[counter], currentdirectory);
            counter++;
        }
    }

}
