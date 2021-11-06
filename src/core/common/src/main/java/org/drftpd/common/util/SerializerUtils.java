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
