package org.drftpd.common.dynamicdata.element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class ConfigElement<T> {
    private T value;
    public ConfigElement(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public JsonElement serialize() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject object = new JsonObject();
        object.addProperty("clz", this.getClass().getCanonicalName());
        object.add("value", gson.toJsonTree(getValue(), getValue().getClass()));
        return object;
    }
}
