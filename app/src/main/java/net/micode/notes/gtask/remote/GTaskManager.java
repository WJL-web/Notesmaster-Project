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

/*
 * 文件名: GTaskManager.java
 * 功能: Google Task同步管理器，负责小米便签与Google Task云端数据的双向同步
 * 作者: The MiCode Open Source Community
 * 创建时间: 2010-2011
 * 修改记录: 实现了完整的同步逻辑，包括冲突检测、数据映射和批量操作
 *
 * 核心功能:
 * 1. 同步流程管理: 控制完整的同步过程，包括登录、数据拉取、同步、冲突解决
 * 2. 数据映射: 维护本地笔记ID与Google Task任务ID的映射关系
 * 3. 冲突解决: 检测本地和云端数据变更，执行相应的同步操作
 * 4. 错误处理: 处理网络错误、内部错误和用户取消操作
 *
 * 版权声明:
 * 遵循Apache License, Version 2.0开源协议
 * 许可证详情: http://www.apache.org/licenses/LICENSE-2.0
 */

// 包声明: Google Task远程同步管理器
package net.micode.notes.gtask.remote;

// Android框架导入
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

// 项目内部导入
import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

// JSON处理库
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Java集合类
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * 类名: GTaskManager
 * 描述: Google Task同步管理器，单例模式，负责协调本地笔记与云端Google Task的完整同步流程
 *
 * 主要职责:
 * 1. 同步状态管理: 跟踪同步进度，处理取消操作
 * 2. 数据映射维护: 维护本地ID与云端GID的双向映射关系
 * 3. 冲突检测与解决: 识别本地和云端的变更，执行相应的同步操作
 * 4. 批量操作协调: 协调多个创建、更新、删除操作，确保数据一致性
 * 5. 元数据管理: 管理同步相关的元数据(最后修改时间、删除状态等)
 *
 * 同步策略: 采用双向同步策略，支持以下同步操作类型
 * 1. 添加本地节点: 云端有，本地没有
 * 2. 添加远程节点: 本地有，云端没有
 * 3. 删除本地节点: 云端已删除，本地需要删除
 * 4. 删除远程节点: 本地已删除，云端需要删除
 * 5. 更新本地节点: 云端有更新，本地需要更新
 * 6. 更新远程节点: 本地有更新，云端需要更新
 * 7. 冲突更新: 本地和云端都有更新，目前简单使用本地更新覆盖
 *
 * 技术架构:
 * 1. 单例模式: 确保全局只有一个同步管理器实例
 * 2. 状态机模式: 管理同步的各种状态(进行中、成功、失败、取消)
 * 3. 观察者模式: 通过回调通知同步进度
 * 4. 数据映射表: 使用HashMap维护ID映射关系
 *
 * 注意事项:
 * 1. 同步是耗时的网络操作，必须在后台线程执行
 * 2. 支持取消操作，用户可以在同步过程中取消
 * 3. 需要处理各种异常情况: 网络中断、数据格式错误、权限问题等
 * 4. 同步过程需要保持本地数据的完整性
 */
public class GTaskManager {
    /** 日志标签: 用于调试和错误追踪 */
    private static final String TAG = GTaskManager.class.getSimpleName();

    /** 同步状态常量定义 */
    public static final int STATE_SUCCESS = 0;            // 同步成功
    public static final int STATE_NETWORK_ERROR = 1;      // 网络错误
    public static final int STATE_INTERNAL_ERROR = 2;     // 内部错误(数据、逻辑等)
    public static final int STATE_SYNC_IN_PROGRESS = 3;   // 同步正在进行中
    public static final int STATE_SYNC_CANCELLED = 4;     // 同步被用户取消

    /** 单例实例: 确保全局只有一个GTaskManager实例 */
    private static GTaskManager mInstance = null;

    /** Activity上下文: 用于获取Google账户认证令牌 */
    private Activity mActivity;

    /** 应用上下文: 用于访问内容解析器和其他系统服务 */
    private Context mContext;

    /** 内容解析器: 用于访问本地笔记数据库 */
    private ContentResolver mContentResolver;

    /** 同步状态标志: 表示同步是否正在进行中 */
    private boolean mSyncing;

    /** 取消标志: 表示用户是否取消了同步操作 */
    private boolean mCancelled;

    /** 任务列表HashMap: 存储从云端获取的任务列表，key为任务列表GID */
    private HashMap<String, TaskList> mGTaskListHashMap;

    /** 任务HashMap: 存储从云端获取的所有任务(包括任务列表和任务)，key为节点GID */
    private HashMap<String, Node> mGTaskHashMap;

    /** 元数据HashMap: 存储元数据，key为相关任务的GID */
    private HashMap<String, MetaData> mMetaHashMap;

    /** 元数据列表: 用于存储同步相关的元数据(最后修改时间等) */
    private TaskList mMetaList;

    /** 本地删除ID集合: 存储需要从本地删除的笔记ID */
    private HashSet<Long> mLocalDeleteIdMap;

    /** GID到本地ID的映射: 云端GID -> 本地笔记ID */
    private HashMap<String, Long> mGidToNid;

    /** 本地ID到GID的映射: 本地笔记ID -> 云端GID */
    private HashMap<Long, String> mNidToGid;

    /**
     * 私有构造方法: 初始化GTaskManager实例
     * 单例模式的一部分，确保只能通过getInstance()获取实例
     */
    private GTaskManager() {
        mSyncing = false;                     // 初始状态: 未同步
        mCancelled = false;                   // 初始状态: 未取消
        mGTaskListHashMap = new HashMap<String, TaskList>();  // 初始化任务列表映射
        mGTaskHashMap = new HashMap<String, Node>();          // 初始化任务映射
        mMetaHashMap = new HashMap<String, MetaData>();       // 初始化元数据映射
        mMetaList = null;                     // 元数据列表初始为null
        mLocalDeleteIdMap = new HashSet<Long>();              // 初始化本地删除ID集合
        mGidToNid = new HashMap<String, Long>();              // 初始化GID到本地ID映射
        mNidToGid = new HashMap<Long, String>();              // 初始化本地ID到GID映射
    }

    /**
     * 方法名: getInstance
     * 功能: 获取GTaskManager的单例实例(线程安全)
     * 设计模式: 懒汉式单例模式
     *
     * @return GTaskManager的唯一实例
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 方法名: setActivityContext
     * 功能: 设置Activity上下文，用于获取Google账户认证令牌
     * 注意: 必须在同步前调用，否则无法获取认证令牌
     *
     * @param activity 用于获取认证令牌的Activity
     */
    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;  // 保存Activity引用，用于获取认证令牌
    }

    /**
     * 方法名: sync
     * 功能: 执行完整的同步流程(核心方法)
     * 同步流程:
     * 1. 检查是否已有同步在进行，避免重复同步
     * 2. 初始化同步状态和数据结构
     * 3. 登录Google Task服务
     * 4. 初始化云端任务列表
     * 5. 执行内容同步(双向同步)
     * 6. 清理资源，返回同步结果
     *
     * @param context 应用上下文，用于访问内容解析器等
     * @param asyncTask 异步任务对象，用于发布同步进度
     * @return 同步结果状态码(成功、网络错误、内部错误、取消)
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 检查是否已有同步在进行
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;  // 返回同步进行中状态
        }

        // 初始化同步状态和数据
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;     // 标记同步开始
        mCancelled = false;  // 重置取消标志

        // 清空所有数据映射
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();  // 重置客户端的更新数组

            // 步骤1: 登录Google Task服务(检查取消标志)
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 步骤2: 从云端获取任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 步骤3: 执行内容同步(双向同步)
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();

        } catch (NetworkFailureException e) {
            // 网络异常处理
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            // 操作失败异常处理
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            // 其他异常处理
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 最终清理: 清空所有数据映射，重置同步状态
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;  // 标记同步结束
        }

        // 根据取消标志返回相应的状态
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 方法名: initGTaskList
     * 功能: 初始化云端任务列表
     * 流程:
     * 1. 获取云端所有任务列表
     * 2. 查找并初始化元数据列表(存储同步元数据)
     * 3. 加载元数据列表中的元数据
     * 4. 如果没有元数据列表，则创建一个
     * 5. 初始化其他任务列表(排除元数据列表)
     * 6. 加载每个任务列表中的任务
     *
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled) return;  // 检查取消标志

        GTaskClient client = GTaskClient.getInstance();
        try {
            // 获取云端所有任务列表
            JSONArray jsTaskLists = client.getTaskLists();

            // 步骤1: 首先初始化元数据列表
            mMetaList = null;  // 重置元数据列表
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 查找元数据列表(名称以特定前缀开头)
                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);  // 设置任务列表内容

                    // 加载元数据列表中的元数据
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);

                        // 只保存有意义的元数据
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 步骤2: 如果没有元数据列表，则创建一个
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // 步骤3: 初始化其他任务列表(排除元数据列表)
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 只处理以特定前缀开头的任务列表(排除元数据列表)
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX) &&
                        !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {

                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);

                    // 保存到任务列表映射
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该任务列表中的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);

                        Task task = new Task();
                        task.setContentByRemoteJSON(object);

                        // 只保存有意义的任务
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));  // 设置元数据
                            tasklist.addChildTask(task);             // 添加到任务列表
                            mGTaskHashMap.put(gid, task);            // 保存到任务映射
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 方法名: syncContent
     * 功能: 执行内容同步(双向同步)
     * 同步策略: 采用双向同步，处理以下情况
     * 1. 本地已删除的笔记 -> 从云端删除
     * 2. 文件夹同步(优先同步文件夹，确保笔记有正确的父文件夹)
     * 3. 现有笔记同步(本地有，云端也有)
     * 4. 远程新增的笔记 -> 添加到本地
     * 5. 批量删除本地已标记删除的笔记
     * 6. 刷新本地同步ID
     *
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;  // 同步类型
        Cursor c = null;
        String gid;
        Node node;

        // 清空本地删除ID集合
        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;  // 检查取消标志
        }

        // 步骤1: 处理本地已删除的笔记(在回收站中)
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)",  // 排除系统类型且在回收站中
                    new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM),
                            String.valueOf(Notes.ID_TRASH_FOLER)
                    },
                    null);

            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 本地已删除，但云端还存在，需要从云端删除
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }

                    // 添加到本地删除集合
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 步骤2: 优先同步文件夹(确保笔记有正确的父文件夹)
        syncFolder();

        // 步骤3: 同步现有笔记(本地数据库中的笔记，排除回收站)
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)",  // 笔记类型且不在回收站中
                    new String[] {
                            String.valueOf(Notes.TYPE_NOTE),
                            String.valueOf(Notes.ID_TRASH_FOLER)
                    },
                    NoteColumns.TYPE + " DESC");  // 按类型降序排序

            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 笔记在本地和云端都存在
                        mGTaskHashMap.remove(gid);

                        // 建立ID映射关系
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);

                        // 获取同步类型(本地更新、云端更新、冲突等)
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增的笔记(GID为空)
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 云端已删除的笔记
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }

                    // 执行同步操作
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 步骤4: 处理云端新增的笔记(在mGTaskHashMap中剩余的节点)
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // 步骤5: 批量删除本地已标记删除的笔记(检查取消标志)
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // 步骤6: 刷新本地同步ID(最后修改时间)(检查取消标志)
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();  // 提交所有未提交的更新
            refreshLocalSyncId();  // 刷新本地同步ID
        }
    }

    /**
     * 方法名: syncFolder
     * 功能: 同步文件夹(特殊处理)
     * 文件夹类型:
     * 1. 根文件夹(Notes.ID_ROOT_FOLDER)
     * 2. 通话记录文件夹(Notes.ID_CALL_RECORD_FOLDER)
     * 3. 普通文件夹
     * 特殊处理: 系统文件夹只更新远程名称(如果需要)
     *
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;  // 检查取消标志
        }

        // 步骤1: 同步根文件夹
        try {
            c = mContentResolver.query(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, Notes.ID_ROOT_FOLDER),
                    SqlNote.PROJECTION_NOTE, null, null, null);

            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);

                if (node != null) {
                    // 根文件夹在云端存在
                    mGTaskHashMap.remove(gid);

                    // 建立ID映射
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);

                    // 对于系统文件夹，只更新远程名称(如果需要)
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    }
                } else {
                    // 根文件夹在云端不存在，添加到云端
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 步骤2: 同步通话记录文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    SqlNote.PROJECTION_NOTE,
                    "(_id=?)",  // 根据ID查询
                    new String[] { String.valueOf(Notes.ID_CALL_RECORD_FOLDER) },
                    null);

            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 通话记录文件夹在云端存在
                        mGTaskHashMap.remove(gid);

                        // 建立ID映射
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);

                        // 对于系统文件夹，只更新远程名称(如果需要)
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                        }
                    } else {
                        // 通话记录文件夹在云端不存在，添加到云端
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 步骤3: 同步普通文件夹(本地数据库中存在的文件夹，排除回收站)
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)",  // 文件夹类型且不在回收站中
                    new String[] {
                            String.valueOf(Notes.TYPE_FOLDER),
                            String.valueOf(Notes.ID_TRASH_FOLER)
                    },
                    NoteColumns.TYPE + " DESC");  // 按类型降序排序

            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 文件夹在云端存在
                        mGTaskHashMap.remove(gid);

                        // 建立ID映射
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);

                        // 获取同步类型
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增的文件夹(GID为空)
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 云端已删除的文件夹
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }

                    // 执行同步操作
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 步骤4: 处理云端新增的文件夹(在mGTaskListHashMap中剩余的文件夹)
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();

            if (mGTaskHashMap.containsKey(gid)) {
                // 文件夹在云端存在但本地不存在
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 提交所有未提交的更新(检查取消标志)
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
        }
    }

    /**
     * 方法名: doContentSync
     * 功能: 根据同步类型执行具体的同步操作
     * 同步类型定义:
     * 1. SYNC_ACTION_ADD_LOCAL: 添加本地节点(云端有，本地没有)
     * 2. SYNC_ACTION_ADD_REMOTE: 添加远程节点(本地有，云端没有)
     * 3. SYNC_ACTION_DEL_LOCAL: 删除本地节点(云端已删除)
     * 4. SYNC_ACTION_DEL_REMOTE: 删除远程节点(本地已删除)
     * 5. SYNC_ACTION_UPDATE_LOCAL: 更新本地节点(云端有更新)
     * 6. SYNC_ACTION_UPDATE_REMOTE: 更新远程节点(本地有更新)
     * 7. SYNC_ACTION_UPDATE_CONFLICT: 冲突更新(两端都有更新)，目前简单使用本地更新
     * 8. SYNC_ACTION_NONE: 无操作
     * 9. SYNC_ACTION_ERROR: 错误
     *
     * @param syncType 同步类型
     * @param node 节点对象(可能为null)
     * @param c 数据库游标(可能为null)
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        MetaData meta;

        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                // 添加本地节点: 云端有，本地没有
                addLocalNode(node);
                break;

            case Node.SYNC_ACTION_ADD_REMOTE:
                // 添加远程节点: 本地有，云端没有
                addRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_DEL_LOCAL:
                // 删除本地节点: 云端已删除
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);  // 删除元数据
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));  // 添加到删除集合
                break;

            case Node.SYNC_ACTION_DEL_REMOTE:
                // 删除远程节点: 本地已删除
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);  // 删除元数据
                }
                GTaskClient.getInstance().deleteNode(node);  // 删除节点
                break;

            case Node.SYNC_ACTION_UPDATE_LOCAL:
                // 更新本地节点: 云端有更新
                updateLocalNode(node, c);
                break;

            case Node.SYNC_ACTION_UPDATE_REMOTE:
                // 更新远程节点: 本地有更新
                updateRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 冲突更新: 两端都有更新，目前简单使用本地更新覆盖
                // 更复杂的实现可以合并更改或让用户选择
                updateRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_NONE:
                // 无操作
                break;

            case Node.SYNC_ACTION_ERROR:
            default:
                // 未知的同步类型，抛出异常
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 方法名: addLocalNode
     * 功能: 添加本地节点(云端有，本地没有)
     * 流程:
     * 1. 创建SqlNote对象
     * 2. 设置内容(从节点获取本地JSON)
     * 3. 设置父文件夹ID(通过映射查找)
     * 4. 设置GTask ID
     * 5. 提交到本地数据库
     * 6. 更新ID映射
     * 7. 更新远程元数据
     *
     * @param node 要添加的节点
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        SqlNote sqlNote;

        if (node instanceof TaskList) {
            // 处理任务列表(文件夹)
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                // 默认文件夹(根文件夹)
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                // 通话记录文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                // 普通文件夹
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());  // 设置内容
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);  // 父文件夹为根文件夹
            }
        } else {
            // 处理任务(笔记)
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();

            try {
                // 处理笔记ID冲突: 如果ID已存在，则创建新ID
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // ID已存在，移除ID，让系统生成新ID
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                // 处理数据ID冲突: 如果数据ID已存在，则创建新ID
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // 数据ID已存在，移除ID
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }

            sqlNote.setContent(js);  // 设置内容

            // 查找父文件夹ID(通过映射)
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());  // 设置父文件夹ID
        }

        // 设置GTask ID
        sqlNote.setGtaskId(node.getGid());

        // 提交到本地数据库
        sqlNote.commit(false);

        // 更新ID映射
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 方法名: updateLocalNode
     * 功能: 更新本地节点(云端有更新，本地需要更新)
     * 流程:
     * 1. 创建SqlNote对象(基于现有游标)
     * 2. 设置新内容(从节点获取本地JSON)
     * 3. 设置父文件夹ID(通过映射查找)
     * 4. 提交更新到本地数据库
     * 5. 更新远程元数据
     *
     * @param node 要更新的节点
     * @param c 数据库游标(指向要更新的本地笔记)
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        SqlNote sqlNote;

        // 创建SqlNote对象(基于现有游标)
        sqlNote = new SqlNote(mContext, c);

        // 设置新内容
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 查找父文件夹ID(通过映射)
        Long parentId = (node instanceof Task) ?
                mGidToNid.get(((Task) node).getParent().getGid()) :
                new Long(Notes.ID_ROOT_FOLDER);

        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }

        sqlNote.setParentId(parentId.longValue());  // 设置父文件夹ID

        // 提交更新到本地数据库
        sqlNote.commit(true);

        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 方法名: addRemoteNode
     * 功能: 添加远程节点(本地有，云端没有)
     * 流程:
     * 1. 创建SqlNote对象(基于现有游标)
     * 2. 根据笔记类型创建任务或任务列表
     * 3. 设置内容(从本地JSON)
     * 4. 添加到云端的父任务列表
     * 5. 在云端创建任务/任务列表
     * 6. 更新本地笔记的GTask ID
     * 7. 重置本地修改标志
     * 8. 更新ID映射
     *
     * @param node 节点对象(可能为null，表示本地新增)
     * @param c 数据库游标(指向要添加的本地笔记)
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;  // 创建的节点

        // 根据笔记类型处理
        if (sqlNote.isNoteType()) {
            // 处理笔记(任务)
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());  // 设置内容

            // 查找父任务列表GID
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }

            // 添加到父任务列表
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            // 在云端创建任务
            GTaskClient.getInstance().createTask(task);

            n = (Node) task;  // 保存创建的节点

            // 更新远程元数据
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // 处理文件夹(任务列表)
            TaskList tasklist = null;

            // 构建文件夹名称
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_DEFAULT;  // 默认文件夹
            } else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;  // 通话记录文件夹
            } else {
                folderName += sqlNote.getSnippet();  // 普通文件夹
            }

            // 检查文件夹是否已存在(通过名称匹配)
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;  // 找到匹配的文件夹
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);  // 从任务映射中移除
                    }
                    break;
                }
            }

            // 如果没有匹配的文件夹，则创建新文件夹
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());  // 设置内容

                // 在云端创建任务列表
                GTaskClient.getInstance().createTaskList(tasklist);

                // 保存到任务列表映射
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }

            n = (Node) tasklist;  // 保存创建的节点
        }

        // 更新本地笔记的GTask ID
        sqlNote.setGtaskId(n.getGid());

        // 提交到本地数据库
        sqlNote.commit(false);

        // 重置本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // 更新ID映射
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 方法名: updateRemoteNode
     * 功能: 更新远程节点(本地有更新，云端需要更新)
     * 流程:
     * 1. 创建SqlNote对象(基于现有游标)
     * 2. 设置节点的新内容(从本地JSON)
     * 3. 添加更新操作到客户端的更新数组
     * 4. 更新远程元数据
     * 5. 如果笔记移动了文件夹，则移动任务
     * 6. 重置本地修改标志
     *
     * @param node 要更新的节点
     * @param c 数据库游标(指向要更新的本地笔记)
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // 设置节点的新内容
        node.setContentByLocalJSON(sqlNote.getContent());

        // 添加更新操作到客户端的更新数组
        GTaskClient.getInstance().addUpdateNode(node);

        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // 如果笔记移动了文件夹，则移动任务
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();  // 原来的父任务列表

            // 查找新的父文件夹GID
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }

            TaskList curParentList = mGTaskListHashMap.get(curParentGid);  // 新的父任务列表

            // 如果父任务列表发生了变化
            if (preParentList != curParentList) {
                // 从原来的父任务列表中移除
                preParentList.removeChildTask(task);

                // 添加到新的父任务列表中
                curParentList.addChildTask(task);

                // 在云端移动任务
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 重置本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 方法名: updateRemoteMeta
     * 功能: 更新远程元数据
     * 元数据包含: 最后修改时间、删除状态等同步相关信息
     * 流程:
     * 1. 检查是否为笔记类型(只有笔记需要元数据)
     * 2. 查找现有的元数据
     * 3. 如果存在，则更新并添加到更新数组
     * 4. 如果不存在，则创建新的元数据并添加到元数据列表
     *
     * @param gid 节点的GID
     * @param sqlNote SqlNote对象
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        // 只有笔记类型需要元数据
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);

            if (metaData != null) {
                // 更新现有元数据
                metaData.setMeta(gid, sqlNote.getContent());

                // 添加到客户端的更新数组
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // 创建新的元数据
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());

                // 添加到元数据列表
                mMetaList.addChildTask(metaData);

                // 保存到元数据映射
                mMetaHashMap.put(gid, metaData);

                // 在云端创建元数据
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 方法名: refreshLocalSyncId
     * 功能: 刷新本地同步ID(最后修改时间)
     * 同步ID用于记录笔记的最后同步时间，下次同步时用于检测变更
     * 流程:
     * 1. 重新初始化云端任务列表(获取最新的修改时间)
     * 2. 遍历本地笔记(排除系统文件夹和回收站)
     * 3. 更新本地笔记的同步ID为云端的最新修改时间
     *
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;  // 检查取消标志
        }

        // 清空数据结构，重新初始化云端任务列表
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 查询本地笔记(排除系统文件夹和回收站)
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)",  // 排除系统类型且不在回收站
                    new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM),
                            String.valueOf(Notes.ID_TRASH_FOLER)
                    },
                    NoteColumns.TYPE + " DESC");  // 按类型降序排序

            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 找到对应的云端节点
                        mGTaskHashMap.remove(gid);

                        // 准备更新内容
                        ContentValues values = new ContentValues();

                        // 设置同步ID为云端节点的最后修改时间
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());

                        // 更新本地数据库
                        mContentResolver.update(
                                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                        c.getLong(SqlNote.ID_COLUMN)),
                                values, null, null);
                    } else {
                        // 本地笔记在云端找不到对应项，抛出异常
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 方法名: getSyncAccount
     * 功能: 获取当前同步的账户名称
     *
     * @return 同步账户的名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 方法名: cancelSync
     * 功能: 取消正在进行的同步操作
     * 机制: 设置取消标志，同步循环会检查此标志并退出
     */
    public void cancelSync() {
        mCancelled = true;  // 设置取消标志
    }
}