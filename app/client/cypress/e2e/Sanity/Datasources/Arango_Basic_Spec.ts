import {
  agHelper,
  entityExplorer,
  entityItems,
  dataSources,
} from "../../../support/Objects/ObjectsCore";

describe("Validate Arango & CURL Import Datasources", () => {
  let dsName: any,
    collectionName = "countries_places_to_visit",
    containerName = "arangodb";
  before("Create a new Arango DS", () => {
    dataSources.StartContainerNVerify("Arango", containerName, 20000);
    dataSources.CreateDataSource("Arango");
    cy.get("@dsName").then(($dsName) => {
      dsName = $dsName;
    });
  });

  it(`1. Create collection ${collectionName} into _system DB & Add data into it via curl`, () => {
    cy.fixture("datasources").then((datasourceFormData) => {
      let curlCollectionCreate =
        `curl --request POST \
    --url http://` +
        datasourceFormData["arango-host"] +
        `:` +
        datasourceFormData["arango-port"] +
        `/_api/collection \
    --header 'authorization: Basic cm9vdDpBcmFuZ28=' \
    --header 'content-type: application/json' \
    --data '{
      "name": "` +
        collectionName +
        `",
      "type": 2,
      "keyOptions": {
          "type": "autoincrement",
          "allowUserKeys": false
      }
    }'`;
      dataSources.FillCurlNImport(curlCollectionCreate);
      agHelper.ActionContextMenuWithInPane({
        action: "Delete",
        entityType: entityItems.Api,
      });
      entityExplorer.ExpandCollapseEntity("Datasources");
      entityExplorer.ExpandCollapseEntity(dsName);
      entityExplorer.ActionContextMenuByEntityName({
        entityNameinLeftSidebar: dsName,
        action: "Refresh",
      });
      agHelper.AssertElementVisible(
        entityExplorer._entityNameInExplorer(collectionName),
      );
      //Add data into this newly created collection
      let curlDataAdd =
        `curl --request POST --url http://` +
        datasourceFormData["arango-host"] +
        `:` +
        datasourceFormData["arango-port"] +
        `/_api/document/${collectionName} \
      --header 'authorization: Basic cm9vdDpBcmFuZ28=' \
      --header 'content-type: application/json' \
      --data '[
        {"country": "Japan",
        "places_to_visit": [{"name": "Tokyo Tower","type": "Landmark","location": {"latitude": 35.6586,"longitude": 139.7454 }},
        {"name": "Mount Fuji", "type": "Natural","location": {"latitude": 35.3606,"longitude": 138.7278 }},
        {"name": "Hiroshima Peace Memorial Park", "type": "Memorial","location": {"latitude": 34.3955,"longitude": 132.4536}}]
      },
      {
        "country": "France",
        "places_to_visit": [
          {
            "name": "Eiffel Tower",
            "type": "Landmark",
            "location": {
              "latitude": 48.8584,
              "longitude": 2.2945
            }
          },
          {
            "name": "Palace of Versailles",
            "type": "Landmark",
            "location": {
              "latitude": 48.8049,
              "longitude": 2.1204
            }
          },
          {
            "name": "Mont Saint-Michel",
            "type": "Natural",
            "location": {
              "latitude": 48.6361,
              "longitude": -1.5118
            }
          }
        ]
      },
      {
        "country": "USA",
        "places_to_visit": [
          {
            "name": "Statue of Liberty",
            "type": "Landmark",
            "location": {
              "latitude": 40.6892,
              "longitude": -74.0445
            }
          },
          {
            "name": "Grand Canyon",
            "type": "Natural",
            "location": {
              "latitude": 36.1069,
              "longitude": -112.1126
            }
          },
          {
            "name": "Disney World",
            "type": "Theme Park",
            "location": {
              "latitude": 28.3852,
              "longitude": -81.5639
            }
          }
        ]
      }
    ]'`;

      dataSources.FillCurlNImport(curlDataAdd);
      agHelper.ActionContextMenuWithInPane({
        action: "Delete",
        entityType: entityItems.Api,
      });
      entityExplorer.ExpandCollapseEntity(dsName);
      entityExplorer.ActionContextMenuByEntityName({
        entityNameinLeftSidebar: dsName,
        action: "Refresh",
      }); //needed for the added data to reflect in template queries
    });
  });

  it("2. Run Select, Create, Update, Delete & few more queries on the created collection", () => {
    //Select's own query
    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Select",
    );
    dataSources.RunQuery();
    dataSources.AssertQueryResponseHeaders([
      "_key",
      "_id",
      "_rev",
      "country",
      "places_to_visit",
    ]);
    dataSources.ReadQueryTableResponse(0).then(($cellData) => {
      expect($cellData).to.eq("1");
    });

    //Filter by country & return specific columns
    let query = `FOR document IN ${collectionName}
    FILTER document.country == "Japan"
    RETURN { country: document.country, places_to_visit: document.places_to_visit }`;
    dataSources.EnterQuery(query);
    dataSources.RunQuery();
    dataSources.AssertQueryResponseHeaders(["country", "places_to_visit"]);
    dataSources.ReadQueryTableResponse(0).then(($cellData) => {
      expect($cellData).to.eq("Japan");
    });

    //Insert a new place
    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Create",
    );

    query = `INSERT
    {
      "country": "Brazil",
      "places_to_visit": [
        {
          "name": "Christ the Redeemer",
          "type": "Landmark",
          "location": {
            "latitude": -22.9519,
            "longitude": -43.2106
          }
        },
        {
          "name": "Iguazu Falls",
          "type": "Natural",
          "location": {
            "latitude": -25.6953,
            "longitude": -54.4367
          }
        },
        {
          "name": "Amazon Rainforest",
          "type": "Natural",
          "location": {
            "latitude": -3.4653,
            "longitude": -62.2159
          }
        }
      ]
    }
    INTO ${collectionName}`;
    dataSources.EnterQuery(query);
    dataSources.RunQueryNVerifyResponseViews();
    dataSources.AssertQueryResponseHeaders(["writesExecuted", "writesIgnored"]);
    dataSources.ReadQueryTableResponse(0).then(($cellData) => {
      expect($cellData).to.eq("1");
    }); //confirming write is successful

    //Filter for Array type & verify for the newly added place also
    query = `FOR doc IN ${collectionName}
    FOR place IN doc.places_to_visit
      FILTER place.type == "Natural"
        RETURN { country: doc.country, name: place.name }`;
    dataSources.EnterQuery(query);
    dataSources.RunQueryNVerifyResponseViews(5); //Verify all records are filtered
    dataSources.ReadQueryTableResponse(0).then(($cellData) => {
      expect($cellData).to.eq("Japan");
    });
    dataSources.ReadQueryTableResponse(1).then(($cellData) => {
      expect($cellData).to.eq("Mount Fuji");
    });
    dataSources.ReadQueryTableResponse(6).then(($cellData) => {
      expect($cellData).to.eq("Brazil");
    });
    dataSources.ReadQueryTableResponse(7).then(($cellData) => {
      expect($cellData).to.eq("Iguazu Falls");
    }); //making sure new inserted record is also considered for filtering

    //Update Japan to Australia
    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Update",
    );

    query = `UPDATE
    {
        _key: "1"
    }
    WITH
    {
      "country": "Australia",
      "places_to_visit": [
        {
          "name": "Sydney Opera House",
          "type": "Landmark",
          "location": {
            "latitude": -33.8568,
            "longitude": 151.2153
          }
        }
        ]
    }
    IN ${collectionName}`;
    dataSources.EnterQuery(query);
    dataSources.RunQueryNVerifyResponseViews();

    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Select",
    );
    dataSources.RunQueryNVerifyResponseViews(1);
    dataSources.ReadQueryTableResponse(3).then(($cellData) => {
      expect($cellData).to.eq("Australia");
    });

    //Delete record from collection
    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Delete",
    );
    dataSources.RunQueryNVerifyResponseViews(1); //Removing Australia

    //Verify no records return for the deleted key
    query = `FOR document IN ${collectionName}
    RETURN { country: document.country }`;
    entityExplorer.ActionTemplateMenuByEntityName(
      `${collectionName}`,
      "Select",
    );
    dataSources.RunQuery();
    agHelper
      .GetText(dataSources._noRecordFound)
      .then(($noRecMsg) => expect($noRecMsg).to.eq("No data records to show"));

    //Verify other records returned fine from collection
    query = `FOR document IN ${collectionName}
      RETURN { country: document.country }`;

    dataSources.EnterQuery(query);
    dataSources.RunQuery();

    dataSources.ReadQueryTableResponse(0).then(($cellData) => {
      expect($cellData).to.eq("France");
    });
    dataSources.ReadQueryTableResponse(1).then(($cellData) => {
      expect($cellData).to.eq("USA");
    });
    dataSources.ReadQueryTableResponse(2).then(($cellData) => {
      expect($cellData).to.eq("Brazil");
    });
  });

  //To add test for duplicate collection name

  after("Delete collection via curl & then data source", () => {
    //Deleting all queries created on this DB
    entityExplorer.ExpandCollapseEntity("Queries/JS");
    entityExplorer.DeleteAllQueriesForDB(dsName);

    //Deleting collection via Curl
    //entityExplorer.CreateNewDsQuery("New cURL import", false); Script failing here, but manually working, to check
    cy.fixture("datasources").then((datasourceFormData) => {
      let curlDeleteCol =
        `curl --request DELETE --url http://` +
        datasourceFormData["arango-host"] +
        `:` +
        datasourceFormData["arango-port"] +
        `/_db/_system/_api/collection/${collectionName} --header 'authorization: Basic cm9vdDpBcmFuZ28='`;
      //dataSources.ImportCurlNRun(curlDeleteCol);
      dataSources.FillCurlNImport(curlDeleteCol);
      agHelper.ActionContextMenuWithInPane({
        action: "Delete",
        entityType: entityItems.Api,
      }); //Deleting api created
      entityExplorer.ExpandCollapseEntity(dsName);
      entityExplorer.ActionContextMenuByEntityName({
        entityNameinLeftSidebar: dsName,
        action: "Refresh",
      }); //needed for the deltion of ds to reflect
      agHelper.AssertElementVisible(dataSources._noSchemaAvailable(dsName));
      //Deleting datasource finally
      dataSources.DeleteDatasouceFromWinthinDS(dsName);
    });

    dataSources.StopNDeleteContainer(containerName);
  });
});
