package net.jcotton.dayschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity {
    final static Uri baseUri = Uri.parse("https://iodine.tjhsst.edu/ajax/dayschedule/json_exp");
    final SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyyMMdd");
    final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
    final static CloseableHttpClient client = HttpClients.createDefault();
    final static HttpGet getter = new HttpGet();
    ArrayAdapter<Period> periodsAdapter;
    ArrayList<Period> periods;
    ListView periodsView;
    TextView dayName, daySummary;
    final Object lock = new Object();
    GetDaysTask task;
    ArrayList<Day> days;
    int dayIndex;
    Calendar start, end;

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        periodsView = (ListView)findViewById(R.id.periods);
        dayName = (TextView)findViewById(R.id.dayName);
        daySummary = (TextView)findViewById(R.id.daySummary);

        start = Calendar.getInstance();
        end = Calendar.getInstance();
        start.add(Calendar.DATE, -3);
        end.add(Calendar.DATE, 3);
        periods = new ArrayList<>(11); // 1-7 + 8A + 8B + Lunch + Break
        periodsAdapter = new ArrayAdapter<Period>(
            this,
            android.R.layout.simple_list_item_1,
            periods
        );
        periodsView.setAdapter(periodsAdapter);
        if(isConnected())
            task = (GetDaysTask)new GetDaysTask(this).execute(start, end);
        else
            loadFromCache(start, end);
    }

    private void loadFromCache(Calendar start, Calendar end) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(getString(R.string.waitMessage));
        dialog.setIndeterminate(true);
        try(BufferedReader reader = new BufferedReader(new FileReader(getCacheDir().getAbsolutePath() + "/cache.json"))) {
            dialog.show();
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null)
                sb.append(line).append("\n");
            days = jsonToDays(sb.toString(), start, end);
        } catch(FileNotFoundException e) {
            // by this point we're not net connected
            new AlertDialog.Builder(this)
                    .setMessage("Cannot load schedule. Please enable WiFi or cellular and try again.")
                    .setTitle("Error")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            MainActivity.this.recreate();
                        }
                    })
                    .show();
        } catch(IOException e) {
            throw new RuntimeException(e);
        } finally {
            dialog.dismiss();
        }
    }

    private void saveToCache() {
        try(JsonWriter writer = new JsonWriter(new FileWriter(getCacheDir().getAbsolutePath() + "/cache.json"))) {
            writer.beginObject();
            for(Day day : days) {
                day.writeJson(writer);
            }
            writer.endObject();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Day> jsonToDays(JsonReader reader, Calendar start, Calendar end) {

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setDay(int index) {
        if(index < 0 || index >= days.size()) {
            if(index < 0) {
                dayIndex = days.size() - 1;
                start.add(Calendar.DATE, -7);
                end.add(Calendar.DATE, -7);
            } else if(index >= days.size()) {
                dayIndex = 0;
                start.add(Calendar.DATE, 7);
                end.add(Calendar.DATE, 7);
            }
            task = (GetDaysTask)new GetDaysTask(this).execute(start, end);
            return;
        }
        dayIndex = index;
        periodsAdapter.setNotifyOnChange(false);
        periodsAdapter.clear();
        periodsAdapter.addAll(days.get(dayIndex).periods);
        periodsAdapter.notifyDataSetChanged();
    }

    public void nextDay(View view) {
        setDay(dayIndex + 1);
    }

    public void previousDay(View view) {
        setDay(dayIndex - 1);
    }

    class Day {
        Date date;
        ArrayList<Period> periods;
        String name;
        String summary;

        Day(ArrayList<Period> periods, String name, String summary, Date date) {
            this.periods = periods;
            this.name = name;
            this.summary = summary;
            this.date = date;
        }

        public void writeJson(JsonWriter writer) throws IOException {
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
    }

    class Period {
        int number;
        String name;
        Date start;
        Date end;

        Period(int number, String name, Date start, Date end) {
            this.number = number;
            this.name = name;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("%s: %tR - %tR", this.name, this.start, this.end);
        }

        public void writeJson(JsonWriter writer) throws IOException {
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
    }

    class GetDaysTask extends AsyncTask<Calendar, Void, ArrayList<Day>> {
        private ProgressDialog dialog;

        GetDaysTask(MainActivity activity) {
            dialog = new ProgressDialog(activity);
        }

        @Override
        protected void onPreExecute() {
            if(dialog.isShowing())
                dialog.dismiss();
            dialog.setMessage(getString(R.string.waitMessage));
            dialog.show();
        }

        @Override
        protected ArrayList<Day> doInBackground(Calendar... params) {
            Calendar start = params[0];
            Calendar end = params[1];
            ArrayList<Day> days;
            try {
                getter.setURI(new URI(baseUri.buildUpon()
                                             .appendQueryParameter("start", dayFormatter.format(start.getTime()))
                                             .appendQueryParameter("end", dayFormatter.format(end.getTime()))
                                             .build().toString()));
            } catch(URISyntaxException e) {
                e.printStackTrace();
            }
            try(CloseableHttpResponse response = client.execute(getter)) {
                String body = EntityUtils.toString(response.getEntity());
                days = jsonToDays(body, start, end);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
            return days;
        }

        @Override
        protected void onCancelled() {
            if(dialog.isShowing())
                dialog.dismiss();
        }

        @Override
        protected void onPostExecute(ArrayList<Day> arrayList) {
            if(dialog.isShowing())
                dialog.dismiss();
            days = arrayList;
            setDay(dayIndex);
        }
    }
}
