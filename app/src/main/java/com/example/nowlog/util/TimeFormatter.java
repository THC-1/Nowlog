package com.example.nowlog.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimeFormatter {
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long THREE_DAYS = TimeUnit.DAYS.toMillis(3);

    public static String format(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;

        if (diff < MINUTE) {
            return "刚刚";
        } else if (diff < HOUR) {
            long minutes = diff / MINUTE;
            return minutes + "分钟前";
        } else if (diff < DAY) {
            long hours = diff / HOUR;
            return hours + "小时前";
        } else if (diff < 2 * DAY) {
            return "昨天";
        } else if (diff < THREE_DAYS) {
            long days = diff / DAY;
            return days + "天前";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
            return sdf.format(new Date(timestamp));
        }
    }
}
