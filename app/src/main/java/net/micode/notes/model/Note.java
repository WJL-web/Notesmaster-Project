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

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记数据模型类
 * 负责管理笔记的创建、更新和同步操作
 * 包含笔记的基本信息和内容数据
 */

public class Note {
    // 笔记的基本信息（如标题、修改时间等）
    private ContentValues mNoteDiffValues;
    // 笔记的详细数据（文本内容、通话记录等）
    private NoteData mNoteData;
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     */

    /**
     * 创建新笔记ID
     * 在数据库中插入一条新笔记记录，返回自动生成的ID
     * @param context 上下文对象，用于访问ContentResolver
     * @param folderId 所属文件夹ID
     * @return 新创建的笔记ID
     */

    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        // 在数据库中创建新笔记
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();   // 获取当前时间作为创建时间
        values.put(NoteColumns.CREATED_DATE, createdTime);  // 设置创建时间
        values.put(NoteColumns.MODIFIED_DATE, createdTime);  // 设置修改时间
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);  // 设置笔记类型为普通笔记
        values.put(NoteColumns.LOCAL_MODIFIED, 1);  // 标记为本地已修改
        values.put(NoteColumns.PARENT_ID, folderId);  // 设置所属文件夹
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从URI中解析出笔记ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造函数
     * 初始化笔记基本信息和数据容器
     */
    public Note() {
        mNoteDiffValues = new ContentValues();  // 初始化笔记基本信息容器
        mNoteData = new NoteData();  // 初始化笔记数据容器
    }

    /**
     * 设置笔记的基本属性（如标题、颜色等）
     * @param key 属性键名
     * @param value 属性值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);  // 存储属性值
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);  // 标记为已修改
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());  // 更新修改时间
    }

    /**
     * 设置笔记的文本内容
     * @param key 文本数据键名
     * @param value 文本内容
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据的ID
     * @param id 文本数据在数据库中的ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据ID
     * @return 文本数据ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据ID
     * @param id 通话记录数据ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话记录数据
     * @param key 数据键名
     * @param value 数据值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查笔记是否在本地被修改过
     * @return true表示有修改，false表示无修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 同步笔记到数据库
     * 将内存中的修改持久化到数据库
     * @param context 上下文对象
     * @param noteId 笔记ID
     * @return true表示同步成功，false表示失败
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 如果没有修改，直接返回成功
        if (!isLocalModified()) {
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */

        /**
         * 理论上，数据变化后应该更新 LOCAL_MODIFIED 和 MODIFIED_DATE 字段
         * 为保证数据安全，即使更新失败也尝试更新笔记数据
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
            // 继续执行，不返回
        }
        mNoteDiffValues.clear();  // 清空临时存储

        // 同步详细数据
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 笔记详细数据内部类
     * 管理笔记的文本内容和通话记录等数据
     */
    private class NoteData {
        private long mTextDataId;  // 文本数据在数据库中的ID

        private ContentValues mTextDataValues;  // 文本数据内容容器

        private long mCallDataId;  // 通话记录数据ID

        private ContentValues mCallDataValues;  // 通话记录数据容器

        private static final String TAG = "NoteData";

        /**
         * 构造函数
         * 初始化数据容器
         */
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 检查数据是否被修改
         * @return true表示有修改，false表示无修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据ID
         * @param id 文本数据ID，必须大于0
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话记录数据ID
         * @param id 通话记录数据ID，必须大于0
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话记录数据
         * @param key 数据键名
         * @param value 数据值
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据
         * @param key 数据键名
         * @param value 数据值
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将数据同步到ContentProvider
         * 执行插入或更新操作
         * @param context 上下文对象
         * @param noteId 所属笔记ID
         * @return 同步成功返回笔记URI，失败返回null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            /**
             * 安全检查
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

          // 批量操作列表，用于一次性执行多个数据库操作
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);  // 关联笔记ID
                if (mTextDataId == 0) {
                    // 如果没有ID，说明是新数据，执行插入操作
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                     // 如果已有ID，说明是更新操作
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();  // 清空临时数据
            }

            // 处理通话记录数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);  // 关联笔记ID
                if (mCallDataId == 0) {
                    // 新通话记录，执行插入
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                     // 已有通话记录，执行更新
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();  // 清空临时数据
            }

            // 执行批量操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}