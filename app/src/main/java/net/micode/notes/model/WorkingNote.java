/*
 * 版权声明 (c) 2010-2011, 米代码开源社区 (www.micode.net)
 *
 * 根据 Apache License 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，根据许可证分发的软件
 * 是按“原样”基础分发的，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */

package net.micode.notes.model;

// Android相关导入
import android.appwidget.AppWidgetManager;      // 桌面小部件管理器
import android.content.ContentUris;             // 操作带ID的URI
import android.content.Context;                 // 上下文对象
import android.database.Cursor;                 // 数据库游标
import android.text.TextUtils;                  // 字符串工具类
import android.util.Log;                        // 日志工具

// 项目内部类导入
import net.micode.notes.data.Notes;             // 便签数据契约类
import net.micode.notes.data.Notes.CallNote;    // 通话便签数据列定义
import net.micode.notes.data.Notes.DataColumns; // 数据表列名
import net.micode.notes.data.Notes.DataConstants; // 数据类型常量
import net.micode.notes.data.Notes.NoteColumns; // 便签表列名
import net.micode.notes.data.Notes.TextNote;    // 文字便签数据列定义
import net.micode.notes.tool.ResourceParser.NoteBgResources; // 便签背景资源

/**
 * WorkingNote 类 - 工作便签类
 *
 * 作用：表示正在编辑或操作的便签对象
 * 封装了便签的数据操作逻辑，是便签功能的核心模型类
 *
 * 功能：
 * 1. 加载和保存便签数据
 * 2. 管理便签属性（内容、提醒时间、背景色等）
 * 3. 支持文字便签和通话便签两种类型
 * 4. 提供便签变更监听器接口
 * 5. 支持待办清单模式切换
 */
public class WorkingNote {

    // ==================== 成员变量 ====================

    /** 便签数据操作对象（封装数据库操作） */
    private Note mNote;

    /** 便签ID（数据库主键） */
    private long mNoteId;

    /** 便签内容（文字内容） */
    private String mContent;

    /** 便签模式（0-普通文本，1-待办清单） */
    private int mMode;

    /** 提醒日期（毫秒时间戳，0表示无提醒） */
    private long mAlertDate;

    /** 最后修改日期（毫秒时间戳） */
    private long mModifiedDate;

    /** 背景颜色ID（对应预定义的颜色方案） */
    private int mBgColorId;

    /** 关联的桌面小部件ID（如果添加到桌面） */
    private int mWidgetId;

    /** 桌面小部件类型（2x2 或 4x4） */
    private int mWidgetType;

    /** 所属文件夹ID */
    private long mFolderId;

    /** 上下文对象 */
    private Context mContext;

    /** 日志标签 */
    private static final String TAG = "WorkingNote";

    /** 是否已标记删除 */
    private boolean mIsDeleted;

    /** 便签设置变更监听器（用于通知UI更新） */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // ==================== 数据库查询投影 ====================

    /**
     * 数据表查询投影
     * 查询data表时需要获取的列
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,           // 数据ID
            DataColumns.CONTENT,      // 内容
            DataColumns.MIME_TYPE,    // MIME类型
            DataColumns.DATA1,        // 通用数据列1（存储模式等）
            DataColumns.DATA2,        // 通用数据列2
            DataColumns.DATA3,        // 通用数据列3
            DataColumns.DATA4,        // 通用数据列4
    };

    /**
     * 便签表查询投影
     * 查询note表时需要获取的列
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,        // 父文件夹ID
            NoteColumns.ALERTED_DATE,     // 提醒日期
            NoteColumns.BG_COLOR_ID,      // 背景颜色ID
            NoteColumns.WIDGET_ID,        // 小部件ID
            NoteColumns.WIDGET_TYPE,      // 小部件类型
            NoteColumns.MODIFIED_DATE     // 修改日期
    };

    // 数据表列索引常量
    private static final int DATA_ID_COLUMN = 0;          // 数据ID
    private static final int DATA_CONTENT_COLUMN = 1;     // 内容
    private static final int DATA_MIME_TYPE_COLUMN = 2;   // MIME类型
    private static final int DATA_MODE_COLUMN = 3;        // 模式（DATA1）

    // 便签表列索引常量
    private static final int NOTE_PARENT_ID_COLUMN = 0;       // 父文件夹ID
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;    // 提醒日期
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;     // 背景颜色ID
    private static final int NOTE_WIDGET_ID_COLUMN = 3;       // 小部件ID
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;     // 小部件类型
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;   // 修改日期

    // ==================== 构造函数 ====================

    /**
     * 构造函数 - 创建新便签（私有）
     *
     * @param context 上下文对象
     * @param folderId 所属文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;                                    // 初始无提醒
        mModifiedDate = System.currentTimeMillis();        // 设置当前时间为修改时间
        mFolderId = folderId;                              // 设置文件夹ID
        mNote = new Note();                                // 创建Note对象
        mNoteId = 0;                                       // 新便签ID为0
        mIsDeleted = false;                                // 未删除
        mMode = 0;                                         // 默认普通文本模式
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;          // 默认无效小部件类型
    }

    /**
     * 构造函数 - 加载已有便签（私有）
     *
     * @param context 上下文对象
     * @param noteId 便签ID
     * @param folderId 所属文件夹ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();        // 从数据库加载便签数据
    }

    // ==================== 数据加载方法 ====================

    /**
     * 从数据库加载便签信息
     *
     * 从note表中读取便签的基本属性
     */
    private void loadNote() {
        // 查询note表获取便签基本信息
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId),
                NOTE_PROJECTION, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);        // 文件夹ID
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);      // 背景颜色
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);         // 小部件ID
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);     // 小部件类型
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);    // 提醒日期
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN); // 修改日期
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();  // 加载便签详细数据
    }

    /**
     * 加载便签的详细数据
     *
     * 从data表中读取便签的内容、模式等信息
     * 支持文字便签和通话便签两种类型
     */
    private void loadNoteData() {
        // 查询data表获取便签详细内容
        Cursor cursor = mContext.getContentResolver().query(
                Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] { String.valueOf(mNoteId) }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);  // 获取MIME类型
                    if (DataConstants.NOTE.equals(type)) {
                        // 文字便签：读取内容和模式
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));  // 保存数据ID
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 通话便签：保存通话数据ID
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

    // ==================== 工厂方法 ====================

    /**
     * 创建空便签（用于新建便签）
     *
     * @param context 上下文对象
     * @param folderId 文件夹ID
     * @param widgetId 小部件ID
     * @param widgetType 小部件类型
     * @param defaultBgColorId 默认背景颜色ID
     * @return 新创建的WorkingNote对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 加载便签（用于打开已有便签）
     *
     * @param context 上下文对象
     * @param id 便签ID
     * @return 加载的WorkingNote对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    // ==================== 数据保存方法 ====================

    /**
     * 保存便签到数据库
     *
     * @return true 保存成功，false 保存失败
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {  // 判断是否值得保存
            if (!existInDatabase()) {  // 如果不在数据库中，创建新便签
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 同步便签数据到数据库
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果该便签关联了桌面小部件，更新小部件内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();  // 通知小部件更新
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断便签是否已存在于数据库中
     *
     * @return true 已存在，false 不存在
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断便签是否值得保存
     *
     * 以下情况不值得保存：
     * 1. 已标记删除
     * 2. 新便签且内容为空
     * 3. 已有便签且没有本地修改
     *
     * @return true 值得保存，false 不值得保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted                              // 已删除
                || (!existInDatabase() && TextUtils.isEmpty(mContent))  // 新便签但内容为空
                || (existInDatabase() && !mNote.isLocalModified())) {   // 已有便签但无本地修改
            return false;
        } else {
            return true;
        }
    }

    // ==================== Setter方法（带监听器通知） ====================

    /**
     * 设置便签设置变更监听器
     *
     * @param l 监听器对象
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒日期
     *
     * @param date 提醒日期（毫秒时间戳）
     * @param set 是否设置提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);  // 通知提醒变化
        }
    }

    /**
     * 标记删除状态
     *
     * @param mark true 标记删除，false 取消删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();  // 通知小部件更新
        }
    }

    /**
     * 设置背景颜色ID
     *
     * @param id 背景颜色ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();  // 通知背景颜色变化
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置待办清单模式
     *
     * @param mode 模式（0-普通文本，1-待办清单）
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);  // 通知模式变化
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置小部件类型
     *
     * @param type 小部件类型（2x2 或 4x4）
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置小部件ID
     *
     * @param id 小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置便签内容
     *
     * @param text 新的便签内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 转换为通话便签
     *
     * 将当前便签转换为通话记录便签类型
     *
     * @param phoneNumber 电话号码
     * @param callDate 通话日期
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    // ==================== Getter方法 ====================

    /** 判断是否有提醒 */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /** 获取便签内容 */
    public String getContent() {
        return mContent;
    }

    /** 获取提醒日期 */
    public long getAlertDate() {
        return mAlertDate;
    }

    /** 获取修改日期 */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /** 获取背景颜色资源ID（用于UI显示） */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /** 获取背景颜色ID */
    public int getBgColorId() {
        return mBgColorId;
    }

    /** 获取标题背景资源ID */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /** 获取待办清单模式 */
    public int getCheckListMode() {
        return mMode;
    }

    /** 获取便签ID */
    public long getNoteId() {
        return mNoteId;
    }

    /** 获取文件夹ID */
    public long getFolderId() {
        return mFolderId;
    }

    /** 获取小部件ID */
    public int getWidgetId() {
        return mWidgetId;
    }

    /** 获取小部件类型 */
    public int getWidgetType() {
        return mWidgetType;
    }

    // ==================== 内部监听器接口 ====================

    /**
     * NoteSettingChangedListener 接口 - 便签设置变更监听器
     *
     * 用于在便签属性发生变化时通知UI层更新显示
     */
    public interface NoteSettingChangedListener {

        /**
         * 当便签的背景颜色发生变化时调用
         */
        void onBackgroundColorChanged();

        /**
         * 当用户设置闹钟提醒时调用
         *
         * @param date 提醒日期
         * @param set 是否设置
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 当用户从桌面小部件创建便签时调用
         */
        void onWidgetChanged();

        /**
         * 当在普通模式和待办清单模式之间切换时调用
         *
         * @param oldMode 切换前的模式
         * @param newMode 切换后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}