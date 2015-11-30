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

import io.crate.shade.com.google.common.base.Joiner;
import io.crate.shade.com.google.common.collect.ImmutableMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
        relocate 'io.crate.action',     'io.crate.testserver.action'
        relocate 'io.crate.client',     'io.crate.testserver.client'
        relocate 'io.crate.shade',      'io.crate.testserver.shade'
        relocate 'org.apache.commons',  'io.crate.testserver.apache.commons'
        relocate 'com.tdunning',        'io.crate.testsercer.tdunning'
 */
public class ShadingClassLoader extends ClassLoader {

    private static final ImmutableMap<String, String> REPLACE_MAP = ImmutableMap.of(
            // gradle shadow plugin does dumb regex replace at word boundary
            // across the whole source, use substring to avoid that
            "Oio.crate.action".substring(1), "io.crate.testserver.action",
            "Oio.crate.client".substring(1), "io.crate.testserver.client",
            "Oio.crate.shade".substring(1), "io.crate.testserver.shade",
            "Oorg.apache.commons".substring(1), "io.crate.testserver.apache.commons",
            "Ocom.tdunning".substring(1), "io.crate.testserver.tdunning"
    );
    private static final Pattern SHADED_PREFIXES_PATTERN = Pattern.compile(
            "((^" + Joiner.on(")|(^").join(
                    // gradle shadow plugin does dumb regex replace at word boundary
                    // across the whole source, use substring to avoid that
                    "Oio.crate.action".substring(1),
                    "Oio.crate.client".substring(1),
                    "Oio.crate.shade".substring(1),
                    "Oorg.apache.commons".substring(1),
                    "Ocom.tdunning".substring(1)
            ) + "))"
    );

    public ShadingClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Matcher matcher = SHADED_PREFIXES_PATTERN.matcher(name);
        try {
            if (matcher.find()) {
                String matchingPrefix = matcher.group();
                String replaceWith = REPLACE_MAP.get(matchingPrefix);
                String replaced;
                if (replaceWith != null) {
                    replaced = matcher.replaceFirst(replaceWith);
                } else {
                    replaced = name;
                }
                // proactively try to load dependency classes with shade prefix
                // so even if those elasticsearch classes are present on the classpath
                // we first try to load our versions
                try {
                    return super.loadClass(replaced, resolve);
                } catch (ClassNotFoundException e) {
                    // proceed with normal name
                }
            }
        } catch (IllegalStateException e) {
            // ok, fall through
        }
        return super.loadClass(name, resolve);
    }
}
