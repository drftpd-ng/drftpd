package org.drftpd.master.usermanager.util;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.commands.usermanagement.notes.metadata.NotesData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.drftpd.master.usermanager.AbstractUser.DATE_FORMAT;

/**
 * This class is just a wrapper to prevent a huge code rewrite
 * Is a way to not really use a keyedMap to save information.
 */
@SuppressWarnings("rawtypes")
public class UserMapHelper {

    private final Map<String, Object> _data;

    private UserMapHelper(Map<String, Object> data) {
        _data = data;
    }

    public static UserMapHelper umap(Map<String, Object> data) {
        return new UserMapHelper(data);
    }

    public void setObject(Key key, Object data) {
        _data.put(key.toString(), data);
    }

    public void remove(Key key) {
        _data.remove(key.toString());
    }

    public Double getObjectDouble(Key key, double defaultValue) {
        Object data = _data.get(key.toString());
        if (data != null) {
            return Double.valueOf(data.toString());
        }
        return defaultValue;
    }

    public Double getObjectDouble(Key key) {
        return getObjectDouble(key, 0);
    }

    public String getObjectString(Key key) throws KeyNotFoundException {
        Object data = _data.get(key.toString());
        if (data != null) {
            return (String) data;
        }
        throw new KeyNotFoundException();
    }

    public String getObjectString(Key key, String defaultString) {
        try {
            return getObjectString(key);
        } catch (KeyNotFoundException e) {
            return defaultString;
        }
    }

    public Date getObjectDate(Key key) throws Exception {
        Object data = _data.get(key.toString());
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        if (data != null) {
            return format.parse(data.toString());
        }
        throw new KeyNotFoundException();
    }

    public Date getObjectDate(Key key, Date defaultDate) {
        try {
            return getObjectDate(key);
        } catch (Exception e) {
            return defaultDate;
        }
    }

    public Boolean getObjectBoolean(Key key) {
        Object data = _data.get(key.toString());
        if (data != null) {
            return (Boolean) data;
        }
        return false;
    }

    public NotesData getObjectNotes(Key key) throws KeyNotFoundException {
        Object data = _data.get(key.toString());
        if (data != null) {
            return (NotesData) data;
        }
        throw new KeyNotFoundException();
    }

    public NotesData getObjectNotes(Key key, NotesData defaultNotes) {
        try {
            return getObjectNotes(key);
        } catch (KeyNotFoundException e) {
            return defaultNotes;
        }
    }

    public void incrementLong(Key key, long inc) {
        Double data = getObjectDouble(key);
        setObject(key, data + inc);
    }

    public void incrementInt(Key key, int inc) {
        Double data = getObjectDouble(key);
        setObject(key, data + inc);
    }

    public void incrementInt(Key<Integer> key) {
        incrementInt(key, 1);
    }
}
