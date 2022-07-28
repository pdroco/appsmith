const explorer = require("../../../../locators/explorerlocators.json");
const testdata = require("../../../../fixtures/testdata.json");
const apiwidget = require("../../../../locators/apiWidgetslocator.json");
const dsl = require("../../../../fixtures/defaultMetaDsl.json");

import {
  WIDGET,
  PROPERTY_SELECTOR,
  getWidgetSelector,
  getWidgetInputSelector,
} from "../../../../locators/WidgetLocators";

const widgetsToTest = {
  [WIDGET.MULTISELECT_WIDGET]: {
    widgetName: "MultiSelect",
    widgetPrefixName: "MultiSelect1",
    textBindingValue: "{{MultiSelect1.selectedOptionValues}}",
    action: () => {
      chooseColMultiSelectAndReset();
    },
  },
  [WIDGET.TAB]: {
    widgetName: "Tab",
    widgetPrefixName: "Tabs1",
    textBindingValue: testdata.tabBindingValue,
    action: () => {
      selectTabAndReset();
    },
  },
  [WIDGET.TABLE]: {
    widgetName: "Table",
    widgetPrefixName: "Table1",
    textBindingValue: testdata.tableBindingValue,
    action: () => {
      selectTableAndReset();
    },
  },
  [WIDGET.SWITCHGROUP]: {
    widgetName: "SwitchGroup",
    widgetPrefixName: "SwitchGroup1",
    textBindingValue: testdata.switchGroupBindingValue,
    action: () => {
      selectSwitchGroupAndReset();
    },
  },
  [WIDGET.SWITCH]: {
    widgetName: "Switch",
    widgetPrefixName: "Switch1",
    textBindingValue: testdata.switchBindingValue,
    action: () => {
      selectSwitchAndReset();
    },
  },
  [WIDGET.SELECT]: {
    widgetName: "Select",
    widgetPrefixName: "Select1",
    textBindingValue: testdata.selectBindingValue,
    action: () => {
      selectAndReset();
    },
  },
  [WIDGET.CURRENCY_INPUT_WIDGET]: {
    widgetName: "CurrencyInput",
    widgetPrefixName: "CurrencyInput1",
    textBindingValue: testdata.currencyBindingValue,
    action: () => {
      selectCurrencyInputAndReset();
    },
  },
  [WIDGET.MULTITREESELECT]: {
    widgetName: "MultiTreeSelect",
    widgetPrefixName: "MultiTreeSelect1",
    textBindingValue: testdata.multitreeselectBindingValue,
    action: () => {
      multiTreeSelectAndReset();
    },
  },
  [WIDGET.RADIO_GROUP]: {
    widgetName: "RadioGroup",
    widgetPrefixName: "RadioGroup1",
    textBindingValue: testdata.radiogroupselectBindingValue,
    action: () => {
      radiogroupAndReset();
    },
  },
  [WIDGET.LIST]: {
    widgetName: "List",
    widgetPrefixName: "List1",
    textBindingValue: testdata.listBindingValue,
    action: () => {
      listwidgetAndReset();
    },
  },
  [WIDGET.RATING]: {
    widgetName: "Rating",
    widgetPrefixName: "Rating1",
    textBindingValue: testdata.ratingBindingValue,
    action: () => {
      ratingwidgetAndReset();
    },
  },
  [WIDGET.CHECKBOXGROUP]: {
    widgetName: "CheckboxGroup",
    widgetPrefixName: "CheckboxGroup1",
    textBindingValue: testdata.checkboxGroupBindingValue,
    action: () => {
      checkboxGroupAndReset();
    },
  },
  [WIDGET.CHECKBOX]: {
    widgetName: "Checkbox",
    widgetPrefixName: "Checkbox1",
    textBindingValue: testdata.checkboxBindingValue,
    action: () => {
      checkboxAndReset();
    },
  },
  [WIDGET.AUDIO]: {
    widgetName: "Audio",
    widgetPrefixName: "Audio1",
    textBindingValue: testdata.audioBindingValue,
    action: () => {
      audioWidgetAndReset();
    },
  },
  [WIDGET.AUDIORECORDER]: {
    widgetName: "AudioRecorder",
    widgetPrefixName: "AudioRecorder1",
    textBindingValue: testdata.audioRecorderBindingValue,
    action: () => {
      audioRecorderWidgetAndReset();
    },
  },
  [WIDGET.PHONEINPUT]: {
    widgetName: "PhoneInput",
    widgetPrefixName: "PhoneInput1",
    textBindingValue: testdata.phoneBindingValue,
    action: () => {
      phoneInputWidgetAndReset();
    },
  },
  [WIDGET.FILEPICKER]: {
    widgetName: "FilePicker",
    widgetPrefixName: "FilePicker1",
    textBindingValue: testdata.fileBindingValue,
    action: () => {
      filePickerWidgetAndReset();
    },
  },
};

function chooseColMultiSelectAndReset() {
  cy.get(".rc-select-selection-overflow").click({ force: true });
  cy.get(".rc-select-item-option-content:contains('Blue')").click({
    force: true,
  });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "BLUE");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "BLUE");
  });
}

function selectTabAndReset() {
  cy.get(".t--tabid-tab2").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "Tab 2");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "Tab 2");
  });
}

function selectTableAndReset() {
  cy.isSelectRow(1);
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "#2");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "#1");
  });
}

function selectSwitchGroupAndReset() {
  cy.get(".bp3-control-indicator")
    .last()
    .click({ force: true });
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "RED");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "RED");
  });
}

function selectSwitchAndReset() {
  cy.get(".bp3-control-indicator")
    .last()
    .click({ force: true });
  cy.get(".t--switch-widget-active").should("not.exist");
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--toast-action span").contains("success");
  cy.get(".t--switch-widget-active").should("be.visible");
}

function selectAndReset() {
  cy.get(".select-button").click({ force: true });
  cy.get(".menu-item-text")
    .contains("Blue")
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "BLUE");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "BLUE");
  });
}

function selectCurrencyInputAndReset() {
  cy.get(".bp3-input")
    .click({ force: true })
    .type("123");
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "123");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "123");
  });
}

function multiTreeSelectAndReset() {
  cy.get(".rc-tree-select-selection-overflow").click({ force: true });
  cy.get(".rc-tree-select-tree-title:contains('Red')").click({
    force: true,
  });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "RED");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "GREEN");
  });
}

function radiogroupAndReset() {
  cy.get("input")
    .last()
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "N");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "Y");
  });
}

function listwidgetAndReset() {
  cy.get(".t--widget-containerwidget")
    .eq(1)
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").should("contain.text", "002");
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").should("contain.text", "001");
}

function ratingwidgetAndReset() {
  cy.get(".bp3-icon-star svg")
    .last()
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "3");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "3");
  });
}

function checkboxGroupAndReset() {
  cy.get("input")
    .last()
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "RED");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("not.contain.text", "RED");
  });
}

function checkboxAndReset() {
  cy.get("input")
    .last()
    .click({ force: true });
  cy.wait(3000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "false");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "true");
  });
}

function audioWidgetAndReset() {
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "false");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
}

function audioRecorderWidgetAndReset() {
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "true");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
}

function phoneInputWidgetAndReset() {
  cy.get(".bp3-input").type("1234");
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "1234");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--toast-action span").contains("success");
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "");
  });
}

function filePickerWidgetAndReset() {
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "false");
  });
  cy.get(commonlocators.filePickerInput)
    .first()
    .attachFile("testFile.mov");
  //eslint-disable-next-line cypress/no-unnecessary-waiting
  cy.wait(500);
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "true");
  });
  cy.get("button:contains('Submit')").click({ force: true });
  cy.wait(1000);
  cy.get(".t--toast-action span").contains("success");
  cy.get(".t--text-widget-container").each((item, index, list) => {
    cy.wrap(item).should("contain.text", "false");
  });
}

Object.entries(widgetsToTest).forEach(([widgetSelector, testConfig]) => {
  describe(`${testConfig.widgetName} widget test for validating reset action`, () => {
    before(() => {
      cy.addDsl(dsl);
    });

    it(`1. DragDrop Widget ${testConfig.widgetName}`, () => {
      cy.get(explorer.addWidget).click();
      cy.dragAndDropToCanvas(widgetSelector, { x: 300, y: 200 });
      cy.get(getWidgetSelector(widgetSelector)).should("exist");
    });

    it("2. Bind Button on click  and Text widget content", () => {
      // Set onClick action, storing value
      cy.openPropertyPane(WIDGET.BUTTON_WIDGET);

      cy.get(PROPERTY_SELECTOR.onClick)
        .find(".t--js-toggle")
        .click();
      cy.updateCodeInput(
        PROPERTY_SELECTOR.onClick,
        `{{resetWidget("${testConfig.widgetPrefixName}",true).then(() => showAlert("success"))}}`,
      );
      // Bind to stored value above
      cy.openPropertyPane(WIDGET.TEXT);
      cy.updateCodeInput(PROPERTY_SELECTOR.text, testConfig.textBindingValue);
    });

    it("3. Publish the app and check the reset action", () => {
      // Set onClick action, storing value
      cy.PublishtheApp();
      testConfig.action();
      cy.get(".t--toast-action span").contains("success");
    });

    it("4. Delete all the widgets on canvas", () => {
      cy.goToEditFromPublish();
      cy.get(getWidgetSelector(widgetSelector)).click();
      cy.get("body").type(`{del}`, { force: true });
    });
  });
});
