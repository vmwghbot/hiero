/*
 * Copyright (c) 2017 VMware Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {TopMenu, SubMenu, TopMenuItem} from "./ui/menu";
import {InitialObject} from "./initialObject";
import {RemoteObject, OnCompleteRenderer} from "./rpc";
import {ICancellable} from "./util";
import {IDataView} from "./ui/dataview";
import {ConsoleDisplay} from "./ui/errReporter";
import {FullPage} from "./ui/fullPage";
import {FileSetDescription, JdbcConnectionInformation, Status} from "./javaBridge";
import {Dialog, FieldKind} from "./ui/dialog";
import {Test} from "./test";

/**
 * The load menu is the first menu that is displayed on the screen.
 */
export class LoadMenu extends RemoteObject implements IDataView {
    private top: HTMLElement;
    private menu: TopMenu;
    private console: ConsoleDisplay;
    private testDatasetsMenu: SubMenu;
    private loadMenu: SubMenu;
    private advanced: boolean;

    constructor(protected init: InitialObject, protected page: FullPage) {
        super(init.remoteObjectId);

        this.advanced = false;
        this.top = document.createElement("div");
        this.testDatasetsMenu = new SubMenu([
            { text: "Flights",
                action: () => init.testDataset(0, this.page),
                help: "The US flights dataset." },
            /*
            { text: "Flights (subset)",
                action: () => init.testDataset(1, this.page),
                help: "A few months of the US flights dataset." },
            { text: "MNIST",
                action: () => init.testDataset(3, this.page),
                help: "Images for the MNIST competition (only on local machine)." },
            { text: "Image segmentation",
                action: () => init.testDataset(4, this.page),
                help: "High dimensional dataset with images (only on local machine)." }
            */
            { text: "Flights (all columns)",
                action: () => init.testDataset(5, this.page),
                help: "The US flights dataset -- all 110 columns." },
        ]);
        this.loadMenu = new SubMenu([
            { text: "System logs",
                action: () => init.loadLogs(this.page),
                help: "The logs generated by the hillview system itself." },
            { text: "CSV files...",
                action: () => this.showCSVFileDialog(),
                help: "A set of comma-separated value files residing on the worker machines." },
            { text: "JSON files...",
                action: () => this.showJSONFileDialog(),
                help: "A set of files containing JSOn values residing on the worker machines." },
            { text: "DB tables...",
                action: () => this.showDBDialog(),
                help: "A set of database tables residing in databases on each worker machine." }
        ]);

        let items: TopMenuItem[] = [
            { text: "Test datasets", help: "Hardwired datasets for testing Hillview.",
                subMenu: this.testDatasetsMenu
            }, {
                text: "Load", help: "Load data from the worker machines.",
                subMenu: this.loadMenu }
        ];
        /**
         * These are operations supported by the back-end management API.
         * They are mostly for testing, debugging, maintenance and measurement.
         */
        items.push(
            {
                text: "Test", help: "Run UI tests", subMenu: new SubMenu([
                    {
                        text: "Run", help: "Run end-to-end tests from the user interface. " +
                        "These tests simulate the user clicking in various menus in the browser." +
                        "The tests must be run " +
                        "immediately after reloading the main web page. The user should " +
                        "not use the mouse during the tests.", action: () => this.runTests()
                    }
                ])
            },
            {
                text: "Manage", help: "Execute cluster management operations.", subMenu: new SubMenu([
                    {
                        text: "List machines",
                        action: () => this.ping(),
                        help: "Produces a list of all worker machines."
                    },
                    {
                        text: "Toggle memoization",
                        action: () => this.command("toggleMemoization"),
                        help: "Asks the workers to memoize/not memoize query results."
                    },
                    {
                        text: "Memory use",
                        action: () => this.command("memoryUse"),
                        help: "Reports Java memory use for each worker."
                    },
                    {
                        text: "Purge memoized",
                        action: () => this.command("purgeMemoization"),
                        help: "Remove all memoized datasets from the workers."
                    },
                    {
                        text: "Purge root datasets",
                        action: () => this.command("purgeDatasets"),
                        help: "Remove all datasets stored at the root node."
                    },
                    {
                        text: "Purge leaf datasets",
                        action: () => this.command("purgeLeafDatasets"),
                        help: "Remove all datasets stored at the worker nodes."
                    }
                ])
            }
        );

        this.menu = new TopMenu(items);
        this.showAdvanced(false);
        this.console = new ConsoleDisplay();
        this.page.setMenu(this.menu);
        this.top.appendChild(this.console.getHTMLRepresentation());
    }

    public toggleAdvanced(): void {
        this.advanced = !this.advanced;
        this.showAdvanced(this.advanced);
    }

    showAdvanced(show: boolean): void {
        this.menu.enable("Manage", show);
        this.menu.enable("Test", show);
        /*
        let special: string[] = [ "MNIST", "Image segmentation" ];
        for (let s of special)
            this.testDatasetsMenu.enable(s, show);
        */
        this.loadMenu.enable("DB tables...", show);
    }

    /**
     * Starts the execution of the UI tests.
     */
    runTests(): void {
        Test.instance.runTests();
    }

    showDBDialog(): void {
        let dialog = new DBDialog();
        dialog.setAction(() => this.init.loadDBTable(dialog.getConnection(), this.page));
        dialog.show();
    }

    showCSVFileDialog(): void {
        let dialog = new CSVFileDialog();
        dialog.setAction(() => this.init.loadCSVFiles(dialog.getFiles(), this.page));
        dialog.show();
    }

    showJSONFileDialog(): void {
        let dialog = new JsonFileDialog();
        dialog.setAction(() => this.init.loadJsonFiles(dialog.getFiles(), this.page));
        dialog.show();
    }

    getHTMLRepresentation(): HTMLElement {
        return this.top;
    }

    ping(): void {
        let rr = this.createStreamingRpcRequest<string[]>("ping", null);
        rr.invoke(new PingReceiver(this.page, rr));
    }

    command(command: string): void {
        let rr = this.createStreamingRpcRequest<Status[]>(command, null);
        rr.invoke(new CommandReceiver(command, this.page, rr));
    }

    setPage(page: FullPage) {
        if (page == null)
            throw("null FullPage");
        this.page = page;
    }

    getPage() : FullPage {
        if (this.page == null)
            throw("Page not set");
        return this.page;
    }

    public refresh(): void {}
}

/**
 * Dialog that asks the user which CSV files to load.
 */
class CSVFileDialog extends Dialog {
    constructor() {
        super("Load CSV files", "Loads comma-separated value (CSV) files from all machines that are part of the service.");
        this.addTextField("folder", "Folder", FieldKind.String, "/",
            "Folder on the remote machines where all the CSV files are found.");
        this.addTextField("fileNamePattern", "File name pattern", FieldKind.String, "*.csv",
            "Shell pattern that describes the names of the files to load.");
        this.addTextField("schemaFile", "Schema file (optional)", FieldKind.String, "schema",
            "The name of a JSON file that contains the schema of the data (leave empty if no schema file exists).");
        this.addBooleanField("hasHeader", "Header row", false, "True if first row in each file is a header row");
        this.setCacheTitle("CSVFileDialog");
    }

    public getFiles(): FileSetDescription {
        return {
            schemaFile: this.getFieldValue("schemaFile"),
            fileNamePattern: this.getFieldValue("fileNamePattern"),
            headerRow: this.getBooleanValue("hasHeader"),
            folder: this.getFieldValue("folder")
        }
    }
}

/**
 * Dialog that asks the user which Json files to load.
 */
class JsonFileDialog extends Dialog {
    constructor() {
        super("Load JSON files", "Loads JSON files from all machines that are part of the service.  Each file should " +
            "contain a JSON array of JSON objects.  All JSON objects should have the same schema.  Each JSON object" +
            "field becomes a separate column.  The schema of all JSON files loaded should be the same.");
        this.addTextField("folder", "Folder", FieldKind.String, "/",
            "Folder on the remote machines where all the CSV files are found.");
        this.addTextField("fileNamePattern", "File name pattern", FieldKind.String, "*.json",
            "Shell pattern that describes the names of the files to load.");
        this.addTextField("schemaFile", "Schema file (optional)", FieldKind.String, "data.schema",
            "The name of a JSON file that contains the schema of the data (leave empty if no schema file exists).");
        this.setCacheTitle("JsonFileDialog");
    }

    public getFiles(): FileSetDescription {
        return {
            schemaFile: this.getFieldValue("schemaFile"),
            fileNamePattern: this.getFieldValue("fileNamePattern"),
            headerRow: false,
            folder: this.getFieldValue("folder")
        }
    }
}

/**
 * Dialog asking the user which DB table to load.
 */
class DBDialog extends Dialog {
    constructor() {
        super("Load DB tables", "Loads one table on each machine that is part of the service.");
        // TODO: this should be a pattern string, based on local worker name.
        let sel = this.addSelectField("databaseKind", "Database kind", ["mysql", "impala"], "mysql",
            "The kind of database.");
        sel.onchange = () => this.dbChanged();
        this.addTextField("host", "Host", FieldKind.String, "localhost",
            "Machine name where database is located; each machine will open a connection to this host");
        this.addTextField("port", "Port", FieldKind.Integer, "3306",
            "Network port to connect to database.");
        this.addTextField("database", "Database", FieldKind.String, null,
            "Name of database to load.");
        this.addTextField("table", "Table", FieldKind.String, null,
            "The name of the table to load.");
        this.addTextField("user", "User", FieldKind.String, null,
            "(Optional) The name of the user opening the connection.");
        this.addTextField("password", "Password", FieldKind.Password, null,
            "(Optional) The password for the user opening the connection.");
        this.setCacheTitle("DBDialog");
    }

    dbChanged(): void {
        let db = this.getFieldValue("databaseKind");
        switch (db) {
            case "mysql":
                this.setFieldValue("port", "3306");
                break;
            case "impala":
                this.setFieldValue("port", "21050");
                break;
        }
    }

    public getConnection(): JdbcConnectionInformation {
        return {
            host: this.getFieldValue("host"),
            port: this.getFieldValueAsNumber("port"),
            database: this.getFieldValue("database"),
            table: this.getFieldValue("table"),
            user: this.getFieldValue("user"),
            password: this.getFieldValue("password"),
            databaseKind: this.getFieldValue("databaseKind")
        }
    }
}

/**
 * This is the main function exposed to the web page, which causes everything
 * to get going.  It creates and displays the menu for loading data.
 */
export function createLoadMenu(): void {
    let page = new FullPage("Load", "Load", null);
    page.append();
    let menu = new LoadMenu(InitialObject.instance, page);
    page.setDataView(menu);
    page.getTitleElement().onclick = () => menu.toggleAdvanced();
}

/**
 * Receives the results of a remote management command.
 * @param T  each individual result has this type.
 */
class CommandReceiver extends OnCompleteRenderer<Status[]> {
    public constructor(name: string, page: FullPage, operation: ICancellable) {
        super(page, operation, name);
    }

    toString(s: Status): string {
        let str = s.hostname + "=>";
        if (s.exception == null)
            str += s.result;
        else
            str += s.exception;
        return str;
    }

    run(value: Status[]): void {
        let res = "";
        for (let s of value) {
            if (res != "")
                res += "\n";
            res += this.toString(s);
        }
        this.page.reportError(res);
    }
}

/**
 * Receives and displays the result of the ping command.
 */
class PingReceiver extends OnCompleteRenderer<string[]> {
    public constructor(page: FullPage, operation: ICancellable) {
        super(page, operation, "ping");
    }

    run(value: string[]): void {
        this.page.reportError(this.value.toString());
    }
}