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

import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CrateTestServer extends ExternalResource {

    private static final CrateVersion MIN_C_OPTION_VERSION = new CrateVersion("1.0.0");

    private final int httpPort;
    private final int transportPort;
    private final int psqlPort;
    private final Path workingDir;
    private final String crateHost;
    private final String clusterName;
    private final String[] unicastHosts;
    private final Map<String, Object> nodeSettings;
    private final String crateVersion;

    private Process crateProcess;


    public int httpPort() {
        return httpPort;
    }

    public int psqlPort() {
        return psqlPort;
    }

    public int transportPort() {
        return transportPort;
    }

    public String crateHost() {
        return crateHost;
    }

    public String clusterName() {
        return clusterName;
    }

    public CrateTestServer(String clusterName,
                           int httpPort,
                           int transportPort,
                           int psqlPort,
                           Path workingDir,
                           String host,
                           Map<String, Object> settings,
                           String crateVersion,
                           String... unicastHosts) {
        this.clusterName = Utils.firstNonNull(clusterName, "Testing-" + transportPort);
        this.crateHost = host;
        this.httpPort = httpPort;
        this.psqlPort = psqlPort;
        this.transportPort = transportPort;
        this.unicastHosts = unicastHosts;
        this.workingDir = workingDir;
        this.nodeSettings = settings == null ? Collections.<String, Object>emptyMap() : settings;
        this.crateVersion = crateVersion;
    }

    @Override
    protected void before() throws Throwable {
        Utils.log("Starting crate server process...");
        startCrateAsDaemon();
    }

    @Override
    protected void after() {
        Utils.log("Stopping crate server process...");
        if (crateProcess != null) {
            try {
                crateProcess.destroy();
                crateProcess.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startCrateAsDaemon() throws IOException, InterruptedException {
        Map<String, Object> settingsMap = new HashMap<String, Object>() {{
            put("index.storage.type", "memory");
            put("network.host", crateHost);
            put("cluster.name", clusterName);
            put("http.port", httpPort);
            put("psql.port", psqlPort);
            put("psql.enabled", true);
            put("transport.tcp.port", transportPort);
            put("discovery.zen.ping.multicast.enabled", "false");
            put("discovery.zen.ping.unicast.hosts", Utils.join(unicastHosts, ","));
        }};

        settingsMap.putAll(nodeSettings);

        String[] command = new String[settingsMap.size() + 1];
        int idx = 0;

        String executable = Paths.get(workingDir.toString(), "bin", "crate").toString();
        if (isWindows()) {
            executable = executable.concat(".bat");
        }
        command[idx++] = executable;

        String settingPrefix = MIN_C_OPTION_VERSION.gt(crateVersion) ? "-Des" : "-C";
        for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
            command[idx++] = String.format(Locale.ENGLISH, "%s.%s=%s", settingPrefix, entry.getKey(), entry.getValue());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        assert Files.exists(workingDir);
        processBuilder.directory(workingDir.toFile());
        processBuilder.inheritIO();
        crateProcess = processBuilder.start();


        // shut down crate process when JVM is cancelled
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Process localProcess = crateProcess;
                    if (localProcess != null) {
                        localProcess.destroy();
                    }
                } catch (Throwable t) {
                    // ignore
                }
            }
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }
}
