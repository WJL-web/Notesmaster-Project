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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import org.json.JSONObject;

/**
 * 节点抽象类，表示 GTasks 同步中的一个数据节点。

 * 该类定义了本地数据与远程 Google Tasks 服务之间同步的核心行为。
 * 具体的节点类型（如任务、列表等）需要继承此类并实现抽象方法。
 *
 * @author MiCode Open Source Community
 */
public abstract class Node {

    // ======================= 同步动作常量定义 =======================

    /** 无需执行任何同步操作 */
    public static final int SYNC_ACTION_NONE = 0;

    /** 需要在远程端添加数据（本地有，远程无） */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 需要在本地端添加数据（远程有，本地无） */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 需要删除远程端的数据（本地已删除） */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 需要删除本地端的数据（远程已删除） */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 需要用本地数据更新远程端 */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 需要用远程数据更新本地端 */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 存在数据冲突，需要人工处理或特殊合并策略 */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 同步过程中发生错误 */
    public static final int SYNC_ACTION_ERROR = 8;

    // ======================= 成员变量 =======================

    /** Google Tasks 服务中的全局唯一标识符 */
    private String mGid;

    /** 节点名称（如任务标题、列表名称） */
    private String mName;

    /** 最后修改时间戳（毫秒） */
    private long mLastModified;

    /** 标记是否已被删除（软删除标志） */
    private boolean mDeleted;

    /**
     * 构造方法。初始化一个空节点，所有字段设为默认值。
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // ======================= 抽象方法（子类必须实现） =======================

    /**
     * 获取用于创建节点的 JSON 动作对象。
     *
     * @param actionId 同步动作ID，取值为 SYNC_ACTION_ADD_REMOTE 或 SYNC_ACTION_ADD_LOCAL
     * @return 包含创建操作所需数据的 JSONObject
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 获取用于更新节点的 JSON 动作对象。
     *
     * @param actionId 同步动作ID，取值为 SYNC_ACTION_UPDATE_REMOTE 或 SYNC_ACTION_UPDATE_LOCAL
     * @return 包含更新操作所需数据的 JSONObject
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 根据远程 JSON 数据设置当前节点的内容。
     * 用于从 Google Tasks 服务拉取数据后更新本地节点。
     *
     * @param js 包含远程节点数据的 JSONObject
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 根据本地 JSON 数据设置当前节点的内容。
     * 用于从本地存储恢复节点数据。
     *
     * @param js 包含本地节点数据的 JSONObject
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前节点的内容转换为本地存储格式的 JSON 对象。
     *
     * @return 包含当前节点所有数据的 JSONObject
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据数据库游标判断当前节点需要执行哪种同步动作。
     * 子类需实现具体的冲突检测和同步策略逻辑。
     *
     * @param c 指向数据库当前行的 Cursor 对象
     * @return 同步动作常量（SYNC_ACTION_* 之一）
     */
    public abstract int getSyncAction(Cursor c);

    // ======================= Getter / Setter 方法 =======================

    /**
     * 设置节点的全局唯一标识符（GID）
     * @param gid Google Tasks 服务返回的唯一标识符
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 设置节点名称
     * @param name 节点名称（任务标题或列表名称）
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * 设置最后修改时间
     * @param lastModified 时间戳（毫秒）
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 设置删除标记
     * @param deleted true 表示已删除（软删除），false 表示未删除
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取节点的全局唯一标识符
     * @return GID 字符串，可能为 null
     */
    public String getGid() {
        return this.mGid;
    }

    /**
     * 获取节点名称
     * @return 节点名称
     */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取最后修改时间
     * @return 时间戳（毫秒）
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取删除标记状态
     * @return true 表示已标记为删除，false 表示未删除
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }
}