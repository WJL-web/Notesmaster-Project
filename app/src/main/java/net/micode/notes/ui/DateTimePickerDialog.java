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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 *
 * 继承自 AlertDialog，提供一个用户友好的日期和时间选择界面。
 * 内部封装了 DateTimePicker 控件，支持年月日时分的选择。
 *
 * 主要功能：
 * - 显示日期时间选择器
 * - 支持 12/24 小时制显示
 * - 实时更新对话框标题显示当前选择的日期时间
 * - 通过回调接口返回用户确认的日期时间
 *
 * 使用场景：
 * - 设置便签的提醒时间
 * - 设置日程安排的时间
 *
 * @author MiCode Open Source Community
 * @see DateTimePicker 实际的日期时间选择控件
 * @see AlertDialog Android 对话框基类
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    /** 日历对象，用于存储用户选择的日期时间 */
    private Calendar mDate = Calendar.getInstance();

    /** 是否使用 24 小时制显示时间 */
    private boolean mIs24HourView;

    /** 日期时间设置完成后的回调监听器 */
    private OnDateTimeSetListener mOnDateTimeSetListener;

    /** 日期时间选择器控件 */
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间设置监听器接口
     *
     * 当用户在对话框中点击"确定"按钮时触发，
     * 将选中的日期时间返回给调用方。
     */
    public interface OnDateTimeSetListener {
        /**
         * 日期时间被设置时回调
         *
         * @param dialog 当前对话框实例
         * @param date   用户选择的日期时间（毫秒时间戳）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造方法
     *
     * 创建日期时间选择对话框，并初始化为指定的日期时间。
     *
     * @param context 上下文对象
     * @param date    初始日期时间（毫秒时间戳）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建日期时间选择器控件
        mDateTimePicker = new DateTimePicker(context);
        // 将选择器设置为对话框的内容视图
        setView(mDateTimePicker);

        // 设置日期时间选择器的值变化监听器
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            /**
             * 当用户在日期时间选择器上滚动选择时调用
             *
             * @param view        日期时间选择器实例
             * @param year        选择的年份
             * @param month       选择的月份（0-11，0 表示一月）
             * @param dayOfMonth  选择的日期（1-31）
             * @param hourOfDay   选择的小时（0-23）
             * @param minute      选择的分钟（0-59）
             */
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                // 更新日历对象中的各个字段
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新对话框标题显示当前选择的日期时间
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 设置初始日期时间
        mDate.setTimeInMillis(date);
        // 将秒数归零（提醒只精确到分钟）
        mDate.set(Calendar.SECOND, 0);
        // 同步日期时间选择器的显示
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置对话框按钮
        // 确定按钮：使用 this 作为点击监听器（当前类实现了 OnClickListener）
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        // 取消按钮：不设置监听器（点击后自动关闭对话框）
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统设置判断是否使用 24 小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 更新对话框标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用 24 小时制显示时间
     *
     * @param is24HourView true 表示 24 小时制，false 表示 12 小时制（上下午）
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间设置完成的回调监听器
     *
     * @param callBack 实现了 OnDateTimeSetListener 接口的回调对象
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题
     *
     * 根据指定的日期时间，格式化为可读的日期时间字符串，
     * 并设置为对话框的标题。
     *
     * @param date 日期时间（毫秒时间戳）
     */
    private void updateTitle(long date) {
        // 定义格式化标志
        int flag = DateUtils.FORMAT_SHOW_YEAR |   // 显示年份
                DateUtils.FORMAT_SHOW_DATE |   // 显示日期（月/日）
                DateUtils.FORMAT_SHOW_TIME;    // 显示时间

        // 根据 12/24 小时制设置时间格式标志
        // 注意：原代码这里写错了，两个分支都使用了 FORMAT_24HOUR
        // 正确的应该是：mIs24HourView ? DateUtils.FORMAT_24HOUR : 0
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;

        // 格式化日期时间并设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框按钮点击回调
     *
     * 当用户点击"确定"按钮时调用此方法。
     * 如果设置了回调监听器，则回调并传递用户选择的日期时间。
     *
     * @param dialog 触发点击的对话框
     * @param arg1   被点击的按钮标识（未使用）
     */
    public void onClick(DialogInterface dialog, int arg1) {
        if (mOnDateTimeSetListener != null) {
            // 触发回调，传递当前选择的日期时间
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}