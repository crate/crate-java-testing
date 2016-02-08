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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

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

    static void log(String message, Object... params) {
        System.out.println(String.format(Locale.ENGLISH, message, params));
    }

    public static boolean deleteRecursively(File root, boolean deleteRoot) {
        if (root != null) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File aChildren : children) {
                    deleteRecursively(aChildren, true);
                }
            }

            if (deleteRoot) {
                return root.delete();
            } else {
                return true;
            }
        }
        return false;
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

    public static String join(String[] items, String on) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(on);
            }
            sb.append(item);
        }
        return sb.toString();
    }

}
