package com.example.yuleil01.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "NfcDemo";

    private TextView mTextView;
    private NfcAdapter mNfcAdapter;

    //private static String debugMsg = "initial";
    private AlertDialog autoUpdate;
    private int currentAppVerCode;
    private int latestAppVerCode;
    private String currentAppVerName;
    private String latestAppVerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.textView_explanation);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!mNfcAdapter.isEnabled()) {
            mTextView.setText("NFC is disabled.");
        } else {
            mTextView.setText(R.string.explanation);
        }

        //handleIntent(getIntent());
        currentAppVerCode = getVersionCode(this);
        currentAppVerName = getVersionName(this);
        //new CheckLatestVerInfo().execute();

        AutoCompleteTextView abc = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView);
        final String[] ezlinkcards = new String[] {
                "Abcdefs",
                "1009622007955551",
                "1009622008118657",
                "8009150000708910"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, ezlinkcards);
        abc.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        filters[0].addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        /*/try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }//*/

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);

        //Toast.makeText(this, "yes detected...", Toast.LENGTH_LONG).show();
        //mTextView.setText("i found u.");
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
            mTextView.setText("i am here.");
        } else {
            //posb everyday card: android.nfc.tech.IsoDep android.nfc.tech.NfcB
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            IsoDep isoDep = IsoDep.get(tag);
            try {
                isoDep.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }

            isoDep.getHistoricalBytes();
            mTextView.setText("yes: " + isoDep.getHiLayerResponse().toString());
        }
    }
    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     *
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                mTextView.setText("Read content: " + result);
            }
        }
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    private static int getVersionCode(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {}
        return 0;
    }

    private static String getVersionName(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException ex) {}
        return null;
    }

    private InputStream openHttpConnection() throws IOException {
        InputStream in = null;
        int response = -1;

        //String httpsAddr = "https://drive.google.com/file/d/1cSuOyu3lo0Vu5rTBWb1QDogXfIxlM-GG/view";
        String httpsAddr = "https://raw.githubusercontent.com/andesliuyulei/MyApplication/master/app/src/main/res/versioninfo.txt";
        URL url = new URL(httpsAddr);
        URLConnection conn = url.openConnection();

        if (!(conn instanceof HttpURLConnection)) {
            throw new IOException("Not an HTTP connection");
        }
        try {
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setAllowUserInteraction(false);
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod("GET");
            httpConn.connect();

            response = httpConn.getResponseCode();
            if (response == HttpURLConnection.HTTP_OK) {
                in = httpConn.getInputStream();
            }
        } catch (Exception ex) {
            throw new IOException("Error connecting");
        }
        return in;
    }

    private class CheckLatestVerInfo extends AsyncTask<Void,Void,InputStream> {
        protected void onPreExecute() {
            //display progress dialog.
        }

        protected InputStream doInBackground(Void... params) {
            try {
                return openHttpConnection();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(InputStream in) {
            //dismiss progress dialog and update ui
            int BUFFER_SIZE = 2000;
            String str = "";
            if (in != null) {
                InputStreamReader isr = new InputStreamReader(in);
                int charRead;
                char[] inputBuffer = new char[BUFFER_SIZE];
                try {
                    while ((charRead = isr.read(inputBuffer)) > 0) {
                        // ---convert the chars to a String---
                        String readString = String.copyValueOf(inputBuffer, 0, charRead);
                        str += readString;
                        inputBuffer = new char[BUFFER_SIZE];
                    }
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            String[] versioninfo = str.split(":");
            latestAppVerCode = Integer.parseInt(versioninfo[0]);
            latestAppVerName = versioninfo[1];
            if (latestAppVerCode > currentAppVerCode) {
                //autoUpdate.show();
                Intent updateIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://raw.githubusercontent.com/andesliuyulei/MyApplication/master/app/src/main/res/app-debug.apk"));
                startActivity(updateIntent);
            }
            mTextView.setText("current version code: " + currentAppVerCode +
                    "\ncurrent version name: " + currentAppVerName +
                    "\nlatest version code: " + latestAppVerCode +
                    "\nlatest version name: " + latestAppVerName);
        }
    }
}
