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

import io.crate.shade.org.elasticsearch.common.io.FileSystemUtils;
import io.crate.testing.CrateTestServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FromVersionTest {

    static {
        File downloadFolder = new File(CrateTestServer.DEFAULT_WORKING_DIR, "/parts");
        FileSystemUtils.deleteRecursively(downloadFolder, false);
    }

    private static final String CLUSTER_NAME = "from-version";
    private static final String VERSION = "0.53.1";

    @ClassRule
    public static CrateTestServer fromUrlServer = CrateTestServer.fromVersion(VERSION).clusterName(CLUSTER_NAME).build();


    @Test
    public void testFromVersion() throws Exception {
        assertThat(
                (String) fromUrlServer.execute("select name from sys.cluster").rows()[0][0],
                is(CLUSTER_NAME));
        assertThat(
                (String) fromUrlServer.execute("select version['number'] from sys.nodes").rows()[0][0],
                is(VERSION));
    }
}
