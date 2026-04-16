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

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

// WorkingNote class - Main operation class for note management
/* 工作笔记类 - 笔记的主要操作类，负责笔记的加载、保存、修改等核心功能 */
public class WorkingNote {
    // Note for the working note
    /* 工作笔记对象 */
    private Note mNote;
    // Note Id
    /* 笔记ID */
    private long mNoteId;
    // Note content
    /* 笔记内容 */
    private String mContent;
    // Note mode
    /* 笔记模式（普通模式/清单模式） */
    private int mMode;

    /* 提醒日期时间 */
    private long mAlertDate;

    /* 最后修改时间 */
    private long mModifiedDate;

    /* 背景颜色ID */
    private int mBgColorId;

    /* 桌面小部件ID */
    private int mWidgetId;

    /* 桌面小部件类型 */
    private int mWidgetType;

    /* 所属文件夹ID */
    private long mFolderId;

    /* 上下文对象 */
    private Context mContext;

    private static final String TAG = "WorkingNote";

    /* 是否已标记删除 */
    private boolean mIsDeleted;

    /* 笔记设置变化监听器 */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // Data projection for query - defines which columns to query from data table
    /* 数据表查询投影 - 定义要查询的数据列，用于查询笔记的详细内容数据 */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // Note projection for query - defines which columns to query from note table
    /* 笔记表查询投影 - 定义要查询的笔记列，用于查询笔记的基本信息 */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // Data table column index constants
    /* 数据表列索引常量 */
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;

    // Note table column index constants
    /* 笔记表列索引常量 */
    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // New note construct
    /* 构造函数 - 创建新笔记 */
    /* @param context 上下文对象 */
    /* @param folderId 所属文件夹ID */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    // Existing note construct
    /* 构造函数 - 加载已有笔记 */
    /* @param context 上下文对象 */
    /* @param noteId 笔记ID */
    /* @param folderId 所属文件夹ID */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    // Load note basic info from database
    /* 从数据库加载笔记的基本信息（背景色、提醒时间等属性） */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    // Load note detail data (text content, call records, etc.)
    /* 加载笔记的详细数据（文本内容、通话记录等） */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    // Factory method - create empty note
    /* 工厂方法 - 创建空笔记 */
    /* @param context 上下文 */
    /* @param folderId 文件夹ID */
    /* @param widgetId 小部件ID */
    /* @param widgetType 小部件类型 */
    /* @param defaultBgColorId 默认背景颜色ID */
    /* @return 新创建的工作笔记对象 */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    // Factory method - load existing note
    /* 工厂方法 - 加载已有笔记 */
    /* @param context 上下文 */
    /* @param id 笔记ID */
    /* @return 加载的工作笔记对象 */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    // Save note to database
    /* 保存笔记到数据库 */
    /* @return true保存成功，false保存失败 */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
             * 如果笔记有关联的桌面小部件，更新小部件内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    // Check if note exists in database
    /* 检查笔记是否已存在于数据库中 */
    /* @return true存在，false不存在 */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    // Check if note needs to be saved
    /* 判断笔记是否需要保存 */
    /* @return true需要保存，false无需保存 */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    // Set note setting change listener
    /* 设置笔记设置变化监听器 */
    /* @param l 监听器对象 */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    // Set alert date
    /* 设置提醒日期 */
    /* @param date 提醒日期时间戳 */
    /* @param set 是否设置提醒 */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    // Mark note as deleted
    /* 标记删除笔记 */
    /* @param mark true标记删除，false取消删除标记 */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    // Set note background color
    /* 设置笔记背景颜色 */
    /* @param id 背景颜色资源ID */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    // Set check list mode (normal mode / todo list mode)
    /* 设置清单模式（普通模式/待办清单模式） */
    /* @param mode 模式代码 */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    // Set widget type
    /* 设置小部件类型 */
    /* @param type 小部件类型 */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    // Set widget id
    /* 设置小部件ID */
    /* @param id 小部件ID */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    // Set note content
    /* 设置笔记内容 */
    /* @param text 笔记文本内容 */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    // Convert to call record note
    /* 转换为通话记录笔记 */
    /* @param phoneNumber 电话号码 */
    /* @param callDate 通话日期 */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    // Check if has clock alert
    /* 检查是否有时钟提醒 */
    /* @return true有提醒，false无提醒 */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    // Getter methods
    /* Getter 方法 - 获取笔记属性 */
    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    // Get background color resource id
    /* 获取背景颜色资源ID */
    /* @return 背景颜色资源 */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    // Get title bar background resource id
    /* 获取标题栏背景资源ID */
    /* @return 标题背景颜色资源 */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * Note setting change listener interface
     * Used to listen to note property changes (background color, alert, widget, check list mode, etc.)
     */
    /* 笔记设置变化监听器接口 */
    /* 用于监听笔记的各种属性变化（背景色、提醒、小部件、清单模式等） */
    public interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         */
        /* 当当前笔记的背景颜色改变时调用 */
        void onBackgroundColorChanged();

        /**
         * Called when user set clock
         */
        /* 当用户设置时钟提醒时调用 */
        /* @param date 提醒日期 */
        /* @param set 是否设置 */
        void onClockAlertChanged(long date, boolean set);

        /**
         * Call when user create note from widget
         */
        /* 当用户从小部件创建笔记时调用 */
        void onWidgetChanged();

        /**
         * Call when switch between check list mode and normal mode
         */
        /* 当在清单模式和普通模式之间切换时调用 */
        /* @param oldMode is previous mode before change 改变前的模式 */
        /* @param newMode is new mode 改变后的模式 */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}