package me.letal1s.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Locale;
import java.util.Objects;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus;

import static androidx.core.app.ActivityCompat.startActivityForResult;
import static androidx.core.app.NotificationCompat.PRIORITY_DEFAULT;
import static androidx.core.app.NotificationCompat.PRIORITY_MAX;
import static androidx.core.app.NotificationCompat.PRIORITY_MIN;
import static de.blinkt.openvpn.core.ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

public class KillswitchService extends VpnService {
    private Thread mThread;
    private String mServerAddress = "127.0.0.1";
    private int mServerPort = 55555;
    private ParcelFileDescriptor mInterface;
    //private PendingIntent mConfigureIntent;
    private DatagramChannel tunnel;
    private VpnService.Builder builder = new VpnService.Builder();
    private boolean shouldRun = true;

    private BroadcastReceiver stopBr = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("stop_killswitch".equals(intent.getAction())) {
                onDestroy();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(stopBr, new IntentFilter("stop_killswitch"));
    }

    private void setTunnel(DatagramChannel tunnel) {
        this.tunnel = tunnel;
    }

    private void setFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        this.mInterface = fileDescriptor;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
        }
        // Start a new session by creating a new thread.
        mThread = new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    builder.addDisallowedApplication("net.centrivpn.centrivpn");
                setFileDescriptor(builder.setSession("KILLSWITCH").setMtu(1500).
                        addAddress("10.0.0.2", 32).addRoute("0.0.0.0", 0).establish());
                setTunnel(DatagramChannel.open());
                tunnel.connect(new InetSocketAddress(mServerAddress, mServerPort));
                protect(tunnel.socket());
                while (shouldRun)
                    Thread.sleep(100L);
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                if (mInterface != null) {
                    try {
                        mInterface.close();
                        setFileDescriptor(null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        mThread.start();

        guiHandler = new Handler(getMainLooper());
        showNotification("Killswitch enabled.",
                "Killswitch enabled.", NOTIFICATION_CHANNEL_NEWSTATUS_ID, 0, ConnectionStatus.LEVEL_NONETWORK, null);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);

        if (mThread != null) {
            mThread.interrupt();
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        super.onDestroy();
    }

    /*@Override
    public synchronized void run() {
        try {
            InetSocketAddress server = new InetSocketAddress(
                    mServerAddress, mServerPort);

            run(server);

        } catch (Exception e) {
            try {
                mInterface.close();
            } catch (Exception e2) {
                // ignore
            }

        } finally {

        }
    }

    DatagramChannel mTunnel = null;

    private void run(InetSocketAddress server) throws Exception {
        // Create a DatagramChannel as the VPN tunnel.
        mTunnel = DatagramChannel.open();

        // Protect the tunnel before connecting to avoid loopback.
        if (!protect(mTunnel.socket())) {
            throw new IllegalStateException("Cannot protect the tunnel");
        }

        // Connect to the server.
        mTunnel.connect(server);

        // For simplicity, we use the same thread for both reading and
        // writing. Here we put the tunnel into non-blocking mode.
        mTunnel.configureBlocking(false);

        // Authenticate and configure the virtual network interface.
        handshake();

        new Thread ()
        {
            public void run ()
            {
                // Packets to be sent are queued in this input stream.
                FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
                // Allocate the buffer for a single packet.
                ByteBuffer packet = ByteBuffer.allocate(32767);
                int length;
                try
                {
                    while (true)
                    {
                        while ((length = in.read(packet.array())) > 0) {
                            // Write the outgoing packet to the tunnel.
                            packet.limit(length);
                            //mTunnel.write(packet);
                            packet.clear();
                        }
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }.start();

        new Thread ()
        {

            public void run ()
            {
                DatagramChannel tunnel = mTunnel;
                // Allocate the buffer for a single packet.
                ByteBuffer packet = ByteBuffer.allocate(8096);
                // Packets received need to be written to this output stream.
                FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());

                while (true)
                {
                    try
                    {
                        int length;
                        while ((length = tunnel.read(packet)) > 0)
                        {
                            // Write the incoming packet to the output stream.
                            out.write(packet.array(), 0, length);

                            packet.clear();

                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void handshake() throws Exception {

        if (mInterface == null)
        {
            Builder builder = new Builder();

            builder.setMtu(1500);
            builder.addAddress("10.0.0.2",32);
            builder.addRoute("0.0.0.0", 0);

            // Close the old interface since the parameters have been changed.
            try {
                mInterface.close();
            } catch (Exception e) {
                // ignore
            }

            // Create a new interface using the builder and save the parameters.
            mInterface = builder.setSession("KILLSWITCH")
                    .setConfigureIntent(mConfigureIntent)
                    .establish();
        }
    }*/

    //Notification stuff:

    public static final String NOTIFICATION_CHANNEL_BG_ID = "killswitch_bg";
    public static final String NOTIFICATION_CHANNEL_USERREQ_ID = "killswitch_userreq";
    public static final String NOTIFICATION_CHANNEL_NEWSTATUS_ID = "killswitch_newstat";
    private String lastChannel;
    private Handler guiHandler;
    private Toast mlastToast;
    private static Class<? extends Activity> mNotificationActivityClass;

    PendingIntent getGraphPendingIntent() {
        // Let the configure Button show the Log


        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, getPackageName() + ".view.MainActivity"));

        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;

    }

    public static void setNotificationActivityClass(Class<? extends Activity> activityClass) {
        mNotificationActivityClass = activityClass;
    }

    PendingIntent getContentIntent() {
        try {
            if (mNotificationActivityClass != null) {
                // Let the configure Button show the Log
                Intent intent = new Intent(getBaseContext(), mNotificationActivityClass);
                String typeStart = Objects.requireNonNull(
                        mNotificationActivityClass.getField("TYPE_START").get(null)).toString();
                Integer typeFromNotify = Integer.parseInt(Objects.requireNonNull(mNotificationActivityClass.getField("TYPE_FROM_NOTIFY").get(null)).toString());
                intent.putExtra(typeStart, typeFromNotify);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
                return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        } catch (Exception e) {
            Log.e(this.getClass().getCanonicalName(), "Build detail intent error", e);
            e.printStackTrace();
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        chan.setSound(null, null);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    private void showNotification(final String msg, String tickerText, @NonNull String channel,
                                  long when, ConnectionStatus status, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = createNotificationChannel(channel, "VPN Status");
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            channel = "";
        }

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        android.app.Notification.Builder nbuilder = new Notification.Builder(this);

        int priority;
        if (channel.equals(NOTIFICATION_CHANNEL_BG_ID))
            priority = PRIORITY_MIN;
        else if (channel.equals(NOTIFICATION_CHANNEL_USERREQ_ID))
            priority = PRIORITY_MAX;
        else
            priority = PRIORITY_DEFAULT;

        nbuilder.setContentTitle("Disconnected from CentriVPN");

        nbuilder.setContentText(msg);
        nbuilder.setOnlyAlertOnce(true);
        nbuilder.setOngoing(true);
        nbuilder.setSmallIcon(R.drawable.ic_notification);
        if (status == LEVEL_WAITING_FOR_USER_INPUT) {
            PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
            nbuilder.setContentIntent(pIntent);
        } else {
            PendingIntent contentPendingIntent = getContentIntent();
            if (contentPendingIntent != null) {
                nbuilder.setContentIntent(contentPendingIntent);
            } else {
                nbuilder.setContentIntent(getGraphPendingIntent());
            }
        }

        if (when != 0)
            nbuilder.setWhen(when);


        // Try to set the priority available since API 16 (Jellybean)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            jbNotificationExtras(priority, nbuilder);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            lpNotificationExtras(nbuilder, Notification.CATEGORY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            nbuilder.setChannelId(channel);
        }

        if (tickerText != null && !tickerText.equals(""))
            nbuilder.setTicker(tickerText);
        try {
            Notification notification = nbuilder.build();

            int notificationId = channel.hashCode();

            mNotificationManager.notify(notificationId, notification);

            startForeground(notificationId, notification);

            if (lastChannel != null && !channel.equals(lastChannel)) {
                // Cancel old notification
                mNotificationManager.cancel(lastChannel.hashCode());
            }
        } catch (Throwable th) {
            Log.e(getClass().getCanonicalName(), "Error when show notification", th);
        }

        // Check if running on a TV
        if (runningOnAndroidTV() && !(priority < 0))
            guiHandler.post(new Runnable() {

                @Override
                public void run() {

                    if (mlastToast != null)
                        mlastToast.cancel();
                    String toastText = String.format("Disconnected from CentriVPN.", msg);
                    mlastToast = Toast.makeText(getBaseContext(), toastText, Toast.LENGTH_SHORT);
                    mlastToast.show();
                }
            });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void lpNotificationExtras(Notification.Builder nbuilder, String category) {
        nbuilder.setCategory(category);
        nbuilder.setLocalOnly(true);

    }

    private boolean runningOnAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void jbNotificationExtras(int priority,
                                      android.app.Notification.Builder nbuilder) {
        try {
            if (priority != 0) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                setpriority.invoke(nbuilder, priority);

                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);

            }

            //ignore exception
        } catch (NoSuchMethodException | IllegalArgumentException |
                InvocationTargetException | IllegalAccessException e) {
            VpnStatus.logException(e);
        }

    }
}
