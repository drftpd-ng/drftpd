package org.drftpd.master.commands.usermanagement.notes;

import org.drftpd.master.commands.usermanagement.notes.metadata.NotesData;
import org.drftpd.common.dynamicdata.element.ConfigElement;

public class ConfigNotes extends ConfigElement<NotesData> {

    public ConfigNotes(NotesData value) {
        super(value);
    }
}
