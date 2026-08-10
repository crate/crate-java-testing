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

package io.crate.testing.download;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

class VersionDownloadSource implements DownloadSource {

    public static final String RELEASE_PLATFORM_URL = "https://cdn.crate.io/downloads/releases/cratedb/%s/crate-%s.tar.gz";
    public static final String NIGHTLY_URL = "https://cdn.crate.io/downloads/releases/nightly/crate-latest.tar.gz";
    public static final String NIGHTLY_PLATFORM_URL = "https://cdn.crate.io/downloads/releases/nightly/%s/crate-latest.tar.gz";
    public static final String X64_LINUX = "x64_linux";
    public static final String X64_WINDOWS = "x64_windows";
    public static final String AARCH64_LINUX = "aarch64_linux";
    public static final String AARCH64_MAC = "aarch64_mac";

    private final String version;
    private final String folderName;

    public VersionDownloadSource(String version) {
        this.version = version;
        this.folderName = String.format(Locale.ENGLISH, "crate-%s", version);
    }

    @Override
    public File folder(File containingFolder) {
        return new File(containingFolder, folderName);
    }

    @Override
    public URL downloadUrl() throws MalformedURLException {
        return buildDownloadUrl(this.version, platform(
                System.getProperty("os.name"),
                System.getProperty("os.arch")));
    }

    static URL buildDownloadUrl(String version, String platform) throws MalformedURLException {
        if (version.equals("latest") || version.equals("nightly")) {
            switch (platform) {
                case AARCH64_MAC:
                case AARCH64_LINUX:
                    return new URL(String.format(Locale.ENGLISH, NIGHTLY_PLATFORM_URL, platform));
                case X64_LINUX:
                    return new URL(NIGHTLY_URL);
                default:
                    throw new MalformedURLException(String.format("Platform %s not supported", platform));
            }
        }
        switch (platform) {
            // There are currently no aarch64_mac releases besides nightly. Fallback to x64_mac release
            // which needs an emulation layer (i.e. Rosetta 2)
            case AARCH64_MAC:
                return new URL(String.format(Locale.ENGLISH, RELEASE_PLATFORM_URL, "x64_mac", version));
            case AARCH64_LINUX:
            case X64_WINDOWS:
            case X64_LINUX:
                return new URL(String.format(Locale.ENGLISH, RELEASE_PLATFORM_URL, platform, version));
            default:
                throw new MalformedURLException(String.format("Platform %s not supported", platform));
        }
    }

    static String platform(String rawOsName, String rawArchName) {
        String archName = rawArchName.toLowerCase();
        String osName = rawOsName.toLowerCase();
        String os;
        String arch;

        if (archName.equals("arm") || archName.equals("aarch64")) {
            arch = "aarch64";
        } else {
            arch = "x64";
        }

        if (osName.equals("mac os x")) {
            os = "mac";
        } else if (osName.startsWith("windows")) {
            os = "windows";
        } else {
            os = osName;
        }
        return arch + "_" + os;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "VERSION[%s]", version);
    }
}
