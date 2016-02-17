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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.crate.testing.download.DownloadSource;
import io.crate.testing.download.DownloadSources;
import org.junit.rules.ExternalResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

public class CrateTestCluster extends ExternalResource {

    private final int numberOfNodes;
    private final String clusterName;
    private final String workingDir;
    private final DownloadSource downloadSource;
    private final Map<String, Object> settings;
    private final String hostAddress;
    private final boolean keepWorkingDir;

    private volatile CrateTestServer[] servers;

    private CrateTestCluster(int numberOfNodes,
                             String clusterName,
                             String workingDir,
                             DownloadSource downloadSource,
                             Map<String, Object> settings,
                             String hostAddress,
                             boolean keepWorkingDir) {
        this.numberOfNodes = numberOfNodes;
        this.clusterName = clusterName;
        this.workingDir = workingDir;
        this.downloadSource = downloadSource;
        this.settings = settings;
        this.hostAddress = hostAddress;
        this.keepWorkingDir = keepWorkingDir;
    }

    public static class Builder {

        private final DownloadSource downloadSource;

        private int numberOfNodes = 1;
        private String clusterName = "TestingCluster";
        private String workingDir = CrateTestServer.TMP_WORKING_DIR.toString();
        private Map<String, Object> settings = Collections.emptyMap();
        private String hostAddress = InetAddress.getLoopbackAddress().getHostAddress();
        private boolean keepWorkingDir = false;

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
            if (numberOfNodes <= 0) {
                throw new IllegalArgumentException(String.format("invalid number of nodes: %d", numberOfNodes));
            }
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

        public Builder keepWorkingDir(boolean keepWorkingDir) {
            this.keepWorkingDir = keepWorkingDir;
            return this;
        }

        public CrateTestCluster build() {
            return new CrateTestCluster(numberOfNodes, clusterName, workingDir, downloadSource, settings, hostAddress, keepWorkingDir);
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

    public static Builder fromSysProperties() {
        String version = System.getProperty("crate.testing.from_version");
        String url = System.getProperty("crate.testing.from_url");

        if (version != null && !version.trim().isEmpty()) {
            return Builder.fromVersion(version);
        } else if (url != null && !url.trim().isEmpty()) {
            return Builder.fromURL(url);
        } else {
            throw new RuntimeException("\"crate.testing.from_version\" " +
                    "or \"crate.testing.from_version\" system property must be provided");
        }
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
            servers[i] = new CrateTestServer.Builder()
                    .clusterName(clusterName)
                    .fromDownloadSource(downloadSource)
                    .workingDir(workingDir)
                    .host(hostAddress)
                    .httpPort(httpPorts[i])
                    .transportPort(transportPorts[i])
                    .settings(settings)
                    .addUnicastHosts(unicastHosts)
                    .keepWorkingDir(keepWorkingDir)
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

    private void waitUntilClusterIsReady(final int timeoutMillis) throws TimeoutException, InterruptedException {
        final CrateTestServer[] localServers = serversSafe();
        int iteration = 0;
        long startWaiting = System.currentTimeMillis();
        while (true) {
            try {
                if (System.currentTimeMillis() - startWaiting > timeoutMillis) {
                    throw new TimeoutException(String.format("Cluster has not been started within %d seconds",
                            timeoutMillis / 1000));
                }
                Thread.sleep(++iteration * 100);
                if (clusterIsReady(localServers)) {
                    break;
                }
            } catch (IOException e) {
                // carry on
            }
        }
    }

    private boolean clusterIsReady(CrateTestServer[] servers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) randomUrlFromServers().openConnection();
        connection.setRequestMethod("POST");

        String query = "{\"stmt\": \"select count(*) as nodes from sys.nodes\"}";
        byte[] body = query.getBytes("UTF-8");
        connection.setRequestProperty("Content-Type", "application/text");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        connection.setDoOutput(true);
        connection.getOutputStream().write(body);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JsonObject response = parseResponse(connection.getInputStream());
            JsonArray rows = response.getAsJsonArray("rows");
            JsonArray rowsNestedArray = rows.get(0).getAsJsonArray();
            return servers.length == rowsNestedArray.get(0).getAsInt();
        }
        return false;
    }

    private URL randomUrlFromServers() throws MalformedURLException {
        CrateTestServer server = randomServer();
        return new URL(String.format("http://%s:%d/_sql", server.crateHost(), server.httpPort()));
    }

    private static JsonObject parseResponse(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder res = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            res.append(line);
        }
        br.close();
        return new JsonParser().parse(res.toString()).getAsJsonObject();
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
        try {
            waitUntilClusterIsReady(30 * 1000);
        } catch (Exception e) {
            after();
            throw new IllegalStateException("Crate Test Cluster not started completely", e);
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
