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

        /*
         * Valid command input:
         * site new
         * site new <number>
         * site new <section> <number>
         */
        SectionInterface specificSection = null;
        int count = defaultCount;

        if (request.hasArgument()) {
            StringTokenizer st = new StringTokenizer(request.getArgument());
            if (st.countTokens() > 2) {
                // invalid number of arguments.
                throw new ImproperUsageException();
            }

            while (st.hasMoreTokens()) {
                String param = st.nextToken();
                try {
                    count = Integer.parseInt(param);
                    if (count > maxCount) {
                        count = maxCount;
                    }
                    break;
                } catch (NumberFormatException e) {
                    if (specificSection != null) {
                        // 2 non integer parameters, incorrect input
                        throw new ImproperUsageException();
                    }
                    // Parameter is supposed to be a section
                    specificSection = sectionManager.getSection(param);
                    if (specificSection.getName().length() == 0 || specificSection.getName().equals("/")) {
                        // not a valid section.
                        return new CommandResponse(501, "Invalid/Non-Existant section");
                    }
                }
            }
        }

        if (specificSection != null) {
            sections.put(specificSection.getName(), specificSection);
        } else {
            sections.putAll(sectionManager.getSectionsMap());
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
            return response;
        }

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
                env.put("name", specificSection != null ? dir.getPath() : dir.getName());
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

        return response;
    }

    private static class DateComparator implements Comparator<DirectoryHandle> {
        public int compare(DirectoryHandle d1, DirectoryHandle d2) {
            long created1 = 0;
            long created2 = 0;

            try {
                // lastModified can change if a slave is remerging, which will break the contract
                created1 = d1.creationTime();
                created2 = d2.creationTime();
            } catch (FileNotFoundException e) {
                // This is valid if the directory was deleted whilst this was running.
                // This will be thrown again when building the output and the deleted dir omitted.
            }

            if (created1 == created2) {
                return 0;
            }

            return (created1 > created2) ? (-1) : 1;
        }
    }
}
