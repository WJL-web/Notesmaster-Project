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

package net.micode.notes.ui;

// Android相关导入
import android.content.Context;        // 上下文对象
import android.database.Cursor;        // 数据库游标
import android.text.TextUtils;         // 字符串工具类

// 项目内部类导入
import net.micode.notes.data.Contact;   // 联系人数据操作类
import net.micode.notes.data.Notes;     // 便签数据契约类
import net.micode.notes.data.Notes.NoteColumns;  // 便签表列名
import net.micode.notes.tool.DataUtils; // 数据工具类

/**
 * NoteItemData 类 - 便签列表项数据模型
 *
 * 作用：封装便签列表中每一项的数据
 * 用于 NotesListAdapter 中显示便签列表
 *
 * 功能：
 * 1. 从数据库游标提取便签数据
 * 2. 存储便签的各种属性（ID、内容、提醒时间、背景色等）
 * 3. 处理通话记录便签的联系人信息
 * 4. 计算列表项的位置信息（是否第一个、最后一个等）
 * 5. 判断文件夹和便签的层级关系
 *
 * 设计目的：
 * - 避免在Adapter中直接操作Cursor
 * - 缓存数据，提高列表滚动性能
 * - 封装数据获取逻辑
 */
public class NoteItemData {

    // ==================== 数据库查询投影 ====================

    /**
     * 便签表查询投影
     * 定义需要从数据库查询的列
     */
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,              // 便签ID
            NoteColumns.ALERTED_DATE,    // 提醒日期
            NoteColumns.BG_COLOR_ID,     // 背景颜色ID
            NoteColumns.CREATED_DATE,    // 创建日期
            NoteColumns.HAS_ATTACHMENT,  // 是否有附件
            NoteColumns.MODIFIED_DATE,   // 修改日期
            NoteColumns.NOTES_COUNT,     // 便签数量（仅文件夹有效）
            NoteColumns.PARENT_ID,       // 父文件夹ID
            NoteColumns.SNIPPET,         // 便签摘要（预览内容）
            NoteColumns.TYPE,            // 类型（便签/文件夹/系统）
            NoteColumns.WIDGET_ID,       // 桌面小部件ID
            NoteColumns.WIDGET_TYPE,     // 桌面小部件类型
    };

    // 列索引常量（用于从Cursor中快速读取）
    private static final int ID_COLUMN                    = 0;   // 便签ID
    private static final int ALERTED_DATE_COLUMN          = 1;   // 提醒日期
    private static final int BG_COLOR_ID_COLUMN           = 2;   // 背景颜色ID
    private static final int CREATED_DATE_COLUMN          = 3;   // 创建日期
    private static final int HAS_ATTACHMENT_COLUMN        = 4;   // 是否有附件
    private static final int MODIFIED_DATE_COLUMN         = 5;   // 修改日期
    private static final int NOTES_COUNT_COLUMN           = 6;   // 便签数量
    private static final int PARENT_ID_COLUMN             = 7;   // 父文件夹ID
    private static final int SNIPPET_COLUMN               = 8;   // 摘要内容
    private static final int TYPE_COLUMN                  = 9;   // 类型
    private static final int WIDGET_ID_COLUMN             = 10;  // 小部件ID
    private static final int WIDGET_TYPE_COLUMN           = 11;  // 小部件类型

    // ==================== 便签基础属性 ====================

    private long mId;                 // 便签ID（数据库主键）
    private long mAlertDate;          // 提醒日期（毫秒时间戳，0表示无提醒）
    private int mBgColorId;           // 背景颜色ID
    private long mCreatedDate;        // 创建日期
    private boolean mHasAttachment;   // 是否有附件（图片、录音等）
    private long mModifiedDate;       // 最后修改日期
    private int mNotesCount;          // 便签数量（仅当Type为文件夹时有效）
    private long mParentId;           // 父文件夹ID
    private String mSnippet;          // 便签摘要（列表显示的内容预览）
    private int mType;                // 类型：0-便签，1-文件夹，2-系统
    private int mWidgetId;            // 关联的桌面小部件ID
    private int mWidgetType;          // 小部件类型（2x2 或 4x4）

    // ==================== 通话记录相关属性 ====================

    private String mName;             // 联系人名称（通话记录便签专用）
    private String mPhoneNumber;      // 电话号码（通话记录便签专用）

    // ==================== 列表位置信息 ====================

    private boolean mIsLastItem;      // 是否为列表中的最后一项
    private boolean mIsFirstItem;     // 是否为列表中的第一项
    private boolean mIsOnlyOneItem;   // 是否只有这一项
    private boolean mIsOneNoteFollowingFolder;     // 是否为文件夹后只有一个便签
    private boolean mIsMultiNotesFollowingFolder;  // 是否为文件夹后有多个便签

    // ==================== 构造函数 ====================

    /**
     * 构造函数 - 从游标创建NoteItemData对象
     *
     * @param context 上下文对象（用于查询联系人信息）
     * @param cursor 数据库游标，已移动到要读取的位置
     */
    public NoteItemData(Context context, Cursor cursor) {
        // ========== 读取便签基础属性 ==========
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);

        // 获取摘要并移除待办清单的标记符号（✓ 和 ◯）
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "")      // 移除已选中标记
                .replace(NoteEditActivity.TAG_UNCHECKED, "");   // 移除未选中标记

        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        // ========== 处理通话记录便签 ==========
        mPhoneNumber = "";
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            // 根据便签ID获取关联的电话号码
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                // 根据电话号码获取联系人姓名
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    // 如果没有联系人姓名，直接显示电话号码
                    mName = mPhoneNumber;
                }
            }
        }

        // 确保mName不为null
        if (mName == null) {
            mName = "";
        }

        // 计算列表位置信息
        checkPostion(cursor);
    }

    // ==================== 位置信息计算方法 ====================

    /**
     * 检查并计算列表项的位置信息
     *
     * 功能：
     * 1. 判断是否为第一个/最后一个/唯一一个
     * 2. 判断文件夹后的便签数量（用于UI显示优化）
     *
     * @param cursor 数据库游标，位于当前项
     */
    private void checkPostion(Cursor cursor) {
        // 基础位置信息
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 处理文件夹后的便签层级关系
        // 如果当前项是便签，且不是第一项
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();  // 记录当前位置

            // 向前移动一位，检查前一项是否为文件夹
            if (cursor.moveToPrevious()) {
                // 如果前一项是文件夹或系统文件夹
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    // 判断该文件夹后面有多少个便签
                    if (cursor.getCount() > (position + 1)) {
                        // 文件夹后有多个便签
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        // 文件夹后只有一个便签
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                // 恢复游标位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // ==================== Getter方法 ====================

    /** 判断是否为文件夹后只有一个便签（用于UI显示） */
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    /** 判断是否为文件夹后有多个便签（用于UI显示） */
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    /** 判断是否为列表中的最后一项 */
    public boolean isLast() {
        return mIsLastItem;
    }

    /** 获取联系人名称（通话记录便签专用） */
    public String getCallName() {
        return mName;
    }

    /** 判断是否为列表中的第一项 */
    public boolean isFirst() {
        return mIsFirstItem;
    }

    /** 判断是否只有这一项 */
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    /** 获取便签ID */
    public long getId() {
        return mId;
    }

    /** 获取提醒日期 */
    public long getAlertDate() {
        return mAlertDate;
    }

    /** 获取创建日期 */
    public long getCreatedDate() {
        return mCreatedDate;
    }

    /** 判断是否有附件 */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /** 获取修改日期 */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /** 获取背景颜色ID */
    public int getBgColorId() {
        return mBgColorId;
    }

    /** 获取父文件夹ID */
    public long getParentId() {
        return mParentId;
    }

    /** 获取便签数量（仅文件夹有效） */
    public int getNotesCount() {
        return mNotesCount;
    }

    /** 获取文件夹ID（与getParentId相同） */
    public long getFolderId() {
        return mParentId;
    }

    /** 获取类型（便签/文件夹/系统） */
    public int getType() {
        return mType;
    }

    /** 获取小部件类型 */
    public int getWidgetType() {
        return mWidgetType;
    }

    /** 获取小部件ID */
    public int getWidgetId() {
        return mWidgetId;
    }

    /** 获取便签摘要（列表显示内容） */
    public String getSnippet() {
        return mSnippet;
    }

    /** 判断是否有提醒 */
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 判断是否为通话记录便签
     * 条件：父文件夹为通话记录文件夹，且电话号码不为空
     */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    // ==================== 静态工具方法 ====================

    /**
     * 获取游标中当前行的便签类型
     *
     * @param cursor 数据库游标
     * @return 类型值（0-便签，1-文件夹，2-系统）
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}