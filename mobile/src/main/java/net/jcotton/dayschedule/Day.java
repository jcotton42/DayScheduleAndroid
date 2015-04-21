package net.jcotton.dayschedule;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
* Created by Joshua on 2015-04-09.
*/
class Day {
    Date date;
    ArrayList<Period> periods;
    String name;
    String summary;

    public void writeJson(JsonWriter writer) throws IOException {
        SimpleDateFormat apiDayFormat = MainActivity.apiDayFormat;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.setTime(date);
        Calendar yesterday = (Calendar)tomorrow.clone();
        tomorrow.add(Calendar.DATE, 1);
        yesterday.add(Calendar.DATE, -1);
        writer.name(apiDayFormat.format(date));
        writer.beginObject();
        writer.name("dayname").value(name);
        writer.name("summary").value(summary);
        writer.name("date")
                .beginObject()
                    .name("tomorrow").value(apiDayFormat.format(tomorrow.getTime()))
                    .name("yesterday").value(apiDayFormat.format(yesterday.getTime()))
                    .name("today").value(apiDayFormat.format(date))
                .endObject();
        writer.name("schedule");
        writer.beginObject();
        writer.name("date").value(apiDayFormat.format(date));
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
        SimpleDateFormat apiDayFormat = MainActivity.apiDayFormat;
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
                    try {
                        reader.beginObject();
                        while(!reader.nextName().equals("today"))
                            reader.skipValue();
                        day.date = apiDayFormat.parse(reader.nextString());
                        if(reader.hasNext())
                            reader.skipValue();
                        reader.endObject();
                    } catch(ParseException e) {
                        e.printStackTrace();
                    }
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
                                while(reader.hasNext()) {
                                    try {
                                        day.periods.add(Period.readJson(reader));
                                    } catch(ParseException e) {
                                        e.printStackTrace();
                                    }
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
