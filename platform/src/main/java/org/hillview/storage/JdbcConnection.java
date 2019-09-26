/*
 * Copyright (c) 2017 VMware Inc. All Rights Reserved.
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

package org.hillview.storage;

import org.hillview.table.Schema;
import org.hillview.utils.Utilities;

import java.util.HashMap;

/**
 * Base abstract class that handles various specifics of JDBC driver requirements.
 */
abstract class JdbcConnection {
    /**
     * Separates options from each other in rul.
     */
    private final char urlOptionsSeparator;
    /**
     * Separates options from the rest of the url.
     */
    private final char urlOptionsBegin;
    public final JdbcConnectionInformation info;
    private final HashMap<String, String> params = new HashMap<String, String>();

    static JdbcConnection create(JdbcConnectionInformation conn) {
        if (Utilities.isNullOrEmpty(conn.databaseKind))
            throw new RuntimeException("Database kind cannot be empty");
        switch (conn.databaseKind) {
            case "mysql":
                return new MySqlJdbcConnection(conn);
            case "impala":
                return new ImpalaJdbcConnection(conn);
            default:
                throw new RuntimeException("Unsupported JDBC database kind " + conn.databaseKind);
        }
    }

    /**
     * Construct the URL used to connect to the database.
     */
    public abstract String getURL();

    /**
     * Construct the query string to read the specified table.
     * @param table     Table to read.
     * @param rowCount  Number of rows to read.
     * @return          A SQL query string that reads the specified number of rows.
     */
    public abstract String getQueryToReadTable(String table, int rowCount);

    String getQueryToReadSize(String table) {
        return "SELECT COUNT(*) FROM " + table;
    }

    String getQueryToComputeDistinctCount(String table, String column) {
        return "SELECT COUNT(DISTINCT " + column + ") FROM " + table;
    }

    void addBaseUrl(StringBuilder urlBuilder) {
        urlBuilder.append("jdbc:");
        urlBuilder.append(info.databaseKind);
        urlBuilder.append("://");
        urlBuilder.append(info.host);
        if (info.port >= 0) {
            urlBuilder.append(":");
            urlBuilder.append(info.port);
        }
        urlBuilder.append("/");
        urlBuilder.append(info.database);
    }

    /**
     * Append all query parameters to a StringBuilder which is used
     * to construct a query url.
     * @param urlBuilder  StringBuilder used to construct the query url.
     */
    void appendParametersToUrl(StringBuilder urlBuilder) {
        urlBuilder.append(this.urlOptionsBegin);
        boolean first = true;
        for (String p: this.params.keySet()) {
            if (!first)
                urlBuilder.append(this.urlOptionsSeparator);
            first = false;
            urlBuilder.append(p);
            urlBuilder.append("=");
            urlBuilder.append(this.params.get(p));
        }
    }

    void addParameter(String param, String value) {
        this.params.put(param, value);
    }

    JdbcConnection(char urlOptionsSeparator, char urlOptionsBegin,
                   JdbcConnectionInformation info) {
        this.urlOptionsSeparator = urlOptionsSeparator;
        this.urlOptionsBegin = urlOptionsBegin;
        this.info = info;
    }

    String getQueryToComputeFreqValues(String table, Schema schema, int minCt) {
        StringBuilder builder = new StringBuilder();
        String ctcol = schema.newColumnName("countcol");

        /*
        e.g., select * from
                 (select gender, first_name, count(*) as count
                 from employees
                 group by gender, first_name) tmp
              where count > minCt
              order by count desc
         */

        boolean first = true;
        StringBuilder cols = new StringBuilder();
        for (String col : schema.getColumnNames()) {
            if (!first)
                cols.append(", ");
            first = false;
            cols.append(col);
        }
        builder.append("SELECT * FROM (SELECT ");
        builder.append(cols.toString());
        builder.append(", count(*) AS ").append(ctcol).append(" FROM ")
                .append(table);
        builder.append(" group by ")
                .append(cols.toString());
        builder.append(") tmp where ").append(ctcol).append(" > ").append(minCt);
        builder.append(" order by ").append(ctcol).append(" desc");
        return builder.toString();
    }
}
