package org.drftpd.common.util;

import com.google.gson.*;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.element.ConfigElement;

public class SerializerUtils {

    public static Gson getSerializer() {
        JsonSerializer<ConfigElement<?>> configSerializer = (src, typeOfSrc, context) -> src.serialize();
        JsonSerializer<Key<?>> keySerializer = (src, typeOfSrc, context) -> new JsonPrimitive(src.toString());
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(ConfigElement.class, configSerializer)
                .registerTypeAdapter(Key.class, keySerializer)
                .create();
    }

    public static Gson getDeserializer() {
        JsonDeserializer<ConfigElement<?>> configDeserializer = (json, typeOfT, context) -> {
            JsonObject jsonObject = json.getAsJsonObject();
            String className = jsonObject.get("clz").getAsString();
            try {
                Class<?> configElementClass = Class.forName(className);
                return context.deserialize(jsonObject, configElementClass);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };
        JsonDeserializer<Key<?>> keyDeserializer = (json, typeOfT, context) -> {
            String key = json.getAsJsonPrimitive().getAsString();
            return new Key<>(key);
        };
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(ConfigElement.class, configDeserializer)
                .registerTypeAdapter(Key.class, keyDeserializer)
                .create();
    }
}
