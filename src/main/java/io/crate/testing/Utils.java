/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.testing;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class Utils {

    /**
     * @return a random available port for binding
     */
    static int randomAvailablePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static int randomAvailablePort(int from, int to) {
        int repeat = 5;
        while (repeat > 0)
            try {
                int port = ThreadLocalRandom.current().nextInt(from, to + 1);
                ServerSocket socket = new ServerSocket(port);
                socket.close();
                return port;
            } catch (IOException ignored) {
                repeat--;
            }
        throw new RuntimeException("no free port found");
    }

    static void log(String message, Object... params) {
        System.out.println(String.format(Locale.ENGLISH, message, params));
    }

    public static void deletePath(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }

        });
    }

    public static String sha1(String input) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
            for (byte res : messageDigest.digest(input.getBytes())) {
                stringBuilder.append(Integer.toString((res & 0xff) + 0x100, 16).substring(1));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Hashing algorithms does not exist");
        }
        return stringBuilder.toString();
    }

    static <T> String join(T[] items, String on) {
        StringBuilder sb = new StringBuilder();
        for (T item : items) {
            if (sb.length() > 0) {
                sb.append(on);
            }
            sb.append(item.toString());
        }
        return sb.toString();
    }

    @SafeVarargs
    static <T> T firstNonNull(T... items) {
        for (T item : items) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    static void uncompressTarGZ(File tarFile, File dest) throws IOException {
        TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GzipCompressorInputStream(
                        new BufferedInputStream(
                                new FileInputStream(
                                        tarFile
                                )
                        )
                )
        );

        TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
        // tarIn is a TarArchiveInputStream
        while (tarEntry != null) {
            Path entryPath = Paths.get(tarEntry.getName());
            if (entryPath.getNameCount() == 1) {
                tarEntry = tarIn.getNextTarEntry();
                continue;
            }
            Path strippedPath = entryPath.subpath(1, entryPath.getNameCount());
            File destPath = new File(dest, strippedPath.toString());

            if (tarEntry.isDirectory()) {
                destPath.mkdirs();
            } else {
                destPath.createNewFile();
                byte[] btoRead = new byte[1024];
                try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destPath))) {
                    int len;
                    while ((len = tarIn.read(btoRead)) != -1) {
                        bout.write(btoRead, 0, len);
                    }
                }
                if (destPath.getPath().endsWith("bin/crate")) {
                    destPath.setExecutable(true);
                } else if (destPath.getPath().endsWith("/bin/java")) {
                    destPath.setExecutable(true);
                }
            }
            tarEntry = tarIn.getNextTarEntry();
        }
        tarIn.close();
    }

}
