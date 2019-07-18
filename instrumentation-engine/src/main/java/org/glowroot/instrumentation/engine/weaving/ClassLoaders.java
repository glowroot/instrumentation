/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.engine.weaving;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Closer;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClassLoaders {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaders.class);

    private static final Object lock = new Object();

    private ClassLoaders() {}

    static void defineClassesInBootstrapClassLoader(Collection<LazyDefinedClass> lazyDefinedClasses,
            Instrumentation instrumentation, File tmpDir, String fileNamePrefix)
            throws IOException {

        if (lazyDefinedClasses.isEmpty()) {
            return;
        }
        // using a flattened and ordered lists so that unique hash and file contents will be the
        // same regardless or ordering
        Collection<LazyDefinedClass> flattenedAndOrderedList =
                getFlattenedAndOrderedList(lazyDefinedClasses);

        String uniqueHash = getUniqueHash(flattenedAndOrderedList);
        File generatedJarFile = new File(tmpDir, fileNamePrefix + uniqueHash + ".jar");
        if (!generatedJarFile.exists()) {
            File tmpFile = createTempFile(fileNamePrefix + uniqueHash, ".jar", tmpDir);
            Closer closer = Closer.create();
            try {
                FileOutputStream out = closer.register(new FileOutputStream(tmpFile));
                JarOutputStream jarOut = closer.register(new JarOutputStream(out));
                generate(flattenedAndOrderedList, jarOut);
            } catch (Throwable t) {
                throw closer.rethrow(t);
            } finally {
                closer.close();
            }
            if (!tmpFile.renameTo(generatedJarFile)) {
                if (!generatedJarFile.exists()) {
                    logger.warn("could not rename file {} to {}", tmpFile.getAbsolutePath(),
                            generatedJarFile.getAbsolutePath());
                    // use tmpFile instead
                    generatedJarFile = tmpFile;
                } else if (!tmpFile.delete()) {
                    logger.warn("could not delete file {}", tmpFile.getAbsolutePath());
                }
            }
        }
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(generatedJarFile));
        // appendToBootstrapClassLoaderSearch() line above does not add to the bootstrap resource
        // search path, only to the bootstrap class search path (this is different from
        // appendToSystemClassLoaderSearch() which adds to both the system resource search path and
        // the system class search path)
        //
        // adding the generated jar file to the bootstrap resource search path is probably needed
        // more generally, but it is at least needed to support jboss 4.2.0 - 4.2.3 because
        // org.jboss.mx.loading.LoadMgr3.beginLoadTask() checks that the class loader has the class
        // as a resource before loading it, so without adding the generated jar file to the
        // bootstrap resource search path, jboss ends up throwing ClassNotFoundException for the
        // generated classes that have been added to the bootstrap class loader search path
        // (see issue #101 for more info on this particular jboss issue)
        appendToBootstrapResourcePath(generatedJarFile);
    }

    static void defineClasses(Collection<LazyDefinedClass> lazyDefinedClasses, ClassLoader loader)
            throws Exception {
        for (LazyDefinedClass lazyDefinedClass : lazyDefinedClasses) {
            defineClass(lazyDefinedClass, loader);
        }
    }

    static void defineClassIfNotExists(LazyDefinedClass lazyDefinedClass, ClassLoader loader)
            throws Exception {
        defineClassIfNotExists(lazyDefinedClass, loader, new HashSet<Type>());
    }

    static void defineClass(String name, byte[] bytes, ClassLoader loader) throws Exception {
        Method defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass",
                String.class, byte[].class, int.class, int.class);
        defineClassMethod.setAccessible(true);
        defineClassMethod.invoke(loader, name, bytes, 0, bytes.length);
    }

    static void createDirectoryOrCleanPreviousContentsWithPrefix(File dir, String prefix)
            throws IOException {
        deleteIfRegularFile(dir);
        if (dir.exists()) {
            deleteFilesWithPrefix(dir, prefix);
        } else {
            createDirectory(dir);
        }
    }

    private static void defineClass(LazyDefinedClass lazyDefinedClass, ClassLoader loader)
            throws Exception {
        for (LazyDefinedClass dependency : lazyDefinedClass.getDependencies()) {
            defineClass(dependency, loader);
        }
        defineClass(lazyDefinedClass.getType().getClassName(), lazyDefinedClass.getBytes(), loader);
    }

    private static void defineClassIfNotExists(LazyDefinedClass lazyDefinedClass,
            ClassLoader loader, Set<Type> alreadyDefinedOrToBeDefined) throws Exception {
        if (!alreadyDefinedOrToBeDefined.add(lazyDefinedClass.getType())) {
            // this is to deal with circular references between lazy defined classes since they are
            // now cached/shared across advice
            return;
        }
        for (LazyDefinedClass dependency : lazyDefinedClass.getDependencies()) {
            defineClassIfNotExists(dependency, loader, alreadyDefinedOrToBeDefined);
        }
        String className = lazyDefinedClass.getType().getClassName();
        // synchronized block is needed to guard against race condition (for class loaders that
        // support concurrent class loading), otherwise can have two threads evaluate !classExists,
        // and both try to defineClass, leading to one of them getting java.lang.LinkageError:
        // "attempted duplicate class definition for name"
        //
        // deadlock should not be possible here since ifNotExists is only called from Weaver, and
        // ClassFileTransformers are not re-entrant, so defineClass() should be self contained
        synchronized (lock) {
            if (!classExists(className, loader)) {
                defineClass(className, lazyDefinedClass.getBytes(), loader);
            }
        }
    }

    private static boolean classExists(String name, ClassLoader loader) throws Exception {
        Method findLoadedClassMethod =
                ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
        findLoadedClassMethod.setAccessible(true);
        return findLoadedClassMethod.invoke(loader, name) != null;
    }

    private static void generate(Collection<LazyDefinedClass> flattenedAndOrderedList,
            JarOutputStream jarOut) throws IOException {
        for (LazyDefinedClass lazyDefinedClass : flattenedAndOrderedList) {
            JarEntry jarEntry =
                    new JarEntry(lazyDefinedClass.getType().getInternalName() + ".class");
            jarOut.putNextEntry(jarEntry);
            jarOut.write(lazyDefinedClass.getBytes());
            jarOut.closeEntry();
        }
    }

    private static void deleteIfRegularFile(File file) throws IOException {
        if (file.isFile() && !file.delete()) {
            logger.debug("could not delete file: " + file.getAbsolutePath());
        }
    }

    private static void deleteFilesWithPrefix(File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            // strangely, listFiles() returns null if an I/O error occurs
            throw new IOException("Could not get listing for directory: " + dir.getAbsolutePath());
        }
        for (File file : files) {
            if (file.getName().startsWith(prefix) && !file.delete()) {
                logger.debug("could not delete file: " + file.getAbsolutePath());
            }
        }
    }

    private static void createDirectory(File dir) throws IOException {
        if (!dir.mkdirs()) {
            throw new IOException("Could not create directory: " + dir.getAbsolutePath());
        }
    }

    private static void appendToBootstrapResourcePath(File generatedJarFile) {
        try {
            Class<?> launcherClass = Class.forName("sun.misc.Launcher", false, null);
            Method getBootstrapClassPathMethod = launcherClass.getMethod("getBootstrapClassPath");
            Class<?> urlClassPathClass = Class.forName("sun.misc.URLClassPath", false, null);
            Method addUrlMethod = urlClassPathClass.getMethod("addURL", URL.class);
            Object urlClassPath = getBootstrapClassPathMethod.invoke(null);
            addUrlMethod.invoke(urlClassPath, generatedJarFile.toURI().toURL());
        } catch (Exception e) {
            // NOTE sun.misc.Launcher no longer exists in Java 9
            logger.debug(e.getMessage(), e);
        }
    }

    private static Collection<LazyDefinedClass> getFlattenedAndOrderedList(
            Collection<LazyDefinedClass> lazyDefinedClasses) {
        Map<String, LazyDefinedClass> flattenedMap = Maps.newTreeMap();
        put(flattenedMap, lazyDefinedClasses);
        return flattenedMap.values();
    }

    private static void put(Map<String, LazyDefinedClass> flattenedMap,
            Collection<LazyDefinedClass> lazyDefinedClasses) {
        for (LazyDefinedClass lazyDefinedClass : lazyDefinedClasses) {
            flattenedMap.put(lazyDefinedClass.getType().getInternalName(), lazyDefinedClass);
            put(flattenedMap, lazyDefinedClass.getDependencies());
        }
    }

    // not using File.createTempFile() because that calls SecureRandom under the hood which can be
    // really slow the first time it's used (and so can have a big impact on startup time)
    private static File createTempFile(String prefix, String suffix, File tmpDir)
            throws IOException {
        String baseName = prefix + "-" + System.currentTimeMillis() + "-";
        for (int i = 0; i < 10000; i++) {
            File tempFile = new File(tmpDir, baseName + i + suffix);
            if (tempFile.createNewFile()) {
                return tempFile;
            }
        }
        throw new IOException("Could not create temp file, tried " + baseName + "0" + suffix
                + " through " + baseName + "999" + suffix);
    }

    private static String getUniqueHash(Collection<LazyDefinedClass> flattened) {
        Hasher hasher = Hashing.sha1().newHasher();
        for (LazyDefinedClass lazyDefinedClass : flattened) {
            hasher.putBytes(lazyDefinedClass.getBytes());
        }
        return hasher.hash().toString();
    }

    static class LazyDefinedClass {

        private final Type type;
        private final byte[] bytes;
        private final Set<LazyDefinedClass> dependencies = Sets.newConcurrentHashSet();

        LazyDefinedClass(String internalName, byte[] bytes) {
            this.type = Type.getObjectType(internalName);
            this.bytes = bytes;
        }

        public Type getType() {
            return type;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public Set<LazyDefinedClass> getDependencies() {
            return dependencies;
        }
    }
}
