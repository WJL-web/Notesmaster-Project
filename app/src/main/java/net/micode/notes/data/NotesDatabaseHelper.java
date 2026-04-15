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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 小米便签 数据库帮助类
 * 功能：负责创建/升级数据库、创建数据表、定义触发器、初始化系统文件夹
 * 作用：整个 APP 所有笔记数据的存储地基，所有数据都存在这个类管理的数据库中
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名：note.db
    private static final String DB_NAME = "note.db";

    // 数据库版本号：用于后续升级
    private static final int DB_VERSION = 4;

    /**
     * 数据库表名接口（统一管理表名，避免硬编码）
     */
    public interface TABLE {
        // 笔记/文件夹 主表
        public static final String NOTE = "note";
        // 笔记内容/附件 明细表
        public static final String DATA = "data";
    }

    // 日志TAG
    private static final String TAG = "NotesDatabaseHelper";

    // 单例实例：全局唯一数据库帮助类
    private static NotesDatabaseHelper mInstance;

    // ====================== 1. 创建 note 表（笔记/文件夹主表）SQL ======================
    private static final String CREATE_NOTE_TABLE_SQL =
            "CREATE TABLE " + TABLE.NOTE + "(" +
                    NoteColumns.ID + " INTEGER PRIMARY KEY," +             // 主键ID
                    NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 父文件夹ID（0=根目录）
                    NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," + // 提醒时间
                    NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," + // 背景色ID
                    // 创建时间（默认当前时间戳）
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," + // 是否有附件
                    // 修改时间（默认当前时间戳）
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," + // 文件夹内笔记数量
                    NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," + // 笔记摘要（列表显示）
                    NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," + // 类型：0=笔记 1=文件夹
                    NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," + // 桌面小部件ID
                    NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," + // 小部件类型
                    NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," + // 同步ID（云同步）
                    NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," + // 本地是否修改
                    NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 原始父文件夹ID
                    NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," + // 谷歌任务ID
                    NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" + // 数据版本号
                    ")";

    // ====================== 2. 创建 data 表（笔记内容明细表）SQL ======================
    private static final String CREATE_DATA_TABLE_SQL =
            "CREATE TABLE " + TABLE.DATA + "(" +
                    DataColumns.ID + " INTEGER PRIMARY KEY," + // 主键
                    DataColumns.MIME_TYPE + " TEXT NOT NULL," + // 数据类型：文本/图片/音频等
                    DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," + // 所属笔记ID（关联note表）
                    // 创建时间
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    // 修改时间
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," + // 笔记正文内容
                    DataColumns.DATA1 + " INTEGER," + // 扩展字段1
                    DataColumns.DATA2 + " INTEGER," + // 扩展字段2
                    DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," + // 扩展字段3
                    DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," + // 扩展字段4
                    DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" + // 扩展字段5
                    ")";

    // 为 data 表的 note_id 建立索引（加速查询：根据笔记ID查内容）
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                    TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ====================== 数据库触发器（核心业务自动逻辑）======================
    /**
     * 触发器1：移动笔记到文件夹 → 目标文件夹笔记数 +1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update "+
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器2：从文件夹移出笔记 → 原文件夹笔记数 -1
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
                    " END";

    /**
     * 触发器3：新建笔记放入文件夹 → 文件夹笔记数 +1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器4：删除文件夹内笔记 → 文件夹笔记数 -1
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
                    " END";

    /**
     * 触发器5：插入笔记内容 → 自动更新 note 表的摘要（列表预览文字）
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.DATA +
                    " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器6：修改笔记内容 → 自动更新 note 表摘要
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器7：删除笔记内容 → 清空 note 表摘要
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器8：删除笔记 → 自动删除该笔记的所有内容数据（级联删除）
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
            "CREATE TRIGGER delete_data_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.DATA +
                    "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器9：删除文件夹 → 自动删除文件夹内所有笔记（级联删除）
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.NOTE +
                    "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器10：文件夹移入回收站 → 里面所有笔记一并移入回收站
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.NOTE +
                    " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    // 构造方法：固定传入 上下文、数据库名、版本号
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // 创建 note 表 + 触发器 + 系统文件夹
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    // 重新创建 note 表所有触发器（先删旧的，再建新的）
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    // 初始化4个系统默认文件夹（APP必备）
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        // 1. 通话记录文件夹（存放通话笔记）
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 2. 根文件夹（默认笔记目录）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 3. 临时文件夹（移动笔记时用）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 4. 回收站文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    // 创建 data 表 + 触发器 + 索引
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    // 重新创建 data 表触发器
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    // 单例模式：全局获取唯一数据库帮助类（线程安全）
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    // ====================== 系统重写方法 ======================
    // 首次创建数据库：创建两张表
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    // 数据库升级（版本变化时执行）
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // 从v1升级到v2
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;
            oldVersion++;
        }

        // 从v2升级到v3
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // 从v3升级到v4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 升级后重建触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 升级失败异常
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    // v1→v2：重建表（早期版本兼容）
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    // v2→v3：新增GTASK_ID字段 + 新建回收站
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除无用触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 新增谷歌任务ID字段
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 创建回收站系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    // v3→v4：新增版本号字段（用于同步）
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}