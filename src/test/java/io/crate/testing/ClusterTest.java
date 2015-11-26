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
import io.crate.shade.org.elasticsearch.common.settings.ImmutableSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ClusterTest {

    public static final String CLUSTER_NAME = "cluster";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testClusterBuilder() throws Throwable {
        CrateTestCluster cluster = CrateTestCluster.builder(CLUSTER_NAME)
                .workingDir(tempFolder.getRoot().getAbsolutePath())
                .fromVersion("0.53.0")
                .numberOfNodes(3)
                .settings(ImmutableSettings.builder()
                        .put("stats.enabled", true)
                        .build())
                .build();
        Collection<CrateTestServer> servers = cluster.servers();
        assertThat(servers.size(), is(3));

        try {
            cluster.before();
            for (CrateTestServer server : servers) {
                SQLResponse response = server.execute("select version['number'] from sys.nodes");
                assertThat(response.rowCount(), is(3L));
                assertThat((String) response.rows()[0][0], is("0.53.0"));


                SQLResponse clusterResponse = server.execute("select name, settings['stats']['enabled'] from sys.cluster");
                assertThat((String) clusterResponse.rows()[0][0], is(CLUSTER_NAME));

                assertThat(String.valueOf(clusterResponse.rows()[0][1]), is("true"));
            }
        } finally {
            cluster.after();
        }
    }

    @Test
    public void testNoURL() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("no crate version, file or download url given");
        CrateTestCluster.builder(CLUSTER_NAME)
                .workingDir(tempFolder.getRoot().getAbsolutePath())
                .numberOfNodes(3)
                .settings(ImmutableSettings.builder()
                        .put("stats.enabled", true)
                        .build())
                .build();
    }
}
