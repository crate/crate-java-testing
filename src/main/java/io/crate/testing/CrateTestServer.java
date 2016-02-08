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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.rules.ExternalResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;

public class CrateTestServer extends ExternalResource {

    public static final String DEFAULT_WORKING_DIR = System.getProperty("user.dir");
    public static final String CRATE_INSTANCES_DIR = "/parts";

    private final int httpPort;
    private final int transportPort;
    private final String crateHost;
    private final String workingDir;
    private final String clusterName;
    private final String[] unicastHosts;
    private final DownloadSource downloadSource;
    private final Map<String, Object> nodeSettings;
    private boolean isReady = false;

    private ExecutorService executor;
    private Process crateProcess;

    public static class Builder {
        private String host = InetAddress.getLoopbackAddress().getHostAddress();
        private int httpPort = Utils.randomAvailablePort();
        private int transportPort = Utils.randomAvailablePort();
        private String workingDir = DEFAULT_WORKING_DIR;
        private String clusterName = "Testing-" + transportPort;
        private List<String> unicastHosts = new ArrayList<>();
        private DownloadSource downloadSource = null;
        private Map<String, Object> nodeSettings = Collections.emptyMap();

        private Builder() {
        }

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

        public Builder addUnicastHosts(String... unicastHosts) {
            Collections.addAll(this.unicastHosts, unicastHosts);
            return this;
        }

        Builder fromDownloadSource(DownloadSource downloadSource) {
            this.downloadSource = downloadSource;
            return this;
        }

        public Builder settings(Map<String, Object> nodeSettings) {
            this.nodeSettings = nodeSettings;
            return this;
        }

        public CrateTestServer build() {
            if (downloadSource == null) {
                throw new IllegalArgumentException("no download source given (version, git-ref, url, file)");
            }
            return new CrateTestServer(clusterName, downloadSource, httpPort, transportPort, workingDir,
                    host, nodeSettings, unicastHosts.toArray(new String[unicastHosts.size()]));
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
                            Map<String, Object> settings,
                            String... unicastHosts) {
        this.clusterName = firstNonNull(clusterName, "Testing-" + transportPort);
        this.downloadSource = downloadSource;
        this.crateHost = host;
        this.httpPort = httpPort;
        this.transportPort = transportPort;
        this.unicastHosts = unicastHosts;
        this.workingDir = workingDir;
        this.nodeSettings = settings == null ? Collections.<String, Object>emptyMap() : settings;
    }

    private static String firstNonNull(String... items) {
        for (String item : items)
            if (item != null) {
                return item;
            }
        return null;
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

    public boolean isReady() {
        return isReady;
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
                byte[] btoRead = new byte[1024];
                BufferedOutputStream bout =
                        new BufferedOutputStream(new FileOutputStream(destPath));
                int len;
                while ((len = tarIn.read(btoRead)) != -1) {
                    bout.write(btoRead, 0, len);
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
        command[idx++] = "bin/crate";

        for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
            command[idx++] = String.format(Locale.ENGLISH, "-Des.%s=%s", entry.getKey(), entry.getValue());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
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

    private boolean serverIsReady(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");

        String query = "{\"stmt\": \"select id from sys.cluster\"}";
        byte[] body = query.getBytes("UTF-8");
        connection.setRequestProperty("Content-Type", "application/text");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        connection.setDoOutput(true);
        connection.getOutputStream().write(body);
        return connection.getResponseCode() == 200;
    }

    /**
     * wait until crate is ready
     *
     * @param timeoutMillis the number of milliseconds to wait
     * @return true if server is ready, false if a timeout or another IOException occurred
     */
    private boolean waitUntilServerIsReady(final int timeoutMillis) throws IOException {
        final URL serverUrl = new URL(String.format("http://%s:%d/_sql", crateHost, httpPort));

        FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (true) {
                    try {
                        if (serverIsReady(serverUrl)) {
                            isReady = true;
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
