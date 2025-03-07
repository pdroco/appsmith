package com.appsmith.external.models;

import com.appsmith.external.exceptions.ErrorDTO;
import com.appsmith.external.helpers.Identifiable;
import com.appsmith.external.views.Views;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Transient;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ActionDTO implements Identifiable {

    @Transient
    @JsonView(Views.Public.class)
    private String id;

    @Transient
    @JsonView(Views.Public.class)
    String applicationId;

    @Transient
    @JsonView(Views.Public.class)
    String workspaceId;

    @Transient
    @JsonView(Views.Public.class)
    PluginType pluginType;

    // name of the plugin. used to log analytics events where pluginName is a required attribute
    // It'll be null if not set
    @Transient
    @JsonView(Views.Public.class)
    String pluginName;

    @Transient
    @JsonView(Views.Public.class)
    String pluginId;

    @JsonView(Views.Public.class)
    String name;

    // The FQN for an action will also include any collection it is a part of as collectionName.actionName
    @JsonView(Views.Public.class)
    String fullyQualifiedName;

    @JsonView(Views.Public.class)
    Datasource datasource;

    @JsonView(Views.Public.class)
    String pageId;

    @JsonView(Views.Public.class)
    String collectionId;

    @JsonView(Views.Public.class)
    ActionConfiguration actionConfiguration;

    //this attribute carries error messages while processing the actionCollection
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Transient
    @JsonView(Views.Public.class)
    List<ErrorDTO> errorReports;

    @JsonView(Views.Public.class)
    Boolean executeOnLoad;

    @JsonView(Views.Public.class)
    Boolean clientSideExecution;

    /*
     * This is a list of fields specified by the client to signify which fields have dynamic bindings in them.
     * TODO: The server can use this field to simplify our Mustache substitutions in the future
     */
    @JsonView(Views.Public.class)
    List<Property> dynamicBindingPathList;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(Views.Public.class)
    Boolean isValid;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(Views.Public.class)
    Set<String> invalids;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(Views.Public.class)
    Set<String> messages = new HashSet<>();


    // This is a list of keys that the client whose values the client needs to send during action execution.
    // These are the Mustache keys that the server will replace before invoking the API
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(Views.Public.class)
    Set<String> jsonPathKeys;

    @JsonView(Views.Internal.class)
    String cacheResponse;

    @Transient
    @JsonView(Views.Public.class)
    String templateId; //If action is created via a template, store the id here.

    @Transient
    @JsonView(Views.Public.class)
    String providerId; //If action is created via a template, store the template's provider id here.

    @Transient
    @JsonView(Views.Public.class)
    ActionProvider provider;

    @JsonView(Views.Internal.class)
    Boolean userSetOnLoad = false;

    @JsonView(Views.Public.class)
    Boolean confirmBeforeExecute = false;

    @Transient
    @JsonView(Views.Public.class)
    Documentation documentation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @JsonView(Views.Public.class)
    Instant deletedAt = null;

    @Deprecated
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @JsonView(Views.Public.class)
    Instant archivedAt = null;

    @Transient
    @JsonView(Views.Internal.class)
    protected Set<Policy> policies = new HashSet<>();

    @Transient
    @JsonView(Views.Public.class)
    public Set<String> userPermissions = new HashSet<>();

    // This field will be used to store the default/root actionId and applicationId for actions generated for git
    // connected applications and will be used to connect actions across the branches
    @JsonView(Views.Internal.class)
    DefaultResources defaultResources;

    // This field will be used to store analytics data related to this specific domain object. It's been introduced in order to track
    // success metrics of modules. Learn more on GitHub issue#24734
    @JsonView(Views.Public.class)
    AnalyticsInfo eventData;

    @JsonView(Views.Internal.class)
    protected Instant createdAt;

    @JsonView(Views.Internal.class)
    protected Instant updatedAt;

    /**
     * If the Datasource is null, create one and set the autoGenerated flag to true. This is required because spring-data
     * cannot add the createdAt and updatedAt properties for null embedded objects. At this juncture, we couldn't find
     * a way to disable the auditing for nested objects.
     *
     * @return
     */
    @JsonView(Views.Public.class)
    public Datasource getDatasource() {
        if (this.datasource == null) {
            this.datasource = new Datasource();
            this.datasource.setIsAutoGenerated(true);
        }
        return datasource;
    }

    @JsonView(Views.Public.class)
    public String getValidName() {
        if (this.fullyQualifiedName == null) {
            return this.name;
        } else {
            return this.fullyQualifiedName;
        }
    }

    public void sanitiseToExportDBObject() {
        this.setEventData(null);
        this.setDefaultResources(null);
        this.setCacheResponse(null);
        if (this.getDatasource() != null) {
            this.getDatasource().setCreatedAt(null);
            this.getDatasource().setDatasourceStorages(null);
        }
        if (this.getUserPermissions() != null) {
            this.getUserPermissions().clear();
        }
        if (this.getPolicies() != null) {
            this.getPolicies().clear();
        }
    }
}
