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

package io.crate.testing;

class CrateVersion implements Comparable<String> {

    private final String version;

    CrateVersion(String version) {
        this.version = version;
    }

    @Override
    public int compareTo(String o) {
        String[] v1 = version.split("\\.");
        String[] v2 = o.split("\\.");
        int i = 0;
        while (i < v1.length && i < v2.length && v1[i].equals(v2[i])) {
            i++;
        }
        if (i < v1.length && i < v2.length) {
            int diff = Integer.valueOf(v1[i]).compareTo(Integer.valueOf(v2[i]));
            return Integer.signum(diff);
        }
        return Integer.signum(v1.length - v2.length);
    }

    boolean gt(String version) {
        return this.compareTo(version) > 0;
    }

    boolean lt(String version) {
        return this.compareTo(version) < 0;
    }
}
