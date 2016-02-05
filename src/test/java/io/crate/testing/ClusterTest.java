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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.HashMap;

import static org.hamcrest.core.Is.is;

public class ClusterTest extends BaseTest {

    private static final String CLUSTER_NAME = "cluster";
    private static final String VERSION = "0.54.0";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testClusterBuilder() throws Throwable {
        CrateTestCluster cluster = CrateTestCluster
                .fromVersion(VERSION)
                .clusterName(CLUSTER_NAME)
                .numberOfNodes(3)
                .settings(new HashMap<String, Object>() {{
                    put("stats.enabled", true);
                }})
                .build();

        try {
            cluster.before();
            Collection<CrateTestServer> servers = cluster.servers();
            assertThat(servers.size(), is(3));
            for (CrateTestServer server : servers) {
                crateClient = crateClient(server.crateHost(), server.transportPort());
                SQLResponse response = execute("select version['number'] from sys.nodes");
                assertThat(response.rowCount(), is(3L));
                assertThat((String) response.rows()[0][0], is(VERSION));

                SQLResponse clusterResponse = execute("select name, settings['stats']['enabled'] from sys.cluster");
                assertThat((String) clusterResponse.rows()[0][0], is(CLUSTER_NAME));
                assertThat(String.valueOf(clusterResponse.rows()[0][1]), is("true"));

                ensureYellow(server);
                ensureGreen(server);
            }
        } finally {
            cluster.after();
        }
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

}
