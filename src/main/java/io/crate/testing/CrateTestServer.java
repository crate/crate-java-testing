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

import io.crate.action.sql.SQLBulkRequest;
import io.crate.action.sql.SQLBulkResponse;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.client.InternalCrateClient;
import io.crate.shade.com.google.common.base.Joiner;
import io.crate.shade.com.google.common.base.MoreObjects;
import io.crate.shade.com.google.common.base.Preconditions;
import io.crate.shade.org.elasticsearch.ElasticsearchTimeoutException;
import io.crate.shade.org.elasticsearch.action.ActionFuture;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;
import io.crate.shade.org.elasticsearch.client.transport.TransportClient;
import io.crate.shade.org.elasticsearch.common.settings.ImmutableSettings;
import io.crate.shade.org.elasticsearch.common.settings.Settings;
import io.crate.shade.org.elasticsearch.common.transport.InetSocketTransportAddress;
import io.crate.shade.org.elasticsearch.common.unit.TimeValue;
import io.crate.testing.download.DownloadSource;
import io.crate.testing.download.DownloadSources;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.rules.ExternalResource;

import java.io.*;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.assertFalse;

public class CrateTestServer extends ExternalResource implements TestCluster {

    public static final TimeValue DEFAULT_TIMEOUT = TimeValue.timeValueSeconds(10);
    public static final String DEFAULT_WORKING_DIR = System.getProperty("user.dir");
    public static final String CRATE_INSTANCES_DIR = "/parts";

    public final int httpPort;
    public final int transportPort;
    public final String crateHost;
    private final String workingDir;
    private final String clusterName;
    private final String[] unicastHosts;
    private final DownloadSource downloadSource;
    private final Settings nodeSettings;

    private ExecutorService executor;
    private CrateClient crateClient;
    private TransportClient transportClient;
    private Process crateProcess;

    public static class Builder {
        private String host = InetAddress.getLoopbackAddress().getHostAddress();
        private int httpPort = Utils.randomAvailablePort();
        private int transportPort = Utils.randomAvailablePort();
        private String workingDir = DEFAULT_WORKING_DIR;
        private String clusterName = "Testing-" + transportPort;
        private List<String> unicastHosts = new ArrayList<>();
        private DownloadSource downloadSource = null;
        private Settings nodeSettings = ImmutableSettings.EMPTY;

        private Builder() {}

        public static Builder fromURL(String url) {
            return new Builder().fromDownloadSource(DownloadSources.URL(url));
        }

        public static Builder fromVersion(String crateVersion) {
            return new Builder().fromDownloadSource(DownloadSources.VERSION(crateVersion));
        }

        public static Builder fromFile(String pathToTarGzCrateDistribution) {
            return new Builder().fromDownloadSource(DownloadSources.FILE(pathToTarGzCrateDistribution));
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

        public Builder workingDir(String workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder addUnicastHosts(String ... unicastHosts) {
            Collections.addAll(this.unicastHosts, unicastHosts);
            return this;
        }

        Builder fromDownloadSource(DownloadSource downloadSource) {
            this.downloadSource = downloadSource;
            return this;
        }

        public Builder settings(Settings nodeSettings) {
            this.nodeSettings = nodeSettings;
            return this;
        }

        public CrateTestServer build() {
            Preconditions.checkArgument(downloadSource != null, "no download source given (version, git-ref, url, file)");
            return new CrateTestServer(clusterName, downloadSource, httpPort, transportPort, workingDir, host, nodeSettings, unicastHosts.toArray(new String[unicastHosts.size()]));
        }
    }

    /**
     * @deprecated use {@link #fromURL(String)}
     */
    @Deprecated()
    public static Builder builder() {
        return new Builder();
    }

    public static Builder fromURL(String url) {
        return Builder.fromURL(url);
    }

    public static Builder fromFile(String pathToTarGzCrateDistribution) {
        return Builder.fromFile(pathToTarGzCrateDistribution);
    }

    public static Builder fromVersion(String version) {
        return Builder.fromVersion(version);
    }

    public static CrateTestServer fromURL(String url, String clusterName) {
        return fromURL(url).clusterName(clusterName).build();
    }

    public static CrateTestServer fromFile(String pathToTarGzCrateDistribution, String clusterName) {
        return fromFile(pathToTarGzCrateDistribution).clusterName(clusterName).build();
    }

    private CrateTestServer(String clusterName,
                           DownloadSource downloadSource,
                           int httpPort,
                           int transportPort,
                           String workingDir,
                           String host,
                           Settings settings,
                           String ... unicastHosts) {
        this.clusterName = MoreObjects.firstNonNull(clusterName, "Testing-" + transportPort);
        this.downloadSource = downloadSource;
        this.crateHost = host;
        this.httpPort = httpPort;
        this.transportPort = transportPort;
        this.unicastHosts = unicastHosts;
        this.workingDir = workingDir;
        this.nodeSettings = settings == null ? ImmutableSettings.EMPTY : settings;
    }

    public SQLResponse execute(String statement) {
        return execute(statement, SQLRequest.EMPTY_ARGS, DEFAULT_TIMEOUT);
    }

    public SQLResponse execute(String statement, TimeValue timeout) {
        return execute(statement, SQLRequest.EMPTY_ARGS, timeout);
    }

    public SQLResponse execute(String statement, Object[] args) {
        return execute(statement, args, DEFAULT_TIMEOUT);
    }

    public SQLResponse execute(String statement, Object[] args, TimeValue timeout) {
        return crateClient.sql(new SQLRequest(statement, args)).actionGet(timeout);
    }

    public SQLBulkResponse execute(String statement, Object[][] bulkArgs) {
        return execute(statement, bulkArgs, DEFAULT_TIMEOUT);
    }

    public SQLBulkResponse execute(String statement, Object[][] bulkArgs, TimeValue timeout) {
        return crateClient.bulkSql(new SQLBulkRequest(statement, bulkArgs)).actionGet(timeout);
    }

    public ActionFuture<SQLResponse> executeAsync(String statement) {
        return executeAsync(statement, SQLRequest.EMPTY_ARGS);
    }

    public ActionFuture<SQLResponse> executeAsync(String statement, Object[] args) {
        return crateClient.sql(new SQLRequest(statement, args));
    }

    public ActionFuture<SQLBulkResponse> executeAsync(String statement, Object[][] bulkArgs) {
        return crateClient.bulkSql(new SQLBulkRequest(statement, bulkArgs));
    }

    private CrateClient ensureCrateClient() {
        if (crateClient == null) {
            crateClient = new CrateClient(ImmutableSettings.builder()
                    // use a custom classloader to avoid shading hassle
                    .classLoader(new ShadingClassLoader(getClass().getClassLoader()))
                    .build(), true);

            // TODO: hack ahead: use reflection only until new CrateClient release contains new constructor
            //       for creating the client with settings and server addresses
            try {
                Field internalCrateClientField = CrateClient.class.getDeclaredField("internalClient");
                internalCrateClientField.setAccessible(true);
                InternalCrateClient internalCrateClient = (InternalCrateClient)internalCrateClientField.get(crateClient);
                internalCrateClient.addTransportAddress(new InetSocketTransportAddress(crateHost, transportPort));
            } catch (NoSuchFieldException|IllegalAccessException e) {
                throw new RuntimeException(e);
            }

        }
        return crateClient;
    }

    private synchronized TransportClient ensureTransportClient() {
        if (transportClient == null) {
            Settings clientSettings = ImmutableSettings.builder()
                    .put("cluster.name", clusterName)
                    // use a classloader to avoid shading hassle
                    .classLoader(new ShadingClassLoader(getClass().getClassLoader()))
                    .build();
            transportClient = new TransportClient(clientSettings);
            transportClient.addTransportAddress(new InetSocketTransportAddress(crateHost, transportPort));
        }
        return transportClient;
    }

    public void ensureYellow() {
        ensureTransportClient().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
    }

    public void ensureGreen() {
        ensureTransportClient().admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
    }

    private static void uncompressTarGZ(File tarFile, File dest) throws IOException {
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
        while (tarEntry != null) {// create a file with the same name as the tarEntry
            Path entryPath = Paths.get(tarEntry.getName());

            if (entryPath.getNameCount() == 1) {
                // strip root folder
                tarEntry = tarIn.getNextTarEntry();
                continue;
            }

            Path strippedPath = entryPath.subpath(1, entryPath.getNameCount());
            File destPath = new File(dest, strippedPath.toString());
            Utils.log("extract: %s", destPath.getCanonicalPath());

            if (tarEntry.isDirectory()) {
                destPath.mkdirs();
            } else {
                destPath.createNewFile();
                byte [] btoRead = new byte[1024];
                BufferedOutputStream bout =
                        new BufferedOutputStream(new FileOutputStream(destPath));
                int len;
                while((len = tarIn.read(btoRead)) != -1)
                {
                    bout.write(btoRead,0, len);
                }

                bout.close();
                if (destPath.getParent().equals(dest.getPath() + "/bin")) {
                    destPath.setExecutable(true);
                }
            }
            tarEntry = tarIn.getNextTarEntry();
        }
        tarIn.close();
    }

    private void downloadCrate() throws IOException {
        File crateDir = downloadSource.folder(new File(workingDir, CRATE_INSTANCES_DIR));
        if (crateDir.exists()) {
            Utils.log("No need to download crate. already downloaded %s to %s", downloadSource, crateDir);
            return;
        }
        Path downloadLocation = Files.createTempDirectory("crate-download");
        Utils.log("Downloading Crate %s to: %s", downloadSource, crateDir);

        try (InputStream in = downloadSource.downloadUrl().openStream()) {
            Files.copy(in, downloadLocation, StandardCopyOption.REPLACE_EXISTING);
            uncompressTarGZ(downloadLocation.toFile(), crateDir);
        }
        downloadLocation.toFile().delete();
    }

    @Override
    protected void before() throws Throwable {
        downloadCrate();
        Utils.log("Starting crate server process...");
        executor = Executors.newFixedThreadPool(2); // new threadpool for new process instance
        crateClient = ensureCrateClient();
        startCrateAsDaemon();
        if (!waitUntilServerIsReady(60 * 1000)) { // wait 1 minute max
            after(); // after is not called by the test runner when an error happens here
            throw new IllegalStateException("Crate Test Server not started");
        }
    }

    @Override
    protected void after() {
        Utils.log("Stopping crate server process...");
        if (crateProcess != null) {
            try {
                crateProcess.destroy();
                crateProcess.waitFor();
                wipeDataDirectory();
                wipeLogs();
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
        if (crateClient != null) {
            crateClient.close();
            crateClient = null;
        }
        if (transportClient != null) {
            transportClient.close();
            transportClient = null;
        }
    }

    private void startCrateAsDaemon() throws IOException, InterruptedException {
        ImmutableSettings.Builder settingsBuilder = ImmutableSettings.builder();
        // add defaults and network configs
        settingsBuilder
                .put("index.storage.type", "memory")
                .put("network.host", crateHost)
                .put("cluster.name", clusterName)
                .put("http.port", httpPort)
                .put("transport.tcp.port", transportPort)
                .put("discovery.zen.ping.multicast.enabled", "false")
                .put("discovery.zen.ping.unicast.hosts", Joiner.on(",").join(unicastHosts));
        // override with additional settings
        settingsBuilder.put(nodeSettings);
        Map<String, String> settingsMap = settingsBuilder.build().getAsMap();

        String[] command = new String[settingsMap.size()+1];
        int idx = 0;
        command[idx++] = "bin/crate";

        for (Map.Entry<String, String> entry : settingsMap.entrySet()) {
            command[idx++] = String.format(Locale.ENGLISH, "-Des.%s=%s", entry.getKey(), entry.getValue());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
            command
        );
        assert new File(workingDir).exists();
        processBuilder.directory(downloadSource.folder(new File(workingDir, CRATE_INSTANCES_DIR)));
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

    /**
     * wait until crate is ready
     * @param timeoutMillis the number of milliseconds to wait
     * @return true if server is ready, false if a timeout or another IOException occurred
     */
    private boolean waitUntilServerIsReady(final int timeoutMillis) throws IOException {
        FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                int timeoutRetries = 3;
                while (true) {
                    try {
                        crateClient.sql("select id from sys.cluster")
                                .actionGet(timeoutMillis/10, TimeUnit.MILLISECONDS);
                        break;
                    } catch (NoNodeAvailableException e) {
                       // carry on no matter what
                    } catch (ElasticsearchTimeoutException e) {
                        if (timeoutRetries == 0) {
                            e.printStackTrace();
                            return false;
                        }
                        timeoutRetries--;
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

    private void deletePath(Path path) throws Exception {
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

    private void wipeDataDirectory() throws Exception {
        File dataDir = resourceDirectory("data");
        if (dataDir.exists()) {
            deletePath(dataDir.toPath());
            assertFalse(dataDir.exists());
        }
    }

    private void wipeLogs() throws Exception {
        File logDir = resourceDirectory("logs");
        if (logDir.exists()) {
            deletePath(logDir.toPath());
            assertFalse(logDir.exists());
        }
    }

    private File resourceDirectory(String resource) {
        File parts = downloadSource.folder(new File(workingDir, CRATE_INSTANCES_DIR));
        return new File(parts, resource);
    }

}
