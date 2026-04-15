/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, ");
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
 * 谷歌任务同步 顶层抽象基类（Node：节点）
 * 功能：定义【任务/列表/元数据】的公共属性 + 同步行为规范
 * 地位：所有同步数据类的父类（Task、MetaData、TaskList 都继承它）
 * 特点：抽象类，不能直接实例化，必须由子类实现具体逻辑
 */
public abstract class Node {

    // ====================== 同步动作常量定义（核心：同步要做什么）======================
    /** 无操作，无需同步 */
    public static final int SYNC_ACTION_NONE = 0;
    /** 需要在【远程谷歌】新增（本地有，云端无） */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;
    /** 需要在【本地】新增（云端有，本地无） */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;
    /** 需要从【远程谷歌】删除（本地删了，云端要同步删除） */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;
    /** 需要从【本地】删除（云端删了，本地要同步删除） */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;
    /** 需要【更新远程谷歌】（本地新，云端旧） */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;
    /** 需要【更新本地数据】（云端新，本地旧） */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;
    /** 同步冲突（本地云端都修改，需要处理冲突） */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;
    /** 同步出错 / 异常 */
    public static final int SYNC_ACTION_ERROR = 8;

    // ====================== 所有同步节点共有的基础字段 ======================
    /** 谷歌任务唯一ID（全局唯一标识，云端分配） */
    private String mGid;
    /** 节点名称（任务标题 / 列表名称） */
    private String mName;
    /** 最后修改时间（用于判断谁更新：时间大=最新） */
    private long mLastModified;
    /** 是否已删除（标记删除，不直接物理删除） */
    private boolean mDeleted;

    /**
     * 构造方法：初始化默认值
     */
    public Node() {
        mGid = null;             // 刚创建没有云端ID
        mName = "";              // 名称为空
        mLastModified = 0;       // 初始时间为0
        mDeleted = false;        // 默认未删除
    }

    // ====================== 子类必须实现的抽象方法（同步核心规范）======================
    /**
     * 生成【云端创建】的JSON请求
     * @param actionId 动作ID
     * @return 符合谷歌API格式的JSON
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成【云端更新】的JSON请求
     * @param actionId 动作ID
     * @return 符合谷歌API格式的JSON
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从【远程JSON】解析数据到本地对象
     * 作用：云端 → 本地
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从【本地JSON】解析数据到对象
     * 作用：本地数据 → 同步对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将对象内容转为【本地JSON】
     * 作用：同步对象 → 存数据库
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 计算同步动作（判断该增/删/改/冲突）
     * @param c 本地数据库游标
     * @return 同步动作常量（SYNC_ACTION_XXX）
     */
    public abstract int getSyncAction(Cursor c);

    // ====================== Getter & Setter 统一封装 ======================
    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }

}