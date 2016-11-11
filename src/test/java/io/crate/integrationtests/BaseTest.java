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

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.crate.testing.CrateTestCluster;
import io.crate.testing.CrateTestServer;
import io.crate.testing.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

@ThreadLeakScope(ThreadLeakScope.Scope.SUITE)
public abstract class BaseTest extends RandomizedTest {

    static {
        try {
            Utils.deletePath(CrateTestCluster.TMP_WORKING_DIR);
        } catch (IOException ignored) {
        }
    }

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static URL url;

    protected static void prepare(CrateTestCluster crateCluster) throws MalformedURLException {
        CrateTestServer server = crateCluster.randomServer();
        url = new URL(String.format("http://%s:%d/_sql", server.crateHost(), server.httpPort()));
    }

    protected JsonObject execute(String statement) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");

        String query = "{\"stmt\": \"" + statement + "\"}";
        byte[] body = query.getBytes("UTF-8");
        connection.setRequestProperty("Content-Type", "application/text");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        connection.setDoOutput(true);
        connection.getOutputStream().write(body);
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        return parseResponse(connection.getInputStream());
    }

    private static JsonObject parseResponse(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder res = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            res.append(line);
        }
        br.close();
        return new JsonParser().parse(res.toString()).getAsJsonObject();
    }
}
