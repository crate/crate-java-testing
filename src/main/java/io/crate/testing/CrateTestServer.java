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
import io.crate.shade.com.google.common.base.Joiner;
import io.crate.shade.com.google.common.base.MoreObjects;
import io.crate.shade.org.elasticsearch.action.ActionFuture;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;
import io.crate.shade.org.elasticsearch.common.Nullable;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.rules.ExternalResource;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.*;

import static org.junit.Assert.assertFalse;

public class CrateTestServer extends ExternalResource {

    public final int httpPort;
    public final int transportPort;
    public final String crateHost;
    private final String workingDir;
    private final String clusterName;
    private final String[] unicastHosts;
    private final String crateVersion;

    private CrateClient crateClient;


    private Process crateProcess;
    private ThreadPoolExecutor executor;

    public static CrateTestServer[] cluster(String clusterName, String crateVersion, int numberOfNodes) {
        int transportPorts[] = new int[numberOfNodes];
        int httpPorts[] = new int[numberOfNodes];
        for (int i = 0; i<numberOfNodes; i++) {
            transportPorts[i] = randomAvailablePort();
            httpPorts[i] = randomAvailablePort();
        }
        String hostAddress = InetAddress.getLoopbackAddress().getHostAddress();
        CrateTestServer[] servers = new CrateTestServer[numberOfNodes];
        String[] unicastHosts = getUnicastHosts(hostAddress, transportPorts);
        for (int i = 0; i< numberOfNodes; i++) {
            servers[i] = new CrateTestServer(clusterName, crateVersion, hostAddress,
                    httpPorts[i], transportPorts[i],
                    unicastHosts);
        }
        return servers;
    }

    private static String[] getUnicastHosts(String hostAddress, int[] transportPorts) {
        String[] result = new String[transportPorts.length];
        for (int i=0; i < transportPorts.length;i++) {
            result[i] = String.format(Locale.ENGLISH, "%s:%d", hostAddress, transportPorts[i]);
        }
        return result;
    }

    public CrateTestServer(@Nullable String clusterName, String crateVersion) {
        this(clusterName,
             crateVersion,
             randomAvailablePort(),
             randomAvailablePort(),
             System.getProperty("user.dir"),
             InetAddress.getLoopbackAddress().getHostAddress());
    }

    public CrateTestServer(@Nullable String clusterName, String crateVersion, String host, int httpPort, int transportPort, String ... unicastHosts) {
        this(clusterName, crateVersion, httpPort, transportPort, System.getProperty("user.dir"), host, unicastHosts);
    }

    public CrateTestServer(@Nullable String clusterName, String crateVersion, int httpPort, int transportPort,
                           String workingDir, String host, String ... unicastHosts) {
        this.clusterName = MoreObjects.firstNonNull(clusterName, "Testing" + transportPort);
        this.crateVersion = crateVersion;
        this.crateHost = host;
        this.httpPort = httpPort;
        this.transportPort = transportPort;
        this.unicastHosts = unicastHosts;
        this.workingDir = workingDir;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(3);
        executor = new ThreadPoolExecutor(3, 3, 10, TimeUnit.SECONDS, workQueue, new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                System.err.println(r.toString() + " got rejected");
            }
        });
        executor.prestartAllCoreThreads();
    }


    public SQLResponse execute(String statement) {
        return execute(statement, new Object[0]);
    }

    public SQLResponse execute(String statement, Object[] args) {
        ActionFuture<SQLResponse> future = crateClient.sql(new SQLRequest(statement, args));
        return future.actionGet(10, TimeUnit.SECONDS);
    }

    public SQLBulkResponse execute(String statement, Object[][] bulkArgs) {
        ActionFuture<SQLBulkResponse> bulkResponse = crateClient.bulkSql(new SQLBulkRequest(statement, bulkArgs));
        return bulkResponse.actionGet(10, TimeUnit.SECONDS);
    }

    /**
     * @return a random available port for binding
     */
    private static int randomAvailablePort() {
        try {
            ServerSocket socket = new  ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            File destPath = new File(dest, tarEntry.getName().replaceFirst("crate-(.*?)/", ""));
            System.out.println("extract: " + destPath.getCanonicalPath());
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
        File crateDir = new File(workingDir, "/parts/crate");
        if (crateDir.exists()) {
            return;
        }
        Path downloadLocation = Files.createTempDirectory("crate-download");
        System.out.println("Downloading Crate to: "+downloadLocation);

        URL url = new URL("https://cdn.crate.io/downloads/releases/crate-" + crateVersion + ".tar.gz");
        try (InputStream in = url.openStream()) {
            Files.copy(in, downloadLocation, StandardCopyOption.REPLACE_EXISTING);
            uncompressTarGZ(downloadLocation.toFile(), crateDir);
        }
        downloadLocation.toFile().delete();

    }



    @Override
    protected void before() throws Throwable {
        downloadCrate();
        System.out.println("Starting crate server process...");
        crateClient = new CrateClient(crateHost + ":" + transportPort);
        startCrateAsDaemon();
        if (!waitUntilServerIsReady(60 * 1000)) { // wait 1 minute max
            crateProcess.destroy();
            throw new IllegalStateException("Crate Test Server not started");
        }

    }

    @Override
    protected void after() {
        System.out.println("Stopping crate server process...");
        crateProcess.destroy();
        try {
            crateProcess.waitFor();
            wipeDataDirectory();
            wipeLogs();
        } catch (Exception e) {
            e.printStackTrace();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        crateClient.close();
        crateClient = null;
    }

    private void startCrateAsDaemon() throws IOException, InterruptedException {
        String[] command = new String[]{
                "bin/crate",
                "-Des.index.storage.type=memory",
                "-Des.network.host=" + crateHost,
                "-Des.cluster.name=" + clusterName,
                "-Des.http.port=" + httpPort,
                "-Des.transport.tcp.port=" + transportPort,
                "-Des.discovery.zen.ping.multicast.enabled=false",
                "-Des.discovery.zen.ping.unicast.hosts=" + Joiner.on(",").join(unicastHosts)
        };
        ProcessBuilder processBuilder = new ProcessBuilder(
            command
        );
        assert new File(workingDir).exists();
        processBuilder.directory(new File(workingDir, "/parts/crate"));
        processBuilder.redirectErrorStream(true);
        crateProcess = processBuilder.start();
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

                while (true) {
                    try {
                        ActionFuture<SQLResponse> future = crateClient.sql("select id from sys.cluster");
                        future.actionGet(timeoutMillis, TimeUnit.MILLISECONDS);
                        break;
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
        File dataDir = new File(workingDir + "/parts/crate/data");
        if (dataDir.exists()) {
            deletePath(dataDir.toPath());
            assertFalse(dataDir.exists());
        }
    }

    private void wipeLogs() throws Exception {
        File logDir = new File(workingDir + "/parts/crate/logs");
        if (logDir.exists()) {
            deletePath(logDir.toPath());
            assertFalse(logDir.exists());
        }
    }
}
