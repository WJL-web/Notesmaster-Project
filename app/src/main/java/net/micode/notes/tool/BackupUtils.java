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

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

// BackupUtils class - Utility class for backup and restore operations
/* 备份工具类 - 用于笔记的备份和恢复操作，支持导出为文本文件 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";
    // Singleton stuff
    /* 单例模式相关 */
    private static BackupUtils sInstance;

    // Singleton pattern - get instance
    /* 单例模式 - 获取实例 */
    /* @param context 上下文对象 */
    /* @return BackupUtils 单例实例 */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * Following states are signs to represents backup or restore
     * status
     */
    /* 以下状态码用于表示备份或恢复的状态 */
    
    // Currently, the sdcard is not mounted
    /* SD卡未挂载 */
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // The backup file not exist
    /* 备份文件不存在 */
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // The data is not well formated, may be changed by other programs
    /* 数据格式错误，可能被其他程序修改 */
    public static final int STATE_DATA_DESTROIED               = 2;
    // Some run-time exception which causes restore or backup fails
    /* 系统运行时异常导致备份或恢复失败 */
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // Backup or restore success
    /* 备份或恢复成功 */
    public static final int STATE_SUCCESS                      = 4;

    // Text export utility instance
    /* 文本导出工具实例 */
    private TextExport mTextExport;

    // Private constructor
    /* 私有构造函数 */
    /* @param context 上下文对象 */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    // Check if external storage is available
    /* 检查外部存储（SD卡）是否可用 */
    /* @return true可用，false不可用 */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    // Export notes to text file
    /* 导出笔记到文本文件 */
    /* @return 状态码（成功/失败） */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    // Get exported text file name
    /* 获取导出的文本文件名 */
    /* @return 文件名 */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    // Get exported text file directory
    /* 获取导出的文本文件目录 */
    /* @return 文件目录路径 */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    // TextExport inner class - handles text export logic
    /* 文本导出内部类 - 处理将笔记导出为文本文件的具体逻辑 */
    private static class TextExport {
        // Note table projection for query
        /* 笔记表查询投影 */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,              // 笔记ID
                NoteColumns.MODIFIED_DATE,   // 修改日期
                NoteColumns.SNIPPET,         // 摘要
                NoteColumns.TYPE             // 类型（文件夹/笔记）
        };

        // Note table column index constants
        /* 笔记表列索引常量 */
        private static final int NOTE_COLUMN_ID = 0;               // ID列索引
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;    // 修改日期列索引
        private static final int NOTE_COLUMN_SNIPPET = 2;          // 摘要列索引

        // Data table projection for query
        /* 数据表查询投影 */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,    // 内容
                DataColumns.MIME_TYPE,  // MIME类型
                DataColumns.DATA1,      // 扩展数据1
                DataColumns.DATA2,      // 扩展数据2
                DataColumns.DATA3,      // 扩展数据3
                DataColumns.DATA4,      // 扩展数据4
        };

        // Data table column index constants
        /* 数据表列索引常量 */
        private static final int DATA_COLUMN_CONTENT = 0;        // 内容列索引
        private static final int DATA_COLUMN_MIME_TYPE = 1;      // MIME类型列索引
        private static final int DATA_COLUMN_CALL_DATE = 2;      // 通话日期列索引
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;    // 电话号码列索引

        // Text format array
        /* 文本格式数组 - 定义导出文本的格式模板 */
        private final String [] TEXT_FORMAT;
        
        // Format type constants
        /* 格式类型常量 */
        private static final int FORMAT_FOLDER_NAME          = 0;  // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE            = 1;  // 笔记日期格式
        private static final int FORMAT_NOTE_CONTENT         = 2;  // 笔记内容格式

        private Context mContext;           // 上下文对象
        private String mFileName;           // 导出文件名
        private String mFileDirectory;      // 导出文件目录

        // Constructor
        /* 构造函数 */
        /* @param context 上下文对象 */
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        // Get format string by id
        /* 根据ID获取格式字符串 */
        /* @param id 格式ID */
        /* @return 格式字符串 */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * Export the folder identified by folder id to text
         */
        /* 将指定文件夹ID下的所有笔记导出到文本 */
        /* @param folderId 文件夹ID */
        /* @param ps 打印流 */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // Query notes belong to this folder
            /* 查询属于该文件夹的笔记 */
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                        folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // Print note's last modified date
                        /* 打印笔记的最后修改日期 */
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        /* 查询属于该笔记的数据 */
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * Export note identified by id to a print stream
         */
        /* 将指定ID的笔记导出到打印流 */
        /* @param noteId 笔记ID */
        /* @param ps 打印流 */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                        noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // Print phone number
                            /* 打印电话号码 */
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // Print call date
                            /* 打印通话日期 */
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // Print call attachment location
                            /* 打印通话附件位置 */
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // print a line separator between note
            /* 在笔记之间打印分隔符 */
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * Note will be exported as text which is user readable
         */
        /* 将笔记导出为用户可读的文本格式 */
        /* @return 状态码 */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            // First export folder and its notes
            /* 首先导出文件夹及其中的笔记 */
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // Print folder's name
                        /* 打印文件夹名称 */
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // Export notes in root's folder
            /* 导出根文件夹中的笔记 */
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        /* 查询属于该笔记的数据 */
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * Get a print stream pointed to the file {@generateExportedTextFile}
         */
        /* 获取指向导出文件的打印流 */
        /* @return 打印流，失败返回null */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * Generate the text file to store imported data
     */
    /* 生成用于存储导出数据的文本文件 */
    /* @param context 上下文对象 */
    /* @param filePathResId 文件路径资源ID */
    /* @param fileNameFormatResId 文件名格式资源ID */
    /* @return 生成的文件对象，失败返回null */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            if (!filedir.exists()) {
                filedir.mkdir();  // 创建目录
            }
            if (!file.exists()) {
                file.createNewFile();  // 创建文件
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}