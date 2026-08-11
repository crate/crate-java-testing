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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        VersionDownloadSourceTest.PlatformTest.class,
        VersionDownloadSourceTest.BuildDownloadUrlTest.class,
        VersionDownloadSourceTest.BuildDownloadUrlExceptionsTest.class
})
public class VersionDownloadSourceTest {

    @RunWith(Parameterized.class)
    public static class PlatformTest {
        @Parameterized.Parameters(name = "OS Name: {0}, Arch: {1}, Expected: {2}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "Mac OS X", "arm", "aarch64_mac" },
                    { "Mac OS X", "aarch64", "aarch64_mac" },
                    { "Linux", "x86_64", "x64_linux" },
                    { "Windows 10", "amd64", "x64_windows" },
            });
        }

        private String osName;
        private String arch;
        private String expected;

        public PlatformTest(String osName, String arch, String expected) {
            this.osName = osName;
            this.arch = arch;
            this.expected = expected;
        }

        @Test
        public void testPlatform() {
            assertThat(VersionDownloadSource.platform(osName, arch), is(expected));
        }

    }

    @RunWith(Parameterized.class)
    public static class BuildDownloadUrlTest {
        @Parameterized.Parameters(name = "Version: {0}, Platform: {1}, Expected: {2}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "6.4.0", "aarch64_mac",
                            "https://cdn.crate.io/downloads/releases/cratedb/x64_mac/crate-6.4.0.tar.gz" },
                    { "6.4.0", "aarch64_linux",
                            "https://cdn.crate.io/downloads/releases/cratedb/aarch64_linux/crate-6.4.0.tar.gz" },
                    { "6.4.0", "x64_windows",
                            "https://cdn.crate.io/downloads/releases/cratedb/x64_windows/crate-6.4.0.tar.gz" },
                    { "6.4.0", "x64_linux",
                            "https://cdn.crate.io/downloads/releases/cratedb/x64_linux/crate-6.4.0.tar.gz" },
                    { "latest", "aarch64_mac",
                            "https://cdn.crate.io/downloads/releases/nightly/aarch64_mac/crate-latest.tar.gz" },
                    { "latest", "aarch64_linux",
                            "https://cdn.crate.io/downloads/releases/nightly/aarch64_linux/crate-latest.tar.gz" },
                    { "latest", "x64_linux",
                            "https://cdn.crate.io/downloads/releases/nightly/crate-latest.tar.gz" },
            });
        }

        private String version;
        private String platform;
        private String expected;

        public BuildDownloadUrlTest(String version, String platform, String expected) {
            this.version = version;
            this.platform = platform;
            this.expected = expected;
        }

        @Test
        public void testBuildDownloadUrl() throws Exception {
            assertThat(VersionDownloadSource.buildDownloadUrl(version, platform).toString(), is(expected));
        }
    }

    @RunWith(Parameterized.class)
    public static class BuildDownloadUrlExceptionsTest {
        @Parameterized.Parameters(name = "Version: {0}, Platform: {1}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "latest", "x64_windows" },
                    { "6.4.0", "x64_sunos" }
            });
        }

        private String version;
        private String platform;

        public BuildDownloadUrlExceptionsTest(String version, String platform) {
            this.version = version;
            this.platform = platform;
        }

        @Test(expected = java.net.MalformedURLException.class)
        public void testBuildDownloadUrlException() throws Exception {
            VersionDownloadSource.buildDownloadUrl(version, platform);
        }
    }

}
