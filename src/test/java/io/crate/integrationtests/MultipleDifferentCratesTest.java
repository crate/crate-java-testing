/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.integrationtests;

import com.google.gson.JsonObject;
import io.crate.testing.CrateTestCluster;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class MultipleDifferentCratesTest extends BaseTest {

    private static final String CLUSTER_NAME_FIRST = "multiples-1";
    private static final String CLUSTER_NAME_SECOND = "multiples-2";
    private static final String FIRST_VERSION = "0.54.2";
    private static final String SECOND_VERSION = "0.54.1";

    @ClassRule
    public static CrateTestCluster FIRST_CLUSTER = CrateTestCluster
        .fromVersion(FIRST_VERSION)
        .clusterName(CLUSTER_NAME_FIRST)
        .build();

    @ClassRule
    public static CrateTestCluster SECOND_CLUSTER = CrateTestCluster
        .fromVersion(SECOND_VERSION)
        .clusterName(CLUSTER_NAME_SECOND)
        .build();

    @Test
    public void testAgainstMultipleCrates() throws Exception {
        prepare(SECOND_CLUSTER);
        JsonObject obj = execute("select name from sys.cluster");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(CLUSTER_NAME_SECOND));

        obj = execute("select version['number'] from sys.nodes");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(SECOND_VERSION));

        prepare(FIRST_CLUSTER);
        obj = execute("select name from sys.cluster");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(CLUSTER_NAME_FIRST));

        obj = execute("select version['number'] from sys.nodes");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(FIRST_VERSION));
    }
}
