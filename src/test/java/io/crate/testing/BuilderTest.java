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

import java.util.HashMap;

import static org.hamcrest.core.Is.is;

public class BuilderTest extends BaseTest {

    private static final String CLUSTER_NAME = "mycluster";
    private static final String VERSION = "0.52.0";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testBuilder() throws Throwable {
        CrateTestServer testServer = CrateTestServer
                .fromVersion(VERSION)
                .clusterName(CLUSTER_NAME)
                .host("127.0.0.1")
                .httpPort(12345)
                .transportPort(55555)
                .settings(new HashMap<String, Object>() {{
                    put("stats.enabled", true);
                }})
                .build();
        assertThat(testServer.crateHost(), is("127.0.0.1"));
        assertThat(testServer.httpPort(), is(12345));
        assertThat(testServer.transportPort(), is(55555));

        try {
            testServer.before();
            crateClient = crateClient(testServer.crateHost(), testServer.transportPort());
            SQLResponse response = execute("select version['number'] from sys.nodes");
            assertThat(response.rowCount(), is(1L));
            assertThat((String) response.rows()[0][0], is(VERSION));

            SQLResponse clusterResponse = execute("select name, settings['stats']['enabled'] from sys.cluster");
            assertThat((String) clusterResponse.rows()[0][0], is(CLUSTER_NAME));
            assertThat(String.valueOf(clusterResponse.rows()[0][1]), is("true"));

            ensureYellow(testServer);
            ensureGreen(testServer);

        } finally {
            testServer.after();
        }
    }

    @Test
    public void testNoURL() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("no download source given (version, git-ref, url, file)");
        CrateTestServer.builder()
                .clusterName(CLUSTER_NAME)
                .host("127.0.0.1")
                .httpPort(12345)
                .transportPort(55555)
                .settings(new HashMap<String, Object>() {{
                    put("stats.enabled", true);
                }})
                .build();

    }

}
