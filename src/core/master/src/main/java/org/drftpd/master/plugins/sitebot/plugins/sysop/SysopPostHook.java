package org.drftpd.master.plugins.sitebot.plugins.sysop;


import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.plugins.sitebot.plugins.sysop.event.SysopEvent;

public class SysopPostHook {

    @CommandHook(commands = {"doPASS", "doIDNT", "doUSER"}, type = HookType.POST)
    public void doLOGINPostHook(CommandRequest request, CommandResponse response) {
        String user;
        String cmd = request.getCommand().toUpperCase();

        if (cmd.equals("IDNT") && response == null) {
            response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        }

        String code = String.valueOf(response.getCode());
        String message = cmd;
        if (cmd.equals("USER")) {
            user = request.getArgument();
        } else if (cmd.equals("PASS")) {
            user = ((BaseFtpConnection) request.getSession()).getUserNullUnchecked().getName();
            message = "LOGIN";
        } else { // IDNT
            user = response.getUser();
        }
        if ((code.startsWith("5") || code.startsWith("4")) && !code.startsWith("530") && showFailed(cmd)) {
            GlobalContext.getEventService().publishAsync(new SysopEvent(user, message, response
                            .getMessage(), true, false));
        } else if (showSuccessful(cmd)) {
            GlobalContext.getEventService().publishAsync(new SysopEvent(user, message, response
                            .getMessage(), true, true));
        }
    }

    @CommandHook(commands = {"doSITE_ADDIP", "doSITE_ADDUSER", "doSITE_BAN", "doSITE_CHANGEUSER", "doSITE_CHGRP",
			"doSITE_CHPASS", "doSITE_DELIP", "doSITE_DELUSER", "doSITE_GADDUSER", "doSITE_GIVE", "doSITE_GRPREN",
			"doSITE_KICK", "doSITE_PASSWD", "doSITE_PURGE", "doSITE_READD", "doSITE_RENUSER", "doSITE_TAGLINE",
			"doSITE_TAKE", "doSITE_UNBAN", "doSITE_CHANGEGROUP", "doSITE_ADDGROUP", "doSITE_DELGROUP",
            "doSITE_CHANGEGROUPADMIN", }, type = HookType.POST)
    public void doSITEPostHook(CommandRequest request, CommandResponse response) {
        String cmd = request.getCommand().toUpperCase();
        if (cmd.startsWith("SITE ")) {
            cmd = cmd.substring(5);
        }
        String arg = request.getArgument();
        String code = String.valueOf(response.getCode());
        String message;
        switch (cmd) {
            case "CHPASS":
            case "ADDUSER":
                int i = arg.indexOf(" ");
                if (i == -1) {
                    //Syntax check to not throw an StringIndexOutOfBoundsException
                    return;
                }
                message = cmd + " " + arg.substring(0, arg.indexOf(" "));
                break;
            case "GADDUSER":
                String[] arguments = arg.split(" ");
                if (arguments.length < 2) {
                    //Syntax check to not throw an ArrayIndexOutOfBoundsException
                    return;
                }
                message = cmd + " " + arguments[0] + " " + arguments[1];
                break;
            default:
                message = cmd + " " + arg;
                break;
        }
        if ((code.startsWith("5") || code.startsWith("4")) && !code.startsWith("530") && showFailed(cmd)) {
            GlobalContext.getEventService().publishAsync(
                    new SysopEvent(request.getUser(), message, response
                            .getMessage(), false, false));
        } else if (showSuccessful(cmd)) {
            GlobalContext.getEventService().publishAsync(
                    new SysopEvent(request.getUser(), message, response
                            .getMessage(), false, true));
        }
    }

    private boolean showSuccessful(String cmd) {
        Integer value = SysopManager.CONFIG.get(cmd);
        return value == null || value == 0 || value == 2;
    }

    private boolean showFailed(String cmd) {
        Integer value = SysopManager.CONFIG.get(cmd);
        return value == null || value == 0 || value == 3;
    }
}
