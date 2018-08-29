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

import {Receiver} from "../rpc";
import {
    CombineOperators,
    Heatmap,
    RecordOrder,
    RemoteObjectId
} from "../javaBridge";
import {FullPage} from "../ui/fullPage";
import {TableTargetAPI} from "../tableTarget";
import {SchemaClass} from "../schemaClass";
import {ICancellable, PartialResult} from "../util";
import {AxisData, AxisKind} from "./axisData";
import {
    IViewSerialization,
    TrellisHistogram2DSerialization
} from "../datasetView";
import {IDataView} from "../ui/dataview";
import {Resolution} from "../ui/ui";
import {PlottingSurface} from "../ui/plottingSurface";
import {SubMenu, TopMenu} from "../ui/menu";
import {Histogram2DPlot} from "../ui/Histogram2DPlot";
import {DataRangesCollector, TrellisShape} from "./dataRangesCollectors";
import {TrellisChartView} from "./trellisChartView";
import {NextKReceiver, TableView} from "./tableView";
import {BucketDialog, HistogramViewBase} from "./histogramViewBase";

export class TrellisHistogram2DView extends TrellisChartView {
    protected hps: Histogram2DPlot[];
    protected buckets: number;
    protected xAxisData: AxisData;
    protected legendAxisData: AxisData;
    protected relative: boolean;
    protected data: Heatmap[];

    public constructor(
        remoteObjectId: RemoteObjectId,
        rowCount: number,
        schema: SchemaClass,
        protected shape: TrellisShape,
        protected samplingRate: number,
        page: FullPage) {
        super(remoteObjectId, rowCount, schema, shape, page, "Trellis2DHistogram");
        this.topLevel = document.createElement("div");
        this.topLevel.className = "chart";
        this.hps = [];
        this.data = null;

        this.menu = new TopMenu( [{
            text: "Export",
            help: "Save the information in this view in a local file.",
            subMenu: new SubMenu([{
                text: "As CSV",
                help: "Saves the data in this view in a CSV file.",
                action: () => { this.export(); },
            }]),
        }, { text: "View", help: "Change the way the data is displayed.", subMenu: new SubMenu([
                { text: "refresh",
                    action: () => { this.refresh(); },
                    help: "Redraw this view.",
                },
                { text: "table",
                    action: () => this.showTable(),
                    help: "Show the data underlying view using a table view.",
                },
                { text: "exact",
                    action: () => this.exactHistogram(),
                    help: "Draw this data without making any approximations.",
                },
                { text: "# buckets...",
                    action: () => this.chooseBuckets(),
                    help: "Change the number of buckets used to draw the histograms. " +
                        "The number of buckets must be between 1 and " + Resolution.maxBucketCount,
                },
                { text: "heatmap...",
                    action: () => this.heatmap(),
                    help: "Show this data as a Trellis plot of heatmaps.",
                },
            ]) },
            this.dataset.combineMenu(this, page.pageId),
        ]);

        this.page.setMenu(this.menu);
        this.buckets = Math.round(shape.size.width / Resolution.minBarWidth);
    }

    public setAxes(xAxisData: AxisData,
                   legendAxisData: AxisData,
                   groupByAxisData: AxisData): void {
        this.xAxisData = xAxisData;
        this.legendAxisData = legendAxisData;
        this.groupByAxisData = groupByAxisData;

        this.createSurfaces( (surface) => {
            const hp = new Histogram2DPlot(surface);
            this.hps.push(hp);
        });
    }

    protected onMouseMove(): void {
        // TODO
    }

    protected showTable(): void {
        const newPage = this.dataset.newPage("Table", this.page);
        const table = new TableView(this.remoteObjectId, this.rowCount, this.schema, newPage);
        newPage.setDataView(table);
        table.schema = this.schema;

        const order =  new RecordOrder([ {
            columnDescription: this.xAxisData.description,
            isAscending: true
        }, {
            columnDescription: this.legendAxisData.description,
            isAscending: true
        }, {
            columnDescription: this.groupByAxisData.description,
            isAscending: true
        }]);
        const rr = table.createNextKRequest(order, null, Resolution.tableRowsOnScreen);
        rr.invoke(new NextKReceiver(newPage, table, rr, false, order, null));
    }

    protected exactHistogram(): void {
        const buckets = HistogramViewBase.maxTrellis3DBuckets(this.page);
        const cds = [this.xAxisData.description,
            this.legendAxisData.description, this.groupByAxisData.description];
        const rr = this.createDataRangesRequest(cds, buckets);
        rr.invoke(new DataRangesCollector(this, this.page, rr, this.schema,
            [this.buckets, this.legendAxisData.bucketCount, this.shape.bucketCount],
                cds, null, {
                reusePage: true, relative: this.relative,
                chartKind: "Trellis2DHistogram", exact: true
            }));
    }

    protected chooseBuckets(): void {
        const bucketDialog = new BucketDialog();
        bucketDialog.setAction(() => this.changeBuckets(bucketDialog.getBucketCount()));
        bucketDialog.show();
    }

    protected changeBuckets(bucketCount: number): void {
        // TODO
    }

    protected heatmap(): void {
        const buckets = HistogramViewBase.maxTrellis3DBuckets(this.page);
        const cds = [this.xAxisData.description,
            this.legendAxisData.description, this.groupByAxisData.description];
        const rr = this.createDataRangesRequest(cds, buckets);
        rr.invoke(new DataRangesCollector(this, this.page, rr, this.schema,
            [this.buckets, this.legendAxisData.bucketCount, this.shape.bucketCount],
            cds, null, {
                reusePage: false, relative: this.relative,
                chartKind: "TrellisHeatmap", exact: true
            }));
    }

    protected export(): void {
        // TODO
    }

    public resize(): void {
        this.updateView(this.data, 0);
    }

    public refresh(): void {
        this.updateView(this.data, 0);
    }

    public serialize(): IViewSerialization {
        const ser: TrellisHistogram2DSerialization = {
            ...super.serialize(),
            samplingRate: this.samplingRate,
            columnDescription0: this.xAxisData.description,
            columnDescription1: this.legendAxisData.description,
            xBucketCount: this.xAxisData.bucketCount,
            yBucketCount: this.legendAxisData.bucketCount,
            relative: this.relative,
            groupByColumn: this.groupByAxisData.description,
            xWindows: this.shape.xNum,
            yWindows: this.shape.yNum,
            groupByBucketCount: this.groupByAxisData.bucketCount
        };
        return ser;
    }

    public static reconstruct(ser: TrellisHistogram2DSerialization, page: FullPage): IDataView {
        if (ser.remoteObjectId == null || ser.rowCount == null || ser.xWindows == null ||
            ser.yWindows == null || ser.groupByBucketCount ||
            ser.samplingRate == null || ser.schema == null)
            return null;
        const schema = new SchemaClass([]).deserialize(ser.schema);
        const shape = TrellisChartView.deserializeShape(ser, page);
        const view = new TrellisHistogram2DView(ser.remoteObjectId, ser.rowCount,
            schema, shape, ser.samplingRate, page);
        view.setAxes(new AxisData(ser.columnDescription0, null),
            new AxisData(ser.columnDescription1, null),
            new AxisData(ser.groupByColumn, null));
        return view;
    }

    public updateView(data: Heatmap[], elapsedMs: number): void {
        this.data = data;
        for (let i = 0; i < data.length; i++) {
            const histo = data[i];
            const plot = this.hps[i];
            plot.setData(histo, this.xAxisData, this.samplingRate, false);
            plot.draw();
        }

        // We draw the axes after drawing the data
        this.xAxisData.setResolution(this.shape.size.width, AxisKind.Bottom);
        const yAxis = this.hps[0].getYAxis();
        this.drawAxes(this.xAxisData, yAxis);
        this.setupMouse();
        this.page.reportTime(elapsedMs);
    }

    // combine two views according to some operation
    public combine(how: CombineOperators): void {
        // TODO
    }
}

/**
 * Renders a Trellis plot of 2D histograms
 */
export class TrellisHistogram2DRenderer extends Receiver<Heatmap[]> {
    protected trellisView: TrellisHistogram2DView;

    constructor(page: FullPage,
                remoteTable: TableTargetAPI,
                protected rowCount: number,
                protected schema: SchemaClass,
                protected axes: AxisData[],
                protected samplingRate: number,
                protected shape: TrellisShape,
                operation: ICancellable<Heatmap[]>,
                protected reusePage: boolean) {
        super(
            reusePage ? page : page.dataset.newPage(
                "Histograms " + schema.displayName(axes[0].description.name) +
                ", " + schema.displayName(axes[1].description.name) +
                " grouped by " + schema.displayName(axes[2].description.name), page),
            operation, "histogram");
        this.trellisView = new TrellisHistogram2DView(
            remoteTable.remoteObjectId, rowCount, schema,
            this.shape, this.samplingRate, this.page);
        this.trellisView.setAxes(axes[0], axes[1], axes[2]);
        this.page.setDataView(this.trellisView);
    }

    public onNext(value: PartialResult<Heatmap[]>): void {
        super.onNext(value);
        if (value == null) {
            return;
        }

        this.trellisView.updateView(value.data, this.elapsedMilliseconds());
    }
}
