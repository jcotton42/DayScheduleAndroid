package net.jcotton.dayschedule;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
* Created by Joshua on 2015-04-09.
*/
class Period {
    int number;
    String name;
    Date start;
    Date end;

    @Override
    public String toString() {
        return String.format("%s: %tR - %tR", this.name, this.start, this.end);
    }

    public void writeJson(JsonWriter writer) throws IOException {
        SimpleDateFormat timeFormatter = MainActivity.timeFormatter;
        writer
                .beginObject()
                .name("num").value(number)
                .name("name").value(name)
                .name("times")
                    .beginObject()
                    .name("start").value(timeFormatter.format(start))
                    .name("end").value(timeFormatter.format(end))
                    .name("times").value(timeFormatter.format(start) + " - " + timeFormatter.format(end))
                    .endObject()
                .endObject();
    }

    public static Period readJson(JsonReader reader) throws IOException, ParseException {
        SimpleDateFormat timeFormatter = MainActivity.timeFormatter;
        Period period = new Period();
        reader.beginObject();
        while(reader.hasNext()) {
            switch(reader.nextName()) {
                case "num":
                    period.number = reader.nextInt();
                    break;
                case "name":
                    period.name = reader.nextString();
                    break;
                case "times":
                    reader.beginObject();
                    while(reader.hasNext()) {
                        switch(reader.nextName()) {
                            case "start":
                                period.start = timeFormatter.parse(reader.nextString());
                                break;
                            case "end":
                                period.end = timeFormatter.parse(reader.nextString());
                                break;
                            case "times":
                                reader.skipValue();
                                break;
                        }
                    }
                    reader.endObject();
            }
        }
        reader.endObject();
        return period;
    }
}
