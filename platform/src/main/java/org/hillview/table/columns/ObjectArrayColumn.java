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

package org.hillview.table.columns;

import net.openhft.hashing.LongHashFunction;
import org.hillview.table.ColumnDescription;
import org.hillview.table.api.*;
import org.hillview.utils.Converters;

import javax.annotation.Nullable;
import java.security.InvalidParameterException;
import java.util.List;

/*
 * Column of objects of any type; only for moving data around.
 * Size of column expected to be small.
 */
public final class ObjectArrayColumn extends BaseArrayColumn {
    static final long serialVersionUID = 1;

    private final Object[] data;

    public ObjectArrayColumn(final ColumnDescription description, final int size) {
        super(description, size);
        this.data = new Object[size];
    }

    private ObjectArrayColumn(final ColumnDescription description,
                              final Object[] data) {
        super(description, data.length);
        this.data = data;
    }

    public IColumn rename(String newName) {
        return new ObjectArrayColumn(this.description.rename(newName), this.data);
    }

    @Override
    public int sizeInRows() { return this.data.length; }

    @Override
    public double asDouble(final int rowIndex) {
        switch (this.description.kind) {
            case Json:
            case String:
                String str = this.getString(rowIndex);
                return IStringColumn.stringToDouble(str);
            case Integer:
                return this.getInt(rowIndex);
            case Date:
            case Double:
            case Duration:
            case Time:
            case LocalDate:
                return this.getDouble(rowIndex);
            case Interval:
                return this.getEndpoint(rowIndex, true);
            case None:
            default:
                throw new RuntimeException("Unexpected data kind " + this.description.kind);
        }
    }

    @Override
    public String asString(final int rowIndex) {
        return this.data[rowIndex].toString();
    }

    @Override
    public IndexComparator getComparator() {
        return new IndexComparator() {
            @Override
            public int compare(final int i, final int j) {
                if (ObjectArrayColumn.this.description.kind == ContentsKind.None)
                    return 0;
                final boolean iMissing = ObjectArrayColumn.this.isMissing(i);
                final boolean jMissing = ObjectArrayColumn.this.isMissing(j);
                if (iMissing && jMissing) {
                    return 0;
                } else if (iMissing) {
                    return 1;
                } else if (jMissing) {
                    return -1;
                } else {
                    switch (ObjectArrayColumn.this.description.kind) {
                        case Json:
                        case String: {
                            String si = ObjectArrayColumn.this.getString(i);
                            String sj = ObjectArrayColumn.this.getString(j);
                            assert si != null;
                            assert sj != null;
                            return si.compareTo(sj);
                        }
                        case Integer:
                            return Integer.compare(ObjectArrayColumn.this.getInt(i),
                                    ObjectArrayColumn.this.getInt(j));
                        case Date:
                        case Double:
                        case Duration:
                        case Time:
                        case LocalDate:
                            return Double.compare(ObjectArrayColumn.this.getDouble(i),
                                    ObjectArrayColumn.this.getDouble(j));
                        case Interval:
                        {
                            Interval ii = ObjectArrayColumn.this.getInterval(i);
                            Interval ij = ObjectArrayColumn.this.getInterval(j);
                            assert ii != null;
                            assert ij != null;
                            return ii.compareTo(ij);
                        }
                        // case None:  done above.
                        default:
                            throw new RuntimeException("Unexpected data type");
                    }
                }
            }
        };
    }

    @Override
    public IColumn convertKind(
            ContentsKind kind, String newColName, IMembershipSet set) {
        throw new UnsupportedOperationException("Converting object columns");
    }

    @Override
    public int getInt(final int rowIndex) {
        return (int)this.data[rowIndex];
    }

    @Override
    public Interval getInterval(final int rowIndex) {
        return (Interval)this.data[rowIndex];
    }

    @Override
    public double getEndpoint(final int rowIndex, boolean start) {
        Interval i = this.getInterval(rowIndex);
        return Converters.checkNull(i).get(start);
    }

    @Override
    public double getDouble(final int rowIndex) {
        return (double)this.data[rowIndex];
    }

    @Override
    public String getString(final int rowIndex) {
        return (String)this.data[rowIndex];
    }

    public void set(final int rowIndex, @Nullable final Object value) {
        assert(value == null ||
                value instanceof String ||
                value instanceof Integer ||
                value instanceof Double);
        this.data[rowIndex] = value;
    }

    @Override
    public boolean isMissing(final int rowIndex) { return this.data[rowIndex] == null; }

    @Override
    public void setMissing(final int rowIndex) { this.set(rowIndex, null);}

    /**
     * Given two Columns left and right, merge them to a single Column, using the Boolean
     * array mergeLeft which represents the order in which elements merge.
     * mergeLeft[i] = true means the i^th element comes from the left column.
     * @param left The left column
     * @param right The right column
     * @param mergeLeft The order in which to merge the two columns.
     * @return The merged column.
     */
    public static ObjectArrayColumn mergeColumns(final IColumn left, final IColumn right,
                                                 final boolean[] mergeLeft) {
        if (mergeLeft.length != (left.sizeInRows() + right.sizeInRows())) {
            throw new InvalidParameterException("Length of mergeOrder must equal " +
                    "sum of lengths of the columns");
        }
        final ObjectArrayColumn merged = new
                ObjectArrayColumn(left.getDescription(), mergeLeft.length);
        int i = 0, j = 0, k = 0;
        while (k < mergeLeft.length) {
            if (mergeLeft[k]) {
                merged.set(k, left.getData(i));
                i++;
            } else {
                merged.set(k, right.getData(j));
                j++;
            }
            k++;
        }
        return merged;
    }

    /**
     * Given two Columns left and right, merge them to a single Column, using an Integer
     * array mergeOrder which represents the order in which elements merge as follows:
     * -1: left; +1: right; 0: both are equal, so add either but advance in both lists.
     * @param left       The left column
     * @param right      The right column
     * @param mergeOrder The order in which to merge the two columns.
     * @param maxSize Bound on the size of the merged column
     * @return The merged column.
     */
    public static ObjectArrayColumn mergeColumns(final IColumn left, final IColumn right,
                                                 final List<Integer> mergeOrder, int maxSize) {
        final int size = Math.min(maxSize, mergeOrder.size());
        final ObjectArrayColumn merged = new ObjectArrayColumn(left.getDescription(), size);
        int i = 0, j = 0, k = 0;
        while (k < size) {
            if (mergeOrder.get(k) < 0) {
                merged.set(k, left.getData(i));
                i++;
            } else if (mergeOrder.get(k) > 0) {
                merged.set(k, right.getData(j));
                j++;
            } else {
                merged.set(k, right.getData(j));
                i++;
                j++;
            }
            k++;
        }
        return merged;
    }

    @Override
    public long hashCode64(int rowIndex, LongHashFunction hash) {
        if (this.isMissing(rowIndex))
            return MISSING_HASH_VALUE;
        switch (ObjectArrayColumn.this.description.kind) {
            case Json:
            case String:
                String str = this.getString(rowIndex);
                assert str != null;
                return hash.hashChars(str);
            case Date:
            case Double:
            case Duration:
            case Time:
            case LocalDate:
                return hash.hashLong(Double.doubleToLongBits(this.getDouble(rowIndex)));
            case Integer:
                return hash.hashInt(this.getInt(rowIndex));
            case Interval:
                Interval i = this.getInterval(rowIndex);
                assert i != null;
                return i.hash(hash);
            default:
            case None:  // handled above
                throw new RuntimeException("Unexpected data kind " + ObjectArrayColumn.this.description.kind);
        }
    }
}
