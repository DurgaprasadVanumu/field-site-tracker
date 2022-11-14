package com.durga.bt_hmi;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LedControl extends AppCompatActivity {

    Button testTripButton, downloadLogsButton, disconnectDeviceButton;
    String address = null;
    String deviceName = null;
    TextView lumn;
    TextView dname;
    TextView daddress;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // declaring width and height
    // for our PDF file.
    int pageHeight = 1120;
    int pagewidth = 792;
    private static final int PERMISSION_REQUEST_CODE = 200;

    private static String fileGenerationFailedMsg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led_control2);

        Intent intent = getIntent();
        address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS);
        deviceName = intent.getStringExtra(MainActivity.DEVICE_NAME);

        testTripButton =  findViewById(R.id.button6);
        downloadLogsButton =  findViewById(R.id.button7);
        disconnectDeviceButton = findViewById(R.id.button4);
        lumn =  findViewById(R.id.textView2);
        dname = findViewById(R.id.dname);
        daddress = findViewById(R.id.daddress);

        new LedControl.ConnectBT().execute();

        // below code is used for
        // checking our permissions.
        if (checkPermission()) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
        } else {
            requestPermission();
        }

        dname.setText("Device Name: "+deviceName);
        daddress.setText("Address: "+address);

        testTripButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                sendSignal("T");
            }
        });

        downloadLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
//                sendSignal("D");
                new LedControl.GenerateLogFile().execute();
            }
        });

        disconnectDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                Disconnect();
            }
        });
    }

    private void sendSignal ( String number ) {
        if ( btSocket != null ) {
            try {
                btSocket.getOutputStream().write(number.toString().getBytes());
//                Thread.sleep(2000);
                receiveData(btSocket);
            } catch (Exception e) {
                msg("Error in sending data to device");
            }
        }
    }

    private void receiveData(BluetoothSocket btSocket){
        try{
            byte[] byteArray = new byte[255];
            InputStream inputStream = btSocket.getInputStream();
            int len = inputStream.read(byteArray);
            String line = new String(byteArray, 0, len);
            msg("Received data: "+line);
//            generatePdf();
        }catch (Exception e) {
            msg("Error while reading the data from Device");
        }
    }

    private void generatePdf(List<String> logs){
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo myPageInfo = new PdfDocument.PageInfo.Builder(pagewidth, pageHeight, 1).create();
        PdfDocument.Page myPage = pdfDocument.startPage(myPageInfo);
        Canvas canvas = myPage.getCanvas();

        Paint line = new Paint();
        line.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        line.setTextSize(15);
        line.setColor(ContextCompat.getColor(this, R.color.colorPrimary));

        int posFromLeft = 50;
        canvas.drawText("Test trip logs", 50, 80, line);
        int posFromTopStart = 100;
        for(int i=0;i<logs.size();i++){
            if(i==25){
                canvas.drawText("Temperature logs", posFromLeft, posFromTopStart+(i*20), line);
            }
            if(i<25){
                canvas.drawText(logs.get(i), posFromLeft, posFromTopStart+(i*20), line);
            }else{
                canvas.drawText(logs.get(i), posFromLeft, posFromTopStart+(i+1)*20, line);
            }
        }

        pdfDocument.finishPage(myPage);

        String fileName = new Long(System.currentTimeMillis()).toString().replaceAll(":",".")+".pdf";
        File file = new File(Environment.getExternalStorageDirectory(), fileName);
        try{
            pdfDocument.writeTo(new FileOutputStream(file));
        }catch (IOException e){
            fileGenerationFailedMsg = "Failed to generate file "+e.getMessage();
        }catch (Exception e){
            fileGenerationFailedMsg = "Unknown error while generating file "+e.getMessage();
        }finally {
            pdfDocument.close();
        }
    }

    private void Disconnect () {
        if ( btSocket!=null ) {
            try {
                btSocket.close();
            } catch(IOException e) {
                msg("Error");
            }
        }

        finish();
    }

    private void msg (String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private class GenerateLogFile extends AsyncTask<Void, Void, Void> {
        private boolean fileGenerationSuccess = true;

        @Override
        protected void onPreExecute(){
            progress = ProgressDialog.show(LedControl.this, "Generating file..", "File is being generated, please wait...");
        }

        @Override
        protected Void doInBackground (Void... devices) {
            try{
                if(btSocket!=null && isBtConnected){
                    List<String> logs = new ArrayList<>();
                    for(int i=0;i<50;i++){
                        btSocket.getOutputStream().write("D".toString().getBytes());
                        byte[] byteArray = new byte[100];
                        InputStream inputStream = btSocket.getInputStream();
                        int len = inputStream.read(byteArray);
                        logs.add(new String(byteArray, 0, len));
                    }
                    generatePdf(logs);
                }else{
                    fileGenerationSuccess = false;
                    fileGenerationFailedMsg = "No device connected.";
                }
            } catch (Exception e){
                fileGenerationSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute (Void result) {
            super.onPostExecute(result);
            if(fileGenerationSuccess){
                msg("File generation successful.");
            }else{
                msg("Failed to generate log file. Error: "+fileGenerationFailedMsg);
            }
            progress.dismiss();
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected  void onPreExecute () {
            progress = ProgressDialog.show(LedControl.this, "Connecting...", "Please Wait!!!");
        }

        @Override
        protected Void doInBackground (Void... devices) {
            try {
                if ( btSocket==null || !isBtConnected ) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }

            return null;
        }

        @Override
        protected void onPostExecute (Void result) {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Please make sure the device has HC05 bluetooth support and you are around the device. Try again.");
//                finish();
            } else {
                msg("Connected");
                isBtConnected = true;
            }

            progress.dismiss();
        }
    }

    private boolean checkPermission() {
        // checking of permissions.
        int permission1 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        return permission1 == PackageManager.PERMISSION_GRANTED && permission2 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        // requesting permissions if not provided.
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {

                // after requesting permissions we are showing
                // users a toast message of permission granted.
                boolean writeStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean readStorage = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (writeStorage && readStorage) {
                    Toast.makeText(this, "Permission Granted..", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission Denied.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }
}
