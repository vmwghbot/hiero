/*
 * Copyright (c) 2018 VMware Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hillview.sketches;

import org.hillview.dataset.api.Empty;
import org.hillview.dataset.api.TableSketch;
import org.hillview.storage.CsvFileWriter;
import org.hillview.storage.ITableWriter;
import org.hillview.storage.OrcFileWriter;
import org.hillview.table.Schema;
import org.hillview.table.api.ITable;
import org.hillview.utils.Converters;
import org.hillview.utils.HillviewLogger;
import org.hillview.utils.Utilities;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * This sketch saves a table into a set of files in the specified folder.
 * TODO: Today the save can succeed on some machines, and fail on others.
 * There is no cleanup if that happens.
 * If the saving fails this will trigger an exception.
 */
public class SaveAsFileSketch implements TableSketch<Empty> {
    static final long serialVersionUID = 1;

    private final String kind;
    private final String folder;
    @Nullable
    private final Schema schema;
    /**
     * If true a schema file will also be created.
     */
    private final boolean createSchema;

    public SaveAsFileSketch(
            final String kind,
            final String folder,
            @Nullable final Schema schema,
            boolean createSchema) {
        this.kind = kind;
        this.folder = folder;
        this.createSchema = createSchema;
        this.schema = schema;
    }

    @Override
    public Empty create(@Nullable ITable data) {
        Converters.checkNull(data);
        try {
            if (this.schema != null)
                data = data.project(this.schema);

            // Executed for side-effect.
            data.getLoadedColumns(data.getSchema().getColumnNames());
            File file = new File(this.folder);
            @SuppressWarnings("unused")
            boolean ignored = file.mkdir();
            // There is a race here: multiple workers may try to create the
            // folder at the same time, so we don't bother if the creation fails.
            // If the folder can't be created the writing below will fail.

            String tableFile = data.getSourceFile();
            if (tableFile == null)
                throw new RuntimeException("I don't know how to generate file names for the data");
            String baseName = Utilities.getBasename(tableFile);
            String path = Paths.get(this.folder, baseName + "." + kind).toString();
            HillviewLogger.instance.info("Writing data to files", "{0}", path);
            ITableWriter writer;
            switch (kind) {
                case "orc":
                    writer = new OrcFileWriter(path);
                    break;
                case "db":
                    writer = new CsvFileWriter(path).setWriteHeaderRow(false);
                    break;
                case "csv":
                    writer = new CsvFileWriter(path);
                    break;
                default:
                    throw new RuntimeException("Unknown file kind: " + kind);
            }
            writer.writeTable(data);

            if (this.createSchema) {
                String schemaFile = baseName + ".schema";
                Path schemaPath = Paths.get(this.folder, schemaFile);
                Schema toWrite = data.getSchema();
                toWrite.writeToJsonFile(schemaPath);
                Path finalSchemaPath = Paths.get(this.folder, Schema.schemaFileName);
                // Attempt to atomically rename the schema; this is also a race which
                // may be won by multiple participants.  Hopefully all the schemas
                // written should be identical, so it does not matter if this happens
                // many times.
                Files.move(schemaPath, finalSchemaPath, StandardCopyOption.ATOMIC_MOVE);
            }
            return this.zero();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public Empty zero() {
        return Empty.getInstance();
    }

    @Nullable
    @Override
    public Empty add(@Nullable Empty left, @Nullable Empty right) {
        return left;
    }

    @Override
    public String toString() {
        return Paths.get(this.folder,  "*." + kind).toString();
    }
}
