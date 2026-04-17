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
 * distributed under the License is distributed on an "AS IS"分隔符
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


/**
 * 小米便签 备份工具类
 * 核心功能：将本地便签/文件夹/通话记录 导出为 TXT 文本文件保存到 SD 卡
 * 采用单例模式，内部封装 TextExport 实现具体导出逻辑
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";

    // 单例实例
    private static BackupUtils sInstance;

    /**
     * 获取单例对象（线程安全）
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    // -------------------------- 导出/恢复状态常量定义 --------------------------
    // SD 卡未挂载
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // 数据格式损坏
    public static final int STATE_DATA_DESTROIED               = 2;
    // 系统运行时异常
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // 操作成功
    public static final int STATE_SUCCESS                      = 4;

    // 文本导出内部实现类实例
    private TextExport mTextExport;

    /**
     * 私有构造方法（单例）
     * 初始化文本导出工具
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 判断外部存储（SD卡）是否可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 对外暴露的导出方法：执行文本导出
     * @return 导出状态码
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出文件的目录路径
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 文本导出内部实现类
     * 负责：查询数据库 → 格式化数据 → 写入 TXT 文件
     */
    private static class TextExport {
        // 便签表查询字段：ID、修改时间、摘要、类型
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        // 便表字段索引
        private static final int NOTE_COLUMN_ID = 0;
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;
        private static final int NOTE_COLUMN_SNIPPET = 2;

        // 便签内容表查询字段
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,      // 内容
                DataColumns.MIME_TYPE,    // 数据类型
                DataColumns.DATA1,        // 扩展字段1（通话日期）
                DataColumns.DATA2,
                DataColumns.DATA3,        // 扩展字段3（电话号码）
                DataColumns.DATA4,
        };

        // 内容表字段索引
        private static final int DATA_COLUMN_CONTENT = 0;
        private static final int DATA_COLUMN_MIME_TYPE = 1;
        private static final int DATA_COLUMN_CALL_DATE = 2;    // 通话日期
        private static final int DATA_COLUMN_PHONE_NUMBER = 4; // 电话号码

        // 导出文本格式化字符串数组（从资源文件读取）
        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME          = 0; // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE            = 1; // 便签日期格式
        private static final int FORMAT_NOTE_CONTENT         = 2; // 便签内容格式

        private Context mContext;
        private String mFileName;      // 导出文件名
        private String mFileDirectory; // 导出文件目录

        public TextExport(Context context) {
            // 加载导出格式配置
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 获取指定类型的格式化字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出【单个文件夹】下的所有便签到文本
         * @param folderId 文件夹ID
         * @param ps 文本输出流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有便签
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                            folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 写入便签修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 导出该便签的详细内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close(); // 关闭游标
            }
        }

        /**
         * 导出【单个便签】的内容到文本
         * @param noteId 便签ID
         * @param ps 文本输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            // 查询该便签对应的所有内容数据
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                            noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        // 处理【通话记录便签】
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 获取通话记录信息
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 写入电话号码
                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // 写入通话日期
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // 写入通话归属地
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        }
                        // 处理【普通文本便签】
                        else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close(); // 关闭游标
            }

            // 便签之间写入分隔符，区分不同便签
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 【核心导出方法】
         * 将所有便签、文件夹导出为 TXT 文件
         * @return 导出状态
         */
        public int exportToText() {
            // 1. 检查 SD 卡是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 2. 获取文件输出流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // 3. 导出【所有文件夹】+ 文件夹内的便签（不含回收站）
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称（通话记录文件夹特殊处理）
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        // 写入文件夹名称
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        // 导出该文件夹下的便签
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // 4. 导出【根目录下的便签】（不属于任何文件夹的便签）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 写入便签修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 导出便签内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            // 5. 关闭流，完成导出
            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 创建并获取导出文件的输出流
         */
        private PrintStream getExportToTextPrintStream() {
            // 生成备份文件（SD卡指定目录）
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            // 记录文件名和目录
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
     * 在 SD 卡上生成备份文件
     * @param context 上下文
     * @param filePathResId 文件目录资源ID
     * @param fileNameFormatResId 文件名格式资源ID
     * @return 生成的文件对象
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        // 拼接路径：SD卡根目录 + 配置的文件路径
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());

        // 拼接文件名：路径 + 日期格式化的文件名
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            // 目录不存在则创建
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 文件不存在则创建
            if (!file.exists()) {
                file.createNewFile();
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