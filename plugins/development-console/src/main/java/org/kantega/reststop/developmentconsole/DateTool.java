package org.kantega.reststop.developmentconsole;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 *
 */
public class DateTool {

    private SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private SimpleDateFormat todayFormat = new SimpleDateFormat("HH:mm:ss");

    public String formatTime(long time) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        Date date = new Date(time);
        Date startOfDay = cal.getTime();
        if(date.before(startOfDay)) {
            return dayFormat.format(date);
        } else {
            return todayFormat.format(date);
        }
    }
}
