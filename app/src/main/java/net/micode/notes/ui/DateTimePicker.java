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

// Java标准库导入
import java.text.DateFormatSymbols;    // 日期格式符号（用于获取AM/PM字符串）
import java.util.Calendar;             // 日历类，用于日期时间计算

// Android相关导入
import android.content.Context;        // 上下文对象
import android.text.format.DateFormat; // 日期格式化工具
import android.view.View;              // 视图基类
import android.widget.FrameLayout;     // 帧布局容器
import android.widget.NumberPicker;    // 数字选择器（滚轮控件）

// 项目内部导入
import net.micode.notes.R;             // 资源文件

/**
 * DateTimePicker 类 - 日期时间选择器
 *
 * 作用：提供一个用户友好的日期时间选择控件
 * 继承自 FrameLayout，组合多个 NumberPicker 实现滚轮选择
 *
 * 功能：
 * 1. 日期选择（显示星期和月日，如 "05.16 星期四"）
 * 2. 小时选择（支持12小时制和24小时制）
 * 3. 分钟选择（0-59）
 * 4. AM/PM 选择（12小时制时显示）
 * 5. 支持通过代码设置/获取日期时间
 * 6. 提供日期时间变化监听器
 *
 * 布局文件：R.layout.datetime_picker
 */
public class DateTimePicker extends FrameLayout {

    // ==================== 常量定义 ====================

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLE_STATE = true;

    /** 半天的小时数（12小时） */
    private static final int HOURS_IN_HALF_DAY = 12;

    /** 全天的小时数（24小时） */
    private static final int HOURS_IN_ALL_DAY = 24;

    /** 一周的天数（7天） */
    private static final int DAYS_IN_ALL_WEEK = 7;

    /** 日期选择器最小值（0） */
    private static final int DATE_SPINNER_MIN_VAL = 0;

    /** 日期选择器最大值（6，一周7天） */
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;

    /** 24小时制小时选择器最小值（0） */
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;

    /** 24小时制小时选择器最大值（23） */
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;

    /** 12小时制小时选择器最小值（1） */
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;

    /** 12小时制小时选择器最大值（12） */
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;

    /** 分钟选择器最小值（0） */
    private static final int MINUT_SPINNER_MIN_VAL = 0;

    /** 分钟选择器最大值（59） */
    private static final int MINUT_SPINNER_MAX_VAL = 59;

    /** AM/PM选择器最小值（0） */
    private static final int AMPM_SPINNER_MIN_VAL = 0;

    /** AM/PM选择器最大值（1） */
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    // ==================== UI组件 ====================

    /** 日期选择器（显示星期和月日） */
    private final NumberPicker mDateSpinner;

    /** 小时选择器 */
    private final NumberPicker mHourSpinner;

    /** 分钟选择器 */
    private final NumberPicker mMinuteSpinner;

    /** AM/PM选择器（上午/下午） */
    private final NumberPicker mAmPmSpinner;

    /** 当前日期时间（Calendar对象） */
    private Calendar mDate;

    /** 日期显示值数组（存储一周每天的显示文本） */
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    /** 是否为上午（true=上午，false=下午） */
    private boolean mIsAm;

    /** 是否为24小时制（true=24小时制，false=12小时制） */
    private boolean mIs24HourView;

    /** 控件是否启用 */
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    /** 是否正在初始化（用于避免初始化时触发回调） */
    private boolean mInitialising;

    /** 日期时间变化监听器 */
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    // ==================== 监听器实现 ====================

    /**
     * 日期变化监听器
     * 当用户滚动日期选择器时，更新当前日期
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据滚动差值调整日期
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            // 更新日期控件显示
            updateDateControl();
            // 通知日期时间已变化
            onDateTimeChanged();
        }
    };

    /**
     * 小时变化监听器
     * 处理小时变化时的逻辑：
     * - 12小时制下跨越12点时要切换AM/PM
     * - 24小时制下跨越0点时要改变日期
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();

            if (!mIs24HourView) {
                // ========== 12小时制处理 ==========
                // 从上午11点滚动到下午12点：日期+1
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 从下午12点滚动到上午11点：日期-1
                else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }

                // 跨越12点（11↔12）时，切换AM/PM
                if ((oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) ||
                        (oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1)) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();  // 更新AM/PM控件
                }
            } else {
                // ========== 24小时制处理 ==========
                // 从23点滚动到0点：日期+1
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 从0点滚动到23点：日期-1
                else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 计算实际小时数（24小时制）
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();  // 通知变化

            // 如果日期发生变化，更新日期相关控件
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟变化监听器
     * 处理分钟滚动时的边界情况（59→0 或 0→59）
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;

            // 处理分钟滚动的边界：从59到0时小时+1，从0到59时小时-1
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }

            if (offset != 0) {
                // 调整小时
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl();

                // 更新AM/PM状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                    updateAmPmControl();
                } else {
                    mIsAm = true;
                    updateAmPmControl();
                }
            }
            // 设置分钟
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * AM/PM变化监听器
     * 当用户切换上午/下午时，调整小时数（±12小时）
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            if (mIsAm) {
                // 切换到上午：小时-12
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                // 切换到下午：小时+12
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    // ==================== 监听器接口 ====================

    /**
     * 日期时间变化监听器接口
     * 当用户滚动任何选择器时回调
     */
    public interface OnDateTimeChangedListener {
        /**
         * 日期时间变化时的回调方法
         *
         * @param view DateTimePicker实例
         * @param year 年份
         * @param month 月份（0-11）
         * @param dayOfMonth 日期（1-31）
         * @param hourOfDay 小时（0-23）
         * @param minute 分钟（0-59）
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    // ==================== 构造函数 ====================

    /**
     * 构造函数 - 使用当前时间
     *
     * @param context 上下文对象
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造函数 - 使用指定时间，自动判断时间制式
     *
     * @param context 上下文对象
     * @param date 初始日期时间（毫秒时间戳）
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 构造函数 - 完整参数版本
     *
     * 初始化所有UI组件并设置初始值
     *
     * @param context 上下文对象
     * @param date 初始日期时间（毫秒时间戳）
     * @param is24HourView 是否24小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);

        // 初始化日历和状态
        mDate = Calendar.getInstance();
        mInitialising = true;
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;

        // 加载布局文件
        inflate(context, R.layout.datetime_picker, this);

        // ========== 初始化日期选择器 ==========
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // ========== 初始化小时选择器 ==========
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // ========== 初始化分钟选择器 ==========
        mMinuteSpinner = (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);  // 长按时更新间隔100ms
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // ========== 初始化AM/PM选择器 ==========
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();  // 获取系统AM/PM字符串
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新所有控件到初始状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置时间制式
        set24HourView(is24HourView);

        // 设置当前时间
        setCurrentDate(date);

        // 设置启用状态
        setEnabled(isEnabled());

        // 初始化完成
        mInitialising = false;
    }

    // ==================== 启用/禁用方法 ====================

    /**
     * 设置控件启用状态
     *
     * @param enabled true=启用，false=禁用
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    /**
     * 获取控件启用状态
     *
     * @return true=启用，false=禁用
     */
    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    // ==================== 日期时间设置/获取方法 ====================

    /**
     * 获取当前日期时间（毫秒时间戳）
     *
     * @return 当前日期时间的毫秒时间戳
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前日期时间
     *
     * @param date 日期时间（毫秒时间戳）
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前日期时间（各分量单独指定）
     *
     * @param year 年份
     * @param month 月份（0-11）
     * @param dayOfMonth 日期（1-31）
     * @param hourOfDay 小时（0-23）
     * @param minute 分钟（0-59）
     */
    public void setCurrentDate(int year, int month,
                               int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    // ==================== 年份操作 ====================

    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    // ==================== 月份操作 ====================

    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    // ==================== 日期操作 ====================

    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    // ==================== 小时操作 ====================

    /**
     * 获取当前小时（24小时制，0-23）
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前小时（根据当前制式返回对应值）
     * 24小时制：返回0-23
     * 12小时制：返回1-12
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;  // 下午：13→1, 14→2...
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;  // 0点显示为12
            }
        }
    }

    /**
     * 设置当前小时（24小时制，0-23）
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);

        if (!mIs24HourView) {
            // 12小时制下需要同步更新AM/PM状态
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;  // 下午
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;  // 13→1, 14→2...
                }
            } else {
                mIsAm = true;   // 上午
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;  // 0点显示为12
                }
            }
            updateAmPmControl();
        }

        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    // ==================== 分钟操作 ====================

    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    // ==================== 时间制式操作 ====================

    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * 设置时间制式（24小时制 或 12小时制AM/PM）
     *
     * @param is24HourView true=24小时制，false=12小时制AM/PM
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;

        // 24小时制时隐藏AM/PM选择器
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);

        int hour = getCurrentHourOfDay();
        updateHourControl();      // 更新小时选择器的范围
        setCurrentHour(hour);     // 重新设置小时值（适配新制式）
        updateAmPmControl();      // 更新AM/PM显示
    }

    // ==================== UI更新方法 ====================

    /**
     * 更新日期控件显示
     *
     * 显示格式："MM.dd EEEE"（如 "05.16 星期四"）
     * 以当前日期为中心，显示前后各3天，共7天
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);  // 往前推3天

        mDateSpinner.setDisplayedValues(null);
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);  // 设置中间项为当前日期
        mDateSpinner.invalidate();
    }

    /**
     * 更新AM/PM控件显示
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时控件范围
     * 根据时间制式设置小时选择器的取值范围
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    // ==================== 监听器管理 ====================

    /**
     * 设置日期时间变化监听器
     *
     * @param callback 监听器回调，为null时不执行任何操作
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 通知日期时间已变化
     * 触发监听器的回调方法
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}