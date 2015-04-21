package net.jcotton.dayschedule;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import java.util.Arrays;


public class SettingsActivity extends Activity {
    private Spinner dateFormat;
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        settings = getSharedPreferences(MainActivity.PREFS_NAME, 0);
        editor = settings.edit();
        dateFormat = (Spinner)findViewById(R.id.dateFormat);
        dateFormat.setSelection(Arrays.asList(getResources().getStringArray(R.array.dateFormats)).indexOf(
                settings.getString("dateFormat", MainActivity.DATE_FORMAT_DEFAULT)
                                                                                                    ));
        dateFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString("dateFormat", getResources().getStringArray(R.array.dateFormats)[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
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

    @Override
    protected void onStop() {
        super.onStop();
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        intent.setAction(MainActivity.REFRESH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
