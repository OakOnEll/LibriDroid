package com.oakonell.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Duration {
    private static final int TEN = 10;
    private static final int SECONDS_IN_MINUTE = 60;
    private static final int MINUTES_IN_HOUR = 60;

    private final int hours;
    private final int minutes;
    private final int seconds;

    public Duration(int theHours, int theMinutes, int theSeconds) {
        // normalize the time units
        int tempSeconds = theSeconds % SECONDS_IN_MINUTE;
        int tempMinutes = theMinutes + (theSeconds / SECONDS_IN_MINUTE);
        int tempHours = theHours + (tempMinutes / MINUTES_IN_HOUR);
        tempMinutes = tempMinutes % MINUTES_IN_HOUR;

        hours = tempHours;
        minutes = tempMinutes;
        seconds = tempSeconds;
    }

    private static final Pattern COLON_PATTERN = Pattern.compile("(((\\d+):)?(\\d+):)?(\\d+)");
    private static final int HOURS_GROUP = 3;
    private static final int MINUTES_GROUP = 4;
    private static final int SECONDS_GROUP = 5;
    private static final int MS_IN_SECOND = 1000;

    public static Duration from(String string) {
        Matcher matcher = COLON_PATTERN.matcher(string);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format '"
                    + string + "'");
        }
        String hoursStr = matcher.group(HOURS_GROUP);
        String minutesStr = matcher.group(MINUTES_GROUP);
        String secondsStr = matcher.group(SECONDS_GROUP);

        int hours = 0;
        if (hoursStr != null) {
            hours = Integer.parseInt(hoursStr);
        }
        int minutes = 0;
        if (minutesStr != null) {
            minutes = Integer.parseInt(minutesStr);
        }
        int seconds = 0;
        if (secondsStr != null) {
            seconds = Integer.parseInt(secondsStr);
        }
        return new Duration(hours, minutes, seconds);
    }

    public int getHours() {
        return hours;
    }

    public int getMinutes() {
        return minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public int getTotalSeconds() {
        return MINUTES_IN_HOUR * SECONDS_IN_MINUTE * hours + SECONDS_IN_MINUTE * minutes + seconds;
    }

    public int getTotalMilliseconds() {
        return getTotalSeconds() * MS_IN_SECOND;
    }

    public Duration add(Duration other) {
        int totalSeconds = other.getSeconds() + seconds;
        int totalMinutes = totalSeconds / SECONDS_IN_MINUTE;
        totalSeconds = totalSeconds % SECONDS_IN_MINUTE;

        totalMinutes += other.getMinutes() + minutes;
        int totalHours = totalMinutes / MINUTES_IN_HOUR;
        totalMinutes = totalMinutes % MINUTES_IN_HOUR;

        totalHours += other.getHours() + hours;
        return new Duration(totalHours, totalMinutes, totalSeconds);
    }

    public Duration subtract(Duration other) {
        int totalSeconds = getTotalSeconds();
        int otherTotalSeconds = other.getTotalSeconds();
        return new Duration(0, 0, totalSeconds - otherTotalSeconds);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (hours != 0) {
            builder.append(hours);
            builder.append("h:");
        }
        if (minutes < TEN) {
            builder.append('0');
        }
        builder.append(minutes);
        builder.append("m:");
        if (seconds < TEN) {
            builder.append('0');
        }
        builder.append(seconds);
        builder.append('s');

        return builder.toString();
    }

}
