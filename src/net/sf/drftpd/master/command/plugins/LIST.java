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
package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.ListUtils;
import org.drftpd.remotefile.RemoteFileInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

import java.net.Socket;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;


/**
 * @author mog
 *
 * @version $Id$
 */
public class LIST extends CommandHandler implements CommandHandlerFactory {
    private final static DateFormat AFTER_SIX = new SimpleDateFormat(" yyyy");
    private final static DateFormat BEFORE_SIX = new SimpleDateFormat("HH:mm");
    private final static String DELIM = " ";
    private final static DateFormat FULL = new SimpleDateFormat("HH:mm:ss yyyy");
    private static final Logger logger = Logger.getLogger(LIST.class);
    private final static String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
            "Nov", "Dec"
        };
    private final static String NEWLINE = "\r\n";

    /**
     * Get size
     * @deprecated
     */
    private static String getLength(RemoteFileInterface fl) {
        String initStr = "             ";
        String szStr = Long.toString(fl.length());

        if (szStr.length() > initStr.length()) {
            return szStr;
        }

        return initStr.substring(0, initStr.length() - szStr.length()) + szStr;
    }

    /*    */

    /**
    * Get file name.
    */

    /*
    private static String getName(LinkedRemoteFileInterface fl) {
     String flName = fl.getName();

     int lastIndex = flName.lastIndexOf("/");

     if (lastIndex == -1) {
         return flName;
     } else {
         return flName.substring(lastIndex + 1);
     }
    }*/

    /**
     * Get permission string.
     */
    private static String getPermission(RemoteFileInterface fl) {
        StringBuffer sb = new StringBuffer(13);

        if (fl.isLink()) {
            sb.append("l");
        } else if (fl.isDirectory()) {
            sb.append("d");
        } else {
            sb.append("-");
        }

        sb.append("rw");
        sb.append(fl.isDirectory() ? "x" : "-");

        sb.append("rw");
        sb.append(fl.isDirectory() ? "x" : "-");

        sb.append("rw");
        sb.append(fl.isDirectory() ? "x" : "-");

        return sb.toString();
    }

    public static String getUnixDate(long date, boolean fulldate) {
        Date date1 = new Date(date);
        long dateTime = date1.getTime();

        if (dateTime < 0) {
            return "------------";
        }

        Calendar cal = new GregorianCalendar();
        cal.setTime(date1);

        String firstPart = MONTHS[cal.get(Calendar.MONTH)] + ' ';

        String dateStr = String.valueOf(cal.get(Calendar.DATE));

        if (dateStr.length() == 1) {
            dateStr = ' ' + dateStr;
        }

        firstPart += (dateStr + ' ');

        long nowTime = System.currentTimeMillis();

        if (fulldate) {
            return firstPart + FULL.format(date1);
        } else if (Math.abs(nowTime - dateTime) > (183L * 24L * 60L * 60L * 1000L)) {
            return firstPart + AFTER_SIX.format(date1);
        } else {
            return firstPart + BEFORE_SIX.format(date1);
        }
    }

    /**
     * Get each directory line.
     */
    private static void printLine(RemoteFileInterface fl, Writer out,
        boolean fulldate) throws IOException {
        StringBuffer line = new StringBuffer();

        if (fl instanceof LinkedRemoteFileInterface &&
                !((LinkedRemoteFileInterface) fl).isAvailable()) {
            line.append("----------");
        } else {
            line.append(getPermission(fl));
        }

        line.append(DELIM);
        line.append((fl.isDirectory() ? "3" : "1"));
        line.append(DELIM);
        line.append(ListUtils.padToLength(fl.getUsername(), 8));
        line.append(DELIM);
        line.append(ListUtils.padToLength(fl.getGroupname(), 8));
        line.append(DELIM);
        line.append(getLength(fl));
        line.append(DELIM);
        line.append(getUnixDate(fl.lastModified(), fulldate));
        line.append(DELIM);
        line.append(fl.getName());
        if (fl.isLink()) {
        	line.append(DELIM + "->" + DELIM + fl.getLinkPath());
        }
        line.append(NEWLINE);
        out.write(line.toString());
    }

    /**
     * Print file list. Detail listing.
     * <pre>
     *   -a : display all (including hidden files)
     * </pre>
     * @return true if success
     */
    private static void printList(Collection files, Writer os, boolean fulldate)
        throws IOException {
        //out = new BufferedWriter(out);
        os.write("total 0" + NEWLINE);

        // print file list
        for (Iterator iter = files.iterator(); iter.hasNext();) {
            RemoteFileInterface file = (RemoteFileInterface) iter.next();
            LIST.printLine(file, os, fulldate);
        }
    }

    /**
     * Print file list.
     * <pre>
     *   -l : detail listing
     *   -a : display all (including hidden files)
     * </pre>
     * @return true if success
     */
    private static void printNList(Collection fileList, boolean bDetail,
        Writer out) throws IOException {
        for (Iterator iter = fileList.iterator(); iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();

            if (bDetail) {
                printLine(file, out, false);
            } else {
                out.write(file.getName() + NEWLINE);
            }
        }
    }

    /**
     * <code>NLST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
     *
     * This command causes a directory listing to be sent from
     * server to user site.  The pathname should specify a
     * directory or other system-specific file group descriptor; a
     * null argument implies the current directory.  The server
     * will return a stream of names of files and no other
     * information.
     *
     *
     * <code>LIST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
     *
     * This command causes a list to be sent from the server to the
     * passive DTP.  If the pathname specifies a directory or other
     * group of files, the server should transfer a list of files
     * in the specified directory.  If the pathname specifies a
     * file then the server should send current information on the
     * file.  A null argument implies the user's current working or
     * default directory.  The data transfer is over the data
     * connection
     *
     *                LIST
     *                   125, 150
     *                      226, 250
     *                      425, 426, 451
     *                   450
     *                   500, 501, 502, 421, 530
     */
    public Reply execute(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        String directoryName = null;
        String options = "";

        //String pattern = "*";
        // get options, directory name and pattern
        //argument == null if there was no argument for LIST
        if (request.hasArgument()) {
            //argument = argument.trim();
            StringBuffer optionsSb = new StringBuffer(4);
            StringTokenizer st = new StringTokenizer(request.getArgument(), " ");

            while (st.hasMoreTokens()) {
                String token = st.nextToken();

                if (token.charAt(0) == '-') {
                    if (token.length() > 1) {
                        optionsSb.append(token.substring(1));
                    }
                } else {
                    directoryName = token;
                }
            }

            options = optionsSb.toString();
        }

        // check options
        //		boolean allOption = options.indexOf('a') != -1;
        boolean fulldate = options.indexOf('T') != -1;
        boolean detailOption = request.getCommand().equals("LIST") ||
            request.getCommand().equals("STAT") ||
            (options.indexOf('l') != -1);

        //		boolean directoryOption = options.indexOf("d") != -1;
        DataConnectionHandler dataconn = null;

        if (!request.getCommand().equals("STAT")) {
            dataconn = conn.getDataConnectionHandler();

            if (!dataconn.isPasv() && !dataconn.isPort()) {
                return Reply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
            }
        }

        LinkedRemoteFileInterface directoryFile;

        if (directoryName != null) {
            try {
                directoryFile = conn.getCurrentDirectory().lookupFile(directoryName);
            } catch (FileNotFoundException ex) {
                return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
            }

            if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), directoryFile, true)) {
                return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
            }
        } else {
            directoryFile = conn.getCurrentDirectory();
        }

        PrintWriter out = conn.getControlWriter();
        Socket dataSocket = null;
        Writer os;

        if (request.getCommand().equals("STAT")) {
            os = out;
            out.write("213- Status of " + request.getArgument() + ":" +
                NEWLINE);
        } else {
            if (!dataconn.isEncryptedDataChannel() &&
                    conn.getGlobalContext().getConfig().checkPermission("denydiruncrypted", conn.getUserNull())) {
                return new Reply(550, "Secure Listing Required");
            }

            out.write(Reply.RESPONSE_150_OK);
            out.flush();

            try {
                dataSocket = dataconn.getDataSocket();
                os = new PrintWriter(new OutputStreamWriter(
                            dataSocket.getOutputStream()));

                //out2 = dataSocket.getChannel();
            } catch (IOException ex) {
                logger.warn("from master", ex);

                return new Reply(425, ex.getMessage());
            }
        }

        ////////////////
        List listFiles = ListUtils.list(directoryFile, conn);

        ////////////////
        try {
            if (request.getCommand().equals("LIST") ||
                    request.getCommand().equals("STAT")) {
                printList(listFiles, os, fulldate);
            } else if (request.getCommand().equals("NLST")) {
                printNList(listFiles, detailOption, os);
            }

            Reply response = (Reply) Reply.RESPONSE_226_CLOSING_DATA_CONNECTION.clone();

            try {
                if (!request.getCommand().equals("STAT")) {
                    os.close();
                    dataSocket.close();
                    response.addComment(conn.status());

                    return response;
                }

                return new Reply(213, "End of Status");
            } catch (IOException ioe) {
                logger.error("", ioe);

                return new Reply(450, ioe.getMessage());
            }
        } catch (IOException ex) {
            logger.warn("from master", ex);

            return new Reply(450, ex.getMessage());
        }

        //redo connection handling
        //conn.reset();
    }

    public String[] getFeatReplies() {
        return null;
    }

    /**
     * <code>STAT [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
     *
     * This command shall cause a status response to be sent over
     * the control connection in the form of a reply.
     */

    //	public void doSTAT(FtpRequest request, PrintWriter out) {
    //		reset();
    //		if (request.hasArgument()) {
    //			doLIST(request, out);
    //		} else {
    //			out.print(FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
    //		}
    //		return;
    //	}

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
     */
    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
