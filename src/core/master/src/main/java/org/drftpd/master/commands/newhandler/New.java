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
package org.drftpd.master.commands.newhandler;

import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sections.SectionManagerInterface;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.util.Time;
import org.drftpd.master.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.*;


/**
 * @author zubov
 * @author fr0w
 * @version $Id$
 */
public class New extends CommandInterface {

    private ResourceBundle _bundle;

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    public CommandResponse doNEW(CommandRequest request) throws ImproperUsageException {
        int defaultCount = Integer.parseInt(request.getProperties().getProperty("default", "5"));
        int maxCount = Integer.parseInt(request.getProperties().getProperty("max", "25"));
        String sectionFilter = request.getProperties().getProperty("filtered_sections", "").trim();
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        SectionManagerInterface sectionManager = GlobalContext.getGlobalContext().getSectionManager();
        HashMap<String, SectionInterface> sections = new HashMap<>();

        // site new
        // site new <number>
        // site new <section> <number>
        SectionInterface specificSection = null;
        int count = defaultCount;
        boolean allSections = false;

        if (request.hasArgument()) {
            StringTokenizer st = new StringTokenizer(request.getArgument());
            if (st.countTokens() > 2)
                throw new ImproperUsageException(); // invalid number of arguments.

            while (st.hasMoreTokens()) {
                String parm = st.nextToken();

                if (isInteger(parm)) { // found a number.
                    count = Integer.parseInt(parm);

                    if (count > maxCount)
                        count = maxCount;

                    if (specificSection == null) { // not section specified, adding all.
                        sections.putAll(sectionManager.getSectionsMap());
                        allSections = true;
                    }

                    break; // nothing else to do.
                }
                specificSection = sectionManager.getSection(parm);
                if (specificSection.getName().equals("")) { // not a valid section.
                    return new CommandResponse(501, "Invalid/Non-Existant section");
                }
                sections.put(specificSection.getName(), specificSection);
            }
        } else {
            // no param found. setting default values.
            sections.putAll(sectionManager.getSectionsMap());
            allSections = true;
        }

        if (allSections) {
            for (String s : sectionFilter.split(" ")) {
                sections.remove(s);
            }
        }

        User user = request.getSession().getUserNull(request.getUser());

        // Collect all dirs from all sections
        ArrayList<DirectoryHandle> directories = new ArrayList<>();
        for (SectionInterface section : sections.values()) {
            try {
                directories.addAll(section.getCurrentDirectory().getDirectories(user));
            } catch (FileNotFoundException e) {
                // Will happen if a section is rm'd whilst this is running
            }
        }

        Map<String, Object> env = new HashMap<>();
        if (directories.size() == 0) {
            response.addComment(request.getSession().jprintf(_bundle, "new.empty", env, request.getUser()));
        } else {
            directories.sort(new DateComparator());

            response.addComment(request.getSession().jprintf(_bundle, "header", env, request.getUser()));

            // Print the reply!
            int pos = 1;

            for (Iterator<DirectoryHandle> iter = directories.iterator(); iter.hasNext() && (pos <= count); pos++) {
                try {
                    DirectoryHandle dir = iter.next();
                    if (dir.isHidden(user)) {
                        // User do not have access to this dir, skip and decrement pos.
                        pos--;
                        continue;
                    }
                    env.put("pos", "" + pos);
                    env.put("name", allSections ? dir.getPath() : dir.getName());
                    SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
                    env.put("section", section.getName());
                    env.put("sectioncolor", section.getColor());
                    env.put("diruser", dir.getUsername());
                    env.put("files", "" + dir.getInodeHandles(user).size());
                    env.put("size", Bytes.formatBytes(dir.getSize()));
                    env.put("age", Time.formatTime(System.currentTimeMillis() - dir.lastModified()));
                    response.addComment(request.getSession().jprintf(_bundle, "new", env, request.getUser()));
                } catch (FileNotFoundException e) {
                    // Directory was deleted whilst this was running, simply omit the dir
                    // Decrement pos to account for the directory we were forced to skip
                    pos--;
                }
            }
            response.addComment(request.getSession().jprintf(_bundle, "footer", env, request.getUser()));
        }

        return response;
    }

    private static class DateComparator implements Comparator<DirectoryHandle> {
        public int compare(DirectoryHandle d1, DirectoryHandle d2) {
            long lastModified1 = 0;
            long lastModified2 = 0;

            try {
                lastModified1 = d1.lastModified();
                lastModified2 = d2.lastModified();
            } catch (FileNotFoundException e) {
                // This is valid if the directory was deleted whilst this was running.
                // This will be thrown again when building the output and the deleted dir omitted.
            }

            if (lastModified1 == lastModified2) {
                return 0;
            }

            return (lastModified1 > lastModified2) ? (-1) : 1;
        }
    }
}
