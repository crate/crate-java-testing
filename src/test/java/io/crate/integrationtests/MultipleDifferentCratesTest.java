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

import io.crate.testing.CrateTestServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.is;

public class MultipleDifferentCratesTest extends BaseTest {

    private static final String CLUSTER_NAME = "multiples";
    private static final String FIRST_VERSION = "0.54.2";
    private static final String SECOND_VERSION = "0.54.1";


    @ClassRule
    public static CrateTestServer FIRST_CLUSTER = CrateTestServer
            .fromVersion(FIRST_VERSION)
            .clusterName(CLUSTER_NAME)
            .build();

    @ClassRule
    public static CrateTestServer SECOND_CLUSTER = CrateTestServer
            .fromVersion(SECOND_VERSION)
            .clusterName(CLUSTER_NAME)
            .build();

    @Test
    public void testAgainstMultipleCrates() throws Exception {
        assertThat(Files.exists(Paths.get(String.format("parts/crate-%s", FIRST_VERSION))), is(true));
        assertThat(Files.exists(Paths.get(String.format("parts/crate-%s", SECOND_VERSION))), is(true));

        crateClient = crateClient(SECOND_CLUSTER.crateHost(), SECOND_CLUSTER.transportPort());
        assertThat(
                (String) execute("select name from sys.cluster").rows()[0][0],
                is(CLUSTER_NAME));
        assertThat(
                (String) execute("select version['number'] from sys.nodes").rows()[0][0],
                is(SECOND_VERSION));

        crateClient = crateClient(FIRST_CLUSTER.crateHost(), FIRST_CLUSTER.transportPort());
        assertThat(
                (String) execute("select name from sys.cluster").rows()[0][0],
                is(CLUSTER_NAME));
        assertThat(
                (String) execute("select version['number'] from sys.nodes").rows()[0][0],
                is(FIRST_VERSION));

    }

}
