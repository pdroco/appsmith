/* eslint-disable cypress/no-unnecessary-waiting */
const commonlocators = require("../../../../../locators/commonlocators.json");
import {
  agHelper,
  apiPage,
  entityExplorer,
  propPane,
} from "../../../../../support/Objects/ObjectsCore";

describe("Test Create Api and Bind to Table widget", function () {
  before(() => {
    cy.fixture("tableTextPaginationDsl").then((val) => {
      agHelper.AddDsl(val);
    });
  });

  it("1. Create an API and Execute the API and bind with Table", function () {
    // Create and execute an API and bind with table
    apiPage.CreateAndFillApi(
      this.dataSet.paginationUrl + this.dataSet.paginationParam,
    );
    agHelper.VerifyEvaluatedValue(
      this.dataSet.paginationUrl + "mock-api?records=20&page=1&size=10",
    );
    apiPage.RunAPI();
    //Test: Validate Table with API data and then add a column
    // Open property pane
    entityExplorer.SelectEntityByName("Table1");
    // Clear Table data and enter Apil data into table data
    cy.testJsontext("tabledata", "{{Api1.data}}");
    // Check Widget properties
    cy.CheckWidgetProperties(commonlocators.serverSidePaginationCheckbox);
    // Open Text1 in propert pane
    entityExplorer.SelectEntityByName("Text1");
    propPane.UpdatePropertyFieldValue("Text", "{{Table1.selectedRow.url}}");
    // Open Table1 propert pane
    entityExplorer.SelectEntityByName("Table1");
    // Compare table 1st index data with itself
    cy.readTabledata("0", "0").then((tabData) => {
      const tableData = tabData;
      localStorage.setItem("tableDataPage1", tableData);
    });
    // Verify 1st index data
    cy.readTabledata("0", "4").then((tabData) => {
      const tableData = tabData;
      expect(tableData).to.equal("1");
    });
    // add new column
    cy.addColumn("CustomColumn");
    //Test:Table widget toggle test for background color
    // Open id property pane
    cy.editColumn("id");
    // eslint-disable-next-line cypress/no-unnecessary-waiting
    cy.wait(1000);
    // Click on cell background JS button
    propPane.EnterJSContext("Cell Background", "Green");
    cy.get("[data-testid='t--property-pane-back-btn']").click({ force: true });
    cy.wait("@updateLayout");
    // verify the cell background color
    cy.readTabledataValidateCSS("1", "4", "background-color", "rgb(0, 128, 0)");
  });

  it("2. Edit column name and validate test for computed value based on column type selected", function () {
    // opoen customColumn1 property pane
    cy.editColumn("customColumn1");
    // Enter Apil 1st user email data into customColumn1
    cy.readTabledataPublish("1", "7").then((tabData) => {
      const tabValue = tabData;
      cy.updateComputedValue("{{Api1.data[0].email}}");
      cy.readTabledataPublish("1", "7").then((tabData) => {
        expect(tabData).not.to.be.equal(tabValue);
        cy.log("computed value of plain text " + tabData);
      });
    });
    cy.closePropertyPane();
    //Test: Update table json data and check the column names updated
    // Open table propert pane
    entityExplorer.SelectEntityByName("Table1");
    cy.backFromPropertyPanel();
    // Change the table data
    cy.testJsontext("tabledata", JSON.stringify(this.dataSet.TableInputUpdate));
    cy.wait("@updateLayout");
    // verify columns are visible or not in the propert pane
    cy.tableColumnDataValidation("id");
    cy.tableColumnDataValidation("email");
    cy.tableColumnDataValidation("userName");
    cy.tableColumnDataValidation("productName");
    cy.tableColumnDataValidation("orderAmount");
    cy.tableColumnDataValidation("customColumn1");
    // Hide the columns in property pane
    cy.hideColumn("email");
    cy.hideColumn("userName");
    cy.hideColumn("productName");
    cy.hideColumn("orderAmount");
    // verify customColumn is visible in the table
    cy.get(".draggable-header:contains('CustomColumn')").should("be.visible");
    cy.closePropertyPane();
  });
});
