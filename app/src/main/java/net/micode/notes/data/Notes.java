/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.net.Uri;

/**
 * 小米便签 【数据契约类】
 * 作用：统一定义整个APP所有数据相关的常量
 * 包括：数据库表字段、ContentProvider地址、数据类型、系统ID、跳转参数等
 * 相当于整个APP的数据字典
 */
public class Notes {

    /**
     * ContentProvider 授权名称（唯一标识）
     * 作用：跨进程访问数据时的唯一标识
     */
    public static final String AUTHORITY = "micode_notes";

    // 日志TAG
    public static final String TAG = "Notes";

    // ====================== 数据类型常量 ======================
    /** 普通笔记类型 */
    public static final int TYPE_NOTE = 0;
    /** 文件夹类型 */
    public static final int TYPE_FOLDER = 1;
    /** 系统文件夹类型（不可删除） */
    public static final int TYPE_SYSTEM = 2;

    // ====================== 系统固定文件夹ID ======================
    /** 根文件夹ID（默认文件夹） */
    public static final int ID_ROOT_FOLDER = 0;
    /** 临时文件夹ID（移动笔记时用） */
    public static final int ID_TEMPARAY_FOLDER = -1;
    /** 通话记录笔记文件夹ID */
    public static final int ID_CALL_RECORD_FOLDER = -2;
    /** 回收站文件夹ID */
    public static final int ID_TRASH_FOLER = -3;

    // ====================== 页面跳转/Intent传参 常量 ======================
    /** 传递：提醒时间 */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    /** 传递：背景色ID */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    /** 传递：桌面小部件ID */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    /** 传递：小部件类型 */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    /** 传递：文件夹ID */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    /** 传递：通话时间 */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ====================== 桌面小部件类型 ======================
    /** 无效小部件 */
    public static final int TYPE_WIDGET_INVALIDE = -1;
    /** 2x大小小部件 */
    public static final int TYPE_WIDGET_2X = 0;
    /** 4x大小小部件 */
    public static final int TYPE_WIDGET_4X = 1;

    /**
     * 数据类型常量
     * 定义便签的MIME类型：普通文本、通话笔记
     */
    public static class DataConstants {
        /** 文本便签类型 */
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        /** 通话便签类型 */
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // ====================== ContentProvider 访问URI ======================
    /** 访问笔记主表（note表）的URI */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");
    /** 访问数据明细表（data表）的URI */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 文本便签 接口
     * 继承DataColumns，拥有所有data表字段，并扩展文本便签专属常量
     */
    public interface TextNote extends DataColumns {
        /** 单条文本便签的MIME类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        /** 多条文本便签的MIME类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        /** 文本便签的访问URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");

        /** 便签模式：使用DATA1字段存储 */
        public static final String MODE = DATA1;
        /** 模式：清单模式（待办事项） */
        public static final int MODE_CHECK_LIST = 1;
    }

    /**
     * 通话笔记 接口
     * 通话记录相关的便签，自动关联手机号、通话时间
     */
    public interface CallNote extends DataColumns {
        /** 单条通话便签MIME类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        /** 多条通话便签MIME类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        /** 通话便签访问URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");

        /** 通话时间：存在DATA1字段 */
        public static final String CALL_DATE = DATA1;
        /** 手机号码：存在DATA2字段 */
        public static final String PHONE_NUMBER = DATA2;
    }

    /**
     * note 表（笔记/文件夹主表）字段定义
     * 对应数据库中的 note 表
     */
    public interface NoteColumns {
        /** 主键ID */
        public static final String ID = "_id";
        /** 父文件夹ID */
        public static final String PARENT_ID = "parent_id";
        /** 创建时间 */
        public static final String CREATED_DATE = "created_date";
        /** 修改时间 */
        public static final String MODIFIED_DATE = "modified_date";
        /** 提醒时间 */
        public static final String ALERTED_DATE = "alert_date";
        /** 笔记摘要（列表显示） */
        public static final String SNIPPET = "snippet";
        /** 绑定的桌面小部件ID */
        public static final String WIDGET_ID = "widget_id";
        /** 小部件类型 */
        public static final String WIDGET_TYPE = "widget_type";
        /** 背景色ID */
        public static final String BG_COLOR_ID = "bg_color_id";
        /** 是否有附件 */
        public static final String HAS_ATTACHMENT = "has_attachment";
        /** 文件夹内笔记数量 */
        public static final String NOTES_COUNT = "notes_count";
        /** 类型：笔记/文件夹/系统 */
        public static final String TYPE = "type";
        /** 同步ID（云同步） */
        public static final String SYNC_ID = "sync_id";
        /** 本地是否修改（同步标记） */
        public static final String LOCAL_MODIFIED = "local_modified";
        /** 原始父文件夹ID（移动前） */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";
        /** 谷歌任务ID */
        public static final String GTASK_ID = "gtask_id";
        /** 数据版本号 */
        public static final String VERSION = "version";
    }

    /**
     * data 表（内容详情表）字段定义
     * 存放笔记正文、附件、多媒体等详细数据
     */
    public interface DataColumns {
        /** 主键ID */
        public static final String ID = "_id";
        /** 数据类型（文本/通话/图片等） */
        public static final String MIME_TYPE = "mime_type";
        /** 所属笔记ID（关联note表） */
        public static final String NOTE_ID = "note_id";
        /** 创建时间 */
        public static final String CREATED_DATE = "created_date";
        /** 修改时间 */
        public static final String MODIFIED_DATE = "modified_date";
        /** 内容主体（文字内容） */
        public static final String CONTENT = "content";
        /** 通用扩展字段1 */
        public static final String DATA1 = "data1";
        /** 通用扩展字段2 */
        public static final String DATA2 = "data2";
        /** 通用扩展字段3 */
        public static final String DATA3 = "data3";
        /** 通用扩展字段4 */
        public static final String DATA4 = "data4";
        /** 通用扩展字段5 */
        public static final String DATA5 = "data5";
    }
}