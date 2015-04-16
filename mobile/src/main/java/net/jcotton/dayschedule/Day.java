package net.jcotton.dayschedule;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.SimpleFormatter;

/**
* Created by Joshua on 2015-04-09.
*/
class Day {
    Date date;
    ArrayList<Period> periods;
    String name;
    String summary;

    public void writeJson(JsonWriter writer) throws IOException {
        SimpleDateFormat dayFormatter = MainActivity.dayFormatter;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.setTime(date);
        Calendar yesterday = (Calendar)tomorrow.clone();
        tomorrow.add(Calendar.DATE, 1);
        yesterday.add(Calendar.DATE, -1);
        writer.name(dayFormatter.format(date));
        writer.beginObject();
        writer.name("dayname").value(name);
        writer.name("summary").value(summary);
        writer.name("date")
                .beginObject()
                    .name("tomorrow").value(dayFormatter.format(tomorrow.getTime()))
                    .name("yesterday").value(dayFormatter.format(yesterday.getTime()))
                    .name("today").value(dayFormatter.format(date))
                .endObject();
        writer.name("schedule");
        writer.beginObject();
        writer.name("date").value(dayFormatter.format(date));
        writer.name("period");
        writer.beginArray();
        for(Period period : periods) {
            period.writeJson(writer);
        }
        writer.endArray(); // period
        writer.endObject(); // schedule
        writer.endObject(); // day
    }

    public static Day readJson(JsonReader reader) throws IOException {
        Day day = new Day();
        day.name = reader.nextName();
        reader.beginObject();
        while(reader.hasNext()) {
            switch(reader.nextName()) {
                case "dayname":
                    day.name = reader.nextString();
                    break;
                case "summary":
                    day.summary = reader.nextString();
                    break;
                case "date":
                    reader.skipValue();
                    break;
                case "schedule":
                    reader.beginObject();
                    while(reader.hasNext()) {
                        switch(reader.nextName()) {
                            case "date":
                                reader.skipValue();
                                break;
                            case "period":
                                day.periods = new ArrayList<>(11);
                                reader.beginArray();
                                while(reader.hasNext())
                                    try {
                                        day.periods.add(Period.readJson(reader));
                                    } catch(ParseException e) {
                                        e.printStackTrace();
                                    }
                                reader.endArray();
                        }
                    }
                    reader.endObject();
                    break;
            }
        }
        reader.endObject();
        return day;
    }
}
