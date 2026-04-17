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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Google Task 同步核心管理类（单例模式）
 * 功能：实现本地便签/文件夹 与 Google Task 云端的双向同步、数据映射、冲突处理
 * 核心流程：登录Google → 拉取云端任务列表 → 同步本地/云端数据 → 提交变更 → 更新本地ID
 */
public class GTaskManager {
    // 日志TAG
    private static final String TAG = GTaskManager.class.getSimpleName();

    // -------------------------- 同步状态常量定义 --------------------------
    public static final int STATE_SUCCESS = 0;                    // 同步成功
    public static final int STATE_NETWORK_ERROR = 1;              // 网络错误
    public static final int STATE_INTERNAL_ERROR = 2;             // 内部逻辑错误
    public static final int STATE_SYNC_IN_PROGRESS = 3;           // 同步正在进行中
    public static final int STATE_SYNC_CANCELLED = 4;             // 同步已取消

    // -------------------------- 单例对象 --------------------------
    private static GTaskManager mInstance = null;

    // -------------------------- 上下文相关变量 --------------------------
    private Activity mActivity;                                   // 用于获取Google认证Token
    private Context mContext;                                     // 上下文
    private ContentResolver mContentResolver;                     // 内容解析器，操作本地数据库

    // -------------------------- 同步状态控制变量 --------------------------
    private boolean mSyncing;                                     // 是否正在同步
    private boolean mCancelled;                                   // 是否取消同步

    // -------------------------- 数据缓存映射 --------------------------
    private HashMap<String, TaskList> mGTaskListHashMap;          // 云端任务列表（文件夹）集合 <Gid, TaskList>
    private HashMap<String, Node> mGTaskHashMap;                  // 云端所有任务/文件夹节点集合 <Gid, Node>
    private HashMap<String, MetaData> mMetaHashMap;                // 元数据映射 <关联Gid, MetaData>
    private TaskList mMetaList;                                   // 存储元数据的专用任务列表
    private HashSet<Long> mLocalDeleteIdMap;                      // 本地需要删除的便签ID集合
    private HashMap<String, Long> mGidToNid;                      // 云端Gid → 本地数据库Nid
    private HashMap<Long, String> mNidToGid;                      // 本地数据库Nid → 云端Gid

    /**
     * 私有构造方法（单例模式，禁止外部new对象）
     * 初始化所有集合和状态变量
     */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /**
     * 获取单例实例（线程安全）
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 设置Activity上下文，用于Google账号登录授权
     */
    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;
    }

    /**
     * 【核心同步入口方法】
     * 执行完整的Google Task同步流程
     * @param context 上下文
     * @param asyncTask 异步任务，用于更新同步进度
     * @return 同步状态码
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 如果正在同步，直接返回进行中状态
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }

        // 初始化同步环境
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;

        // 清空所有缓存数据
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // 1. 登录Google Task（未取消同步时执行）
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 2. 初始化云端任务列表（拉取云端数据）
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 3. 执行核心内容同步（本地↔云端）
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();

        } catch (NetworkFailureException e) {
            // 网络异常
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            // 业务逻辑异常
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            // 其他未知异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 无论成功失败，清空缓存+重置同步状态
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        // 根据是否取消，返回对应状态
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化云端任务列表
     * 1. 拉取Google Task所有任务列表
     * 2. 初始化元数据列表（存储便签额外信息）
     * 3. 加载小米便签专属的任务列表/任务
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            // 获取云端所有任务列表
            JSONArray jsTaskLists = client.getTaskLists();

            // 第一步：初始化【元数据列表】（存储便签的额外同步信息）
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 找到小米便签的元数据专用列表
                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载元数据列表中的所有元数据
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 如果没有元数据列表，自动创建一个
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // 第二步：初始化【小米便签业务任务列表/任务】
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 只处理小米便签专属的任务列表（排除元数据列表）
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该列表下的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            // JSON解析异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 【核心】同步本地与云端的内容数据
     * 流程：处理回收站删除数据 → 同步文件夹 → 同步便签 → 处理剩余数据 → 提交变更
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // 1. 处理【本地回收站】中的便签：同步删除云端数据
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        // 执行：删除云端节点
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
                    // 记录本地需要删除的ID
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

        // 2. 优先同步【文件夹】（文件夹是便签的父节点）
        syncFolder();

        // 3. 同步【本地已存在的便签】
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 云端存在该数据：更新映射关系
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        // 获取同步类型（更新/无操作/冲突）
                        syncType = node.getSyncAction(c);
                    } else {
                        // 云端不存在：判断是本地新增 或 云端删除
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE; // 本地新增→同步到云端
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL; // 云端删除→删除本地
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

        // 4. 处理【云端剩余数据】：云端有、本地无 → 新增到本地
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // 5. 批量删除【本地标记删除】的便签
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // 6. 提交云端变更 + 刷新本地同步ID
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }

    }

    /**
     * 同步文件夹（根文件夹、通话记录文件夹、自定义文件夹）
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // 1. 同步【根文件夹】
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 系统文件夹：仅在名称不一致时更新云端
                    if (!node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    // 云端无则创建
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

        // 2. 同步【通话记录文件夹】
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        if (!node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
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

        // 3. 同步【本地自定义文件夹】
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE; // 本地新增→云端
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL; // 云端删除→本地
                        }
                    }
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

        // 4. 同步【云端新增文件夹】→ 添加到本地
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 提交文件夹变更
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 执行具体的同步操作（分发不同同步类型）
     * @param syncType 同步类型（新增/删除/更新/冲突等）
     * @param node 云端节点
     * @param c 本地数据游标
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:       // 云端→本地：新增
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:     // 本地→云端：新增
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:      // 云端删除→删除本地
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:      // 本地删除→删除云端
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:    // 云端→本地：更新
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:   // 本地→云端：更新
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:// 同步冲突：默认以本地为准
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:            // 无操作
                break;
            case Node.SYNC_ACTION_ERROR:           // 异常
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 将云端节点【添加到本地】数据库
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 处理文件夹节点
        if (node instanceof TaskList) {
            if (node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 处理便签节点
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 校验ID唯一性，避免冲突
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 设置父文件夹ID
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // 保存到本地数据库
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // 更新ID映射关系
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // 更新云端元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 用云端数据【更新本地】便签/文件夹
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 设置父节点
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 将本地数据【添加到云端】Google Task
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // 同步便签到云端
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            // 获取父文件夹GID
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            // 创建云端任务
            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // 更新元数据
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // 同步文件夹到云端
            TaskList tasklist = null;
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            // 拼接系统文件夹名称
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            // 检查云端是否已存在同名文件夹
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();
                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // 不存在则创建
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // 更新本地数据的GID
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // 更新ID映射
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 用本地数据【更新云端】Google Task
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // 更新云端节点内容
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // 便签需要处理【移动文件夹】逻辑
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            // 父文件夹不一致：移动云端任务
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 清除本地修改标记
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 更新云端【元数据】（存储便签的额外同步信息）
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                // 更新已有元数据
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // 创建新元数据
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 同步完成后：刷新本地数据库的同步ID（最后修改时间）
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 重新拉取最新云端数据
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        // 更新本地同步ID为云端最后修改时间
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException("some local items don't have gid after sync");
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
     * 获取当前同步的Google账号名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消正在进行的同步
     */
    public void cancelSync() {
        mCancelled = true;
    }
}