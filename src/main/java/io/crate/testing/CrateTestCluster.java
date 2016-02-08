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

import io.crate.testing.download.DownloadSource;
import io.crate.testing.download.DownloadSources;
import org.junit.rules.ExternalResource;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

public class CrateTestCluster extends ExternalResource {

    private final int numberOfNodes;
    private final String clusterName;
    private final String workingDir;
    private final DownloadSource downloadSource;
    private final Map<String, Object> settings;
    private final String hostAddress;

    private volatile CrateTestServer[] servers;
    private ExecutorService executor;

    private CrateTestCluster(int numberOfNodes,
                             String clusterName,
                             String workingDir,
                             DownloadSource downloadSource,
                             Map<String, Object> settings,
                             String hostAddress) {
        this.numberOfNodes = numberOfNodes;
        this.clusterName = clusterName;
        this.workingDir = workingDir;
        this.downloadSource = downloadSource;
        this.settings = settings;
        this.hostAddress = hostAddress;
        if (numberOfNodes <= 0) {
            throw new IllegalArgumentException(String.format("invalid number of nodes: %d", numberOfNodes));
        }
        executor = Executors.newFixedThreadPool(1);
    }

    public static class Builder {

        private final DownloadSource downloadSource;

        private int numberOfNodes = 2;
        private String clusterName = "TestingCluster";
        private String workingDir = CrateTestServer.DEFAULT_WORKING_DIR;
        private Map<String, Object> settings = Collections.emptyMap();
        private String hostAddress = InetAddress.getLoopbackAddress().getHostAddress();

        private Builder(DownloadSource downloadSource) {
            this.downloadSource = downloadSource;
        }

        public static Builder fromURL(String url) {
            return new Builder(DownloadSources.URL(url));
        }


        public static Builder fromVersion(String crateVersion) {
            return new Builder(DownloadSources.VERSION(crateVersion));
        }

        public static Builder fromFile(String pathToTarGzCrateDistribution) {
            return new Builder(DownloadSources.FILE(pathToTarGzCrateDistribution));
        }

        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder settings(Map<String, Object> settings) {
            this.settings = settings;
            return this;
        }

        public Builder numberOfNodes(int numberOfNodes) {
            this.numberOfNodes = numberOfNodes;
            return this;
        }

        public Builder workingDir(String workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        public Builder host(String host) {
            this.hostAddress = host;
            return this;
        }

        public CrateTestCluster build() {
            if (clusterName == null) {
                throw new IllegalArgumentException("no cluster name given");
            }
            return new CrateTestCluster(numberOfNodes, clusterName, workingDir, downloadSource, settings, hostAddress);
        }
    }

    public static Builder fromURL(String downloadUrl) {
        return Builder.fromURL(downloadUrl);
    }

    public static Builder fromFile(String pathToTarGzCrateDistribution) {
        return Builder.fromFile(pathToTarGzCrateDistribution);
    }

    public static Builder fromVersion(String crateVersion) {
        return Builder.fromVersion(crateVersion);
    }

    public static CrateTestCluster cluster(String clusterName,
                                           String crateVersion,
                                           int numberOfNodes) {
        return Builder.fromVersion(crateVersion)
                .clusterName(clusterName)
                .numberOfNodes(numberOfNodes)
                .build();
    }

    private CrateTestServer[] buildServers() {
        int transportPorts[] = new int[numberOfNodes];
        int httpPorts[] = new int[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            transportPorts[i] = Utils.randomAvailablePort();
            httpPorts[i] = Utils.randomAvailablePort();
        }
        CrateTestServer[] servers = new CrateTestServer[numberOfNodes];

        String[] unicastHosts = getUnicastHosts(hostAddress, transportPorts);
        for (int i = 0; i < numberOfNodes; i++) {
            servers[i] = CrateTestServer.builder()
                    .clusterName(clusterName)
                    .fromDownloadSource(downloadSource)
                    .workingDir(workingDir)
                    .host(hostAddress)
                    .httpPort(httpPorts[i])
                    .transportPort(transportPorts[i])
                    .settings(settings)
                    .addUnicastHosts(unicastHosts)
                    .build();
        }
        return servers;
    }

    private static String[] getUnicastHosts(String hostAddress, int[] transportPorts) {
        String[] result = new String[transportPorts.length];
        for (int i = 0; i < transportPorts.length; i++) {
            result[i] = String.format(Locale.ENGLISH, "%s:%d", hostAddress, transportPorts[i]);
        }
        return result;
    }

    private boolean waitUntilClusterIsReady(final int timeoutMillis) {
        final CrateTestServer[] localServers = serversSafe();
        final long numNodes = localServers.length;
        FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (true) {
                    try {
                        long minNumNodes = 0;
                        for (CrateTestServer server : localServers) {
                            if (server.isReady()) {
                                minNumNodes++;
                            }
                        }
                        if (minNumNodes == numNodes) {
                            break;
                        }
                    } catch (Exception e) {
                    }
                    Thread.sleep(100);
                }
                return true;
            }
        });
        executor.submit(task);
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            task.cancel(true);
            return false;
        }
    }

    @Override
    protected void before() throws Throwable {
        servers = buildServers();
        for (CrateTestServer server : servers) {
            try {
                server.before();
            } catch (IllegalStateException e) {
                after(); // ensure that all testservers are shutdown (and free their port)
                throw new IllegalStateException("Crate Test Cluster not started completely", e);
            }
        }
        if (!waitUntilClusterIsReady(60 * 1000)) { // wait for 1 min max
            after(); // after is not called when an error happens here
            throw new IllegalStateException("Crate Test Cluster not started completely");
        }
    }

    @Override
    protected void after() {
        CrateTestServer[] localServers = serversSafe();
        for (CrateTestServer server : localServers) {
            server.after();
        }
        servers = null;
    }

    private CrateTestServer[] serversSafe() {
        if (servers == null) {
            throw new IllegalStateException("servers not started yet");
        }
        return servers;
    }

    public CrateTestServer randomServer() {
        CrateTestServer[] localServers = serversSafe();
        return localServers[ThreadLocalRandom.current().nextInt(localServers.length)];
    }

    public Collection<CrateTestServer> servers() {
        return Collections.unmodifiableList(Arrays.asList(serversSafe()));
    }

}
