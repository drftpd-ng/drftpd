/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.MLSTSerialize;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.FileNotFoundException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//import org.apache.log4j.Logger;

/**
 * @author B SITE FIND <options>-action <action>Options: -user <user>-group
 *         <group>-nogroup -nouser Options: -mtime [-]n -type [f|d] -slave
 *         <slave>-size [-]size Options: -name <name>(* for wildcard)
 *         -incomplete -offline Actions: print, wipe, delete Multipe options and
 *         actions are allowed. If multiple options are given a file must match
 *         all options for action to be taken.
 */
public class Find implements CommandHandlerFactory, CommandHandler {
    public void unload() {
    }

    public void load(CommandManagerFactory initializer) {
    }

    private static void findFile(BaseFtpConnection conn, FtpReply response,
        LinkedRemoteFileInterface dir, Collection options, Collection actions,
        boolean files, boolean dirs) {
        //TODO optimize me, checking using regexp for all dirs is possibly slow
        if (!conn.getGlobalContext().getConnectionManager().getGlobalContext()
                     .getConfig().checkPrivPath(conn.getUserNull(), dir)) {
            //Logger.getLogger(Find.class).debug("privpath: " + dir.getPath());
            return;
        }

        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                findFile(conn, response, file, options, actions, files, dirs);
            }

            if ((dirs && file.isDirectory()) || (files && file.isFile())) {
                boolean checkIt = true;

                for (Iterator iterator = options.iterator();
                        iterator.hasNext();) {
                    if (response.size() >= 100) {
                        return;
                    }

                    FindOption findOption = (FindOption) iterator.next();

                    if (!findOption.isTrueFor(file)) {
                        checkIt = false;

                        break;
                    }
                }

                if (!checkIt) {
                    continue;
                }

                for (Iterator i = actions.iterator(); i.hasNext();) {
                    FindAction findAction = (FindAction) i.next();
                    response.addComment(findAction.exec(conn, file));

                    if (response.size() >= 100) {
                        response.addComment("<snip>");

                        return;
                    }
                }
            }
        }
    }

    private FindAction getAction(String actionName) {
        if (actionName.equals("print")) {
            return new ActionPrint();
        } else if (actionName.equals("wipe")) {
            return new ActionWipe();
        } else if (actionName.equals("delete")) {
            return new ActionDelete();
        } else if (actionName.equals("printf")) {
            return new ActionPrintf();
        } else {
            return null;
        }
    }

    private FindAction getActionWithArgs(String actionName, String args) {
        if (actionName.equals("printf")) {
            return new ActionPrintf(args);
        }

        return null;
    }

    private static FtpReply getHelpMsg() {
        FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
        response.addComment("SITE FIND <options> -action <action>");
        response.addComment(
            "Options: -user <user> -group <group> -nogroup -nouser");
        response.addComment(
            "Options: -mtime [-]n -type [f|d] -slave <slave> -size [-]size");
        response.addComment(
            "Options: -name <name>(* for wildcard) -incomplete -offline");
        response.addComment("Actions: print, printf[(format)], wipe, delete");
        response.addComment("Options for printf format:");
        response.addComment("#f - filename");
        response.addComment("#s - filesize");
        response.addComment("#u - user");
        response.addComment("#g - group");
        response.addComment("#x - slave");
        response.addComment("#t - last modified");
        response.addComment("#h - parent");
        response.addComment(
            "Example: SITE FIND -action printf(filename: #f size: #s");
        response.addComment("Multipe options and actions");
        response.addComment(
            "are allowed. If multiple options are given a file must match all");
        response.addComment("options for action to be taken.");

        return response;
    }

    private static FtpReply getShortHelpMsg() {
        FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
        response.addComment("Usage: SITE FIND <options> -action <action>");
        response.addComment("SITE FIND -help for more info.");

        return response;
    }

    private static String getArgs(String str) {
        int start = str.indexOf("(");
        int end = str.indexOf(")");

        if ((start == -1) || (end == -1)) {
            return null;
        }

        if (start > end) {
            return null;
        }

        return str.substring(start + 1, end);
    }

    public FtpReply execute(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            //return FtpReply.RESPONSE_501_SYNTAX_ERROR;
            return getShortHelpMsg();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length == 0) {
            //return FtpReply.RESPONSE_501_SYNTAX_ERROR;
            return getShortHelpMsg();
        }

        Collection c = Arrays.asList(args);
        ArrayList options = new ArrayList();
        ArrayList actions = new ArrayList();
        boolean files = true;
        boolean dirs = true;
        boolean forceFilesOnly = false;
        boolean forceDirsOnly = false;

        for (Iterator iter = c.iterator(); iter.hasNext();) {
            String arg = iter.next().toString();

            if (arg.toLowerCase().equals("-user")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                options.add(new OptionUser(iter.next().toString()));
            } else if (arg.toLowerCase().equals("-group")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                options.add(new OptionGroup(iter.next().toString()));
            } else if (arg.toLowerCase().equals("-name")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                options.add(new OptionName(iter.next().toString()));
            } else if (arg.toLowerCase().equals("-slave")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                RemoteSlave rs = null;
                String slaveName = iter.next().toString();

                try {
                    rs = conn.getGlobalContext().getSlaveManager()
                             .getRemoteSlave(slaveName);
                } catch (ObjectNotFoundException e) {
                    return new FtpReply(500,
                        "Slave " + slaveName + " was not found.");
                }

                forceFilesOnly = true;
                options.add(new OptionSlave(rs));
            } else if (arg.toLowerCase().equals("-mtime")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                int offset = 0;

                try {
                    offset = Integer.parseInt(iter.next().toString());
                } catch (NumberFormatException e) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                options.add(new OptionMTime(offset));
            } else if (arg.toLowerCase().equals("-size")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                long size = 0;
                boolean bigger = true;
                String bytes = iter.next().toString();

                if (bytes.startsWith("-")) {
                    bigger = false;
                    bytes = bytes.substring(1);
                }

                try {
                    size = Bytes.parseBytes(bytes);
                } catch (NumberFormatException e) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                options.add(new OptionSize(size, bigger));
            } else if (arg.toLowerCase().equals("-type")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                String type = iter.next().toString().toLowerCase();

                if (type.equals("f")) {
                    dirs = false;
                } else if (type.equals("d")) {
                    files = false;
                } else {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }
            } else if (arg.toLowerCase().equals("-help")) {
                return getHelpMsg();
            } else if (arg.toLowerCase().equals("-nouser")) {
                options.add(new OptionUser("nobody"));
            } else if (arg.toLowerCase().equals("-incomplete")) {
                forceDirsOnly = true;
                options.add(new OptionIncomplete());
            } else if (arg.toLowerCase().equals("-offline")) {
                forceDirsOnly = true;
                options.add(new OptionOffline());
            } else if (arg.toLowerCase().equals("-nogroup")) {
                options.add(new OptionGroup("drftpd"));
            } else if (arg.toLowerCase().equals("-action")) {
                if (!iter.hasNext()) {
                    return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                }

                String action = iter.next().toString();

                if (action.indexOf("(") != -1) {
                    String cmd = action.substring(0, action.indexOf("("));
                    boolean go = true;

                    while (go) {
                        if (action.endsWith(")")) {
                            FindAction findAction = getActionWithArgs(cmd,
                                    getArgs(action));
                            actions.add(findAction);
                            go = false;

                            continue;
                        } else if (!iter.hasNext()) {
                            return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                        } else {
                            action += (" " + iter.next().toString());
                        }
                    }
                } else {
                    FindAction findAction = getAction(action.toLowerCase());

                    if (findAction == null) {
                        return FtpReply.RESPONSE_501_SYNTAX_ERROR;
                    }

                    if (findAction instanceof ActionWipe) {
                        if (!conn.getUserNull().isAdmin()) {
                            return FtpReply.RESPONSE_530_ACCESS_DENIED;
                        }
                    }

                    actions.add(findAction);
                }
            } else {
                return FtpReply.RESPONSE_501_SYNTAX_ERROR;
            }
        }

        FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();

        //if (actions.size() == 0 || options.size() == 0)
        //return FtpReply.RESPONSE_501_SYNTAX_ERROR;
        if (actions.size() == 0) {
            actions.add(new ActionPrint());
        }

        if (!dirs && !files) {
            dirs = true;
            files = true;
        }

        //FtpReply response = (FtpReply)
        // FtpReply.RESPONSE_200_COMMAND_OK.clone();
        if (forceFilesOnly && forceDirsOnly) {
            return new FtpReply(500,
                "Option conflict.  Possibly -slave and -incomplete.");
        } else if (forceFilesOnly) {
            dirs = false;
            response.addComment(
                "Forcing a file only search because of -slave option.");
        } else if (forceDirsOnly) {
            files = false;
            response.addComment("Forcing a dir only search.");
        }

        options.add(new OptionType(files, dirs));
        findFile(conn, response, conn.getCurrentDirectory(), options, actions,
            files, dirs);

        return response;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String[] getFeatReplies() {
        return null;
    }

    private interface FindAction {
        public String exec(BaseFtpConnection conn,
            LinkedRemoteFileInterface file);
    }

    private interface FindOption {
        public boolean isTrueFor(LinkedRemoteFileInterface file);
    }

    private static class ActionDelete implements FindAction {
        private String doDELE(BaseFtpConnection conn,
            LinkedRemoteFileInterface file) {
            //FtpRequest request = conn.getRequest();
            // argument check
            //if (!request.hasArgument()) {
            //out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
            //return FtpReply.RESPONSE_501_SYNTAX_ERROR;
            //}
            // get filenames
            //String fileName = file.getName();
            //try {
            //requestedFile = getVirtualDirectory().lookupFile(fileName);
            //requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
            //} catch (FileNotFoundException ex) {
            //return new FtpReply(550, "File not found: " + ex.getMessage());
            //}
            // check permission
            if (file.getUsername().equals(conn.getUserNull().getUsername())) {
                if (!conn.getGlobalContext().getConnectionManager()
                             .getGlobalContext().getConfig().checkDeleteOwn(conn.getUserNull(),
                            file)) {
                    //return FtpReply.RESPONSE_530_ACCESS_DENIED;
                    return "Access denied for " + file.getPath();
                }
            } else if (!conn.getGlobalContext().getConnectionManager()
                                .getGlobalContext().getConfig().checkDelete(conn.getUserNull(),
                        file)) {
                //return FtpReply.RESPONSE_530_ACCESS_DENIED;
                return "Access denied for " + file.getPath();
            }

            //FtpReply reply = (FtpReply)
            // FtpReply.RESPONSE_250_ACTION_OKAY.clone();
            String reply = "Deleted " + file.getPath();
            User uploader;

            try {
                uploader = conn.getGlobalContext().getConnectionManager()
                               .getGlobalContext().getUserManager()
                               .getUserByName(file.getUsername());
                uploader.updateCredits((long) -(file.length() * uploader.getRatio()));
            } catch (UserFileException e) {
                reply += ("Error removing credits: " + e.getMessage());
            } catch (NoSuchUserException e) {
                reply += ("Error removing credits: " + e.getMessage());
            }

            //conn.getConnectionManager()
            //.dispatchFtpEvent(
            //new DirectoryFtpEvent(conn.getUserNull(), "DELE",
            //requestedFile));
            file.delete();

            return reply;
        }

        /*
         * (non-Javadoc)
         *
         * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
         *      net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
         */
        public String exec(BaseFtpConnection conn,
            LinkedRemoteFileInterface file) {
            return doDELE(conn, file);
        }
    }

    private static class ActionPrint implements FindAction {
        /*
         * (non-Javadoc)
         *
         * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.remotefile.LinkedRemoteFile)
         */
        public String exec(BaseFtpConnection conn,
            LinkedRemoteFileInterface file) {
            return file.getPath();
        }
    }

    class ActionPrintf implements FindAction {
        private String format;
        private String parent;
        private boolean useFormat;

        public ActionPrintf() {
            useFormat = false;
        }

        public ActionPrintf(String f) {
            format = f;

            if (format == null) {
                useFormat = false;
            } else {
                useFormat = true;
            }
        }

        public String exec(BaseFtpConnection conn,
            LinkedRemoteFileInterface file) {
            try {
                parent = file.getParent();
            } catch (FileNotFoundException e) {
                parent = "/";
            }

            String mlst = MLSTSerialize.toMLST(file);
            String retval = null;

            try {
                retval = formatMLST(mlst);
            } catch (NumberFormatException e) {
                return mlst;
            }

            return retval;
        }

        private String formatMLST(String mlst) throws NumberFormatException {
            String strDate = getValue(mlst, "modify=");
            long date = Long.parseLong(strDate.replaceAll("[.]", ""));
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d H:mm z");
            String retval = mlst.replaceAll(strDate, sdf.format(new Date(date)));
            String strSize = getValue(mlst, "size=");
            long size = Long.parseLong(strSize);
            retval = retval.replaceAll(strSize, Bytes.formatBytes(size));

            //String[] forms = retval.split(";");
            //if(forms.length < 6)
            //return retval;
            //retval = forms[1] + " | " + forms[2] + " | " + forms[3] + " | " +
            // forms[4];
            //if(forms.length == 6)
            //retval = forms[5] + " | " + retval;
            //else
            //retval = forms[6] + " | " + retval + " | " + forms[5];
            //return retval;
            //			boolean keep = false;
            //			String buff = "";
            // type=file:::size=309B:::modify=Sep 20 9:36
            // EDT:::unix.owner=drftpd:::unix.group=drftpd:::x.slaves=drftpd:::
            // 00-jet-get_born-(bonus_dvd)-2004-rns.sfv
            Matcher mlstMatch = Pattern.compile(
                    "type=(.*);size=(.*);modify=(.*);unix[.]owner=(.*);unix[.]group=(.*);.*=(.*); (.*)")
                                       .matcher(retval);

            //Pattern.compile("type=([a-z]);size=(.*);modify=(.*);[a-z]+[.]owner=(\\w+);[a-z]+[.]group=(\\w+);[a-z][.]slaves=(\\w+);
            // (.*)").matcher(retval);
            Matcher mlstDirMatch = Pattern.compile(
                    "type=(.*);size=(.*);modify=(.*);unix[.]owner=(.*);unix[.]group=(.*); (.*)")
                                          .matcher(retval);
            String mrRegex = null;

            if (mlstMatch.matches()) {
                //mrRegex = mlstMatch.group(1) + " - " + mlstMatch.group(2) + "
                //- " + mlstMatch.group(3) + " - " + mlstMatch.group(4) + " - "
                //+ mlstMatch.group(5) + " - " + mlstMatch.group(6) + " - " +
                //mlstMatch.group(7);
                if (!useFormat) {
                    mrRegex = mlstMatch.group(7) + " | " + mlstMatch.group(2) +
                        " | " + mlstMatch.group(3) + " | " +
                        mlstMatch.group(4) + " | " + mlstMatch.group(5) +
                        " | " + mlstMatch.group(6);
                } else {
                    HashMap formats = new HashMap();
                    formats.put("#f", mlstMatch.group(7));
                    formats.put("#s", mlstMatch.group(2));
                    formats.put("#u", mlstMatch.group(4));
                    formats.put("#g", mlstMatch.group(5));
                    formats.put("#t", mlstMatch.group(3));
                    formats.put("#x", mlstMatch.group(6));
                    formats.put("#h", parent);

                    Set keys = formats.keySet();
                    String temp = format;

                    for (Iterator iter = keys.iterator(); iter.hasNext();) {
                        String form = iter.next().toString();
                        temp = temp.replaceAll(form,
                                formats.get(form).toString());
                    }

                    mrRegex = temp;
                }

                return mrRegex;
            } else if (mlstDirMatch.matches()) {
                if (!useFormat) {
                    mrRegex = mlstDirMatch.group(6) + " | " +
                        mlstDirMatch.group(2) + " | " + mlstDirMatch.group(3) +
                        " | " + mlstDirMatch.group(4) + " | " +
                        mlstDirMatch.group(5);
                } else {
                    HashMap formats = new HashMap();
                    formats.put("#f", mlstDirMatch.group(6));
                    formats.put("#s", mlstDirMatch.group(2));
                    formats.put("#u", mlstDirMatch.group(4));
                    formats.put("#g", mlstDirMatch.group(5));
                    formats.put("#t", mlstDirMatch.group(3));
                    formats.put("#x", "");
                    formats.put("#h", parent);

                    Set keys = formats.keySet();
                    String temp = format;

                    for (Iterator iter = keys.iterator(); iter.hasNext();) {
                        String form = iter.next().toString();
                        temp = temp.replaceAll(form,
                                formats.get(form).toString());
                    }

                    mrRegex = temp;
                }

                return mrRegex;
            }

            return retval;
        }

        private String getValue(String main, String sub) {
            int index = main.indexOf(sub);
            int endIndex = main.indexOf(";", index + 1);
            String retval = main.substring(index + sub.length(), endIndex);

            return retval;
        }
    }

    private static class ActionWipe implements FindAction {
        /*
         * (non-Javadoc)
         *
         * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
         *      net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
         */
        public String exec(BaseFtpConnection conn,
            LinkedRemoteFileInterface file) {
            file.delete();

            return "Wiped " + file.getPath();
        }
    }

    private static class OptionGroup implements FindOption {
        private String groupname;

        public OptionGroup(String g) {
            groupname = g;
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            return file.getGroupname().equals(groupname);
        }
    }

    private static class OptionIncomplete implements FindOption {
        /*
         * (non-Javadoc)
         *
         * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
         */
        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            try {
                return !file.lookupSFVFile().getStatus().isFinished();
            } catch (Exception e) {
                return false;
            }

            /*
             * } catch(NoAvailableSlaveException e) { return false; }
             * catch(IOException e) { return false; }
             */
        }
    }

    private static class OptionOffline implements FindOption {
        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            try {
                return file.lookupSFVFile().getStatus().getOffline() != 0;
            } catch (Exception e) {
                return false;
            }

            /*
             * } catch(NoAvailableSlaveException e) { return false; }
             * catch(IOException e) { return false; }
             */
        }
    }

    private static class OptionMTime implements FindOption {
        private Date date;
        boolean after;

        public OptionMTime(int h) {
            after = true;

            if (h < 0) {
                after = false;
                h = Math.abs(h);
            }

            long t = (long) h * 24 * 60 * 60 * 1000;
            Date currentDate = new Date();
            date = new Date(currentDate.getTime() - t);
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            Date fileDate = new Date(file.lastModified());

            return after ? fileDate.after(date) : fileDate.before(date);
        }
    }

    private static class OptionName implements FindOption {
        Pattern pattern;

        public OptionName(String str) {
            pattern = Pattern.compile(str.replaceAll("[*]", ".*"));
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            Matcher m = pattern.matcher(file.getName());

            return m.matches();
        }
    }

    private static class OptionSize implements FindOption {
        boolean bigger;
        long size;

        public OptionSize(long s, boolean b) {
            bigger = b;
            size = s;
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            return bigger ? (file.length() >= size) : (file.length() <= size);
        }
    }

    private static class OptionSlave implements FindOption {
        RemoteSlave slave;

        public OptionSlave(RemoteSlave s) {
            slave = s;
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            return file.hasSlave(slave);
        }
    }

    private static class OptionType implements FindOption {
        boolean files;
        boolean dirs;

        public OptionType(boolean f, boolean d) {
            files = f;
            dirs = d;
        }

        /*
         * (non-Javadoc)
         *
         * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
         */
        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            if (files && dirs) {
                return true;
            } else if (files && !dirs) {
                return file.isFile();
            } else if (!files && dirs) {
                return file.isDirectory();
            }

            return true;
        }
    }

    private static class OptionUser implements FindOption {
        private String username;

        public OptionUser(String u) {
            username = u;
        }

        public boolean isTrueFor(LinkedRemoteFileInterface file) {
            return file.getUsername().equals(username);
        }
    }
}
