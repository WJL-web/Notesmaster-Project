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

package net.micode.notes.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 小米便签 内容提供者（ContentProvider）
 * 功能：对外提供 笔记数据 的 增、删、改、查 操作
 * 地位：APP 所有数据的唯一入口，保护数据库安全
 */
public class NotesProvider extends ContentProvider {
    // Uri 匹配器：识别外界传入的请求地址（是查笔记？查内容？搜索？）
    private static final UriMatcher mMatcher;

    // 数据库帮助类实例（负责真正操作 SQLite）
    private NotesDatabaseHelper mHelper;

    // 日志 TAG
    private static final String TAG = "NotesProvider";

    // 定义 Uri 匹配类型常量
    private static final int URI_NOTE            = 1;    // 操作 note 表（列表）
    private static final int URI_NOTE_ITEM       = 2;    // 操作单条笔记（带ID）
    private static final int URI_DATA            = 3;    // 操作 data 表（内容列表）
    private static final int URI_DATA_ITEM       = 4;    // 操作单条内容（带ID）
    private static final int URI_SEARCH          = 5;    // 搜索笔记
    private static final int URI_SEARCH_SUGGEST  = 6;    // 搜索建议

    // 静态代码块：初始化 Uri 匹配规则
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);                // content://micode_notes/note
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);         // content://micode_notes/note/1
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);                // content://micode_notes/data
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);         // content://micode_notes/data/1
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);            // 搜索
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索结果投影（查询字段）
     * 作用：给系统搜索提供 笔记ID、标题、内容、图标
     * x'0A' = \n 换行符，搜索时替换成空格，显示更美观
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 搜索笔记 SQL
     * 功能：从 note 表模糊搜索，排除回收站，只查普通笔记
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"                    // 模糊匹配内容
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER  // 排除回收站
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;             // 只搜索笔记，不搜文件夹

    /**
     * 创建 Provider：初始化数据库帮助类
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据（最常用）
     * @param uri  查询地址
     * @param projection  查询字段
     * @param selection  查询条件
     * @param selectionArgs  条件值
     * @param sortOrder  排序
     * @return 游标（查询结果）
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;

        // 根据 Uri 类型执行不同查询
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询整个 note 表（文件夹+笔记列表）
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            case URI_NOTE_ITEM:
                // 查询单条笔记（根据ID）
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            case URI_DATA:
                // 查询整个 data 表（所有笔记内容）
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            case URI_DATA_ITEM:
                // 查询单条内容数据
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索 + 搜索建议
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                // 模糊查询：%关键词%
                searchString = String.format("%%%s%%", searchString);
                c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY, new String[] { searchString });
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 数据发生变化时通知刷新（关键：界面自动更新）
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据（新建笔记/新建内容）
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入笔记/文件夹
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;

            case URI_DATA:
                // 插入笔记内容/附件
                noteId = values.getAsLong(DataColumns.NOTE_ID);
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 发送通知：数据变化 → 界面自动刷新
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        // 返回带ID的Uri
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据（删除笔记/内容）
     * 保护机制：系统文件夹（ID≤0）不允许删除
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除多条笔记
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 "; // 禁止删系统文件夹
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                // 删除单条笔记
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                if (noteId <= 0) { // ID≤0 是系统文件夹，不允许删
                    break;
                }
                count = db.delete(TABLE.NOTE, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                // 删除多条内容数据
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;

            case URI_DATA_ITEM:
                // 删除单条内容
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知刷新
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据（修改笔记/内容）
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新多条笔记：自动版本+1（同步用）
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                // 更新单条笔记
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                // 更新多条内容
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;

            case URI_DATA_ITEM:
                // 更新单条内容
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                updateData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知刷新
        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 拼接查询条件：如果有额外条件，前面加 AND
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 自动增加笔记版本号（用于云同步：判断哪条数据最新）
     * 每次更新笔记，版本+1
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 "); // 版本号 +1

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + id);
        }
        if (!TextUtils.isEmpty(selection)) {
            sql.append(id > 0 ? parseSelection(selection) : selection);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 获取数据MIME类型（系统要求，未实现）
     */
    @Override
    public String getType(Uri uri) {
        return null;
    }
}