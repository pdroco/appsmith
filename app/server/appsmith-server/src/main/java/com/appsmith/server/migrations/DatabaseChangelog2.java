package com.appsmith.server.migrations;

import com.appsmith.external.models.Datasource;
import com.appsmith.external.models.Policy;
import com.appsmith.external.models.Property;
import com.appsmith.external.models.QBaseDomain;
import com.appsmith.external.models.QDatasource;
import com.appsmith.server.acl.AclPermission;
import com.appsmith.server.acl.AppsmithRole;
import com.appsmith.server.acl.PolicyGenerator;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.domains.Action;
import com.appsmith.server.domains.ActionCollection;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.ApplicationPage;
import com.appsmith.server.domains.Comment;
import com.appsmith.server.domains.CommentThread;
import com.appsmith.server.domains.Config;
import com.appsmith.server.domains.NewAction;
import com.appsmith.server.domains.NewPage;
import com.appsmith.server.domains.Organization;
import com.appsmith.server.domains.Page;
import com.appsmith.server.domains.PermissionGroup;
import com.appsmith.server.domains.Plugin;
import com.appsmith.server.domains.PricingPlan;
import com.appsmith.server.domains.QAction;
import com.appsmith.server.domains.QActionCollection;
import com.appsmith.server.domains.QApplication;
import com.appsmith.server.domains.QComment;
import com.appsmith.server.domains.QCommentThread;
import com.appsmith.server.domains.QDocumentation;
import com.appsmith.server.domains.QConfig;
import com.appsmith.server.domains.QNewAction;
import com.appsmith.server.domains.QNewPage;
import com.appsmith.server.domains.QOrganization;
import com.appsmith.server.domains.QPage;
import com.appsmith.server.domains.QPlugin;
import com.appsmith.server.domains.QTenant;
import com.appsmith.server.domains.QTheme;
import com.appsmith.server.domains.QUser;
import com.appsmith.server.domains.QUserData;
import com.appsmith.server.domains.QWorkspace;
import com.appsmith.server.domains.Sequence;
import com.appsmith.server.domains.Tenant;
import com.appsmith.server.domains.Theme;
import com.appsmith.server.domains.User;
import com.appsmith.server.domains.UserData;
import com.appsmith.server.domains.UserRole;
import com.appsmith.server.domains.Workspace;
import com.appsmith.server.dtos.ActionDTO;
import com.appsmith.server.dtos.Permission;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.helpers.PolicyUtils;
import com.appsmith.server.helpers.TextUtils;
import com.appsmith.server.repositories.NewPageRepository;
import com.appsmith.server.services.ApplicationService;
import com.appsmith.server.services.WorkspaceService;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;
import com.google.gson.Gson;

import io.changock.migration.api.annotations.NonLockGuarded;
import lombok.extern.slf4j.Slf4j;

import org.bson.BsonArray;
import net.minidev.json.JSONObject;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.appsmith.server.acl.AclPermission.ASSIGN_PERMISSION_GROUPS;
import static com.appsmith.server.acl.AclPermission.MANAGE_INSTANCE_CONFIGURATION;
import static com.appsmith.server.acl.AclPermission.MANAGE_INSTANCE_ENV;
import static com.appsmith.server.acl.AclPermission.MANAGE_PERMISSION_GROUPS;
import static com.appsmith.server.acl.AclPermission.READ_INSTANCE_CONFIGURATION;
import static com.appsmith.server.constants.FieldName.DEFAULT_PERMISSION_GROUP;
import static com.appsmith.server.migrations.DatabaseChangelog.dropIndexIfExists;
import static com.appsmith.server.migrations.DatabaseChangelog.ensureIndexes;
import static com.appsmith.server.migrations.DatabaseChangelog.makeIndex;
import static com.appsmith.server.repositories.BaseAppsmithRepositoryImpl.fieldName;
import static java.lang.Boolean.TRUE;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Slf4j
@ChangeLog(order = "002")
public class DatabaseChangelog2 {

    @ChangeSet(order = "001", id = "fix-plugin-title-casing", author = "")
    public void fixPluginTitleCasing(MongockTemplate mongockTemplate) {
        mongockTemplate.updateFirst(
                query(where(fieldName(QPlugin.plugin.packageName)).is("mysql-plugin")),
                Update.update(fieldName(QPlugin.plugin.name), "MySQL"),
                Plugin.class);

        mongockTemplate.updateFirst(
                query(where(fieldName(QPlugin.plugin.packageName)).is("mssql-plugin")),
                Update.update(fieldName(QPlugin.plugin.name), "Microsoft SQL Server"),
                Plugin.class);

        mongockTemplate.updateFirst(
                query(where(fieldName(QPlugin.plugin.packageName)).is("elasticsearch-plugin")),
                Update.update(fieldName(QPlugin.plugin.name), "Elasticsearch"),
                Plugin.class);
    }

    @ChangeSet(order = "002", id = "deprecate-archivedAt-in-action", author = "")
    public void deprecateArchivedAtForNewAction(MongockTemplate mongockTemplate) {
        // Update actions
        final Query actionQuery = query(where(fieldName(QNewAction.newAction.applicationId)).exists(true))
                .addCriteria(where(fieldName(QNewAction.newAction.unpublishedAction) + "."
                        + fieldName(QNewAction.newAction.unpublishedAction.archivedAt)).exists(true));

        actionQuery.fields()
                .include(fieldName(QNewAction.newAction.id))
                .include(fieldName(QNewAction.newAction.unpublishedAction) + "."
                        + fieldName(QNewAction.newAction.unpublishedAction.archivedAt));

        List<NewAction> actions = mongockTemplate.find(actionQuery, NewAction.class);

        for (NewAction action : actions) {

            final Update update = new Update();

            ActionDTO unpublishedAction = action.getUnpublishedAction();
            if (unpublishedAction != null) {
                final Instant archivedAt = unpublishedAction.getArchivedAt();
                update.set(
                        fieldName(QNewAction.newAction.unpublishedAction) + "."
                                + fieldName(QNewAction.newAction.unpublishedAction.deletedAt),
                        archivedAt);
                update.unset(fieldName(QNewAction.newAction.unpublishedAction) + "."
                        + fieldName(QNewAction.newAction.unpublishedAction.archivedAt));
            }
            mongockTemplate.updateFirst(
                    query(where(fieldName(QNewAction.newAction.id)).is(action.getId())),
                    update,
                    NewAction.class);
        }
    }

    /**
     * To be able to support form and raw mode in the UQI compatible plugins,
     * we need to be migrating all existing data to the data and formData path.
     * Anything that was already raw would not be within formData,
     * so we can blindly switch the contents of formData into inner objects
     * Example: formData.limit will transform to formData.limit.data and
     * formData.limit.formData
     *
     * @param mongockTemplate
     */
    @ChangeSet(order = "003", id = "update-form-data-for-uqi-mode", author = "")
    public void updateActionFormDataPath(MongockTemplate mongockTemplate) {

        // Get all plugin references to Mongo, S3 and Firestore actions
        List<Plugin> uqiPlugins = mongockTemplate.find(
                query(where("packageName").in("mongo-plugin", "amazons3-plugin", "firestore-plugin")),
                Plugin.class);

        final Map<String, String> pluginMap = uqiPlugins.stream()
                .collect(Collectors.toMap(Plugin::getId, Plugin::getPackageName));

        final Set<String> pluginIds = pluginMap.keySet();

        // Find all relevant actions
        final Query actionQuery = query(
                where(fieldName(QNewAction.newAction.pluginId)).in(pluginIds)
                        .and(fieldName(QNewAction.newAction.deleted)).ne(true)); // setting `deleted` != `true`
        actionQuery.fields()
                .include(fieldName(QNewAction.newAction.id));

        List<NewAction> uqiActions = mongockTemplate.find(
                actionQuery,
                NewAction.class);

        // Retrieve the formData path for all actions
        for (NewAction uqiActionWithId : uqiActions) {

            // Fetch one action at a time to avoid OOM.
            final NewAction uqiAction = mongockTemplate.findOne(
                    query(where(fieldName(QNewAction.newAction.id)).is(uqiActionWithId.getId())),
                    NewAction.class);

            assert uqiAction != null;
            ActionDTO unpublishedAction = uqiAction.getUnpublishedAction();

            /* No migrations required if action configuration does not exist. */
            if (unpublishedAction == null || unpublishedAction.getActionConfiguration() == null) {
                continue;
            }

            try {
                if (pluginMap.get(uqiAction.getPluginId()).equals("firestore-plugin")) {
                    migrateFirestoreActionsFormData(uqiAction);
                } else if (pluginMap.get(uqiAction.getPluginId()).equals("amazons3-plugin")) {
                    migrateAmazonS3ActionsFormData(uqiAction);
                } else {
                    migrateMongoActionsFormData(uqiAction);
                }
            } catch (AppsmithException e) {
                // This action is already migrated, move on
                log.error("Failed with error: {}", e.getMessage());
                log.error("Failing action: {}", uqiAction.getId());
                continue;
            }
            mongockTemplate.save(uqiAction);
        }
    }

    public static void migrateFirestoreActionsFormData(NewAction uqiAction) {
        ActionDTO unpublishedAction = uqiAction.getUnpublishedAction();
        /**
         * Migrate unpublished action configuration data.
         */
        final Map<String, Object> unpublishedFormData = unpublishedAction.getActionConfiguration().getFormData();

        if (unpublishedFormData != null) {
            final Object command = unpublishedFormData.get("command");

            if (!(command instanceof String)) {
                throw new AppsmithException(AppsmithError.MIGRATION_ERROR);
            }

            unpublishedFormData
                    .keySet()
                    .stream()
                    .forEach(k -> {
                        if (k != null) {
                            final Object oldValue = unpublishedFormData.get(k);
                            final HashMap<String, Object> map = new HashMap<>();
                            map.put("data", oldValue);
                            map.put("componentData", oldValue);
                            map.put("viewType", "component");
                            unpublishedFormData.put(k, map);
                        }
                    });

        }

        final String unpublishedBody = unpublishedAction.getActionConfiguration().getBody();
        if (StringUtils.hasLength(unpublishedBody)) {
            convertToFormDataObject(unpublishedFormData, "body", unpublishedBody);
            unpublishedAction.getActionConfiguration().setBody(null);
        }

        final String unpublishedPath = unpublishedAction.getActionConfiguration().getPath();
        if (StringUtils.hasLength(unpublishedPath)) {
            convertToFormDataObject(unpublishedFormData, "path", unpublishedPath);
            unpublishedAction.getActionConfiguration().setPath(null);
        }

        final String unpublishedNext = unpublishedAction.getActionConfiguration().getNext();
        if (StringUtils.hasLength(unpublishedNext)) {
            convertToFormDataObject(unpublishedFormData, "next", unpublishedNext);
            unpublishedAction.getActionConfiguration().setNext(null);
        }

        final String unpublishedPrev = unpublishedAction.getActionConfiguration().getPrev();
        if (StringUtils.hasLength(unpublishedPrev)) {
            convertToFormDataObject(unpublishedFormData, "prev", unpublishedPrev);
            unpublishedAction.getActionConfiguration().setPrev(null);
        }

        /**
         * Migrate published action configuration data.
         */
        ActionDTO publishedAction = uqiAction.getPublishedAction();
        if (publishedAction != null && publishedAction.getActionConfiguration() != null &&
                publishedAction.getActionConfiguration().getFormData() != null) {
            final Map<String, Object> publishedFormData = publishedAction.getActionConfiguration().getFormData();

            final Object command = publishedFormData.get("command");

            if (!(command instanceof String)) {
                throw new AppsmithException(AppsmithError.MIGRATION_ERROR);
            }

            publishedFormData
                    .keySet()
                    .stream()
                    .forEach(k -> {
                        if (k != null) {
                            final Object oldValue = publishedFormData.get(k);
                            final HashMap<String, Object> map = new HashMap<>();
                            map.put("data", oldValue);
                            map.put("componentData", oldValue);
                            map.put("viewType", "component");
                            publishedFormData.put(k, map);
                        }
                    });

            final String publishedBody = publishedAction.getActionConfiguration().getBody();
            if (StringUtils.hasLength(publishedBody)) {
                convertToFormDataObject(publishedFormData, "body", publishedBody);
                publishedAction.getActionConfiguration().setBody(null);
            }

            final String publishedPath = publishedAction.getActionConfiguration().getPath();
            if (StringUtils.hasLength(publishedPath)) {
                convertToFormDataObject(publishedFormData, "path", publishedPath);
                publishedAction.getActionConfiguration().setPath(null);
            }

            final String publishedNext = publishedAction.getActionConfiguration().getNext();
            if (StringUtils.hasLength(publishedNext)) {
                convertToFormDataObject(publishedFormData, "next", publishedNext);
                publishedAction.getActionConfiguration().setNext(null);
            }

            final String publishedPrev = publishedAction.getActionConfiguration().getPrev();
            if (StringUtils.hasLength(publishedPrev)) {
                convertToFormDataObject(publishedFormData, "prev", publishedPrev);
                publishedAction.getActionConfiguration().setPrev(null);
            }
        }

        /**
         * Migrate the dynamic binding path list for unpublished action.
         * Please note that there is no requirement to migrate the dynamic binding path
         * list for published actions
         * since the `on page load` actions do not get computed on published actions
         * data. They are only computed
         * on unpublished actions data and copied over for the view mode.
         */
        List<Property> dynamicBindingPathList = unpublishedAction.getDynamicBindingPathList();
        if (dynamicBindingPathList != null && !dynamicBindingPathList.isEmpty()) {
            dynamicBindingPathList
                    .stream()
                    .forEach(dynamicBindingPath -> {
                        if (dynamicBindingPath.getKey().contains("formData")) {
                            final String oldKey = dynamicBindingPath.getKey();
                            final Pattern pattern = Pattern.compile("formData\\.([^.]*)(\\..*)?");

                            Matcher matcher = pattern.matcher(oldKey);

                            while (matcher.find()) {
                                final String fieldName = matcher.group(1);
                                final String remainderPath = matcher.group(2) == null ? "" : matcher.group(2);

                                dynamicBindingPath.setKey("formData." + fieldName + ".data" + remainderPath);
                            }
                        } else {
                            final String oldKey = dynamicBindingPath.getKey();
                            final Pattern pattern = Pattern.compile("^(body|next|prev|path)(\\..*)?");

                            Matcher matcher = pattern.matcher(oldKey);

                            while (matcher.find()) {
                                final String fieldName = matcher.group(1);
                                final String remainderPath = matcher.group(2) == null ? "" : matcher.group(2);

                                dynamicBindingPath.setKey("formData." + fieldName + ".data" + remainderPath);
                            }
                        }
                    });
        }
    }

    private static void convertToFormDataObject(Map<String, Object> formDataMap, String key, Object value) {
        if (value == null) {
            return;
        }
        if (key != null) {
            final HashMap<String, Object> map = new HashMap<>();
            map.put("data", value);
            map.put("componentData", value);
            map.put("viewType", "component");
            formDataMap.put(key, map);
        }
    }

    private static void mapS3ToNewFormData(ActionDTO action, Map<String, Object> f) {
        final Map<String, Object> formData = action.getActionConfiguration().getFormData();

        if (formData == null) {
            return;
        }

        final Object command = formData.get("command");
        if (command == null) {
            return;
        }

        if (!(command instanceof String)) {
            throw new AppsmithException(AppsmithError.MIGRATION_ERROR);
        }

        final String body = action.getActionConfiguration().getBody();
        if (StringUtils.hasLength(body)) {
            convertToFormDataObject(f, "body", body);
            action.getActionConfiguration().setBody(null);
        }

        final String path = action.getActionConfiguration().getPath();
        if (StringUtils.hasLength(path)) {
            convertToFormDataObject(f, "path", path);
            action.getActionConfiguration().setPath(null);
        }

        convertToFormDataObject(f, "command", command);
        convertToFormDataObject(f, "bucket", formData.get("bucket"));
        convertToFormDataObject(f, "smartSubstitution", formData.get("smartSubstitution"));
        switch ((String) command) {
            // No case for delete single and multiple since they only had bucket that needed
            // migration
            case "LIST":
                final Map listMap = (Map) formData.get("list");
                if (listMap == null) {
                    break;
                }
                final Map<String, Object> newListMap = new HashMap<>();
                f.put("list", newListMap);
                convertToFormDataObject(newListMap, "prefix", listMap.get("prefix"));
                convertToFormDataObject(newListMap, "where", listMap.get("where"));
                convertToFormDataObject(newListMap, "signedUrl", listMap.get("signedUrl"));
                convertToFormDataObject(newListMap, "expiry", listMap.get("expiry"));
                convertToFormDataObject(newListMap, "unSignedUrl", listMap.get("unSignedUrl"));
                convertToFormDataObject(newListMap, "sortBy", listMap.get("sortBy"));
                convertToFormDataObject(newListMap, "pagination", listMap.get("pagination"));
                break;
            case "UPLOAD_FILE_FROM_BODY":
            case "UPLOAD_MULTIPLE_FILES_FROM_BODY":
                final Map createMap = (Map) formData.get("create");
                if (createMap == null) {
                    break;
                }
                final Map<String, Object> newCreateMap = new HashMap<>();
                f.put("create", newCreateMap);
                convertToFormDataObject(newCreateMap, "dataType", createMap.get("dataType"));
                convertToFormDataObject(newCreateMap, "expiry", createMap.get("expiry"));
                break;
            case "READ_FILE":
                final Map readMap = (Map) formData.get("read");
                if (readMap == null) {
                    break;
                }
                final Map<String, Object> newReadMap = new HashMap<>();
                f.put("read", newReadMap);
                convertToFormDataObject(newReadMap, "dataType", readMap.get("usingBase64Encoding"));
                break;
        }
    }

    public static void migrateAmazonS3ActionsFormData(NewAction uqiAction) {
        ActionDTO unpublishedAction = uqiAction.getUnpublishedAction();
        /**
         * Migrate unpublished action configuration data.
         */
        Map<String, Object> newUnpublishedFormDataMap = new HashMap<>();
        mapS3ToNewFormData(unpublishedAction, newUnpublishedFormDataMap);
        unpublishedAction.getActionConfiguration().setFormData(newUnpublishedFormDataMap);

        ActionDTO publishedAction = uqiAction.getPublishedAction();
        /**
         * Migrate published action configuration data.
         */
        if (publishedAction.getActionConfiguration() != null) {
            Map<String, Object> newPublishedFormDataMap = new HashMap<>();
            mapS3ToNewFormData(publishedAction, newPublishedFormDataMap);
            publishedAction.getActionConfiguration().setFormData(newPublishedFormDataMap);
        }

        /**
         * Migrate the dynamic binding path list for unpublished action.
         * Please note that there is no requirement to migrate the dynamic binding path
         * list for published actions
         * since the `on page load` actions do not get computed on published actions
         * data. They are only computed
         * on unpublished actions data and copied over for the view mode.
         */
        List<Property> dynamicBindingPathList = unpublishedAction.getDynamicBindingPathList();
        if (dynamicBindingPathList != null && !dynamicBindingPathList.isEmpty()) {
            Map<String, String> dynamicBindingMapper = new HashMap<>();
            dynamicBindingMapper.put("formData.command", "formData.command.data");
            dynamicBindingMapper.put("formData.bucket", "formData.bucket.data");
            dynamicBindingMapper.put("formData.create.dataType", "formData.create.dataType.data");
            dynamicBindingMapper.put("formData.create.expiry", "formData.create.expiry.data");
            dynamicBindingMapper.put("formData.delete.expiry", "formData.delete.expiry.data");
            dynamicBindingMapper.put("formData.list.prefix", "formData.list.prefix.data");
            dynamicBindingMapper.put("formData.list.where", "formData.list.where.data");
            dynamicBindingMapper.put("formData.list.signedUrl", "formData.list.signedUrl.data");
            dynamicBindingMapper.put("formData.list.expiry", "formData.list.expiry.data");
            dynamicBindingMapper.put("formData.list.unSignedUrl", "formData.list.unSignedUrl.data");
            dynamicBindingMapper.put("formData.list.sortBy", "formData.list.sortBy.data");
            dynamicBindingMapper.put("formData.list.pagination", "formData.list.pagination.data");
            dynamicBindingMapper.put("formData.read.usingBase64Encoding", "formData.read.dataType.data");
            dynamicBindingMapper.put("formData.read.expiry", "formData.read.expiry.data");
            dynamicBindingMapper.put("formData.smartSubstitution", "formData.smartSubstitution.data");
            dynamicBindingMapper.put("body", "formData.body.data");
            dynamicBindingMapper.put("path", "formData.path.data");
            dynamicBindingPathList
                    .stream()
                    .forEach(dynamicBindingPath -> {
                        final String currentBinding = dynamicBindingPath.getKey();
                        final Optional<String> matchingBinding = dynamicBindingMapper.keySet().stream()
                                .filter(currentBinding::startsWith).findFirst();
                        if (matchingBinding.isPresent()) {
                            final String newBindingPrefix = dynamicBindingMapper.get(matchingBinding.get());
                            dynamicBindingPath.setKey(currentBinding.replace(matchingBinding.get(), newBindingPrefix));
                        }
                    });
        }
    }

    private static void mapMongoToNewFormData(ActionDTO action, Map<String, Object> f) {
        final Map<String, Object> formData = action.getActionConfiguration().getFormData();
        if (formData == null) {
            return;
        }

        final Object command = formData.get("command");
        if (command == null) {
            return;
        }

        if (!(command instanceof String)) {
            throw new AppsmithException(AppsmithError.MIGRATION_ERROR);
        }

        final String body = action.getActionConfiguration().getBody();
        if (StringUtils.hasLength(body)) {
            convertToFormDataObject(f, "body", body);
            action.getActionConfiguration().setBody(null);
        }

        convertToFormDataObject(f, "command", command);
        convertToFormDataObject(f, "collection", formData.get("collection"));
        convertToFormDataObject(f, "smartSubstitution", formData.get("smartSubstitution"));
        switch ((String) command) {
            case "AGGREGATE":
                final Map aggregateMap = (Map) formData.get("aggregate");
                if (aggregateMap == null) {
                    break;
                }
                final Map<String, Object> newAggregateMap = new HashMap<>();
                f.put("aggregate", newAggregateMap);
                convertToFormDataObject(newAggregateMap, "arrayPipelines", aggregateMap.get("arrayPipelines"));
                convertToFormDataObject(newAggregateMap, "limit", aggregateMap.get("limit"));
                break;
            case "COUNT":
                final Map countMap = (Map) formData.get("count");
                if (countMap == null) {
                    break;
                }
                final Map<String, Object> newCountMap = new HashMap<>();
                f.put("count", newCountMap);
                convertToFormDataObject(newCountMap, "query", countMap.get("query"));
                break;
            case "DELETE":
                final Map deleteMap = (Map) formData.get("delete");
                if (deleteMap == null) {
                    break;
                }
                final Map<String, Object> newDeleteMap = new HashMap<>();
                f.put("delete", newDeleteMap);
                convertToFormDataObject(newDeleteMap, "query", deleteMap.get("query"));
                convertToFormDataObject(newDeleteMap, "limit", deleteMap.get("limit"));
                break;
            case "DISTINCT":
                final Map distinctMap = (Map) formData.get("distinct");
                if (distinctMap == null) {
                    break;
                }
                final Map<String, Object> newDistinctMap = new HashMap<>();
                f.put("distinct", newDistinctMap);
                convertToFormDataObject(newDistinctMap, "query", distinctMap.get("query"));
                convertToFormDataObject(newDistinctMap, "key", distinctMap.get("key"));
                break;
            case "FIND":
                final Map findMap = (Map) formData.get("find");
                if (findMap == null) {
                    break;
                }
                final Map<String, Object> newFindMap = new HashMap<>();
                f.put("find", newFindMap);
                convertToFormDataObject(newFindMap, "query", findMap.get("query"));
                convertToFormDataObject(newFindMap, "sort", findMap.get("sort"));
                convertToFormDataObject(newFindMap, "projection", findMap.get("projection"));
                convertToFormDataObject(newFindMap, "limit", findMap.get("limit"));
                convertToFormDataObject(newFindMap, "skip", findMap.get("skip"));
                break;
            case "INSERT":
                final Map insertMap = (Map) formData.get("insert");
                if (insertMap == null) {
                    break;
                }
                final Map<String, Object> newInsertMap = new HashMap<>();
                f.put("insert", newInsertMap);
                convertToFormDataObject(newInsertMap, "documents", insertMap.get("documents"));
                break;
            case "UPDATE":
                final Map updateMap = (Map) formData.get("updateMany");
                if (updateMap == null) {
                    break;
                }
                final Map<String, Object> newUpdateManyMap = new HashMap<>();
                f.put("updateMany", newUpdateManyMap);
                convertToFormDataObject(newUpdateManyMap, "query", updateMap.get("query"));
                convertToFormDataObject(newUpdateManyMap, "update", updateMap.get("update"));
                convertToFormDataObject(newUpdateManyMap, "limit", updateMap.get("limit"));
                break;
        }
    }

    public static void migrateMongoActionsFormData(NewAction uqiAction) {
        ActionDTO unpublishedAction = uqiAction.getUnpublishedAction();
        /**
         * Migrate unpublished action configuration data.
         */
        Map<String, Object> newUnpublishedFormDataMap = new HashMap<>();
        mapMongoToNewFormData(unpublishedAction, newUnpublishedFormDataMap);
        unpublishedAction.getActionConfiguration().setFormData(newUnpublishedFormDataMap);

        ActionDTO publishedAction = uqiAction.getPublishedAction();
        /**
         * Migrate published action configuration data.
         */
        if (publishedAction.getActionConfiguration() != null) {
            Map<String, Object> newPublishedFormDataMap = new HashMap<>();
            mapMongoToNewFormData(publishedAction, newPublishedFormDataMap);
            publishedAction.getActionConfiguration().setFormData(newPublishedFormDataMap);
        }

        /**
         * Migrate the dynamic binding path list for unpublished action.
         * Please note that there is no requirement to migrate the dynamic binding path
         * list for published actions
         * since the `on page load` actions do not get computed on published actions
         * data. They are only computed
         * on unpublished actions data and copied over for the view mode.
         */
        List<Property> dynamicBindingPathList = unpublishedAction.getDynamicBindingPathList();
        if (dynamicBindingPathList != null && !dynamicBindingPathList.isEmpty()) {
            Map<String, String> dynamicBindingMapper = new HashMap<>();
            dynamicBindingMapper.put("formData.command", "formData.command.data");
            dynamicBindingMapper.put("formData.collection", "formData.collection.data");
            dynamicBindingMapper.put("formData.aggregate.arrayPipelines", "formData.aggregate.arrayPipelines.data");
            dynamicBindingMapper.put("formData.aggregate.limit", "formData.aggregate.limit.data");
            dynamicBindingMapper.put("formData.count.query", "formData.count.query.data");
            dynamicBindingMapper.put("formData.delete.query", "formData.delete.query.data");
            dynamicBindingMapper.put("formData.delete.limit", "formData.delete.limit.data");
            dynamicBindingMapper.put("formData.distinct.query", "formData.distinct.query.data");
            dynamicBindingMapper.put("formData.distinct.key", "formData.distinct.key.data");
            dynamicBindingMapper.put("formData.find.query", "formData.find.query.data");
            dynamicBindingMapper.put("formData.find.sort", "formData.find.sort.data");
            dynamicBindingMapper.put("formData.find.projection", "formData.find.projection.data");
            dynamicBindingMapper.put("formData.find.limit", "formData.find.limit.data");
            dynamicBindingMapper.put("formData.find.skip", "formData.find.skip.data");
            dynamicBindingMapper.put("formData.insert.documents", "formData.insert.documents.data");
            dynamicBindingMapper.put("formData.updateMany.query", "formData.updateMany.query.data");
            dynamicBindingMapper.put("formData.updateMany.update", "formData.updateMany.update.data");
            dynamicBindingMapper.put("formData.updateMany.limit", "formData.updateMany.limit.data");
            dynamicBindingMapper.put("formData.smartSubstitution", "formData.smartSubstitution.data");
            dynamicBindingMapper.put("body", "formData.body.data");
            dynamicBindingPathList
                    .stream()
                    .forEach(dynamicBindingPath -> {
                        final String currentBinding = dynamicBindingPath.getKey();
                        final Optional<String> matchingBinding = dynamicBindingMapper.keySet().stream()
                                .filter(currentBinding::startsWith).findFirst();
                        if (matchingBinding.isPresent()) {
                            final String newBindingPrefix = dynamicBindingMapper.get(matchingBinding.get());
                            dynamicBindingPath.setKey(currentBinding.replace(matchingBinding.get(), newBindingPrefix));
                        }
                    });
        }
    }

    /**
     * Insert isConfigured boolean to check if the datasource is correctly
     * configured. This field will be used during
     * the file or git import to maintain the datasource configuration state
     *
     * @param mongockTemplate
     */
    @ChangeSet(order = "004", id = "add-isConfigured-flag-for-all-datasources", author = "")
    public void updateIsConfiguredFlagForAllTheExistingDatasources(MongockTemplate mongockTemplate) {
        final Query datasourceQuery = query(where(fieldName(QDatasource.datasource.deleted)).ne(true))
                .addCriteria(where(fieldName(QDatasource.datasource.invalids)).size(0));
        datasourceQuery.fields()
                .include(fieldName(QDatasource.datasource.id));

        List<Datasource> datasources = mongockTemplate.find(datasourceQuery, Datasource.class);
        for (Datasource datasource : datasources) {
            final Update update = new Update();
            update.set(fieldName(QDatasource.datasource.isConfigured), TRUE);
            mongockTemplate.updateFirst(
                    query(where(fieldName(QDatasource.datasource.id)).is(datasource.getId())),
                    update,
                    Datasource.class);
        }
    }

    @ChangeSet(order = "005", id = "set-application-version", author = "")
    public void setDefaultApplicationVersion(MongockTemplate mongockTemplate) {
        mongockTemplate.updateMulti(
                Query.query(where(fieldName(QApplication.application.deleted)).is(false)),
                Update.update(fieldName(QApplication.application.applicationVersion),
                        ApplicationVersion.EARLIEST_VERSION),
                Application.class);
    }

    @ChangeSet(order = "006", id = "delete-orphan-pages", author = "")
    public void deleteOrphanPages(MongockTemplate mongockTemplate) {

        final Query validPagesQuery = query(where(fieldName(QApplication.application.deleted)).ne(true));
        validPagesQuery.fields().include(fieldName(QApplication.application.pages));
        validPagesQuery.fields().include(fieldName(QApplication.application.publishedPages));

        final List<Application> applications = mongockTemplate.find(validPagesQuery, Application.class);

        final Update deletionUpdates = new Update();
        deletionUpdates.set(fieldName(QNewPage.newPage.deleted), true);
        deletionUpdates.set(fieldName(QNewPage.newPage.deletedAt), Instant.now());

        // Archive the pages which have the applicationId but the connection is missing from the application object.
        for (Application application : applications) {
            Set<String> validPageIds = new HashSet<>();
            if (!CollectionUtils.isEmpty(application.getPages())) {
                for (ApplicationPage applicationPage : application.getPages()) {
                    validPageIds.add(applicationPage.getId());
                }
            }
            if (!CollectionUtils.isEmpty(application.getPublishedPages())) {
                for (ApplicationPage applicationPublishedPage : application.getPublishedPages()) {
                    validPageIds.add(applicationPublishedPage.getId());
                }
            }
            final Query pageQuery = query(where(fieldName(QNewPage.newPage.deleted)).ne(true));
            pageQuery.addCriteria(where(fieldName(QNewPage.newPage.applicationId)).is(application.getId()));
            pageQuery.fields().include(fieldName(QNewPage.newPage.applicationId));

            final List<NewPage> pages = mongockTemplate.find(pageQuery, NewPage.class);
            for (NewPage newPage : pages) {
                if (!validPageIds.contains(newPage.getId())) {
                    mongockTemplate.updateFirst(
                            query(where(fieldName(QNewPage.newPage.id)).is(newPage.getId())),
                            deletionUpdates,
                            NewPage.class
                    );
                }
            }
        }
    }

    /**
     * This migration introduces indexes on newAction, actionCollection, newPage to improve the query performance for
     * queries like getResourceByDefaultAppIdAndGitSyncId which excludes the deleted entries.
     */
    @ChangeSet(order = "007", id = "update-git-indexes", author = "")
    public void addIndexesForGit(MongockTemplate mongockTemplate) {

        dropIndexIfExists(mongockTemplate, NewAction.class, "defaultApplicationId_gitSyncId_compound_index");
        dropIndexIfExists(mongockTemplate, ActionCollection.class, "defaultApplicationId_gitSyncId_compound_index");

        String defaultResources = fieldName(QBaseDomain.baseDomain.defaultResources);
        ensureIndexes(mongockTemplate, ActionCollection.class,
                makeIndex(
                        defaultResources + "." + FieldName.APPLICATION_ID,
                        fieldName(QBaseDomain.baseDomain.gitSyncId),
                        fieldName(QBaseDomain.baseDomain.deleted)
                )
                .named("defaultApplicationId_gitSyncId_deleted_compound_index")
        );

        ensureIndexes(mongockTemplate, NewAction.class,
                makeIndex(
                        defaultResources + "." + FieldName.APPLICATION_ID,
                        fieldName(QBaseDomain.baseDomain.gitSyncId),
                        fieldName(QBaseDomain.baseDomain.deleted)
                )
                .named("defaultApplicationId_gitSyncId_deleted_compound_index")
        );

        ensureIndexes(mongockTemplate, NewPage.class,
                makeIndex(
                        defaultResources + "." + FieldName.APPLICATION_ID,
                        fieldName(QBaseDomain.baseDomain.gitSyncId),
                        fieldName(QBaseDomain.baseDomain.deleted)
                )
                .named("defaultApplicationId_gitSyncId_deleted_compound_index")
        );
    }


    /**
     * We'll remove the uniqe index on organization slugs. We'll also regenerate the slugs for all organizations as
     * most of them are outdated
     * @param mongockTemplate MongockTemplate instance
     */
    @ChangeSet(order = "008", id = "update-organization-slugs", author = "")
    public void updateOrganizationSlugs(MongockTemplate mongockTemplate) {
        dropIndexIfExists(mongockTemplate, Organization.class, "slug");

        // update organizations
        final Query getAllOrganizationsQuery = query(where("deletedAt").is(null));
        getAllOrganizationsQuery.fields()
                .include(fieldName(QOrganization.organization.name));

        List<Organization> organizations = mongockTemplate.find(getAllOrganizationsQuery, Organization.class);

        for (Organization organization : organizations) {
            mongockTemplate.updateFirst(
                    query(where(fieldName(QOrganization.organization.id)).is(organization.getId())),
                    new Update().set(fieldName(QOrganization.organization.slug), TextUtils.makeSlug(organization.getName())),
                    Organization.class
            );
        }
    }

    @ChangeSet(order = "009", id = "copy-organization-to-workspaces", author = "")
    public void copyOrganizationToWorkspaces(MongockTemplate mongockTemplate) {
        // Drop the workspace collection in case it has been partially run, otherwise it has no effect
        mongockTemplate.dropCollection(Workspace.class);
        Gson gson = new Gson();
        //Memory optimization note:
        //Call stream instead of findAll to avoid out of memory if the collection is big
        //stream implementation lazy loads the data using underlying cursor open on the collection
        //the data is loaded as as and when needed by the pipeline
        try(Stream<Organization> stream = mongockTemplate.stream(new Query().cursorBatchSize(10000), Organization.class)
            .stream()) { 
            stream.forEach((organization) -> {
                Workspace workspace = gson.fromJson(gson.toJson(organization), Workspace.class);
                mongockTemplate.insert(workspace);
            });
        }
    }

    /**
     * We are creating indexes manually because Spring's index resolver creates indexes on fields as well.
     * See https://stackoverflow.com/questions/60867491/ for an explanation of the problem. We have that problem with
     * the `Action.datasource` field.
     */
    @ChangeSet(order = "010", id = "add-workspace-indexes", author = "")
    public void addWorkspaceIndexes(MongockTemplate mongockTemplate) {
        ensureIndexes(mongockTemplate, Workspace.class,
            makeIndex("createdAt")
        );
    }

    @ChangeSet(order = "011", id = "update-sequence-names-from-organization-to-workspace", author = "")
    public void updateSequenceNamesFromOrganizationToWorkspace(MongockTemplate mongockTemplate) {
        for (Sequence sequence : mongockTemplate.findAll(Sequence.class)) {
            String oldName = sequence.getName();
            String newName = oldName.replaceAll("(.*) for organization with _id : (.*)", "$1 for workspace with _id : $2");
            if(!newName.equals(oldName)) {
                //Using strings in the field names instead of QSequence becauce Sequence is not a AppsmithDomain
                mongockTemplate.updateFirst(query(where("name").is(oldName)),
                        Update.update("name", newName),
                        Sequence.class
                );
            }
        }
    }

    @ChangeSet(order = "012", id = "add-default-tenant", author = "")
    public void addDefaultTenant(MongockTemplate mongockTemplate) {

        Query tenantQuery = new Query();
        tenantQuery.addCriteria(where(fieldName(QTenant.tenant.slug)).is("default"));
        Tenant tenant = mongockTemplate.findOne(tenantQuery, Tenant.class);

        // if tenant already exists, don't create a new one.
        if (tenant != null) {
            return;
        }

        Tenant defaultTenant = new Tenant();
        defaultTenant.setDisplayName("Default");
        defaultTenant.setSlug("default");
        defaultTenant.setPricingPlan(PricingPlan.FREE);

        mongockTemplate.save(defaultTenant);

    }

    @ChangeSet(order = "013", id = "add-tenant-to-all-workspaces", author = "")
    public void addTenantToWorkspaces(MongockTemplate mongockTemplate) {

        Query tenantQuery = new Query();
        tenantQuery.addCriteria(where(fieldName(QTenant.tenant.slug)).is("default"));
        Tenant defaultTenant = mongockTemplate.findOne(tenantQuery, Tenant.class);
        assert(defaultTenant != null);

        // Set all the workspaces to be under the default tenant
        mongockTemplate.updateMulti(
                new Query(),
                new Update().set("tenantId", defaultTenant.getId()),
                Workspace.class
        );

    }

    @ChangeSet(order = "014", id = "add-tenant-to-all-users-and-flush-redis", author = "")
    public void addTenantToUsersAndFlushRedis(MongockTemplate mongockTemplate, ReactiveRedisOperations<String, String>reactiveRedisOperations) {

        Query tenantQuery = new Query();
        tenantQuery.addCriteria(where(fieldName(QTenant.tenant.slug)).is("default"));
        Tenant defaultTenant = mongockTemplate.findOne(tenantQuery, Tenant.class);
        assert(defaultTenant != null);

        // Set all the users to be under the default tenant
        mongockTemplate.updateMulti(
                new Query(),
                new Update().set("tenantId", defaultTenant.getId()),
                User.class
        );

        // Now sign out all the existing users since this change impacts the user object.
        final String script =
                "for _,k in ipairs(redis.call('keys','spring:session:sessions:*'))" +
                        " do redis.call('del',k) " +
                        "end";
        final Flux<Object> flushdb = reactiveRedisOperations.execute(RedisScript.of(script));

        flushdb.subscribe();
    }

    @ChangeSet(order = "015", id = "migrate-organizationId-to-workspaceId-in-domain-objects", author = "")
    public void migrateOrganizationIdToWorkspaceIdInDomainObjects(MongockTemplate mongockTemplate, ReactiveRedisOperations<String, String>reactiveRedisOperations) {
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QDatasource.datasource.workspaceId)).toValueOf(Fields.field(fieldName(QDatasource.datasource.organizationId))),
            Datasource.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QActionCollection.actionCollection.workspaceId)).toValueOf(Fields.field(fieldName(QActionCollection.actionCollection.organizationId))),
            ActionCollection.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QApplication.application.workspaceId)).toValueOf(Fields.field(fieldName(QApplication.application.organizationId))),
            Application.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QNewAction.newAction.workspaceId)).toValueOf(Fields.field(fieldName(QNewAction.newAction.organizationId))),
            NewAction.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QTheme.theme.workspaceId)).toValueOf(Fields.field(fieldName(QTheme.theme.organizationId))),
            Theme.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QUserData.userData.recentlyUsedWorkspaceIds)).toValueOf(Fields.field(fieldName(QUserData.userData.recentlyUsedOrgIds))),
            UserData.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QWorkspace.workspace.isAutoGeneratedWorkspace)).toValueOf(Fields.field(fieldName(QWorkspace.workspace.isAutoGeneratedOrganization))),
            Workspace.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update()
                .set(fieldName(QUser.user.workspaceIds)).toValueOf(Fields.field(fieldName(QUser.user.organizationIds)))
                .set(fieldName(QUser.user.currentWorkspaceId)).toValueOf(Fields.field(fieldName(QUser.user.currentOrganizationId)))
                .set(fieldName(QUser.user.examplesWorkspaceId)).toValueOf(Fields.field(fieldName(QUser.user.examplesOrganizationId))),
            User.class);

        // Now sign out all the existing users since this change impacts the user object.
        final String script =
                "for _,k in ipairs(redis.call('keys','spring:session:sessions:*'))" +
                        " do redis.call('del',k) " +
                        "end";
        final Flux<Object> flushdb = reactiveRedisOperations.execute(RedisScript.of(script));

        flushdb.subscribe();

        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QComment.comment.workspaceId)).toValueOf(Fields.field(fieldName(QComment.comment.workspaceId))),
            Comment.class);
        mongockTemplate.updateMulti(new Query(),
            AggregationUpdate.update().set(fieldName(QCommentThread.commentThread.workspaceId)).toValueOf(Fields.field(fieldName(QCommentThread.commentThread.workspaceId))),
            CommentThread.class);
    }

    @ChangeSet(order = "016", id = "organization-to-workspace-indexes-recreate", author = "")
    public void organizationToWorkspaceIndexesRecreate(MongockTemplate mongockTemplate) {
        dropIndexIfExists(mongockTemplate, Application.class, "organization_application_deleted_gitApplicationMetadata_compound_index");
        dropIndexIfExists(mongockTemplate, Datasource.class, "organization_datasource_deleted_compound_index");

        //If this migration is re-run
        dropIndexIfExists(mongockTemplate, Application.class, "workspace_application_deleted_gitApplicationMetadata_compound_index");
        dropIndexIfExists(mongockTemplate, Datasource.class, "workspace_datasource_deleted_compound_index");

        ensureIndexes(mongockTemplate, Application.class,
                makeIndex(
                    fieldName(QApplication.application.workspaceId),
                    fieldName(QApplication.application.name),
                    fieldName(QApplication.application.deletedAt),
                    "gitApplicationMetadata.remoteUrl",
                    "gitApplicationMetadata.branchName")
                        .unique().named("workspace_application_deleted_gitApplicationMetadata_compound_index")
        );
        ensureIndexes(mongockTemplate, Datasource.class,
                makeIndex(fieldName(QDatasource.datasource.workspaceId),
                    fieldName(QDatasource.datasource.name),
                    fieldName(QDatasource.datasource.deletedAt))
                        .unique().named("workspace_datasource_deleted_compound_index")
        );
    }

    @ChangeSet(order = "017", id = "migrate-permission-in-user", author = "")
    public void migratePermissionsInUser(MongockTemplate mongockTemplate) {
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("manage:userOrganization")),
            new Update().set("policies.$.permission", "manage:userWorkspace"),
            User.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("read:userOrganization")),
            new Update().set("policies.$.permission", "read:userWorkspace"),
            User.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("read:userOrganization")),
            new Update().set("policies.$.permission", "read:userWorkspace"),
            User.class);
    }

    @ChangeSet(order = "018", id = "migrate-permission-in-workspace", author = "")
    public void migratePermissionsInWorkspace(MongockTemplate mongockTemplate) {
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("manage:organizations")),
            new Update().set("policies.$.permission", "manage:workspaces"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("read:organizations")),
            new Update().set("policies.$.permission", "read:workspaces"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("manage:orgApplications")),
            new Update().set("policies.$.permission", "manage:workspaceApplications"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("read:orgApplications")),
            new Update().set("policies.$.permission", "read:workspaceApplications"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("publish:orgApplications")),
            new Update().set("policies.$.permission", "publish:workspaceApplications"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("export:orgApplications")),
            new Update().set("policies.$.permission", "export:workspaceApplications"),
            Workspace.class);
        mongockTemplate.updateMulti(
            new Query().addCriteria(where("policies.permission").is("inviteUsers:organization")),
            new Update().set("policies.$.permission", "inviteUsers:workspace"),
            Workspace.class);
    }

    @ChangeSet(order = "019", id = "migrate-organizationId-to-workspaceId-in-newaction-datasource", author = "")
    public void migrateOrganizationIdToWorkspaceIdInNewActionDatasource(MongockTemplate mongockTemplate, ReactiveRedisOperations<String, String>reactiveRedisOperations) {
        mongockTemplate.updateMulti(new Query(Criteria.where("unpublishedAction.datasource.organizationId").exists(true)),
            AggregationUpdate.update().set("unpublishedAction.datasource.workspaceId").toValueOf(Fields.field("unpublishedAction.datasource.organizationId")),
            NewAction.class);
        mongockTemplate.updateMulti(new Query(Criteria.where("publishedAction.datasource.organizationId").exists(true)),
            AggregationUpdate.update().set("publishedAction.datasource.workspaceId").toValueOf(Fields.field("publishedAction.datasource.organizationId")),
            NewAction.class);
    }

    @ChangeSet(order = "020", id = "add-anonymousUser", author = "")
    public void addAnonymousUser(MongockTemplate mongockTemplate) {
        Query tenantQuery = new Query();
        tenantQuery.addCriteria(where(fieldName(QTenant.tenant.slug)).is("default"));
        Tenant tenant = mongockTemplate.findOne(tenantQuery, Tenant.class);

        Query userQuery = new Query();
        userQuery.addCriteria(where(fieldName(QUser.user.email)).is(FieldName.ANONYMOUS_USER))
                .addCriteria(where(fieldName(QUser.user.tenantId)).is(tenant.getId()));
        User anonymousUser = mongockTemplate.findOne(userQuery, User.class);

        if (anonymousUser == null) {
            anonymousUser = new User();
            anonymousUser.setName(FieldName.ANONYMOUS_USER);
            anonymousUser.setEmail(FieldName.ANONYMOUS_USER);
            anonymousUser.setCurrentWorkspaceId("");
            anonymousUser.setWorkspaceIds(new HashSet<>());
            anonymousUser.setIsAnonymous(true);
            anonymousUser.setTenantId(tenant.getId());

            mongockTemplate.save(anonymousUser);
        }
    }

    private String getDefaultNameForGroupInWorkspace(String prefix, String workspaceName) {
        return prefix + " - " + workspaceName;
    }

    private Set<PermissionGroup> generateDefaultPermissionGroupsWithoutPermissions(MongockTemplate mongockTemplate, Workspace workspace) {
        String workspaceName = workspace.getName();
        String workspaceId = workspace.getId();
        // Administrator permission group
        PermissionGroup adminPermissionGroup = new PermissionGroup();
        adminPermissionGroup.setName(getDefaultNameForGroupInWorkspace(FieldName.ADMINISTRATOR, workspaceName));
        adminPermissionGroup.setDefaultWorkspaceId(workspaceId);
        adminPermissionGroup.setTenantId(workspace.getTenantId());
        adminPermissionGroup.setDescription(FieldName.WORKSPACE_ADMINISTRATOR_DESCRIPTION);
        Set<Permission> workspacePermissions = AppsmithRole.ORGANIZATION_ADMIN
                .getPermissions()
                .stream()
                .filter(aclPermission -> aclPermission.getEntity().equals(Workspace.class))
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        adminPermissionGroup.setPermissions(workspacePermissions);
        adminPermissionGroup = mongockTemplate.save(adminPermissionGroup);

        // Developer permission group
        PermissionGroup developerPermissionGroup = new PermissionGroup();
        developerPermissionGroup.setName(getDefaultNameForGroupInWorkspace(FieldName.DEVELOPER, workspaceName));
        developerPermissionGroup.setDefaultWorkspaceId(workspaceId);
        developerPermissionGroup.setTenantId(workspace.getTenantId());
        developerPermissionGroup.setDescription(FieldName.WORKSPACE_DEVELOPER_DESCRIPTION);
        workspacePermissions = AppsmithRole.ORGANIZATION_DEVELOPER
                .getPermissions()
                .stream()
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        developerPermissionGroup.setPermissions(workspacePermissions);
        developerPermissionGroup = mongockTemplate.save(developerPermissionGroup);

        // App viewer permission group
        PermissionGroup viewerPermissionGroup = new PermissionGroup();
        viewerPermissionGroup.setName(getDefaultNameForGroupInWorkspace(FieldName.VIEWER, workspaceName));
        viewerPermissionGroup.setDefaultWorkspaceId(workspaceId);
        viewerPermissionGroup.setTenantId(workspace.getTenantId());
        viewerPermissionGroup.setDescription(FieldName.WORKSPACE_VIEWER_DESCRIPTION);
        workspacePermissions = AppsmithRole.ORGANIZATION_VIEWER
                .getPermissions()
                .stream()
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        viewerPermissionGroup.setPermissions(workspacePermissions);
        viewerPermissionGroup = mongockTemplate.save(viewerPermissionGroup);

        return Set.of(adminPermissionGroup, developerPermissionGroup, viewerPermissionGroup);
    }

    private Set<PermissionGroup> generatePermissionsForDefaultPermissionGroups(MongockTemplate mongockTemplate, PolicyUtils policyUtils, Set<PermissionGroup> permissionGroups, Workspace workspace) {
        PermissionGroup adminPermissionGroup = permissionGroups.stream()
                .filter(permissionGroup -> permissionGroup.getName().startsWith(FieldName.ADMINISTRATOR))
                .findFirst().get();
        PermissionGroup developerPermissionGroup = permissionGroups.stream()
                .filter(permissionGroup -> permissionGroup.getName().startsWith(FieldName.DEVELOPER))
                .findFirst().get();
        PermissionGroup viewerPermissionGroup = permissionGroups.stream()
                .filter(permissionGroup -> permissionGroup.getName().startsWith(FieldName.VIEWER))
                .findFirst().get();

        // Administrator permissions
        Set<Permission> workspacePermissions = AppsmithRole.ORGANIZATION_ADMIN
                .getPermissions()
                .stream()
                .filter(aclPermission -> aclPermission.getEntity().equals(Workspace.class))
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        // The administrator should also be able to assign any of the three permissions groups
        Set<Permission> permissionGroupPermissions = permissionGroups.stream()
                .map(permissionGroup -> new Permission(permissionGroup.getId(), AclPermission.ASSIGN_PERMISSION_GROUPS))
                .collect(Collectors.toSet());


        Set<Permission> permissions = new HashSet<>();
        permissions.addAll(workspacePermissions);
        permissions.addAll(permissionGroupPermissions);
        adminPermissionGroup.setPermissions(permissions);

        // Assign admin user ids to the administrator permission group
        Set<String> adminUserIds = workspace.getUserRoles()
                .stream()
                .filter(userRole -> userRole.getRole().equals(AppsmithRole.ORGANIZATION_ADMIN))
                .map(UserRole::getUserId)
                .collect(Collectors.toSet());
            
        adminPermissionGroup.setAssignedToUserIds(adminUserIds);

        // Developer Permissions
        workspacePermissions = AppsmithRole.ORGANIZATION_DEVELOPER
                .getPermissions()
                .stream()
                .filter(aclPermission -> aclPermission.getEntity().equals(Workspace.class))
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        // The developer should also be able to assign developer & viewer permission groups
        permissionGroupPermissions = Set.of(developerPermissionGroup, viewerPermissionGroup).stream()
                .map(permissionGroup -> new Permission(permissionGroup.getId(), AclPermission.ASSIGN_PERMISSION_GROUPS))
                .collect(Collectors.toSet());
        permissions = new HashSet<>();
        permissions.addAll(workspacePermissions);
        permissions.addAll(permissionGroupPermissions);
        developerPermissionGroup.setPermissions(permissions);

        // Assign developer user ids to the developer permission group
        Set<String> developerUserIds = workspace.getUserRoles()
                .stream()
                .filter(userRole -> userRole.getRole().equals(AppsmithRole.ORGANIZATION_DEVELOPER))
                .map(UserRole::getUserId)
                .collect(Collectors.toSet());

        developerPermissionGroup.setAssignedToUserIds(developerUserIds);

        // App Viewer Permissions
        workspacePermissions = AppsmithRole.ORGANIZATION_VIEWER
                .getPermissions()
                .stream()
                .filter(aclPermission -> aclPermission.getEntity().equals(Workspace.class))
                .map(aclPermission -> new Permission(workspace.getId(), aclPermission))
                .collect(Collectors.toSet());
        // The app viewers should also be able to assign to viewer permission groups
        permissionGroupPermissions = Set.of(viewerPermissionGroup).stream()
                .map(permissionGroup -> new Permission(permissionGroup.getId(), AclPermission.ASSIGN_PERMISSION_GROUPS))
                .collect(Collectors.toSet());
        permissions = new HashSet<>();
        permissions.addAll(workspacePermissions);
        permissions.addAll(permissionGroupPermissions);
        viewerPermissionGroup.setPermissions(permissions);

        // Assign viewer user ids to the viewer permission group
        Set<String> viewerUserIds = workspace.getUserRoles()
                .stream()
                .filter(userRole -> userRole.getRole().equals(AppsmithRole.ORGANIZATION_VIEWER))
                .map(UserRole::getUserId)
                .collect(Collectors.toSet());

        viewerPermissionGroup.setAssignedToUserIds(viewerUserIds);

        Set<PermissionGroup> savedPermissionGroups = Set.of(adminPermissionGroup, developerPermissionGroup, viewerPermissionGroup);

        // Apply the permissions to the permission groups
        for (PermissionGroup permissionGroup : savedPermissionGroups) {
            for (PermissionGroup nestedPermissionGroup : savedPermissionGroups) {
                Map<String, Policy> policyMap = policyUtils.generatePolicyFromPermissionGroupForObject(permissionGroup, nestedPermissionGroup.getId());
                 policyUtils.addPoliciesToExistingObject(policyMap, nestedPermissionGroup);
            }
        }

        // Save the permission groups
        adminPermissionGroup = mongockTemplate.save(adminPermissionGroup);
        developerPermissionGroup = mongockTemplate.save(developerPermissionGroup);
        viewerPermissionGroup = mongockTemplate.save(viewerPermissionGroup);

        return Set.of(adminPermissionGroup, developerPermissionGroup, viewerPermissionGroup);
    }

    @ChangeSet(order = "021", id = "add-default-permission-groups", author = "")
    public void addDefaultPermissionGroups(MongockTemplate mongockTemplate, WorkspaceService workspaceService, @NonLockGuarded PolicyUtils policyUtils) {
        // Drop PermissionGroup collection
        // This ensures that migration can run again if aborted in between
        mongockTemplate.dropCollection(PermissionGroup.class);
        mongockTemplate.stream(new Query(), Workspace.class)
                .stream()
                .forEach(workspace -> {
                    if(workspace.getUserRoles() != null) {
                        // Clear permission groups inside policies
                        // This ensures that migration can run again if aborted in between
                        workspace.getPolicies().forEach(policy -> {
                            policy.setPermissionGroups(new HashSet<>());
                        });
                        Set<PermissionGroup> permissionGroups = generateDefaultPermissionGroupsWithoutPermissions(mongockTemplate, workspace);
                        // Set default permission groups
                        workspace.setDefaultPermissionGroups(permissionGroups.stream().map(PermissionGroup::getId).collect(Collectors.toSet()));
                        // Generate permissions and policies for the default permission groups
                        permissionGroups = generatePermissionsForDefaultPermissionGroups(mongockTemplate, policyUtils, permissionGroups, workspace);
                        // Apply the permissions to the workspace
                        for (PermissionGroup permissionGroup : permissionGroups) {
                            // Apply the permissions to the workspace
                            Map<String, Policy> policyMap = policyUtils.generatePolicyFromPermissionGroupForObject(permissionGroup, workspace.getId());
                            workspace = policyUtils.addPoliciesToExistingObject(policyMap, workspace);
                        }
                        // Save the workspace
                        mongockTemplate.save(workspace);
                    }
                });
    }

    @ChangeSet(order = "022", id = "inherit-policies-to-every-child-object", author = "")
    public void inheritPoliciesToEveryChildObject(MongockTemplate mongockTemplate, @NonLockGuarded PolicyGenerator policyGenerator) {
        mongockTemplate.stream(new Query(), Workspace.class)
                .stream()
                .forEach(workspace -> {
                    // Process applications
                    Set<Policy> applicationPolicies = policyGenerator.getAllChildPolicies(workspace.getPolicies(), Workspace.class, Application.class);
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QApplication.application.workspaceId)).is(workspace.getId())),
                            new Update().set("policies", applicationPolicies),
                            Workspace.class);

                    // Process datasources
                    Set<Policy> datasourcePolicies = policyGenerator.getAllChildPolicies(workspace.getPolicies(), Workspace.class, Datasource.class);
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QDatasource.datasource.workspaceId)).is(workspace.getId())),
                            new Update().set("policies", datasourcePolicies),
                            Workspace.class);

                    // Get application ids
                    Set<String> applicationIds = mongockTemplate.stream(new Query().addCriteria(Criteria.where(fieldName(QApplication.application.workspaceId)).is(workspace.getId())), Application.class)
                            .stream()
                            .map(Application::getId)
                            .collect(Collectors.toSet());

                    // Update pages
                    Set<Policy> pagePolicies = policyGenerator.getAllChildPolicies(applicationPolicies, Application.class, Page.class);
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QNewPage.newPage.applicationId)).in(applicationIds)),
                            new Update().set("policies", pagePolicies),
                            NewPage.class);

                    // Update NewActions
                    Set<Policy> actionPolicies = policyGenerator.getAllChildPolicies(pagePolicies, Page.class, Action.class);
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QNewAction.newAction.applicationId)).in(applicationIds)),
                            new Update().set("policies", actionPolicies),
                            NewAction.class);

                    // Update ActionCollections
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QActionCollection.actionCollection.applicationId)).in(applicationIds)),
                            new Update().set("policies", actionPolicies),
                            ActionCollection.class);

                    // Update Themes
                    Set<Policy> themePolicies = policyGenerator.getAllChildPolicies(applicationPolicies, Application.class, Theme.class);
                    mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QTheme.theme.applicationId)).in(applicationIds)),
                            new Update().set("policies", themePolicies),
                            Theme.class);
                });
    }

    private void makeApplicationPublic(PolicyUtils policyUtils, PolicyGenerator policyGenerator, NewPageRepository newPageRepository, Application application, Workspace workspace, MongockTemplate mongockTemplate, User anonymousUser) {
        PermissionGroup publicPermissionGroup = new PermissionGroup();
        publicPermissionGroup.setName(application.getName() + " Public");
        publicPermissionGroup.setTenantId(workspace.getTenantId());
        publicPermissionGroup.setDescription("Default permissions generated for sharing an application for viewing.");

        Set<Policy> applicationPolicies = application.getPolicies();
        Policy makePublicPolicy = applicationPolicies.stream()
                .filter(policy -> policy.getPermission().equals(AclPermission.MAKE_PUBLIC_APPLICATIONS.getValue()))
                .findFirst()
                .get();

        // Let this newly created permission group be assignable by everyone who has permission for make public application
        Policy assignPermissionGroup = Policy.builder()
                .permission(AclPermission.ASSIGN_PERMISSION_GROUPS.getValue())
                .permissionGroups(makePublicPolicy.getPermissionGroups())
                .build();

        publicPermissionGroup.setPolicies(Set.of(assignPermissionGroup));
        publicPermissionGroup.setAssignedToUserIds(Set.of(anonymousUser.getId()));
        publicPermissionGroup = mongockTemplate.save(publicPermissionGroup);

        application.setDefaultPermissionGroup(publicPermissionGroup.getId());
        
        String permissionGroupId = publicPermissionGroup.getId();

        Map<String, Policy> applicationPolicyMap = policyUtils
                .generatePolicyFromPermissionWithPermissionGroup(AclPermission.READ_APPLICATIONS, permissionGroupId);
        Map<String, Policy> datasourcePolicyMap = policyUtils
                .generatePolicyFromPermissionWithPermissionGroup(AclPermission.EXECUTE_DATASOURCES, permissionGroupId);

        Set<String> datasourceIds = new HashSet<>();

        mongockTemplate.stream(new Query().addCriteria(Criteria.where(fieldName(QNewAction.newAction.applicationId)).is(application.getId())), NewAction.class)
                .stream()
                .forEach(newAction -> {
                    ActionDTO unpublishedAction = newAction.getUnpublishedAction();
                    ActionDTO publishedAction = newAction.getPublishedAction();

                    if (unpublishedAction.getDatasource() != null &&
                            unpublishedAction.getDatasource().getId() != null) {
                        datasourceIds.add(unpublishedAction.getDatasource().getId());
                    }

                    if (publishedAction != null &&
                            publishedAction.getDatasource() != null &&
                            publishedAction.getDatasource().getId() != null) {
                        datasourceIds.add(publishedAction.getDatasource().getId());
                    }
                });

        // Update and save application
        application = policyUtils.addPoliciesToExistingObject(applicationPolicyMap, application);
        mongockTemplate.save(application);
        applicationPolicies = application.getPolicies();

        // Update datasources
        mongockTemplate.stream(new Query().addCriteria(Criteria.where(fieldName(QDatasource.datasource.id)).in(datasourceIds)), Datasource.class)
                .stream()
                .forEach(datasource -> {
                    datasource = policyUtils.addPoliciesToExistingObject(datasourcePolicyMap, datasource);
                    mongockTemplate.save(datasource);
                });

        // Update pages
        Set<Policy> pagePolicies = policyGenerator.getAllChildPolicies(applicationPolicies, Application.class, Page.class);
        mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QNewPage.newPage.applicationId)).is(application.getId())),
                new Update().set("policies", pagePolicies),
                NewPage.class);

        // Update NewActions
        Set<Policy> actionPolicies = policyGenerator.getAllChildPolicies(pagePolicies, Page.class, Action.class);
        mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QNewAction.newAction.applicationId)).is(application.getId())),
                new Update().set("policies", actionPolicies),
                NewAction.class);

        // Update ActionCollections
        mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QActionCollection.actionCollection.applicationId)).is(application.getId())),
                new Update().set("policies", actionPolicies),
                ActionCollection.class);

        // Update Themes
        Set<Policy> themePolicies = policyGenerator.getAllChildPolicies(applicationPolicies, Application.class, Theme.class);
        mongockTemplate.findAndModify(new Query().addCriteria(Criteria.where(fieldName(QTheme.theme.applicationId)).is(application.getId())),
                new Update().set("policies", themePolicies),
                Theme.class);
    }

    @ChangeSet(order = "023", id = "make-applications-public", author = "")
    public void makeApplicationsPublic(MongockTemplate mongockTemplate, @NonLockGuarded PolicyUtils policyUtils, @NonLockGuarded PolicyGenerator policyGenerator, NewPageRepository newPageRepository) {
        User anonymousUser = mongockTemplate.findOne(new Query().addCriteria(Criteria.where(fieldName(QUser.user.email)).is(FieldName.ANONYMOUS_USER)), User.class);
        mongockTemplate.stream(new Query().addCriteria(Criteria.where("policies").elemMatch(Criteria.where("users").is("anonymousUser"))), Application.class)
                .stream()
                .forEach(application -> {
                    Workspace workspace = mongockTemplate.findOne(new Query().addCriteria(Criteria.where(fieldName(QBaseDomain.baseDomain.id)).is(application.getWorkspaceId())), Workspace.class);
                    makeApplicationPublic(policyUtils, policyGenerator, newPageRepository, application, workspace, mongockTemplate, anonymousUser);
                });
    }

    @ChangeSet(order = "024", id = "add-instance-config-object", author = "")
    public void addInstanceConfigurationPlaceHolder(MongockTemplate mongockTemplate) {
        Query instanceConfigurationQuery = new Query();
        instanceConfigurationQuery.addCriteria(where(fieldName(QConfig.config1.name)).is(FieldName.INSTANCE_CONFIG));
        Config instanceAdminConfiguration = mongockTemplate.findOne(instanceConfigurationQuery, Config.class);

        if (instanceAdminConfiguration != null) {
            return;
        }

        instanceAdminConfiguration = new Config();
        instanceAdminConfiguration.setName(FieldName.INSTANCE_CONFIG);
        Config savedInstanceConfig = mongockTemplate.save(instanceAdminConfiguration);

        // Create instance management permission group
        PermissionGroup instanceManagerPermissionGroup = new PermissionGroup();
        instanceManagerPermissionGroup.setName(FieldName.INSTACE_ADMIN_ROLE);
        instanceManagerPermissionGroup.setPermissions(
                Set.of(
                        new Permission(savedInstanceConfig.getId(), MANAGE_INSTANCE_CONFIGURATION)
                )
        );

        Query adminUserQuery = new Query();
        adminUserQuery.addCriteria(where(fieldName(QBaseDomain.baseDomain.policies))
                        .elemMatch(where("permission").is(MANAGE_INSTANCE_ENV.getValue())));
        List<User> adminUsers = mongockTemplate.find(adminUserQuery, User.class);

        instanceManagerPermissionGroup.setAssignedToUserIds(
                adminUsers.stream().map(User::getId).collect(Collectors.toSet())
        );

        PermissionGroup savedPermissionGroup = mongockTemplate.save(instanceManagerPermissionGroup);

        // Update the instance config with the permission group id
        savedInstanceConfig.setConfig(new JSONObject(Map.of(DEFAULT_PERMISSION_GROUP, savedPermissionGroup.getId())));

        Policy editConfigPolicy = Policy.builder().permission(MANAGE_INSTANCE_CONFIGURATION.getValue())
                .permissionGroups(Set.of(savedPermissionGroup.getId()))
                .build();
        Policy readConfigPolicy = Policy.builder().permission(READ_INSTANCE_CONFIGURATION.getValue())
                .permissionGroups(Set.of(savedPermissionGroup.getId()))
                .build();

        savedInstanceConfig.setPolicies(Set.of(editConfigPolicy, readConfigPolicy));

        mongockTemplate.save(savedInstanceConfig);

        // Also give the permission group permission to update & assign to itself
        Policy updatePermissionGroupPolicy = Policy.builder().permission(MANAGE_PERMISSION_GROUPS.getValue())
                .permissionGroups(Set.of(savedPermissionGroup.getId()))
                .build();

        Policy assignPermissionGroupPolicy = Policy.builder().permission(ASSIGN_PERMISSION_GROUPS.getValue())
                .permissionGroups(Set.of(savedPermissionGroup.getId()))
                .build();

        savedPermissionGroup.setPolicies(Set.of(updatePermissionGroupPolicy, assignPermissionGroupPolicy));

        Set<Permission> permissions = new HashSet<>(savedPermissionGroup.getPermissions());
        permissions.addAll(
                Set.of(
                        new Permission(savedPermissionGroup.getId(), MANAGE_PERMISSION_GROUPS),
                        new Permission(savedPermissionGroup.getId(), ASSIGN_PERMISSION_GROUPS)
                )
        );
        savedPermissionGroup.setPermissions(permissions);

        mongockTemplate.save(savedPermissionGroup);
    }
}
