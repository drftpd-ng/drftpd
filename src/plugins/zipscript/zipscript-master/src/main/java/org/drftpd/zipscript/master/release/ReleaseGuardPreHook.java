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
package org.drftpd.zipscript.master.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.LinkHandle;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.release.ReleaseRules.Kind;
import org.drftpd.zipscript.master.release.ReleaseRules.ReleaseDir;
import org.drftpd.zipscript.master.release.ReleaseRules.State;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Pre hooks for the opt-in 'downloadinc' and 'deletecomplete' path rules (#595).
 * <ul>
 *   <li>'downloadinc': RETR of a file is denied while its release is not complete;</li>
 *   <li>'deletecomplete': DELE and SITE WIPE of anything inside a complete release is denied.</li>
 * </ul>
 * Users allowed by the line matching the path are exempt. Without a matching line nothing changes.
 * If the release state cannot be determined (slave offline, unreadable sfv) the command is allowed.
 */
public class ReleaseGuardPreHook {

    private static final Logger logger = LogManager.getLogger(ReleaseGuardPreHook.class);

    @CommandHook(commands = "doRETR", priority = 10, type = HookType.PRE)
    public CommandRequestInterface doRETRPreCheck(CommandRequest request) {
        if (!request.hasArgument()) {
            return request;
        }
        User user = request.getSession().getUserNull(request.getUser());
        try {
            InodeHandle inode = request.getCurrentDirectory().getInodeHandle(request.getArgument(), user);
            if (inode.isLink()) {
                inode = ((LinkHandle) inode).getTargetInodeUnchecked();
            }
            if (!(inode instanceof FileHandle)) {
                // directory, let RETR deal with it
                return request;
            }
            DirectoryHandle parent = inode.getParent();
            if (isExempt(ReleaseRules.DOWNLOAD_INCOMPLETE, user, parent)) {
                return request;
            }
            if (ReleaseRules.denyDownload(stateOfReleaseFor(parent), false)) {
                request.setAllowed(false);
                request.setDeniedResponse(new CommandResponse(550,
                        "Requested action not taken. Release is not complete yet."));
            }
        } catch (FileNotFoundException e) {
            // let RETR report it
        }
        return request;
    }

    @CommandHook(commands = {"doDELE", "doSITE_WIPE"}, priority = 10, type = HookType.PRE)
    public CommandRequestInterface doDeletePreCheck(CommandRequest request) {
        if (!request.hasArgument()) {
            return request;
        }
        String arg = request.getArgument();
        if (arg.startsWith("-r ")) {
            arg = arg.substring(3);
        }
        User user = request.getSession().getUserNull(request.getUser());
        try {
            InodeHandle inode = request.getCurrentDirectory().getInodeHandle(arg, user);
            boolean denied = isDeniedByDeleteRule(user, inode.getParent());
            if (!denied && inode.isLink()) {
                // SITE WIPE removes the link target as well
                InodeHandle target = ((LinkHandle) inode).getTargetInodeUnchecked();
                denied = isDeniedByDeleteRule(user, target.getParent());
            }
            if (denied) {
                request.setAllowed(false);
                request.setDeniedResponse(new CommandResponse(550,
                        "Requested action not taken. Release is complete, it cannot be modified."));
            }
        } catch (FileNotFoundException e) {
            // let the command report it
        }
        return request;
    }

    private static boolean isDeniedByDeleteRule(User user, DirectoryHandle parent) {
        if (isExempt(ReleaseRules.DELETE_COMPLETE, user, parent)) {
            return false;
        }
        return ReleaseRules.denyDelete(stateOfReleaseFor(parent), false);
    }

    /**
     * @return true if no line of the directive matches the path (rule not active here), or the user is allowed
     * by the line that matches (first match wins). This is checked first so that sites without the rule
     * never touch the VFS or the slaves.
     */
    private static boolean isExempt(String directive, User user, DirectoryHandle dir) {
        return GlobalContext.getConfig().checkPathPermission(directive, user, dir, true);
    }

    private static State stateOfReleaseFor(DirectoryHandle dir) {
        ReleaseDir release = ReleaseRules.locate(new Dir(dir));
        if (release == null) {
            return State.UNKNOWN;
        }
        return ((Dir) release).state();
    }

    /** {@link ReleaseDir} backed by the VFS. */
    private static final class Dir implements ReleaseDir {
        private final DirectoryHandle _dir;

        private Dir(DirectoryHandle dir) {
            _dir = dir;
        }

        @Override
        public Kind kind() {
            boolean zip = false;
            try {
                for (FileHandle file : _dir.getFilesUnchecked()) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".sfv")) {
                        return Kind.SFV;
                    }
                    if (name.endsWith(".zip")) {
                        zip = true;
                    }
                }
            } catch (FileNotFoundException e) {
                return null;
            }
            return zip ? Kind.ZIP : null;
        }

        @Override
        public ReleaseDir parent() {
            return _dir.isRoot() ? null : new Dir(_dir.getParent());
        }

        private State state() {
            try {
                if (kind() == Kind.SFV) {
                    SFVStatus status = new ZipscriptVFSDataSFV(_dir).getSFVStatus();
                    return ReleaseRules.stateOf(status.getPresent() + status.getMissing(), status.getMissing());
                }
                DizStatus status = new ZipscriptVFSDataZip(_dir).getDizStatus();
                return ReleaseRules.stateOf(status.getPresent() + status.getMissing(), status.getMissing());
            } catch (IOException | NoAvailableSlaveException | SlaveUnavailableException e) {
                logger.debug("Release state of {} unknown, release rules skipped", _dir.getPath(), e);
                return State.UNKNOWN;
            }
        }
    }
}
