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
package org.drftpd.commands;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.queues.NukeLog;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.dynamicdata.Key;

import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.jdom.Document;
import org.jdom.Element;

import org.jdom.input.SAXBuilder;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * nukedamount -> amount after multiplier
 * amount -> amount before multiplier
 *
 * @author mog
 * @version $Id: Nuke.java 823 2004-11-29 01:36:22Z mog $
 */
public class Nuke implements CommandHandlerFactory, CommandHandler {
    public static final Key NUKED = new Key(Nuke.class, "nuked", Integer.class);
    public static final Key NUKEDBYTES = new Key(Nuke.class, "nukedBytes",
            Long.class);
    private static final Logger logger = Logger.getLogger(Nuke.class);
    public static final Key LASTNUKED = new Key(Nuke.class, "lastNuked",
            Long.class);
    private NukeLog _nukelog;

    public Nuke() {
    }

    public static long calculateNukedAmount(long size, float ratio,
        int multiplier) {
        return (long) ((size * ratio) + (size * (multiplier - 1)));
    }

    private static void nukeRemoveCredits(LinkedRemoteFileInterface nukeDir,
        Hashtable nukees) {
        for (Iterator iter = nukeDir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                nukeRemoveCredits(file, nukees);
            }

            if (file.isFile()) {
                String owner = file.getUsername();
                Long total = (Long) nukees.get(owner);

                if (total == null) {
                    total = new Long(0);
                }

                total = new Long(total.longValue() + file.length());
                nukees.put(owner, total);
            }
        }
    }

    /**
     * USAGE: site nuke <directory> <multiplier> <message>
     * Nuke a directory
     *
     * ex. site nuke shit 2 CRAP
     *
     * This will nuke the directory 'shit' and remove x2 credits with the
     * comment 'CRAP'.
     *
     * NOTE: You can enclose the directory in braces if you have spaces in the name
     * ex. site NUKE {My directory name} 1 because_i_dont_like_it
     *
     * Q)  What does the multiplier in 'site nuke' do?
     * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
     *     credits for the stuff being nuked. If it is 1, user only loses the
     *     amount of credits he gained by uploading the files (which is calculated
     *     by multiplying total size of file by his/her ratio). If multiplier is more
     *     than 1, the user loses the credits he/she gained by uploading, PLUS some
     *     extra credits. The formula is this: size * ratio + size * (multiplier - 1).
     *     This way, multiplier of 2 causes user to lose size * ratio + size * 1,
     *     so the additional penalty in this case is the size of nuked files. If the
     *     multiplier is 3, user loses size * ratio + size * 2, etc.
     */
    private Reply doSITE_NUKE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isNuker()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return new Reply(501, conn.jprintf(Nuke.class, "nuke.usage"));
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument(),
                " ");

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        int multiplier;
        LinkedRemoteFileInterface nukeDir;
        String nukeDirName;

        try {
            nukeDirName = st.nextToken();
            nukeDir = conn.getCurrentDirectory().getFile(nukeDirName);
        } catch (FileNotFoundException e) {
            Reply response = new Reply(550, e.getMessage());

            return response;
        }

        if (!nukeDir.isDirectory()) {
            Reply response = new Reply(550,
                    nukeDirName + ": not a directory");

            return response;
        }

        String nukeDirPath = nukeDir.getPath();

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        try {
            multiplier = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException ex) {
            logger.warn("", ex);

            return new Reply(501, "Invalid multiplier: " + ex.getMessage());
        }

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        } else {
            reason = "";
        }

        //get nukees with string as key
        Hashtable nukees = new Hashtable();
        nukeRemoveCredits(nukeDir, nukees);

        Reply response = new Reply(200, "NUKE suceeded");

        //// convert key from String to User ////
        HashMap nukees2 = new HashMap(nukees.size());

        for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {
            String username = (String) iter.next();
            User user;

            try {
                user = conn.getGlobalContext().getUserManager().getUserByName(username);
            } catch (NoSuchUserException e1) {
                response.addComment("Cannot remove credits from " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                user = null;
            } catch (UserFileException e1) {
                response.addComment("Cannot read user data for " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                response.setMessage("NUKE failed");

                return response;
            }

            // nukees contains credits as value
            if (user == null) {
                Long add = (Long) nukees2.get(null);

                if (add == null) {
                    add = new Long(0);
                }

                nukees2.put(user,
                    new Long(add.longValue() +
                        ((Long) nukees.get(username)).longValue()));
            } else {
                nukees2.put(user, nukees.get(username));
            }
        }

        //rename
        String toDirPath;
        String toName = "[NUKED]-" + nukeDir.getName();

        try {
            toDirPath = nukeDir.getParentFile().getPath();
        } catch (FileNotFoundException ex) {
            logger.fatal("", ex);

            return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
        }

        try {
            nukeDir = nukeDir.renameTo(toDirPath, toName);
            nukeDir.createDirectory(conn.getUserNull().getName(),
                conn.getUserNull().getGroup(), "REASON-" + reason);
        } catch (IOException ex) {
            logger.warn("", ex);
            response.addComment(" cannot rename to \"" + toDirPath + "/" +
                toName + "\": " + ex.getMessage());
            response.setCode(500);
            response.setMessage("NUKE failed");

            return response;
        }

        long nukeDirSize = 0;
        long nukedAmount = 0;

        //update credits, nukedbytes, timesNuked, lastNuked
        for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
            AbstractUser nukee = (AbstractUser) iter.next();

            if (nukee == null) {
                continue;
            }

            long size = ((Long) nukees2.get(nukee)).longValue();

            long debt = calculateNukedAmount(size,
                    nukee.getKeyedMap().getObjectFloat(UserManagment.RATIO), multiplier);

            nukedAmount += debt;
            nukeDirSize += size;
            nukee.updateCredits(-debt);
            nukee.updateUploadedBytes(-size);
            nukee.getKeyedMap().incrementObjectLong(NUKEDBYTES, debt);

            nukee.getKeyedMap().incrementObjectLong(NUKED);
            nukee.getKeyedMap().setObject(Nuke.LASTNUKED, new Long(System.currentTimeMillis()));

            try {
                nukee.commit();
            } catch (UserFileException e1) {
                response.addComment("Error writing userfile: " +
                    e1.getMessage());
                logger.log(Level.WARN, "Error writing userfile", e1);
            }

            response.addComment(nukee.getName() + " " +
                Bytes.formatBytes(debt));
        }

        NukeEvent nuke = new NukeEvent(conn.getUserNull(), "NUKE", nukeDirPath,
                nukeDirSize, nukedAmount, multiplier, reason, nukees);
        getNukeLog().add(nuke);
        conn.getGlobalContext().getConnectionManager().dispatchFtpEvent(nuke);

        return response;
    }

    private Reply doSITE_NUKES(BaseFtpConnection conn) {
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

        for (Iterator iter = getNukeLog().getAll().iterator(); iter.hasNext();) {
            response.addComment(iter.next());
        }

        return response;
    }

    /**
     * USAGE: site unnuke <directory> <message>
     *         Unnuke a directory.
     *
     *         ex. site unnuke shit NOT CRAP
     *
     *         This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
     *
     *         NOTE: You can enclose the directory in braces if you have spaces in the name
     *         ex. site unnuke {My directory name} justcause
     *
     *         You need to configure glftpd to keep nuked files if you want to unnuke.
     *         See the section about glftpd.conf.
     */
    private Reply doSITE_UNNUKE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isNuker()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String toName = st.nextToken();
        String toPath;

        {
            StringBuffer toPath2 = new StringBuffer(conn.getCurrentDirectory()
                                                        .getPath());

            if (toPath2.length() != 1) {
                toPath2.append("/"); // isn't /
            }

            toPath2.append(toName);
            toPath = toPath2.toString();
        }

        String toDir = conn.getCurrentDirectory().getPath();
        String nukeName = "[NUKED]-" + toName;

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("");
        } else {
            reason = "";
        }

        LinkedRemoteFileInterface nukeDir;

        try {
            nukeDir = conn.getCurrentDirectory().getFile(nukeName);
        } catch (FileNotFoundException e2) {
            return new Reply(200,
                nukeName + " doesn't exist: " + e2.getMessage());
        }

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        NukeEvent nuke;

        try {
            nuke = getNukeLog().get(toPath);
        } catch (ObjectNotFoundException ex) {
            response.addComment(ex.getMessage());

            return response;
        }

        //Map nukees2 = new Hashtable();
        //		for (Iterator iter = nuke.getNukees().entrySet().iterator();
        //			iter.hasNext();
        //			) {
        for (Iterator iter = nuke.getNukees2().iterator(); iter.hasNext();) {
            //Map.Entry entry = (Map.Entry) iter.next();
            Nukee nukeeObj = (Nukee) iter.next();

            //String nukeeName = (String) entry.getKey();
            String nukeeName = nukeeObj.getUsername();
            User nukee;

            try {
                nukee = conn.getGlobalContext().getUserManager().getUserByName(nukeeName);
            } catch (NoSuchUserException e) {
                response.addComment(nukeeName + ": no such user");

                continue;
            } catch (UserFileException e) {
                response.addComment(nukeeName + ": error reading userfile");
                logger.fatal("error reading userfile", e);

                continue;
            }

            long nukedAmount = calculateNukedAmount(nukeeObj.getAmount(),
                    nukee.getKeyedMap().getObjectFloat(UserManagment.RATIO),
                    nuke.getMultiplier());

            nukee.updateCredits(nukedAmount);
            nukee.updateUploadedBytes(nukeeObj.getAmount());

            nukee.getKeyedMap().incrementObjectInt(NUKED, -1);

            try {
                nukee.commit();
            } catch (UserFileException e3) {
                logger.log(Level.FATAL,
                    "Eroror saveing userfile for " + nukee.getName(), e3);
                response.addComment("Error saving userfile for " +
                    nukee.getName());
            }

            response.addComment(nukeeName + ": restored " +
                Bytes.formatBytes(nukedAmount));
        }

        try {
            getNukeLog().remove(toPath);
        } catch (ObjectNotFoundException e) {
            response.addComment("Error removing nukelog entry");
        }

        try {
            nukeDir = nukeDir.renameTo(toDir, toName);
        } catch (FileExistsException e1) {
            response.addComment(
                "Error renaming nuke, target dir already exists");
        } catch (IOException e1) {
            response.addComment("Error: " + e1.getMessage());
            logger.log(Level.FATAL,
                "Illegaltargetexception: means parent doesn't exist", e1);
        }

        try {
            LinkedRemoteFileInterface reasonDir = nukeDir.getFile("REASON-" +
                    nuke.getReason());

            if (reasonDir.isDirectory()) {
                reasonDir.delete();
            }
        } catch (FileNotFoundException e3) {
            logger.debug("Failed to delete 'REASON-" + reason +
                "' dir in UNNUKE", e3);
        }

        nuke.setCommand("UNNUKE");
        nuke.setReason(reason);
        nuke.setUser(conn.getUserNull());
        conn.getGlobalContext().getConnectionManager().dispatchFtpEvent(nuke);

        return response;
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        if (_nukelog == null) {
            return new Reply(500, "You must reconnect to use NUKE");
        }

        String cmd = conn.getRequest().getCommand();

        if ("SITE NUKE".equals(cmd)) {
            return doSITE_NUKE(conn);
        }

        if ("SITE NUKES".equals(cmd)) {
            return doSITE_NUKES(conn);
        }

        if ("SITE UNNUKE".equals(cmd)) {
            return doSITE_UNNUKE(conn);
        }

        throw UnhandledCommandException.create(Nuke.class, conn.getRequest());
    }

    public String[] getFeatReplies() {
        return null;
    }

    private NukeLog getNukeLog() {
        return _nukelog;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
        _nukelog = new NukeLog();

        try {
            Document doc = new SAXBuilder().build(new FileReader("nukelog.xml"));
            List nukes = doc.getRootElement().getChildren();

            for (Iterator iter = nukes.iterator(); iter.hasNext();) {
                Element nukeElement = (Element) iter.next();

                User user = initializer.getConnectionManager().getGlobalContext()
                                       .getUserManager().getUserByName(nukeElement.getChildText(
                            "user"));
                String directory = nukeElement.getChildText("path");
                long time = Long.parseLong(nukeElement.getChildText("time"));
                int multiplier = Integer.parseInt(nukeElement.getChildText(
                            "multiplier"));
                String reason = nukeElement.getChildText("reason");

                long size = Long.parseLong(nukeElement.getChildText("size"));
                long nukedAmount = Long.parseLong(nukeElement.getChildText(
                            "nukedAmount"));

                Map nukees = new Hashtable();
                List nukeesElement = nukeElement.getChild("nukees").getChildren("nukee");

                for (Iterator iterator = nukeesElement.iterator();
                        iterator.hasNext();) {
                    Element nukeeElement = (Element) iterator.next();
                    String nukeeUsername = nukeeElement.getChildText("username");
                    Long nukeeAmount = new Long(nukeeElement.getChildText(
                                "amount"));

                    nukees.put(nukeeUsername, nukeeAmount);
                }

                _nukelog.add(new NukeEvent(user, "NUKE", directory, time, size,
                        nukedAmount, multiplier, reason, nukees));
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.DEBUG,
                "nukelog.xml not found, will create it after first nuke.");
        } catch (Exception ex) {
            logger.log(Level.INFO, "Error loading nukelog from nukelog.xml", ex);
        }
    }

    public void unload() {
        _nukelog = null;
    }
}
