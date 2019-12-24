package com.rk.filetransfer;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int RC_FINISH = 1;
    private static final int RC_SHOW_QR = 2;
    private static final int NOTIFICATION_ID = 142;
    private static final String ACTION_CLOSE_ACTIVITY = "com.rk.filetransfer.CLOSE";

    private Intent intent;
    private String type;
    private WebServer server;
    private WifiChangeListener listener;
    private TextView ip;
    private ImageView qrcode;
    private WebServer.MODE mode;
    private Uri[] uri;
    private String text;

    private boolean isWifiOn;
    private boolean isWifiConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("abcd", "onCreate");
        setFinishOnTouchOutside(false);
        initReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("abcd", "onResume");
        intent = getIntent();
        String action = intent.getAction();
        type = intent.getType();
        if (action.equals(Intent.ACTION_SEND) && type != null) {
            sendSingleFile();
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            sendMultipleFile();
        }
        initServer();
    }

    private void initNotification(String content) {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("ABC", "Ongoing Transimission", NotificationManager.IMPORTANCE_HIGH);
            managerCompat.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "ABC");
        builder.setSmallIcon(R.drawable.ic_transfer);
        builder.setAutoCancel(false);
        builder.setContentTitle("Ongoing transmission");
        builder.setOngoing(true);
        builder.setContentText(content);
        Intent closeIntent = new Intent(ACTION_CLOSE_ACTIVITY);
        PendingIntent p1 = PendingIntent.getBroadcast(this, RC_FINISH, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent showQrIntent = new Intent(this,MainActivity.class);
        PendingIntent p2 = PendingIntent.getActivity(this, RC_SHOW_QR, showQrIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_qr_code,"Show QR",p2);
        builder.addAction(R.drawable.ic_close,"Close",p1);
        builder.setContentIntent(p2);
        managerCompat.notify(NOTIFICATION_ID, builder.build());
    }

    private void initServer() {
        if (server == null) {
            if (mode == WebServer.MODE.TEXT)
                server = new WebServer(this, text);
            else if (mode == WebServer.MODE.SINGLE)
                server = new WebServer(this, uri[0]);
            else server = new WebServer(this, uri);
        } else {
            server.reset(mode, text, uri, uri[0]);
        }
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ACTION_CLOSE_ACTIVITY);
        registerReceiver(listener = new WifiChangeListener(), intentFilter);
        TextView openWifi = findViewById(R.id.openWifi);
        TextView close = findViewById(R.id.close);
        qrcode = findViewById(R.id.qrcode);
        ip = findViewById(R.id.ip);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        openWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
    }

    private void sendMultipleFile() {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            uri = new Uri[imageUris.size()];
            int i = 0;
            for (Uri u : imageUris) {
                uri[i++] = u;
//                Log.d("abcd", u.toString());
            }
        }
    }

    private void sendSingleFile() {
        if (type.startsWith("text/")) {
            mode = WebServer.MODE.TEXT;
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else {
            mode = WebServer.MODE.SINGLE;
            uri = new Uri[1];
            uri[0] = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
    }

    private void getWifiDetails() throws IOException, WriterException {
        String ipAddress = getWifiIpAddress();
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode("http://" + ipAddress + ":8080", BarcodeFormat.QR_CODE, (int) getResources().getDimension(R.dimen.qr_width), (int) getResources().getDimension(R.dimen.qr_height));
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        qrcode.setImageBitmap(bmp);
        String addr = "http://" + ipAddress + ":8080";
        ip.setText(addr);
        initNotification("Open " + addr);
        if (!server.isAlive())
            server.start();
    }

    private String getWifiIpAddress() throws UnknownHostException {
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        int ipAddress = info.getIpAddress();
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }
        final byte[] bytes = BigInteger.valueOf(ipAddress).toByteArray();
        final InetAddress address;
        address = InetAddress.getByAddress(bytes);
        return address.getHostAddress();
    }

    @Override
    protected void onDestroy() {
        Log.d("abcd", "onDestroy");
        server.closeAllConnections();
        unregisterReceiver(listener);
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d("abcd", "onbackpressed");
        moveTaskToBack(true);
        Toast.makeText(this,"Transmission active in background",Toast.LENGTH_LONG).show();
    }

    class WifiChangeListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("abcd", intent.getAction());
            switch (intent.getAction()) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
                    manager.isWifiEnabled();
                    isWifiOn = manager.isWifiEnabled();
                    wifiModified();
                    break;
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    isWifiConnected = info.isConnected();
                    wifiModified();
                    break;
                case ACTION_CLOSE_ACTIVITY:
                    finish();
                    break;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void wifiModified() {
        if (isWifiConnected && isWifiOn) {
            Log.d("abcd", "connected");
            try {
                getWifiDetails();
                qrcode.setVisibility(View.VISIBLE);
            } catch (IOException | WriterException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "Some error occurred", Toast.LENGTH_LONG).show();
            }
        } else if (!isWifiOn) {
            Log.d("abcd", "turn off");
            qrcode.setVisibility(View.GONE);
            initNotification("Please turn on WiFi.");
            ip.setText("Please turn on WiFi.");
        } else {
            Log.d("abcd", "disconnected");
            qrcode.setVisibility(View.GONE);
            initNotification("Please connect to receiver's hotspot.");
            ip.setText("Please connect to receiver's hotspot.");
        }
    }
}