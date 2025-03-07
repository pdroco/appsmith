const widgetsPage = require("../../../../../locators/Widgets.json");
import * as _ from "../../../../../support/Objects/ObjectsCore";
const commonlocators = require("../../../../../locators/commonlocators.json");

describe("Table Widget V2 row multi select validation", function () {
  before(() => {
    cy.fixture("tableV2NewDsl").then((val) => {
      _.agHelper.AddDsl(val);
    });
  });

  it("1. Test multi select column shows when enable Multirowselection is true", function () {
    cy.openPropertyPane("tablewidgetv2");
    cy.get(widgetsPage.toggleEnableMultirowselection)
      .first()
      .click({ force: true });
    cy.closePropertyPane("tablewidgetv2");
    cy.get(".t--table-multiselect-header").first().should("be.visible");

    cy.get(".t--table-multiselect").first().should("be.visible");
  });

  it("2. Test click on header cell selects all row", function () {
    // click on header check cell
    cy.get(".t--table-multiselect-header").first().click({ force: true });
    // check if rows selected
    cy.get(".tr").should("have.class", "selected-row");
  });

  it("3. Test click on single row cell changes header select cell state", function () {
    // un select all rows
    cy.get(".t--table-multiselect-header").first().click({ force: true });
    // click on first row select box
    cy.get(".t--table-multiselect").first().click({ force: true });
    // check if header cell is in half check state
    cy.get(".t--table-multiselect-header-half-check-svg")
      .first()
      .should("be.visible");
  });

  it("4. Test action configured on onRowSelected get triggered whenever a table row is selected", function () {
    cy.openPropertyPane("tablewidgetv2");
    cy.getAlert("onRowSelected", "Row Selected");
    // un select first row
    cy.get(".t--table-multiselect").first().click({ force: true });
    cy.get(commonlocators.toastmsg).should("not.exist");
    // click on first row select box
    cy.get(".t--table-multiselect").first().click({ force: true });
    cy.get(commonlocators.toastmsg)
      .contains("Row Selected")
      .should("have.css", "font-size", "14px");
  });

  it("5. It should deselected default Selected Row when the header cell is clicked", () => {
    cy.openPropertyPane("tablewidgetv2");
    cy.testJsontext("defaultselectedrows", "[0]");

    // click on header check cell
    cy.get(".t--table-multiselect-header").first().click({
      force: true,
    });
    // check if rows selected
    cy.get(".tr").should("not.have.class", "selected-row");

    // click on header check cell
    cy.get(".t--table-multiselect-header").first().click({
      force: true,
    });
    // check if rows is not selected
    cy.get(".tr").should("have.class", "selected-row");
  });
});
