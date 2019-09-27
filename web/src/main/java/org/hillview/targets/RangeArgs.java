/*
 * Copyright (c) 2019 VMware Inc. All Rights Reserved.
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

package org.hillview.targets;

import org.hillview.HillviewComputation;
import org.hillview.dataset.api.ISketch;
import org.hillview.sketches.*;
import org.hillview.table.ColumnDescription;
import org.hillview.table.api.ITable;

import java.util.function.BiFunction;

class RangeArgs {
    // if this is String, or Json we are sampling strings
    public ColumnDescription cd = new ColumnDescription();
    public long seed;       // only used if sampling strings
    public int stringsToSample;  // only used if sampling strings

    // This class has a bunch of unchecked casts, but the Java
    // type system is not good enough to express these operations
    // in a type-safe manner.
    ISketch<ITable, BucketsInfo> getSketch() {
        if (this.cd.kind.isString()) {
            int samples = Math.min(this.stringsToSample * this.stringsToSample, 100000);
            ISketch<ITable, MinKSet<String>> s = new SampleDistinctElementsSketch(
                    // We sample stringsToSample squared
                    this.cd.name, this.seed, samples);
            //noinspection unchecked
            return (ISketch<ITable, BucketsInfo>)(Object)s;
        } else {
            ISketch<ITable, DataRange> s = new DoubleDataRangeSketch(this.cd.name);
            //noinspection unchecked
            return (ISketch<ITable, BucketsInfo>)(Object)s;
        }
    }

    BiFunction<BucketsInfo, HillviewComputation, BucketsInfo> getPostProcessing() {
        if (this.cd.kind.isString()) {
            int b = this.stringsToSample;
            //noinspection unchecked
            return (e, c) -> new TableTarget.StringBucketLeftBoundaries((MinKSet<String>)e, b);
        } else {
            return (e, c) -> e;
        }
    }
}
