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

package net.micode.notes.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 *
 * 该类继承自 BroadcastReceiver，负责在系统启动或应用安装时，
 * 重新注册所有未过期的便签提醒闹钟。
 *
 * 主要功能：
 * - 监听系统启动完成事件（BOOT_COMPLETED）
 * - 查询数据库中所有未过期的提醒便签（ALERTED_DATE > 当前时间）
 * - 为每个提醒便签重新注册闹钟
 * - 确保设备重启后提醒功能仍然有效
 *
 * 使用场景：
 * - 设备重启后，系统会发送 BOOT_COMPLETED 广播
 * - 应用安装/更新后重新注册闹钟
 *
 * @author MiCode Open Source Community
 * @see AlarmReceiver 闹钟到期时的广播接收器
 * @see AlarmManager Android 闹钟服务
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * 数据库查询投影（PROJECTION）
     * 只查询便签 ID 和提醒时间两个字段，提高查询效率
     */
    private static final String[] PROJECTION = new String[]{
            NoteColumns.ID,           // 便签 ID（第0列）
            NoteColumns.ALERTED_DATE  // 提醒日期时间戳（第1列）
    };

    /** 便签 ID 在投影中的索引位置 */
    private static final int COLUMN_ID = 0;

    /** 提醒日期在投影中的索引位置 */
    private static final int COLUMN_ALERTED_DATE = 1;

    /**
     * 广播接收回调方法
     *
     * 当系统发送匹配的广播时（如 BOOT_COMPLETED），此方法被调用。
     * 会查询所有未过期的提醒便签，并为每个便签重新设置闹钟。
     *
     * @param context 应用上下文
     * @param intent  触发的广播 Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间（毫秒）
        long currentDate = System.currentTimeMillis();

        // 查询所有未过期的便签提醒
        // 条件：ALERTED_DATE > 当前时间 AND TYPE = TYPE_NOTE（普通便签）
        Cursor c = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,           // 便签表 URI
                PROJECTION,                        // 只查询 ID 和提醒时间
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[]{String.valueOf(currentDate)},  // 替换 "?" 占位符
                null                              // 不排序
        );

        // 遍历查询结果，为每个便签设置闹钟
        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    // 获取提醒时间戳
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 创建发送给 AlarmReceiver 的 Intent
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 设置 Intent 的数据为便签的 URI（用于在 AlarmReceiver 中识别是哪个便签）
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                            c.getLong(COLUMN_ID)));

                    // 创建 PendingIntent，用于在闹钟触发时启动 AlarmReceiver
                    // 注意：使用 PendingIntent.FLAG_IMMUTABLE 确保安全性（Android 12+ 要求）
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context,
                            0,           // requestCode（0 表示不使用）
                            sender,      // 原始 Intent
                            PendingIntent.FLAG_IMMUTABLE  // 不可变标志，安全性要求
                    );

                    // 获取 AlarmManager 系统服务
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 设置闹钟
                    // 参数说明：
                    // - AlarmManager.RTC_WAKEUP: 使用系统真实时间，唤醒设备
                    // - alertDate: 闹钟触发时间（毫秒时间戳）
                    // - pendingIntent: 触发时要执行的 PendingIntent
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext());  // 继续处理下一个便签
            }
            c.close();  // 关闭游标，释放资源
        }
    }
}