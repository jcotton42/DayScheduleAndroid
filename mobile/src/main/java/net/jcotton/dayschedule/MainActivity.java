package net.jcotton.dayschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends Activity {
    public final static String REFRESH_SETTINGS = "REFRESH_SETTINGS";
    public final static String DATE_FORMAT_DEFAULT = "yyyy-MM-dd";
    public final static String PREFS_NAME = "DaySchedulePrefs";
    final static SimpleDateFormat apiDayFormat = new SimpleDateFormat("yyyyMMdd");
    final static SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
    private final static Uri baseUri = Uri.parse("https://iodine.tjhsst.edu/ajax/dayschedule/json_exp");
    private final static CloseableHttpClient client = HttpClients.createDefault();
    private final static HttpGet getter = new HttpGet();
    private static SimpleDateFormat dayDisplayFormat;
    private ArrayAdapter<Period> periodsAdapter;
    private ArrayList<Period> periods;
    private ListView periodsView;
    private TextView dayName, daySummary;
    private ArrayList<Day> days;
    private int dayIndex;
    private Calendar start, end;

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        switch(intent.getAction()) {
            case REFRESH_SETTINGS:
                loadSettings();
                break;
        }
    }

    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        dayDisplayFormat = new SimpleDateFormat(settings.getString("dateFormat", DATE_FORMAT_DEFAULT));
        periodsAdapter.notifyDataSetChanged(); // icky hack
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
        //noinspection Convert2Diamond
        periodsAdapter = new ArrayAdapter<Period>(
            this,
            android.R.layout.simple_list_item_1,
            periods
        );
        periodsView.setAdapter(periodsAdapter);
        loadSettings();
        if(isConnected())
            new GetDaysTask(this).execute(start, end);
        else
            loadFromCache();
    }

    private void loadFromCache() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(getString(R.string.waitMessage));
        dialog.setIndeterminate(true);
        try(JsonReader reader = new JsonReader(new FileReader(getCacheDir().getAbsolutePath() + "/cache.json"))) {
            dialog.show();
            days = jsonToDays(reader);
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

    private ArrayList<Day> jsonToDays(JsonReader reader) throws IOException {
        ArrayList<Day> days = new ArrayList<>();
        reader.beginObject();
        while(reader.hasNext())
            days.add(Day.readJson(reader));
        reader.endObject();
        return days;
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveToCache();
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
            startActivity(new Intent(getBaseContext(), SettingsActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }

    private void setDay(int index) {
        if(index < 0 || index >= days.size()) {
            if(index < 0) {
                dayIndex = days.size() - 1;
                start.add(Calendar.DATE, -7);
                end.add(Calendar.DATE, -7);
            } else {
                dayIndex = 0;
                start.add(Calendar.DATE, 7);
                end.add(Calendar.DATE, 7);
            }
            new GetDaysTask(this).execute(start, end);
            return;
        }
        dayIndex = index;
        Day day = days.get(dayIndex);
        dayName.setText(dayDisplayFormat.format(day.date));
        daySummary.setText(day.summary);
        periodsAdapter.setNotifyOnChange(false);
        periodsAdapter.clear();
        periodsAdapter.addAll(day.periods);
        periodsAdapter.notifyDataSetChanged();
    }

    @SuppressWarnings("UnusedParameters")
    public void nextDay(View view) {
        setDay(dayIndex + 1);
    }

    @SuppressWarnings("UnusedParameters")
    public void previousDay(View view) {
        setDay(dayIndex - 1);
    }

    class GetDaysTask extends AsyncTask<Calendar, Void, ArrayList<Day>> {
        private final ProgressDialog dialog;

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
                                             .appendQueryParameter("start", apiDayFormat.format(start.getTime()))
                                             .appendQueryParameter("end", apiDayFormat.format(end.getTime()))
                                             .build().toString()));
            } catch(URISyntaxException e) {
                e.printStackTrace();
            }
            try(CloseableHttpResponse response = client.execute(getter)) {
                days = jsonToDays(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
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
