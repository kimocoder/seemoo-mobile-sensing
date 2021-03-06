package com.example.remotecontroller;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ServiceInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Timer;

/**
 * @author Timm Lippert
 *
 * Remote control application for DataCollection app to set the same start time and the duration of the recording
 */

public class MainActivity extends AppCompatActivity {


    private String TAG = "RemoteController";
    private NsdManager mNsdManager;
    private ServiceInfo info;
    private ServerSocket mServerSocket;
    private ArrayList<Socket> clients = new ArrayList<>();
    public HashMap<Socket, String> map = new HashMap();
    private ListView listView;
    public ArrayAdapter<String> listAdapter;
    private Timer t = new Timer();
    private ArrayList<AliveChecker> listOfChecker= new ArrayList<>();

    private String time = "";
    private String duration = "8:00";
    private PowerManager.WakeLock wl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //element to show the registered collector phones
        listView = findViewById(R.id.list_view);
        listAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        listView.setAdapter(listAdapter);
        //t.scheduleAtFixedRate(aliveChecker,0,1000);

        //Start the NsdManager in the background
        Thread backgroud = new Thread(new Runnable() {

            @Override
            public void run() {
                registerService(1337);
                //discoverServices();

            }
        });
        backgroud.run();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wl != null){
            wl.release();
        }
    }

    public void registerService(int port) {
        //start the NsdService and quire the wakelock to keep the app alive
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CollectingSensorData");
        wl.acquire();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        //This might be changed+
        serviceInfo.setServiceName("NsdChat");
        serviceInfo.setServiceType("_datachat._tcp");

        try {
            mServerSocket = new ServerSocket(0);
            serviceInfo.setPort(mServerSocket.getLocalPort());
            //On inc. clients accept them and start the protocol by getting the device name and number.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            //constantly look for clients to connect
                            foundSocket(mServerSocket.accept());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }




        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                    @Override
                    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.d(TAG, "onRegistrationFailed: Register Failed");
                    }

                    @Override
                    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.d(TAG, "onUnregistrationFailed: Unregister Failed");
                    }

                    @Override
                    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                        Log.d(TAG, "onServiceRegistered: Name of the Service:" + serviceInfo.getServiceName());
                    }

                    @Override
                    public void onServiceUnregistered(NsdServiceInfo serviceInfo) {

                    }
                });
    }


    /**
     * Accepts the socket and keeps track of it as long as it is alive and sends the set time and duration
     * @param accept the socket to accept
     */
    private void foundSocket(Socket accept) {
        if (!contains(clients,accept)){
            byte[] b = new byte[1024]; //should be big enough
            try {
                accept.getInputStream().read(b);
                accept.setKeepAlive(true);
                final String name = new String(b);
                clients.add(accept);
                map.put(accept,name);
                AliveChecker checker = new AliveChecker(accept, this);
                listOfChecker.add(checker);
                checker.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.add(name);
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Simply checks for dupicates
     * @param clients All current registered clients
     * @param accept the socket that wats be be accepted
     * @return true if socket exists false if socket does not exist
     */
    private boolean contains(ArrayList<Socket> clients, Socket accept) {
        for (Socket s : clients){
            if (s.getInetAddress().equals(accept.getInetAddress())){
                return true;
            }
        }
        return false;
    }


    /**
     * #Shows the set time dialog
     * @param view
     */
    public void setTime(View view){
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        TimePickerDialog timePicker = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {

                TextView dateView = findViewById(R.id.date_view);
                time = hourOfDay+":"+ minute;
                dateView.setText(time);
            }
        },hour,minute,true );
        timePicker.show();

    }

    /**
     * shows the set duration dialog
     * @param view
     */
    public void setDuration(View view){

        final NumberPicker pickerHour = new NumberPicker(this);
        //set range of the number pickerHour
        pickerHour.setMinValue(0);
        pickerHour.setMaxValue(12);
        //set default number to 8 hours
        pickerHour.setValue(8);

        final NumberPicker pickerMinute = new NumberPicker(this);
        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(59);
        pickerMinute.setValue(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT,1f);

        pickerHour.setLayoutParams(params);
        pickerMinute.setLayoutParams(params);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.addView(pickerHour);
        linearLayout.addView(pickerMinute);


        // Create and start a Dialog with the pickerHour set as view and show the dialog to set the number.
        AlertDialog diag = new AlertDialog.Builder(this).setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //DO nothing at all
            }
        }).setPositiveButton("Set", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TextView durView = findViewById(R.id.dur_view);
                String mins  = "";
                if (pickerMinute.getValue() < 10){
                    mins = "0"+pickerMinute.getValue();
                } else {
                    mins = String.valueOf(pickerMinute.getValue());
                }
                duration = String.valueOf(pickerHour.getValue()) + ":" + mins;
                durView.setText(String.valueOf(duration));
            }
        }).setView(linearLayout).setTitle("Set Hour / Min").create();
        diag.show();
    }

    /**
     * sends the start time time to the clients
     * @param view
     */
    public void sendStart(View view) {
        //sends time in "hour:minnute" format and duration to the clients
        send("SetDate:"+time+":"+duration);

    }

    /**
     * Sends a message to all clients
     * @param start
     */
    private void send(final String start) {
        Log.d(TAG, "send: "+start);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Socket s : clients){
                    try {
                        s.getOutputStream().write(start.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }

    /**
     * Simply checks for all clients whether they are still alive or not and delets them if the connection has been canceled
     *
     */
    public class AliveChecker extends Thread {
        final Socket socket;
        final MainActivity activity;

        public AliveChecker(final Socket socket, final MainActivity activity){
            super(new Runnable() {
                @Override
                public void run() {
                    try {
                        //Log.d(TAG, "run: is Closed ? " + socket.getInputStream().read());
                        //if input is -1 connection has been closed
                        while(true){
                            if (socket.getInputStream().read() == -1){
                                socket.close();
                                if (activity != null){
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // remove the closed device
                                            activity.listAdapter.remove(activity.map.get(socket));
                                            activity.map.remove(socket);
                                            activity.clients.remove(socket);
                                            activity.listAdapter.notifyDataSetChanged();
                                        }
                                    });
                                }
                                listOfChecker.remove(this);
                                return;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //faild throw out the socket
                        if (activity != null){
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // remove the device again if something unexpected happens
                                    activity.listAdapter.remove(activity.map.get(socket));
                                    activity.map.remove(socket);
                                    activity.clients.remove(socket);
                                    activity.listAdapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                }
            });
            this.activity = activity;
            this.socket = socket;
        }

    }
}

