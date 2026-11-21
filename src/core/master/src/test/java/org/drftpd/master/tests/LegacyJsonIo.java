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
package org.drftpd.master.tests;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializes objects with the OLD json-io 4.14 (dist/json-io-4.14.0.jar), the version that wrote the
 * userfiles, groupfiles and VFS inodes found on sites that upgrade.
 * <p>
 * The new json-io on the classpath keeps classes with the same names in com.cedarsoftware.util.io, so
 * the old jar is loaded child-first in its own class loader that only serves com.cedarsoftware.*
 * and delegates everything else (drftpd classes, java.*) to the parent. The old writer therefore
 * serializes the very same drftpd objects the test created.
 */
public final class LegacyJsonIo implements Closeable {

    public static final String JAR = "dist/json-io-4.14.0.jar";

    private static final String WRITER = "com.cedarsoftware.util.io.JsonWriter";

    private final ChildFirstLoader _loader;

    private final Class<?> _writerClass;

    public LegacyJsonIo(Path jar, ClassLoader parent) throws IOException, ClassNotFoundException {
        _loader = new ChildFirstLoader(jar.toUri().toURL(), parent);
        _writerClass = _loader.loadClass(WRITER);
        if (_writerClass.getClassLoader() != _loader) {
            throw new IllegalStateException(WRITER + " was not loaded from " + jar);
        }
    }

    /**
     * Walks up from the working directory (the module dir under Maven) to find dist/json-io-4.14.0.jar.
     */
    public static Path locateJar() {
        Path start = Paths.get("").toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(JAR);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find " + JAR + " in " + start + " or any parent directory");
    }

    /**
     * The class loader the old JsonWriter came from.
     */
    public ClassLoader getLoader() {
        return _loader;
    }

    public Class<?> getWriterClass() {
        return _writerClass;
    }

    /**
     * Writes obj the way drftpd wrote it with 4.14:
     * {@code params.put(JsonWriter.PRETTY_PRINT, true); new JsonWriter(out, params).write(obj)}.
     */
    public void write(Object obj, OutputStream out) throws IOException {
        try {
            String prettyPrint = (String) _writerClass.getField("PRETTY_PRINT").get(null);
            Map<String, Object> params = new HashMap<>();
            params.put(prettyPrint, true);
            Constructor<?> ctor = _writerClass.getConstructor(OutputStream.class, Map.class);
            Method write = _writerClass.getMethod("write", Object.class);
            Method close = _writerClass.getMethod("close");
            Object writer = ctor.newInstance(out, params);
            try {
                write.invoke(writer, obj);
            } finally {
                close.invoke(writer);
            }
        } catch (InvocationTargetException e) {
            throw new IOException("json-io 4.14 failed to write " + obj, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unexpected json-io 4.14 API", e);
        }
    }

    public String toJson(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(obj, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        _loader.close();
    }

    /**
     * Serves com.cedarsoftware.* only from the given jar; everything else goes to the parent.
     */
    static final class ChildFirstLoader extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }

        ChildFirstLoader(URL jar, ClassLoader parent) {
            super(new URL[]{jar}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.cedarsoftware.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    // Never fall back to the parent: that would silently hand out the new json-io
                    c = findClass(name);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        @Override
        public URL getResource(String name) {
            if (name.startsWith("com/cedarsoftware/")) {
                return findResource(name);
            }
            return super.getResource(name);
        }
    }
}
