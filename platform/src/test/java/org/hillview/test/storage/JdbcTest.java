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

package org.hillview.test.storage;

import org.hillview.storage.jdbc.JdbcConnectionInformation;
import org.hillview.storage.jdbc.JdbcDatabase;
import org.hillview.table.api.ITable;
import org.hillview.test.BaseTest;
import org.junit.Assert;

import javax.annotation.Nullable;
import java.sql.SQLException;

class JdbcTest extends BaseTest {
    @Nullable
    ITable getTable(JdbcConnectionInformation conn) throws SQLException {
        Assert.assertNotNull(conn.table);
        JdbcDatabase db = new JdbcDatabase(conn);
        try {
            db.connect();
        } catch (Exception e) {
            // This will fail if a database is not deployed, but we don't want to fail the test.
            this.ignoringException("Cannot connect to database", e);
            return null;
        }
        ITable table = db.readTable();
        db.disconnect();
        return table;
    }
}
