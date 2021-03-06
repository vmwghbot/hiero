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

package org.hillview.test.dataStructures;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.hillview.table.LazySchema;
import org.hillview.dataset.api.IJson;
import org.hillview.sketches.results.Count;
import org.hillview.sketches.results.NextKList;
import org.hillview.storage.JsonFileLoader;
import org.hillview.table.*;
import org.hillview.table.api.ContentsKind;
import org.hillview.table.api.IColumn;
import org.hillview.table.api.ITable;
import org.hillview.table.columns.*;
import org.hillview.table.rows.RowSnapshot;
import org.hillview.test.BaseTest;
import org.hillview.utils.JsonGroups;
import org.hillview.utils.JsonList;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JsonTest extends BaseTest {
    @Test
    public void convert() {
        ColumnDescription cd0 = new ColumnDescription("Age", ContentsKind.Integer);
        String s = cd0.toJson();
        Assert.assertEquals(s, "{\"name\":\"Age\",\"kind\":\"Integer\"}");

        ColumnDescription cd1 = new ColumnDescription("Weight", ContentsKind.Double);
        Schema schema = new Schema();
        schema.append(cd0);
        schema.append(cd1);
        s = schema.toJson();
        Assert.assertEquals(s, "[{\"name\":\"Age\",\"kind\":\"Integer\"}," +
        "{\"name\":\"Weight\",\"kind\":\"Double\"}]");

        ColumnDescription cd2 = new ColumnDescription("Name", ContentsKind.String);

        IntArrayColumn iac = new IntArrayColumn(cd0, 2);
        iac.set(0, 10);
        iac.set(1, 20);

        DoubleArrayColumn dac = new DoubleArrayColumn(cd1, 2);
        dac.set(0, 90.0);
        dac.set(1, 120.0);

        StringArrayColumn sac = new StringArrayColumn(cd2, 2);
        sac.set(0, "John");
        sac.set(1, "Mike");

        List<IColumn> l = new ArrayList<IColumn>(3);
        l.add(iac);
        l.add(dac);
        l.add(sac);
        SmallTable t = new SmallTable(l);
        RowSnapshot rs = new RowSnapshot(t, 0);
        s = rs.toJson();
        Assert.assertEquals(s, "[10,90.0,\"John\"]");

        s = t.toJson();
        Assert.assertEquals(s, "{" +
                "\"schema\":[{\"name\":\"Age\",\"kind\":\"Integer\"}," +
                "{\"name\":\"Weight\",\"kind\":\"Double\"}," +
                        "{\"name\":\"Name\",\"kind\":\"String\"}]," +
                "\"rowCount\":2," +
                "\"rows\":[[10,90.0,\"John\"],[20,120.0,\"Mike\"]]" +
        "}");

        Schema back = IJson.gsonInstance.fromJson(schema.toJson(), Schema.class);
        Assert.assertEquals(schema, back);

        IntList li = new IntArrayList();
        li.add(2);
        li.add(3);
        NextKList list = new NextKList(t, null, li, 0, 100);
        s = list.toJson();
        Assert.assertEquals(s, "{" +
                "\"rowsScanned\":100," +
                "\"startPosition\":0," +
                "\"rows\":[" +
                    "{\"count\":2,\"values\":[10,90.0,\"John\"]}," +
                    "{\"count\":3,\"values\":[20,120.0,\"Mike\"]}" +
                "],\"aggregates\":null}");

        String d = "[{\"cd\":{\"name\":\"Age\",\"kind\":\"Integer\"},\"agkind\":\"Sum\"}]";
        AggregateDescription[] desc = IJson.gsonInstance.fromJson(d, AggregateDescription[].class);
        Assert.assertNotNull(desc);
        Assert.assertEquals(1, desc.length);
        Assert.assertEquals("Age", desc[0].cd.name);
        Assert.assertEquals(ContentsKind.Integer, desc[0].cd.kind);
        Assert.assertEquals(AggregateDescription.AggregateKind.Sum, desc[0].agkind);
    }

    @Test
    public void jsonReaderTest() {
        final String jsonFolder = "../data/ontime";
        final String jsonSample = "short.schema";
        JsonFileLoader reader = new JsonFileLoader(jsonFolder + "/" + jsonSample, new LazySchema());
        ITable table = reader.load();
        Assert.assertNotNull(table);
        Assert.assertEquals("Table[2x15]", table.toString());
    }

    @Test
    public void testHierarchySerialization() {
        DoubleColumnQuantization md1 = new DoubleColumnQuantization("name", 12.345, 0.0, 123.45);
        String s = IJson.gsonInstance.toJson(md1);
        ColumnQuantization des = IJson.gsonInstance.fromJson(s, ColumnQuantization.class);
        Assert.assertTrue(des instanceof DoubleColumnQuantization);
    }

    @Test
    public void jsonGroupTest() {
        Count c = new Count(3);
        String s = IJson.gsonInstance.toJson(c);
        Assert.assertEquals("3", s);

        JsonList<Count> list = new JsonList<Count>();
        list.add(new Count(3));
        JsonGroups<Count> jg = new JsonGroups<Count>(list, new Count(5));
        s = IJson.gsonInstance.toJson(jg);
        Assert.assertEquals("{\"perBucket\":[3],\"perMissing\":5}", s);
    }
}
