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

package io.crate.integrationtests;

import io.crate.testing.CrateTestCluster;
import io.crate.testing.CrateTestServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class FromVersionPropertyClusterTest extends BaseTest {

    private static final String VERSION_PROPERTY = "crate.testing.from_version";
    private static final String CLUSTER_NAME = "from-version-property";
    private static final String VERSION = "0.52.3";

    static {
        System.setProperty(VERSION_PROPERTY, VERSION);
    }

    @ClassRule
    public static CrateTestCluster fromSettingsCluster = CrateTestCluster
            .fromSysProperties()
            .clusterName(CLUSTER_NAME)
            .numberOfNodes(2)
            .build();

    @BeforeClass
    public static void init() {
        CrateTestServer server = fromSettingsCluster.randomServer();
        crateClient = crateClient(server.crateHost(), server.transportPort());
    }

    @Test
    public void testFromVersionProperty() {
        assertThat((String) execute("select name from sys.cluster").rows()[0][0], is(CLUSTER_NAME));
        assertThat((String) execute("select version['number'] from sys.nodes").rows()[0][0], is(VERSION));
    }

    @AfterClass
    public static void tearDown() {
        System.clearProperty(VERSION_PROPERTY);
    }

}