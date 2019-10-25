package com.jankin.serialttest;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private Button button;
    private SerialPortFinder serialPortFinder;
    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private WriteThread mWriteThread;
    private String TAG = "MainActivity";
    private ListView mLvList;
    private List<String> mList;
    private ArrayAdapter<String> adapter;

    private void initView() {
        mLvList = (ListView) findViewById(R.id.lv_list);
        mList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                mList);
        mLvList.setAdapter(adapter);
    }

    private class ReadThread extends Thread {

        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[64];
                    if (mInputStream == null) return;
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        Log.e(TAG, "run--------->: " + new String(buffer, 0, size));
                        mList.add(new Date() +""+new String(buffer,0,size));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.notifyDataSetChanged();
                            }
                        });

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private class WriteThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                try {
                    if (mOutputStream == null) return;
                    byte data = 0x11;
                    mOutputStream.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
//        serialInit();
        button = findViewById(R.id.btn_opne);
        button.setOnClickListener(this);
        //mApplication = (Application) getApplication();

    }

    private void serialInit() {
        try {
//            mSerialPort = mApplication.getSerialPort();
            mSerialPort = new SerialPort(new File("/dev/ttyUSB0"), 115200, 0);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();

            /* Create a receiving thread */
            mReadThread = new ReadThread();
            mReadThread.start();

            mWriteThread = new WriteThread();
            mWriteThread.start();
        } catch (SecurityException e) {
            DisplayError(R.string.error_security);
        } catch (IOException e) {
            DisplayError(R.string.error_unknown);
        } catch (InvalidParameterException e) {
            DisplayError(R.string.error_configuration);
        }
    }


    private void DisplayError(int resourceId) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Error");
        b.setMessage(resourceId);
        b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.finish();
            }
        });
        b.show();
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    private native static FileDescriptor open(String path, int baudrate, int flags);

    public native void close();


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
            case R.id.btn_opne:
                Toast.makeText(this, "MainActivity", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "onClick: btn_open");
                serialInit();
                break;
        }
    }


    public class SerialPort {

        private static final String TAG = "SerialPort";

        /*
         * Do not remove or rename the field mFd: it is used by native method close();
         */
        private FileDescriptor mFd;
        private FileInputStream mFileInputStream;
        private FileOutputStream mFileOutputStream;

        public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {

            /* Check access permission */
            if (!device.canRead() || !device.canWrite()) {
                try {
                    /* Missing read/write permission, trying to chmod the file */
                    Process su;
                    su = Runtime.getRuntime().exec("/system/bin/su");
                    String cmd = "chmod 666 " + device.getAbsolutePath() + "\n"
                            + "exit\n";
                    su.getOutputStream().write(cmd.getBytes());
                    if ((su.waitFor() != 0) || !device.canRead()
                            || !device.canWrite()) {
                        throw new SecurityException();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new SecurityException();
                }
            }

            mFd = open(device.getAbsolutePath(), baudrate, flags);
            if (mFd == null) {
                Log.e(TAG, "native open returns null");
                throw new IOException();
            }
            mFileInputStream = new FileInputStream(mFd);
            mFileOutputStream = new FileOutputStream(mFd);
        }

        // Getters and setters
        public InputStream getInputStream() {
            return mFileInputStream;
        }

        public OutputStream getOutputStream() {
            return mFileOutputStream;
        }
    }
}
