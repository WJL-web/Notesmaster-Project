/*
 * 版权声明 (c) 2010-2011, 米代码开源社区 (www.micode.net)
 *
 * 根据 Apache License 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，根据许可证分发的软件
 * 是按“原样”基础分发的，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */

package net.micode.notes.tool;

/**
 * GTaskStringUtils 类 - Google Tasks 字符串常量工具类
 *
 * 作用：集中定义与 Google Tasks API 交互时使用的所有字符串常量
 *
 * 设计目的：
 * 1. 避免硬编码字符串，提高代码可维护性
 * 2. 统一管理JSON键名、操作类型、文件夹名称等常量
 * 3. 减少拼写错误，利用编译器检查
 *
 * 常量分类：
 * - JSON 键名（用于构建和解析API请求/响应）
 * - 操作类型（create、update、move、get_all）
 * - 实体类型（TASK、GROUP）
 * - 特殊文件夹名称
 * - 元数据相关常量
 */
public class GTaskStringUtils {

    // ==================== JSON 键名常量 ====================
    // 以下常量用于 Google Tasks API 的 JSON 请求和响应

    /** 操作ID - 标识每个操作的唯一ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** 操作列表 - 包含多个操作的数组 */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** 操作类型 - 标识操作的类型（create/update/move/get_all） */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /** 结果列表 - API响应中的结果数组 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** 新ID - 创建操作返回的新资源ID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** 客户端版本 - 客户端版本号，用于同步 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** ID - 资源（任务或任务列表）的唯一标识 */
    public final static String GTASK_JSON_ID = "id";

    /** 名称 - 任务或任务列表的名称 */
    public final static String GTASK_JSON_NAME = "name";

    /** 备注 - 任务的详细备注内容 */
    public final static String GTASK_JSON_NOTES = "notes";

    /** 完成状态 - 任务是否已完成 */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** 删除状态 - 资源是否已删除 */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** 最后修改时间 - 资源的最后修改时间戳 */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** 索引 - 任务在其父列表中的位置索引 */
    public final static String GTASK_JSON_INDEX = "index";

    /** 父节点ID - 父资源的ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** 前一个兄弟节点ID - 用于排序，前一个同级任务的ID */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** 实体增量 - 实体数据的变更部分 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** 实体类型 - 实体类型（TASK 或 GROUP） */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** 创建者ID - 创建该资源的用户ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** 列表ID - 任务所属的任务列表ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** 当前列表ID - 当前所在的列表ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** 默认列表ID - 用户的默认任务列表ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** 源列表 - 移动操作中的源任务列表ID */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** 目标列表 - 移动操作中的目标任务列表ID */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** 目标父节点 - 移动操作中的目标父节点ID */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** 目标父节点类型 - 目标父节点的类型（GROUP） */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** 是否获取已删除 - 请求时是否包含已删除的资源 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** 任务列表 - 任务列表数组 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** 任务 - 任务数组 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** 类型 - 类型字段 */
    public final static String GTASK_JSON_TYPE = "type";

    /** 子实体 - 子实体数组 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** 用户 - 用户信息 */
    public final static String GTASK_JSON_USER = "user";

    /** 最新同步点 - 最新同步位置标识 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    // ==================== 操作类型常量 ====================

    /** 创建操作 - 用于创建新任务或任务列表 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 获取全部操作 - 用于获取指定列表中的所有任务 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 移动操作 - 用于移动任务到不同位置或不同列表 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 更新操作 - 用于更新任务或任务列表的属性 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // ==================== 实体类型常量 ====================

    /** 分组类型 - 对应任务列表（文件夹） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 任务类型 - 对应具体的任务（便签） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    // ==================== 文件夹相关常量 ====================

    /**
     * MIUI 文件夹前缀
     * 用于标识由小米便签创建的文件夹，以便在同步时识别
     */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /**
     * 默认文件夹名称
     * 存放普通便签的默认文件夹
     */
    public final static String FOLDER_DEFAULT = "Default";

    /**
     * 通话便签文件夹名称
     * 专门存放通话记录便签的文件夹
     */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /**
     * 元数据文件夹名称
     * 存放同步元数据信息的特殊文件夹
     */
    public final static String FOLDER_META = "METADATA";

    // ==================== 元数据相关常量 ====================

    /**
     * 元数据头部 - GTask ID 键名
     * 存储在元数据JSON中的Google Tasks ID字段名
     */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /**
     * 元数据头部 - 便签信息键名
     * 元数据JSON中存储便签信息的字段名
     */
    public final static String META_HEAD_NOTE = "meta_note";

    /**
     * 元数据头部 - 数据信息键名
     * 元数据JSON中存储数据信息的字段名
     */
    public final static String META_HEAD_DATA = "meta_data";

    /**
     * 元数据便签名称
     * 用于存储元数据的特殊便签的名称
     * 注释提示：不要更新和删除此便签
     */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}