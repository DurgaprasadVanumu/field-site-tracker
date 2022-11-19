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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

//    private static final int NO_OF_COMMANDS = 50; // If you want both trip and temp logs
    private static final int NO_OF_COMMANDS = 25; // If you want only trip and no temp logs

    private static String fileGenerationFailedMsg = null;
    private static String fileGeneratedLocation = null;

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
//                List<String> logs = Arrays.asList("First line", "Second line");
//                boolean success = generatePdf(logs);
//                if(success){
//                    msg("File generated at loc "+fileGeneratedLocation);
//                }else{
//                    msg("Failed to generate the file "+fileGenerationFailedMsg+" to save at "+fileGeneratedLocation);
//                }
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

    private boolean generatePdf(List<String> logs, String deviceName){
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

        try{
            String fileName = Long.valueOf(System.currentTimeMillis()).toString().replaceAll(":",".")+".pdf";
            fileName = new StringBuffer(deviceName).append("_").append(fileName).toString();
//            ContextWrapper cw = new ContextWrapper(getApplicationContext());
//            File directory = cw.getDir("btHmiLogs", Context.MODE_PRIVATE);
//            File file = new File(directory, fileName);
//            pdfDocument.writeTo(new FileOutputStream(file));
//            fileGeneratedLocation = file.toString();
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS+File.separator+"btHmiLogs");

            Uri fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            FileOutputStream fos = (FileOutputStream) resolver.openOutputStream(Objects.requireNonNull(fileUri));
            pdfDocument.writeTo(fos);
            fileGeneratedLocation = fileUri.getPath();
        }catch (IOException e){
            fileGenerationFailedMsg = "Failed to generate file "+e.getMessage();
            pdfDocument.close();
            return false;
        }catch (Exception e){
            fileGenerationFailedMsg = "Unknown error while generating file "+e.getMessage();
            pdfDocument.close();
            return false;
        }
        pdfDocument.close();
        return true;
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

    private void msg (String s, int delay){
        Toast.makeText(getApplicationContext(), s, delay).show();
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
                fileGenerationSuccess = true;
                if(btSocket!=null && isBtConnected){
                    List<String> logs = new ArrayList<>();
                    for(int i=0;i<NO_OF_COMMANDS;i++){
                        btSocket.getOutputStream().write("D".toString().getBytes());
                        byte[] byteArray = new byte[100];
                        InputStream inputStream = btSocket.getInputStream();
                        int len = inputStream.read(byteArray);
                        logs.add(new String(byteArray, 0, len));
                    }
                    fileGenerationSuccess = generatePdf(logs, deviceName);
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
            if (fileGenerationSuccess) {
                msg("File generation successful. Saved at " + fileGeneratedLocation, 5);
            } else {
                msg("Failed to generate log file. Error: " + fileGenerationFailedMsg, 5);
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
