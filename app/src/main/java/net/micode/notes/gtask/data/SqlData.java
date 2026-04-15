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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 谷歌任务同步 - 数据库数据操作类
 * 功能：专门封装对【data 表】的 新增、修改、查询 操作
 * 作用：供同步模块使用，简化数据读写，自动处理差异更新
 * 对应数据库表：data（笔记内容表）
 */
public class SqlData {
    // 日志TAG
    private static final String TAG = SqlData.class.getSimpleName();

    // 无效ID常量（标记未赋值的ID）
    private static final int INVALID_ID = -99999;

    /**
     * 查询 data 表的投影（需要查询的字段）
     * 对应：ID、MIME类型、内容、扩展字段1、扩展字段3
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 投影数组中各字段的下标索引（方便快速取值）
    public static final int DATA_ID_COLUMN = 0;             // ID
    public static final int DATA_MIME_TYPE_COLUMN = 1;      // MIME类型
    public static final int DATA_CONTENT_COLUMN = 2;        // 内容
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3; // 扩展字段1
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4; // 扩展字段3

    // 内容解析器（操作 ContentProvider）
    private ContentResolver mContentResolver;

    // 是否为新建数据（true=未入库，需要insert；false=已存在，需要update）
    private boolean mIsCreate;

    // data 表主键ID
    private long mDataId;

    // 数据MIME类型（文本/通话/同步元数据）
    private String mDataMimeType;

    // 笔记内容
    private String mDataContent;

    // 扩展字段1（同步用）
    private long mDataContentData1;

    // 扩展字段3（同步用）
    private String mDataContentData3;

    // 记录数据差异（只更新变化的字段，优化性能）
    private ContentValues mDiffDataValues;

    /**
     * 构造方法1：创建【全新】数据（用于新增笔记内容）
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                // 标记为新建
        mDataId = INVALID_ID;            // 初始ID无效
        mDataMimeType = DataConstants.NOTE; // 默认文本笔记
        mDataContent = "";               // 内容为空
        mDataContentData1 = 0;           // 扩展字段默认0
        mDataContentData3 = "";          // 扩展字段默认空
        mDiffDataValues = new ContentValues(); // 初始化差异记录
    }

    /**
     * 构造方法2：从【数据库游标】加载数据（用于查询已有数据）
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;               // 标记为已存在
        loadFromCursor(c);              // 从游标读取数据
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从数据库游标中加载数据到成员变量
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从 JSONObject 设置数据（同步时：云端JSON → 本地对象）
     * 自动对比旧数据，只记录变化的字段到 mDiffDataValues
     */
    public void setContent(JSONObject js) throws JSONException {
        // 1. 设置 ID
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 2. 设置 MIME 类型
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 3. 设置内容
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 4. 设置扩展字段1
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 5. 设置扩展字段3
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前数据转为 JSONObject（本地对象 → 云端JSON）
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 提交数据到数据库（执行 insert 或 update）
     * @param noteId 所属笔记ID
     * @param validateVersion 是否校验版本号（同步防冲突）
     * @param version 版本号
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        // 情况1：新建数据 → 执行 insert
        if (mIsCreate) {
            // 如果是无效ID，移除ID字段（数据库自增）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 绑定所属笔记ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            // 插入数据库
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 获取新插入数据的ID
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        }
        // 情况2：已有数据 → 执行 update
        else {
            // 只有数据有变化时才更新
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                // 不校验版本 → 直接更新
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                }
                // 校验版本 → 版本一致才更新（同步冲突保护）
                else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清空差异记录，标记为已入库
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取 data 表ID
     */
    public long getId() {
        return mDataId;
    }
}