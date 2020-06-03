/*
 * Copyright (c) 2020 VMware Inc. All Rights Reserved.
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

import org.hillview.sketches.results.Count;
import org.hillview.sketches.results.IHistogramBuckets;
import org.hillview.table.api.ITable;

/**
 * Standard 2D histogram computed using 2 nested group-by operators.
 */
public class Histogram2DSketch extends GroupBySketch<
        Groups<Count>,
        GroupByWorkspace<EmptyWorkspace>,
        GenericHistogramSketch> {

    public Histogram2DSketch(
            IHistogramBuckets buckets0,
            IHistogramBuckets buckets1) {
        super(buckets1, new GenericHistogramSketch(buckets0));
    }

    @Override
    public GroupByWorkspace<GroupByWorkspace<EmptyWorkspace>> initialize(ITable data) {
        data.getLoadedColumns(this.buckets.getColumn(), super.buckets.getColumn());
        return super.initialize(data);
    }
}
