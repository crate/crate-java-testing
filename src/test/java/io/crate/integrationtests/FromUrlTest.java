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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.hamcrest.CoreMatchers.is;

public class FromUrlTest extends BaseTest {

    private static final String CLUSTER_NAME = "from-uri";
    private static final String VERSION = "0.54.4";

    @ClassRule
    public static CrateTestCluster fromUrlCluster = CrateTestCluster
        .fromURL(String.format("https://cdn.crate.io/downloads/releases/crate-%s.tar.gz", VERSION))
        .clusterName(CLUSTER_NAME)
        .build();

    @Before
    public void setUp() throws MalformedURLException {
        prepare(fromUrlCluster);
    }

    @Test
    public void testFromUrl() throws Exception {
        JsonObject obj = execute("select name from sys.cluster");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(CLUSTER_NAME));

        obj = execute("select version['number'] from sys.nodes");
        assertThat(obj.getAsJsonArray("rows").get(0).getAsString(), is(VERSION));
    }
}
