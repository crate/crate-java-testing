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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CrateTestServer extends ExternalResource {

    private final int httpPort;
    private final int transportPort;
    private final Path workingDir;
    private final String crateHost;
    private final String clusterName;
    private final String[] unicastHosts;
    private final Map<String, Object> nodeSettings;

    private ExecutorService executor;
    private Process crateProcess;

    static class Builder {
        private String host = InetAddress.getLoopbackAddress().getHostAddress();
        private int httpPort = Utils.randomAvailablePort();
        private int transportPort = Utils.randomAvailablePort();
        private Path workingDir = CrateTestCluster.TMP_WORKING_DIR;
        private String clusterName = "Testing-" + transportPort;
        private List<String> unicastHosts = new ArrayList<>();
        private Map<String, Object> nodeSettings = Collections.emptyMap();

        Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder transportPort(int transportPort) {
            this.transportPort = transportPort;
            return this;
        }

        public Builder workingDir(Path workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder addUnicastHosts(String... unicastHosts) {
            Collections.addAll(this.unicastHosts, unicastHosts);
            return this;
        }

        public Builder settings(Map<String, Object> nodeSettings) {
            this.nodeSettings = nodeSettings;
            return this;
        }

        public CrateTestServer build() {
            return new CrateTestServer(clusterName, httpPort, transportPort, workingDir, host,
                    nodeSettings, unicastHosts.toArray(new String[unicastHosts.size()]));
        }
    }

    public int httpPort() {
        return httpPort;
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

    private CrateTestServer(String clusterName,
                            int httpPort,
                            int transportPort,
                            Path workingDir,
                            String host,
                            Map<String, Object> settings,
                            String... unicastHosts) {
        this.clusterName = Utils.firstNonNull(clusterName, "Testing-" + transportPort);
        this.crateHost = host;
        this.httpPort = httpPort;
        this.transportPort = transportPort;
        this.unicastHosts = unicastHosts;
        this.workingDir = workingDir;
        this.nodeSettings = settings == null ? Collections.<String, Object>emptyMap() : settings;
    }

    @Override
    protected void before() throws Throwable {
        Utils.log("Starting crate server process...");
        executor = Executors.newFixedThreadPool(2); // new threadpool for new process instance
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

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            executor.shutdownNow();
        }
    }

    private void startCrateAsDaemon() throws IOException, InterruptedException {
        Map<String, Object> settingsMap = new HashMap<String, Object>() {{
            put("index.storage.type", "memory");
            put("network.host", crateHost);
            put("cluster.name", clusterName);
            put("http.port", httpPort);
            put("transport.tcp.port", transportPort);
            put("discovery.zen.ping.multicast.enabled", "false");
            put("discovery.zen.ping.unicast.hosts", Utils.join(unicastHosts, ","));
        }};

        settingsMap.putAll(nodeSettings);

        String[] command = new String[settingsMap.size() + 1];
        int idx = 0;

        String executable = Paths.get(workingDir.toString() , "bin" , "crate").toString();
        if (isWindows()) {
            executable = executable.concat(".bat");
        }
        command[idx++] = executable;

        for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
            command[idx++] = String.format(Locale.ENGLISH, "-Des.%s=%s", entry.getKey(), entry.getValue());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
                command
        );
        assert Files.exists(workingDir);
        processBuilder.directory(workingDir.toFile());
        processBuilder.redirectErrorStream(true);
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
        // print server stdout to stdout
        executor.submit(new Runnable() {
            @Override
            public void run() {
                InputStream is = crateProcess.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                try {
                    while (true) {

                        if (reader.ready()) {
                            System.out.println(reader.readLine());
                        } else {
                            Thread.sleep(100);
                        }
                        try {
                            crateProcess.exitValue();
                            break;
                        } catch (IllegalThreadStateException e) {
                            // fine
                        }

                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).indexOf("win")>=0;
    }

}
