package azerva.rozer.podometro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;


import azerva.rozer.podometro.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SensorEventListener, OnMapReadyCallback {

    private ActivityMainBinding binding;

    private SensorManager sensorManager;
    private Sensor sensorSteps;
    private boolean isMoving = false;
    private float totalSteps;
    private float startSteps;

    private static final int PERMISSIONS_READ_FINE_LOCATION = 100;
    protected final long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    protected final long FASTEST_INTERVAL = 2000; /* 2 sec */
    protected SupportMapFragment mapFrag;
    FusedLocationProviderClient fusedLocationClient;
    private GoogleMap gMap;
    private ArrayList<LatLng> ruta;
    Polyline line;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View v = binding.getRoot();
        setContentView(v);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorSteps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        binding.btnStart.setOnClickListener(view -> {
            isMoving = true;
            Toast.makeText(this, R.string.msnStartCounter, Toast.LENGTH_SHORT).show();

        });

        loadData();
        resetStartStepCounter();

        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        //Evitamos que el map nos produzca un nullPointException
        assert mapFrag != null;
        mapFrag.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        startLocationUpdates();

        ruta = new ArrayList<>();


    }

    @Override
    protected void onResume() {
        super.onResume();

        if(sensorSteps != null)
            sensorManager.registerListener(this,sensorSteps,SensorManager.SENSOR_DELAY_NORMAL);
        else
            Toast.makeText(this, R.string.msnSensorNull, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(sensorSteps != null)
            sensorManager.unregisterListener(this, sensorSteps);

    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (isMoving) {
            totalSteps = (int) event.values[0];

            int currentSteps = (int) (totalSteps - startSteps);

            binding.tvStepsCount.setText(String.valueOf(currentSteps));

//            binding.tvStepsCount.setText(String.valueOf(event.values[0]));

        }


    }
    private void resetStartStepCounter(){

        binding.tvStepsCount.setOnClickListener(view ->
                Toast.makeText(this, R.string.resetCounter, Toast.LENGTH_SHORT).show());

        binding.tvStepsCount.setOnLongClickListener(view -> {
            startSteps = totalSteps;

            binding.tvStepsCount.setText(String.valueOf(0));
            
            saveData();

            return true;
        });

    }

    private void saveData() {

        SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("key1", startSteps);
        editor.apply();
    }

    private void loadData(){

        SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
        float saveNumber = sharedPreferences.getFloat("key1", 0f);
        startSteps = saveNumber;

    }

    @SuppressLint("MissingPermission")
    protected void startLocationUpdates() {

        // Create the location request to start receiving updates
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        // do work here
                        onLocationChanged(locationResult.getLastLocation());
                    }
                },
                Looper.myLooper());

    }

    @Override
    protected void onStart() {
        super.onStart();

        // This verification should be done during onStart() because the system calls
        // this method when the user returns to the activity, which ensures the desired
        // location provider is enabled each time the activity resumes from the stopped state.
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            // Build an alert dialog here that requests that the user enable
            // the location services, then when the user clicks the "OK" button,
            enableLocationSettings();
        }
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_READ_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                startLocationUpdates();

            } else {

                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
            return;

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void onLocationChanged(@NonNull Location location) {
        // New location has now been determined
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
//        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        // You can now create a LatLng Object for use with maps

        LatLng startPosition = new LatLng(location.getLatitude(), location.getLongitude());
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPosition, 16.0f));

        ruta.add(startPosition);


        startDrawLine();


    }

    private void startDrawLine() {
         if (isMoving){
             PolylineOptions options = new PolylineOptions().width(10).color(Color.RED).geodesic(true);
             for (int i = 0; i < ruta.size(); i++) {
                 LatLng point = ruta.get(i);
                 options.add(point);
             }
             line = gMap.addPolyline(options); //add Polyline

         }


    }

    @SuppressLint("MissingPermission")
    public void getLastLocation() {
        // Get last known recent location using new Google Play Services SDK (v11+)
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    // GPS location can be null if GPS is switched off
                    if (location != null) {
                        onLocationChanged(location);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.d("MapDemoActivity", "Error trying to get last GPS location");
                    e.printStackTrace();
                });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        if (checkPermissions())
            googleMap.setMyLocationEnabled(true);


    }

    private boolean checkPermissions() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissions();
            return false;
        }
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSIONS_READ_FINE_LOCATION);
    }

}