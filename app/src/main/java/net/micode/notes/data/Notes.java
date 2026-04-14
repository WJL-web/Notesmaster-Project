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

public class Notes {
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";
    public static final int TYPE_NOTE = 0;
    public static final int TYPE_FOLDER = 1;
    public static final int TYPE_SYSTEM = 2;

    public static final int ID_ROOT_FOLDER = 0;
    public static final int ID_TEMPARAY_FOLDER = -1;
    public static final int ID_CALL_RECORD_FOLDER = -2;
    public static final int ID_TRASH_FOLER = -3;

    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    public static final int TYPE_WIDGET_INVALIDE = -1;
    public static final int TYPE_WIDGET_2X = 0;
    public static final int TYPE_WIDGET_4X = 1;

    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 补全：文字便签接口定义
     */
    public interface TextNote extends DataColumns {
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");

        // 核心逻辑字段：解决 WorkingNote 和 NoteEditActivity 的报错
        public static final String MODE = DATA1;
        public static final int MODE_CHECK_LIST = 1;
    }

    /**
     * 补全：电话便签接口定义
     */
    public interface CallNote extends DataColumns {
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");

        // 核心逻辑字段：解决 DataUtils 和 WorkingNote 的报错
        public static final String CALL_DATE = DATA1;
        public static final String PHONE_NUMBER = DATA2;
    }

    public interface NoteColumns {
        public static final String ID = "_id";
        public static final String PARENT_ID = "parent_id";
        public static final String CREATED_DATE = "created_date";
        public static final String MODIFIED_DATE = "modified_date";
        public static final String ALERTED_DATE = "alert_date";
        public static final String SNIPPET = "snippet";
        public static final String WIDGET_ID = "widget_id";
        public static final String WIDGET_TYPE = "widget_type";
        public static final String BG_COLOR_ID = "bg_color_id";
        public static final String HAS_ATTACHMENT = "has_attachment";
        public static final String NOTES_COUNT = "notes_count";
        public static final String TYPE = "type";
        public static final String SYNC_ID = "sync_id";
        public static final String LOCAL_MODIFIED = "local_modified";
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";
        public static final String GTASK_ID = "gtask_id";
        public static final String VERSION = "version";
    }

    public interface DataColumns {
        public static final String ID = "_id";
        public static final String MIME_TYPE = "mime_type";
        public static final String NOTE_ID = "note_id";
        public static final String CREATED_DATE = "created_date";
        public static final String MODIFIED_DATE = "modified_date";
        public static final String CONTENT = "content";
        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
    }
}