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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 数据工具类
 *
 * 提供一系列静态方法，用于操作便签数据库（note 表和 data 表）。
 * 封装了常见的数据库操作，如批量删除、移动、查询等。
 *
 * 主要功能：
 * - 批量删除便签（batchDeleteNotes）
 * - 批量移动便签到指定文件夹（batchMoveToFolder）
 * - 单个便签移动并记录原始位置（moveNoteToFoler）
 * - 查询用户创建的文件夹数量（getUserFolderCount）
 * - 检查便签/数据是否存在（existInNoteDatabase、existInDataDatabase）
 * - 获取文件夹关联的桌面小部件（getFolderNoteWidget）
 * - 获取通话记录便签的电话号码和 ID
 * - 获取便签摘要并格式化
 *
 * @author MiCode Open Source Community
 */
public class DataUtils {

    /** 日志标签 */
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签
     *
     * 使用 ContentProviderOperation 批量删除指定的便签。
     * 注意：系统根文件夹（ID_ROOT_FOLDER）不会被删除，会跳过并记录错误日志。
     *
     * @param resolver ContentResolver 实例，用于执行数据库操作
     * @param ids      要删除的便签 ID 集合（HashSet<Long>）
     * @return true 表示删除成功，false 表示删除失败
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // 参数校验：空集合或 null 视为成功（无操作）
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // 构建批量删除操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 保护系统根文件夹，不允许删除
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            // 为每个便签创建删除操作
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }

        // 执行批量操作
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 移动单个便签到指定文件夹（并记录原始位置）
     *
     * 此方法会同时更新：
     * - PARENT_ID：新的父文件夹 ID
     * - ORIGIN_PARENT_ID：原始父文件夹 ID（用于同步冲突处理）
     * - LOCAL_MODIFIED：标记为本地已修改
     *
     * @param resolver    ContentResolver 实例
     * @param id          要移动的便签 ID
     * @param srcFolderId 原始父文件夹 ID
     * @param desFolderId 目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id,
                                       long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);           // 新位置
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);    // 原位置（用于冲突检测）
        values.put(NoteColumns.LOCAL_MODIFIED, 1);                // 标记本地修改
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                values, null, null);
    }

    /**
     * 批量移动便签到指定文件夹
     *
     * 使用批量操作将多个便签移动到同一个目标文件夹。
     * 只更新 PARENT_ID 和 LOCAL_MODIFIED 字段。
     *
     * @param resolver ContentResolver 实例
     * @param ids      要移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return true 表示移动成功，false 表示移动失败
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
                                            long folderId) {
        // 参数校验
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        // 构建批量更新操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);    // 更新父文件夹
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);      // 标记本地修改
            operationList.add(builder.build());
        }

        // 执行批量操作
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量（不包括系统文件夹）
     *
     * 查询条件：
     * - TYPE = TYPE_FOLDER（文件夹类型）
     * - PARENT_ID ≠ ID_TRASH_FOLER（不在回收站中）
     *
     * @param resolver ContentResolver 实例
     * @return 用户文件夹的数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },  // 只查询数量
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER) },
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);  // 获取 COUNT(*) 的结果
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查指定类型的便签是否在数据库中可见（不在回收站中）
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @param type     便签类型（TYPE_NOTE、TYPE_FOLDER 等）
     * @return true 表示存在且可见，false 表示不存在或已在回收站
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,  // 查询所有字段
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String[] { String.valueOf(type) },
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查便签是否存在于数据库中（不考虑可见性）
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查数据条目是否存在于 data 表中
     *
     * @param resolver ContentResolver 实例
     * @param dataId   数据 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查文件夹名称是否已存在（在可见文件夹中）
     *
     * 查询条件：
     * - TYPE = TYPE_FOLDER（文件夹）
     * - PARENT_ID ≠ ID_TRASH_FOLER（不在回收站）
     * - SNIPPET = name（名称匹配）
     *
     * @param resolver ContentResolver 实例
     * @param name     文件夹名称
     * @return true 表示名称已存在，false 表示可用
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹下所有便签关联的桌面小部件信息
     *
     * 遍历文件夹中的所有便签，收集它们使用的桌面小部件（Widget）。
     * 返回一个 HashSet，自动去重。
     *
     * @param resolver ContentResolver 实例
     * @param folderId 文件夹 ID
     * @return 小部件属性集合，如果没有小部件则返回 null
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver,
                                                                  long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);      // 小部件 ID
                        widget.widgetType = c.getInt(1);    // 小部件类型
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据便签 ID 获取通话记录的电话号码
     *
     * 从 data 表中查询指定便签关联的通话记录数据，提取电话号码。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return 电话号码字符串，如果没有找到则返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话时间查找通话记录便签的 ID
     *
     * 使用数据库自定义函数 PHONE_NUMBERS_EQUAL 进行电话号码比较（支持格式化匹配）。
     *
     * @param resolver   ContentResolver 实例
     * @param phoneNumber 电话号码
     * @param callDate    通话时间戳
     * @return 便签 ID，如果没有找到则返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver,
                                                         String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签 ID 获取便签摘要（SNIPPET）
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return 摘要字符串
     * @throws IllegalArgumentException 如果便签不存在则抛出异常
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String[] { String.valueOf(noteId) },
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化摘要文本
     *
     * 去除首尾空白，并截取第一行内容（如果有多行）。
     * 用于在列表视图中显示简洁的摘要。
     *
     * @param snippet 原始摘要文本
     * @return 格式化后的摘要（单行，已 trim）
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();
            int index = snippet.indexOf('\n');  // 查找第一个换行符
            if (index != -1) {
                snippet = snippet.substring(0, index);  // 只取第一行
            }
        }
        return snippet;
    }
}