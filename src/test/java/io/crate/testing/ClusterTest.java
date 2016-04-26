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

import io.crate.action.sql.SQLResponse;
import io.crate.integrationtests.BaseTest;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;

import static org.hamcrest.core.Is.is;

public class ClusterTest extends BaseTest {

    private static final String CLUSTER_NAME = "cluster";
    private static final String VERSION = "0.52.0";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testClusterBuilder() throws Throwable {
        CrateTestCluster cluster = CrateTestCluster.fromVersion(VERSION)
                .clusterName(CLUSTER_NAME)
                .numberOfNodes(2)
                .settings(new HashMap<String, Object>() {{
                    put("stats.enabled", true);
                }})
                .build();

        try {
            cluster.before();
            crateClient = crateClient(cluster);
            Collection<CrateTestServer> servers = cluster.servers();
            assertThat(servers.size(), is(2));
            for (CrateTestServer server : servers) {
                SQLResponse response = execute("select version['number'] from sys.nodes");
                assertThat(response.rowCount(), is(2L));
                assertThat((String) response.rows()[0][0], is(VERSION));

                SQLResponse clusterResponse = execute("select name, settings['stats']['enabled'] from sys.cluster");
                assertThat((String) clusterResponse.rows()[0][0], is(CLUSTER_NAME));

                assertThat(String.valueOf(clusterResponse.rows()[0][1]), is("true"));

                ensureGreen(server);
                ensureYellow(server);
            }
        } finally {
            cluster.after();
        }
    }

    @Test
    public void testBuilderKeepWorkingDir() throws Throwable {
        CrateTestCluster testCluster = CrateTestCluster
                .fromVersion("0.52.0")
                .keepWorkingDir(true)
                .build();

        testCluster.before();
        Path workingPath = testCluster.crateWorkingDir();
        assertThat(Files.exists(workingPath), is(true));


        testCluster.after();
        assertThat(Files.exists(workingPath), is(true));
    }

    @Test
    public void testBuilderDoNotKeepWorkingDir() throws Throwable {
        CrateTestCluster testCluster = CrateTestCluster
                .fromVersion("0.52.0")
                .build();

        testCluster.before();
        assertThat(Files.exists(testCluster.crateWorkingDir()), is(true));

        testCluster.after();
    }

    @Test
    public void testSetWorkingDir() throws Throwable {
        Path actualCratePath = Paths.get(System.getProperty("user.dir"), "crate.testing");
        assertThat(Files.exists(actualCratePath), is(false));

        CrateTestCluster testCluster = CrateTestCluster.fromVersion(VERSION)
                .clusterName(CLUSTER_NAME)
                .workingDir(actualCratePath)
                .build();

        testCluster.before();
        Path workingPath = testCluster.crateWorkingDir();
        assertThat(workingPath.startsWith(actualCratePath), is(true));

        testCluster.after();
        assertThat(Files.exists(workingPath), is(false));

        // clean up
        Utils.deletePath(actualCratePath);
    }

    @Test
    public void testNoNodes() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("invalid number of nodes: 0");
        CrateTestCluster.fromVersion(VERSION)
                .clusterName(CLUSTER_NAME)
                .numberOfNodes(0)
                .settings(new HashMap<String, Object>() {{
                    put("stats.enabled", true);
                }})
                .build();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        Utils.deletePath(CrateTestCluster.TMP_WORKING_DIR);
    }

}
