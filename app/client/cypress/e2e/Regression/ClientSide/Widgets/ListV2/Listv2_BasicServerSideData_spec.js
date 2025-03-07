const publishLocators = require("../../../../../locators/publishWidgetspage.json");
const datasource = require("../../../../../locators/DatasourcesEditor.json");
const queryLocators = require("../../../../../locators/QueryEditor.json");
const commonlocators = require("../../../../../locators/commonlocators.json");
import * as _ from "../../../../../support/Objects/ObjectsCore";

const toggleJSButton = (name) => `.t--property-control-${name} .t--js-toggle`;

describe("List widget v2 - Basic server side data tests", () => {
  before(() => {
    cy.fixture("Listv2/listWithServerSideData").then((val) => {
      _.agHelper.AddDsl(val);
    });
    // Open Datasource editor
    if (!Cypress.env("AIRGAPPED")) {
      cy.wait(2000);
      // Create sample(mock) user database.
      _.dataSources.CreateMockDB("Users").then(() => {
        _.dataSources.CreateQueryAfterDSSaved();
        _.dataSources.ToggleUsePreparedStatement(false);
        _.dataSources.EnterQuery(
          "SELECT * FROM users OFFSET {{List1.pageNo * List1.pageSize}} LIMIT {{List1.pageSize}};",
        );
        _.dataSources.RunQuery();
      });
      _.entityExplorer.SelectEntityByName("Page1");
    } else {
      cy.wait(2000);
      _.dataSources.CreateDataSource("Postgres");
      cy.get("@dsName").then(($dsName) => {
        _.dataSources.CreateQueryFromActiveTab($dsName, true);
        _.dataSources.ToggleUsePreparedStatement(false);
        _.dataSources.EnterQuery(
          "SELECT * FROM users OFFSET {{List1.pageNo * 1}} LIMIT {{List1.pageSize}};",
        );
        _.dataSources.RunQuery();
      });
      _.entityExplorer.SelectEntityByName("Page1");
    }
  });

  it(
    "excludeForAirgap",
    "1. shows correct number of items and binding texts",
    () => {
      cy.wait(2000);
      cy.get(publishLocators.containerWidget).should("have.length", 3);
      cy.get(publishLocators.imageWidget).should("have.length", 3);
      cy.get(publishLocators.textWidget).should("have.length", 6);

      cy.get(publishLocators.containerWidget).each(($containerEl) => {
        cy.wrap($containerEl)
          .get(publishLocators.textWidget)
          .eq(1)
          .find("span")
          .invoke("text")
          .should("have.length.gt", 0);
      });
    },
  );
  it(
    "airgap",
    "1. shows correct number of items and binding texts - airgap",
    () => {
      cy.wait(2000);
      cy.get(publishLocators.containerWidget).should("have.length", 2);
      cy.get(publishLocators.imageWidget).should("have.length", 2);
      cy.get(publishLocators.textWidget).should("have.length", 4);

      cy.get(publishLocators.containerWidget).each(($containerEl) => {
        cy.wrap($containerEl)
          .get(publishLocators.textWidget)
          .eq(1)
          .find("span")
          .invoke("text")
          .should("have.length.gt", 0);
      });
    },
  );

  it(
    "excludeForAirgap",
    "2. next page shows correct number of items and binding text",
    () => {
      cy.get(".t--list-widget-next-page.rc-pagination-next")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(2);

      /**
       * isLoading of the widget does not work properly so for a moment
       * the previous data are visible which can cause the test to pass/fail.
       * Adding a wait makes sure the next page data is loaded.
       */
      cy.wait(3000);

      cy.get(publishLocators.containerWidget).should("have.length", 3);
      cy.get(publishLocators.imageWidget).should("have.length", 3);
      cy.get(publishLocators.textWidget).should("have.length", 6);

      cy.get(publishLocators.containerWidget).each(($containerEl) => {
        cy.wrap($containerEl)
          .get(publishLocators.textWidget)
          .eq(1)
          .find("span")
          .invoke("text")
          .should("have.length.gt", 0);
      });
    },
  );

  it(
    "airgap",
    "2. next page shows correct number of items and binding text - airgap",
    () => {
      cy.get(".t--list-widget-next-page.rc-pagination-next")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(2);
      cy.get(publishLocators.containerWidget).should("have.length", 2);
      cy.get(publishLocators.imageWidget).should("have.length", 2);
      cy.get(publishLocators.textWidget).should("have.length", 4);
      /**
       * isLoading of the widget does not work properly so for a moment
       * the previous data are visible which can cause the test to pass/fail.
       * Adding a wait makes sure the next page data is loaded.
       */
      cy.wait(3000);
    },
  );

  it("3. re-runs query of page 1 when reset", () => {
    // Modify onPageChange
    cy.openPropertyPane("listwidgetv2");
    cy.get(toggleJSButton("onpagechange")).click({ force: true });
    cy.testJsontext(
      "onpagechange",
      "{{Query1.run(() => {showAlert(`Query Ran ${new Date().getTime()}`)}, () => {})}}",
    );

    cy.openPropertyPane("buttonwidget");

    cy.get(toggleJSButton("onclick")).click({ force: true });

    cy.testJsontext("onclick", "{{resetWidget('List1',true)}}");

    // Verify if page 2
    cy.get(".rc-pagination-item").contains(2);

    // Go to next page
    cy.get(".t--list-widget-next-page.rc-pagination-next")
      .find("button")
      .click({ force: true });

    // Verify if page 3
    cy.get(".rc-pagination-item").contains(3);

    /**
     *  Note: Waiting for toastmsg and verifying it can cause flakyness
     * as the APIs could take time to respond and by the response comes,
     * the cypress tests might timeout.
     *  */
    // Represents query fired
    cy.get(commonlocators.toastmsg).should("exist");
    // Represents the toast message is closed
    cy.get(commonlocators.toastmsg).should("not.exist");

    // Reset List widget
    cy.get(".t--draggable-buttonwidget").find("button").click({ force: true });

    // Verify if page 1
    cy.get(".rc-pagination-item").contains(1);

    // Verify if Query fired once
    cy.get(commonlocators.toastmsg).should("exist").should("have.length", 1);
  });

  it(
    "excludeForAirgap",
    "4. retains input values when pages are switched",
    () => {
      // Type a number in each of the item's input widget
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .type(index + 1);
      });

      // Verify the typed value
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .should("have.value", index + 1);
      });

      // Go to page 2
      cy.get(".t--list-widget-next-page.rc-pagination-next")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(2);

      /**
       * isLoading of the widget does not work properly so for a moment
       * the previous data are visible which can cause the test to pass/fail.
       * Adding a wait makes sure the next page data is loaded.
       */
      cy.wait(5000);

      // Type a number in each of the item's input widget
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .type(index + 4);
      });

      // Verify the typed value
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .should("have.value", index + 4);
      });

      // Go to page 1
      cy.get(".t--list-widget-prev-page.rc-pagination-prev")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(1).wait(5000);

      // Verify if previously the typed values are retained
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .should("have.value", index + 1);
      });
    },
  );

  it(
    "airgap",
    "4. retains input values when pages are switched - airgap",
    () => {
      // Type a number in each of the item's input widget
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .clear()
          .type(index + 1);
      });

      // Verify the typed value
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .should("have.value", index + 1);
      });

      // Go to page 2
      cy.get(".t--list-widget-next-page.rc-pagination-next")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(2);

      /**
       * isLoading of the widget does not work properly so for a moment
       * the previous data are visible which can cause the test to pass/fail.
       * Adding a wait makes sure the next page data is loaded.
       */
      cy.wait(5000);

      // Go to page 1
      cy.get(".t--list-widget-prev-page.rc-pagination-prev")
        .find("button")
        .click({ force: true });

      cy.get(".rc-pagination-item").contains(1).wait(5000);

      // Verify if previously the typed values are retained
      cy.get(".t--draggable-inputwidgetv2").each(($inputWidget, index) => {
        cy.wrap($inputWidget)
          .find("input")
          .should("have.value", index + 1);
      });
    },
  );

  it("5. Total Record Count", () => {
    cy.openPropertyPane("listwidgetv2");

    cy.updateCodeInput(".t--property-control-totalrecords", `{{10}}`);

    // With Page Size of 3 and total record count of 10, we should have total page of 4
    cy.get(".rc-pagination .rc-pagination-item-1").should("exist");
    cy.get(".rc-pagination .rc-pagination-item-2").should("exist");
    cy.get(".rc-pagination .rc-pagination-item-3").should("exist");
    cy.get(".rc-pagination .rc-pagination-item-4")
      .should("exist")
      .click({ force: true });

    cy.wait(2000);

    cy.get(commonlocators.listPaginateActivePage).should("have.text", "4");
    cy.get(commonlocators.listPaginateNextButtonDisabled).should("exist");

    // When I clear total Record Count the Page Number should remain the same
    // Although pagination control should only display the active page number
    cy.testJsontextclear("totalrecords");
    cy.get(".rc-pagination .rc-pagination-item-1").should("not.exist");
    cy.get(".rc-pagination .rc-pagination-item-2").should("not.exist");
    cy.get(".rc-pagination .rc-pagination-item-3").should("not.exist");

    //When I reduce the total record count, it should revert to the next max page number and trigger on page change
    cy.updateCodeInput(".t--property-control-totalrecords", `{{3}}`);

    cy.wait(200);
    cy.get(commonlocators.listPaginateActivePage).should("have.text", "1");
    cy.get(commonlocators.toastmsg).should("exist");
  });

  it(
    "excludeForAirgap",
    "6. no of items rendered should be equal to page size",
    () => {
      cy.NavigateToDatasourceEditor();

      // Click on sample(mock) user database.
      cy.get(datasource.mockUserDatabase).click();

      _.dataSources.NavigateToActiveTab();

      // Choose the first data source which consists of users keyword & Click on the "New query +"" button
      cy.get(`${datasource.datasourceCard}`)
        .filter(":contains('Users')")
        .first()
        .within(() => {
          cy.get(`${datasource.createQuery}`).click({
            force: true,
          });
        });

      // Click the editing field
      cy.get(".t--action-name-edit-field").click({
        force: true,
      });

      // Click the editing field
      cy.get(queryLocators.queryNameField).type("Query2");

      // switching off Use Prepared Statement toggle
      cy.get(queryLocators.switch).last().click({
        force: true,
      });

      //.1: Click on Write query area

      _.dataSources.EnterQuery("SELECT * FROM users LIMIT 20;");

      cy.WaitAutoSave();

      cy.runQuery();

      cy.get('.t--entity-name:contains("Page1")').click({
        force: true,
      });

      cy.wait(1000);

      cy.openPropertyPane("listwidgetv2");

      cy.testJsontext("items", "{{Query2.data}}");

      cy.wait(1000);

      // Check if container no of containers are still 3
      cy.get(publishLocators.containerWidget).should("have.length", 3);
    },
  );
  it(
    "airgap",
    "6. no of items rendered should be equal to page size - airgap",
    () => {
      _.dataSources.CreateDataSource("Postgres");
      cy.wait(1000);
      cy.get(datasource.createQuery).click();
      // Click the editing field
      cy.get(".t--action-name-edit-field").click({
        force: true,
      });

      // Click the editing field
      cy.get(queryLocators.queryNameField).type("Query2");

      // switching off Use Prepared Statement toggle
      cy.get(queryLocators.switch).last().click({
        force: true,
      });

      //.1: Click on Write query area
      _.dataSources.EnterQuery("SELECT * FROM users LIMIT 20;");

      cy.WaitAutoSave();

      cy.runQuery();

      cy.get('.t--entity-name:contains("Page1")').click({
        force: true,
      });

      cy.wait(1000);

      cy.openPropertyPane("listwidgetv2");

      cy.testJsontext("items", "{{Query2.data}}");

      cy.wait(1000);

      // Check if container no of containers are still 3
      cy.get(publishLocators.containerWidget).should("have.length", 3);
    },
  );
});
