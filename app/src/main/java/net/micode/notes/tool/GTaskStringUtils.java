/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.tool;

// GTaskStringUtils - String constants for Google Tasks synchronization
/* Google Tasks 字符串工具类 - 定义与 Google Tasks 同步相关的 JSON 键名和文件夹名称常量 */
public class GTaskStringUtils {

    // JSON key: action_id
    /* JSON 键 - 操作ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    // JSON key: action_list
    /* JSON 键 - 操作列表 */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    // JSON key: action_type
    /* JSON 键 - 操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    // Action type: create
    /* 操作类型 - 创建 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    // Action type: get all
    /* 操作类型 - 获取所有 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    // Action type: move
    /* 操作类型 - 移动 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    // Action type: update
    /* 操作类型 - 更新 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // JSON key: creator_id
    /* JSON 键 - 创建者ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    // JSON key: child_entity
    /* JSON 键 - 子实体 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    // JSON key: client_version
    /* JSON 键 - 客户端版本 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    // JSON key: completed
    /* JSON 键 - 是否已完成 */
    public final static String GTASK_JSON_COMPLETED = "completed";

    // JSON key: current_list_id
    /* JSON 键 - 当前列表ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    // JSON key: default_list_id
    /* JSON 键 - 默认列表ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    // JSON key: deleted
    /* JSON 键 - 是否已删除 */
    public final static String GTASK_JSON_DELETED = "deleted";

    // JSON key: dest_list
    /* JSON 键 - 目标列表 */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    // JSON key: dest_parent
    /* JSON 键 - 目标父节点 */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    // JSON key: dest_parent_type
    /* JSON 键 - 目标父节点类型 */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    // JSON key: entity_delta
    /* JSON 键 - 实体增量 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    // JSON key: entity_type
    /* JSON 键 - 实体类型 */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    // JSON key: get_deleted
    /* JSON 键 - 获取已删除项 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    // JSON key: id
    /* JSON 键 - ID */
    public final static String GTASK_JSON_ID = "id";

    // JSON key: index
    /* JSON 键 - 索引位置 */
    public final static String GTASK_JSON_INDEX = "index";

    // JSON key: last_modified
    /* JSON 键 - 最后修改时间 */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    // JSON key: latest_sync_point
    /* JSON 键 - 最新同步点 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    // JSON key: list_id
    /* JSON 键 - 列表ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    // JSON key: lists
    /* JSON 键 - 列表集合 */
    public final static String GTASK_JSON_LISTS = "lists";

    // JSON key: name
    /* JSON 键 - 名称 */
    public final static String GTASK_JSON_NAME = "name";

    // JSON key: new_id
    /* JSON 键 - 新ID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    // JSON key: notes
    /* JSON 键 - 笔记集合 */
    public final static String GTASK_JSON_NOTES = "notes";

    // JSON key: parent_id
    /* JSON 键 - 父节点ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    // JSON key: prior_sibling_id
    /* JSON 键 - 前一个兄弟节点ID */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    // JSON key: results
    /* JSON 键 - 结果集合 */
    public final static String GTASK_JSON_RESULTS = "results";

    // JSON key: source_list
    /* JSON 键 - 源列表 */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    // JSON key: tasks
    /* JSON 键 - 任务集合 */
    public final static String GTASK_JSON_TASKS = "tasks";

    // JSON key: type
    /* JSON 键 - 类型 */
    public final static String GTASK_JSON_TYPE = "type";

    // Value for type: GROUP
    /* 类型值 - 分组（文件夹） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    // Value for type: TASK
    /* 类型值 - 任务（笔记） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    // JSON key: user
    /* JSON 键 - 用户信息 */
    public final static String GTASK_JSON_USER = "user";

    // Prefix for MIUI folder names
    /* MIUI 文件夹名称前缀 */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    // Default folder name
    /* 默认文件夹名称 */
    public final static String FOLDER_DEFAULT = "Default";

    // Call note folder name
    /* 通话记录笔记文件夹名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    // Metadata folder name
    /* 元数据文件夹名称 */
    public final static String FOLDER_META = "METADATA";

    // Metadata key: Google Tasks ID for note
    /* 元数据键 - 笔记对应的 Google Tasks ID */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    // Metadata key: note metadata
    /* 元数据键 - 笔记元数据 */
    public final static String META_HEAD_NOTE = "meta_note";

    // Metadata key: data metadata
    /* 元数据键 - 数据元数据 */
    public final static String META_HEAD_DATA = "meta_data";

    // Metadata note name (do not update or delete)
    /* 元数据笔记名称 - 请勿更新或删除 */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}