const publish = require("../../../../locators/publishWidgetspage.json");
const testdata = require("../../../../fixtures/testdata.json");
import * as _ from "../../../../support/Objects/ObjectsCore";

describe("Binding the multiple input Widget", function () {
  before(() => {
    cy.fixture("formInputTableDsl").then((val) => {
      _.agHelper.AddDsl(val);
    });
  });

  it("1. Input widget test with default value from table widget", function () {
    _.entityExplorer.ExpandCollapseEntity("Form1");
    _.entityExplorer.SelectEntityByName("Input1");

    cy.testJsontext("defaultvalue", testdata.defaultInputWidget + "}}");

    cy.wait(2000);
    cy.wait("@updateLayout").should(
      "have.nested.property",
      "response.body.responseMeta.status",
      200,
    );
    // Validation of data displayed in all widgets based on row selected
    cy.isSelectRow(1);
    cy.readTabledataPublish("1", "0").then((tabData) => {
      const tabValue = tabData;
      expect(tabValue).to.be.equal("2736212");
      cy.log("the value is" + tabValue);

      cy.get(publish.inputWidget + " " + "input")
        .first()
        .invoke("attr", "value")
        .should("contain", tabValue);
      //       cy.get(publish.inputWidget + " " + "input")
      //         .last()
      //         .invoke("attr", "value")
      //         .should("contain", tabValue);
    });
  });
});
