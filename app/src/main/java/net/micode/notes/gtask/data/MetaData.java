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
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 谷歌任务 元数据类
 * 功能：专门用于保存【小米便签 ↔ 谷歌任务】之间的关联信息（特殊任务）
 * 作用：记录每个便签对应的 谷歌任务ID（gid），实现双向同步
 * 特点：继承自 Task，但不存储实际任务内容，只存关联关系
 */
public class MetaData extends Task {
    // 日志TAG
    private final static String TAG = MetaData.class.getSimpleName();

    // 关联的谷歌任务 gid（核心：记录这个元数据对应哪个谷歌任务）
    private String mRelatedGid = null;

    /**
     * 设置元数据（将便签与谷歌任务关联）
     * @param gid 谷歌任务的唯一ID
     * @param metaInfo 元数据JSON对象（存放关联信息）
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将谷歌任务ID存入JSON
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid"); // 记录失败日志
        }
        // 把JSON字符串存入 Task 的 notes 字段（复用父类字段）
        setNotes(metaInfo.toString());
        // 设置元数据名称（固定标识：区分普通任务和元数据）
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取关联的谷歌任务ID
     * @return gid
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断是否值得保存（覆盖父类方法）
     * 规则：只要 notes（元数据JSON）不为空，就值得保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程谷歌任务JSON解析元数据（覆盖父类）
     * 作用：同步时，从云端解析出【便签 ↔ 任务】的关联关系
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        // 先调用父类方法，解析基础任务信息
        super.setContentByRemoteJSON(js);

        // 如果有元数据内容
        if (getNotes() != null) {
            try {
                // 把 notes 字符串转为 JSON 对象
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                // 解析出关联的谷歌任务ID
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid"); // 解析失败
                mRelatedGid = null;
            }
        }
    }

    /**
     * 不支持本地JSON解析（元数据不从本地生成，强制禁止调用）
     * 调用直接抛异常，避免误用
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 不支持生成本地JSON（元数据不需要生成本地数据）
     * 调用直接抛异常
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 不支持同步动作判断（元数据不由同步逻辑直接处理）
     * 调用直接抛异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}