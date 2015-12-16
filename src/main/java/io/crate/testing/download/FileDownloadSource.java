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

package io.crate.testing.download;

import io.crate.shade.com.google.common.base.Charsets;
import io.crate.shade.com.google.common.base.Preconditions;
import io.crate.shade.com.google.common.hash.Hashing;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

class FileDownloadSource implements DownloadSource {

    public static final String FOLDER_PREFIX = "crate-file-%s";

    private final Path pathToTarGzDistribution;
    private final String folderName;

    public FileDownloadSource(String pathToTarGzDistribution) {
        Path path = Paths.get(pathToTarGzDistribution);
        Preconditions.checkArgument(Files.exists(path),
                String.format(Locale.ENGLISH, "%s does not exist", pathToTarGzDistribution));

        this.pathToTarGzDistribution = path;
        this.folderName = String.format(Locale.ENGLISH,
                FOLDER_PREFIX,
                Hashing.sha1().hashString(pathToTarGzDistribution, Charsets.UTF_8).toString());
    }

    @Override
    public File folder(File containingFolder) {
        return new File(containingFolder, folderName);
    }

    @Override
    public URL downloadUrl() throws MalformedURLException {
        return pathToTarGzDistribution.toAbsolutePath().toUri().toURL();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "FILE[%s]", pathToTarGzDistribution);
    }
}
