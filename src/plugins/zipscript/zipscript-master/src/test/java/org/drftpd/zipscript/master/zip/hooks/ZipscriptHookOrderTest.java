package org.drftpd.zipscript.master.zip.hooks;

import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.zipscript.master.sfv.hooks.ZipscriptPostHook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zip integrity check deletes a bad zip, so it has to run before the hook that publishes the
 * transfer event (and with it the NEW and COMPLETE announces) for the file.
 */
class ZipscriptHookOrderTest {

    private static int priority(Class<?> type, String method) throws NoSuchMethodException {
        Method m = type.getMethod(method, CommandRequest.class, CommandResponse.class);
        return m.getAnnotation(CommandHook.class).priority();
    }

    @Test
    void zipIntegrityCheckRunsBeforeSfvPostCheck() throws NoSuchMethodException {
        int integrity = priority(ZipscriptZipPostHook.class, "doZipscriptSTORZipPostCheck");
        int sfv = priority(ZipscriptPostHook.class, "doZipscriptSTORPostCheck");
        assertTrue(integrity < sfv, "integrity " + integrity + " must be below sfv " + sfv);
    }
}
