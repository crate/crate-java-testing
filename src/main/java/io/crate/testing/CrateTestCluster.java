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

import io.crate.action.sql.SQLBulkResponse;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.shade.com.google.common.base.Preconditions;
import io.crate.shade.com.google.common.collect.ImmutableList;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;
import io.crate.shade.org.elasticsearch.common.settings.ImmutableSettings;
import io.crate.shade.org.elasticsearch.common.settings.Settings;
import io.crate.shade.org.elasticsearch.common.unit.TimeValue;
import org.junit.rules.ExternalResource;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.*;

public class CrateTestCluster extends ExternalResource implements TestCluster {

    private final CrateTestServer[] servers;
    private ExecutorService executor;

    private CrateTestCluster(CrateTestServer ... servers) {
        Preconditions.checkArgument(servers.length > 0, "no servers given");
        this.servers = servers;
        executor = Executors.newFixedThreadPool(1);
    }

    public static class Builder {

        private int numberOfNodes = 2;
        private String clusterName = "TestingCluster";
        private String workingDir = CrateTestServer.DEFAULT_WORKING_DIR;
        private URL downloadURL;
        private Settings settings = ImmutableSettings.EMPTY;

        public Builder(String clusterName) {
            this.clusterName = clusterName;
        }

        public Builder fromURL(String url) {
            try {
                this.fromURL(new URL(url));
            } catch (MalformedURLException e) {
                throw new RuntimeException("invalid url", e);
            }

            return this;
        }

        public Builder fromURL(URL url) {
            this.downloadURL = url;
            return this;
        }

        public Builder fromVersion(String crateVersion) {
            this.downloadURL = Utils.downLoadURL(crateVersion);
            return this;
        }

        public Builder fromFile(String pathToTarGzCrateDistribution) {
            Path tarGzPath = Paths.get(pathToTarGzCrateDistribution);
            try {
                this.downloadURL = tarGzPath.toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("invalid file path", e);
            }
            return this;
        }

        public Builder settings(Settings settings) {
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

        private CrateTestServer[] buildServers() {
            int transportPorts[] = new int[numberOfNodes];
            int httpPorts[] = new int[numberOfNodes];
            for (int i = 0; i<numberOfNodes; i++) {
                transportPorts[i] = Utils.randomAvailablePort();
                httpPorts[i] = Utils.randomAvailablePort();
            }
            String hostAddress = InetAddress.getLoopbackAddress().getHostAddress();
            CrateTestServer[] servers = new CrateTestServer[numberOfNodes];
            String[] unicastHosts = getUnicastHosts(hostAddress, transportPorts);
            for (int i = 0; i < numberOfNodes; i++) {
                servers[i] = CrateTestServer.builder()
                        .clusterName(clusterName)
                        .fromURL(downloadURL)
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

        public CrateTestCluster build() {
            Preconditions.checkArgument(downloadURL != null, "no crate version, file or download url given");
            return new CrateTestCluster(buildServers());
        }
    }

    public static Builder builder(String clusterName) {
        return new Builder(clusterName);
    }

    public static CrateTestCluster cluster(String clusterName,
                                           String crateVersion,
                                           int numberOfNodes) {
        return CrateTestCluster.builder(clusterName)
                .fromVersion(crateVersion)
                .numberOfNodes(numberOfNodes)
                .build();
    }

    private static String[] getUnicastHosts(String hostAddress, int[] transportPorts) {
        String[] result = new String[transportPorts.length];
        for (int i=0; i < transportPorts.length;i++) {
            result[i] = String.format(Locale.ENGLISH, "%s:%d", hostAddress, transportPorts[i]);
        }
        return result;
    }

    private boolean waitUntilClusterIsReady(final int timeoutMillis) {
        final long numNodes = servers.length;
        FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                while (true) {
                    try {
                        long minNumNodes = 0;
                        for (CrateTestServer server : servers) {
                            SQLResponse response = server.execute("select id from sys.nodes", TimeValue.timeValueMillis(timeoutMillis/10));
                            minNumNodes = Math.min(response.rowCount(), minNumNodes);
                        }
                        if (minNumNodes == numNodes) {
                            break;
                        }
                    } catch (NoNodeAvailableException e) {
                        // carry on
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
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
        for (CrateTestServer server : servers) {
            server.before();
        }
        waitUntilClusterIsReady(60 * 1000); // wait for 1 min max
    }

    @Override
    protected void after() {
        for (CrateTestServer server : servers) {
            server.after();
        }
    }

    public CrateTestServer randomServer() {
        return servers[ThreadLocalRandom.current().nextInt(servers.length)];
    }

    public Collection<CrateTestServer> servers() {
        return ImmutableList.<CrateTestServer>builder().add(servers).build();
    }

    public SQLResponse execute(String statement) {
        return randomServer().execute(statement, SQLRequest.EMPTY_ARGS);
    }

    public SQLResponse execute(String statement, TimeValue timeout) {
        return randomServer().execute(statement, SQLRequest.EMPTY_ARGS, timeout);
    }

    public SQLResponse execute(String statement, Object[] args) {
        return randomServer().execute(statement, args);
    }

    public SQLResponse execute(String statement, Object[] args, TimeValue timeout) {
        return randomServer().execute(statement, args, timeout);
    }

    public SQLBulkResponse execute(String statement, Object[][] bulkArgs) {
        return randomServer().execute(statement, bulkArgs);
    }

    public SQLBulkResponse execute(String statement, Object[][] bulkArgs, TimeValue timeout) {
        return randomServer().execute(statement, bulkArgs, timeout);
    }

    public void ensureYellow() {
        randomServer().ensureYellow();
    }

    public void ensureGreen() {
        randomServer().ensureGreen();
    }
}
