package com.myapp.posedetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.github.ybq.android.spinkit.style.FadingCircle;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.wonderkiln.camerakit.CameraKitError;
import com.wonderkiln.camerakit.CameraKitEvent;
import com.wonderkiln.camerakit.CameraKitEventListener;
import com.wonderkiln.camerakit.CameraKitImage;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;
import java.io.InputStream;
import java.lang.Math;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    LinearLayout cameraButton, galleryButton;
    CameraView cameraViewPose;
    ProgressBar progressBar;


    // Create an object 'manager' of Class CameraManager
    CameraManager manager;
    String cameraId;

    // declare variable for sensor size, focal length and field of vision
    SizeF sensor_size;
    private double focal_length;
    Size previewSize;

    private double sensorSizeMM;
    private double field_of_vision;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the CameraManager instance
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            // Get the ID of the default back-facing camera
            String[] cameraIds = manager.getCameraIdList();
            String defaultCameraId = null;
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    defaultCameraId = id;
                    break;
                }
            }
            //IDK
            if (defaultCameraId == null && cameraIds.length > 0) {
                defaultCameraId = cameraIds[0];
            }
            cameraId = defaultCameraId;
            // Now you have the ID of the default camera in cameraId
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }

         CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        assert characteristics != null;

        // get the sensor size and focal length
        //WHY FOCAL LENGTH 0
        sensor_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);;
        focal_length = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0];;

        StreamConfigurationMap streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizes = streamMap.getOutputSizes(SurfaceTexture.class);
        previewSize = previewSizes[0];

        Log.d("sensor_size", "Sensor Size Height: " + String.format("%.2f",sensor_size.getHeight()) + " mm");
        Log.d("sensor_size", "Sensor Size Width: " + String.format("%.2f",sensor_size.getWidth()) + " mm");
        Log.d("focal_length", "Focal Length: "+ String.format("%.2f",focal_length) + " mm");

        // calculate Field of vision
        sensorSizeMM = Math.sqrt(Math.pow(sensor_size.getWidth(), 2) + Math.pow(sensor_size.getHeight(), 2));
        field_of_vision = 2 * Math.atan2(sensorSizeMM / 2, focal_length);
        Log.d("fov","Field of Vision: " + String.format("%.2f",field_of_vision));


        cameraButton = findViewById(R.id.cameraBtn);
        galleryButton = findViewById(R.id.galleryBtn);
        cameraViewPose = findViewById(R.id.poseCamera);

        // Progress Bar
        progressBar = findViewById(R.id.spin_kit);
        FadingCircle fadingCircle = new FadingCircle();
        progressBar.setIndeterminateDrawable(fadingCircle);
        progressBar.setVisibility(View.INVISIBLE);

        // OnClick
        cameraButton.setOnClickListener(this);
        galleryButton.setOnClickListener(this);


        cameraViewPose.addCameraKitListener(new CameraKitEventListener() {
            @Override
            public void onEvent(CameraKitEvent cameraKitEvent) {

            }

            @Override
            public void onError(CameraKitError cameraKitError) {

            }

            @Override
            public void onImage(CameraKitImage cameraKitImage) {
                progressBar.setVisibility(View.VISIBLE);
                Bitmap bitmap = cameraKitImage.getBitmap();
                bitmap = Bitmap.createScaledBitmap(bitmap, cameraViewPose.getWidth(), cameraViewPose.getHeight(), false);
                cameraViewPose.stop();
                runPose(bitmap);
            }

            @Override
            public void onVideo(CameraKitVideo cameraKitVideo) {
            }
        });
    }

    // OnClick
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.cameraBtn:
                poseDetect();
                break;
            case R.id.galleryBtn:
                galleryAdd();
                break;
        }
    }

    final int REQUEST_GALLERY = 1;
    private void galleryAdd() {
        Intent mediaIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(mediaIntent, REQUEST_GALLERY);
    }

    Bitmap galleryImage;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //IDK
        switch (requestCode){
            case REQUEST_GALLERY:
                try {
                    Uri imageUri = data.getData();
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    galleryImage = BitmapFactory.decodeStream(inputStream);
                    runPose(galleryImage);
                    progressBar.setVisibility(View.VISIBLE);
                }catch (Exception e){
                    Toast.makeText(MainActivity.this, "Cannot get Image from Gallery",Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    // Camera functions
    private void poseDetect() {
        cameraViewPose.start();
        cameraViewPose.captureImage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraViewPose.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cameraViewPose.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraViewPose.stop();
    }

    // Pose Detect
    //I WILL  EXPLAIN THIS
    AccuratePoseDetectorOptions options =
            new AccuratePoseDetectorOptions.Builder()
                    .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .build();

    PoseDetector poseDetector = PoseDetection.getClient(options);
    Bitmap resizedBitmap;

    private void runPose(Bitmap bitmap) {

        int rotationDegree = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);

        InputImage image = InputImage.fromBitmap(resizedBitmap, rotationDegree);

        poseDetector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<Pose>() {
                            @Override
                            public void onSuccess(Pose pose) {
                                processPose(pose);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, "\n" + "Pose Not Detected",Toast.LENGTH_SHORT).show();
                            }
                        });
    }

    private void processPose(Pose pose) {
        try {

            PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
            PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
            PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
            PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
            PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
            PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
            PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
            PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
            PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
            PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
            PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
            Log.d("PROCESS POSE", "PROCESS POSE FUNCTION IS RUNNING");
            Log.d("right ankle:","right ankle: "+ rightAnkle);

            //Calculate the distance between the user's Ankles in pixels
            assert rightAnkle != null;
            assert leftAnkle != null;
            double distance_pixels = sqrt(Math.pow((rightAnkle.getPosition().x - leftAnkle.getPosition().x),2) + Math.pow((rightAnkle.getPosition().y - leftAnkle.getPosition().y),2));
            Log.d("distP","distance_pixels: "+distance_pixels);
            //this is the distance between user's Ankle in meters
            double distance_from_camera = 0.5;

            //calculate the Pixel to Meter Ratio
            double pixel_to_meter_ratio = distance_pixels / (2 * distance_from_camera * tan(field_of_vision / 2));
            Log.d("ptm","pixel to meters: "+pixel_to_meter_ratio);

            // Calculate the user's height
            assert leftShoulder != null;
            double heightInPixels = sqrt(Math.pow((leftAnkle.getPosition().x - leftShoulder.getPosition().x),2) + Math.pow((leftAnkle.getPosition().y - leftShoulder.getPosition().y),2));
            double heightInMeters = heightInPixels / pixel_to_meter_ratio;

            // Calculate the user's thigh length
            assert leftHip != null;
            assert leftKnee != null;
            double thighLengthInPixels = sqrt(Math.pow((leftKnee.getPosition().x - leftHip.getPosition().x),2) + Math.pow((leftKnee.getPosition().y - leftHip.getPosition().y),2));
            double thighLengthInMeters = thighLengthInPixels / pixel_to_meter_ratio;

            // Calculate the user's calf length
            double calfLengthInPixels = sqrt(Math.pow((leftAnkle.getPosition().x - leftKnee.getPosition().x),2) + Math.pow((leftAnkle.getPosition().y - leftKnee.getPosition().y),2));
            double calfLengthInMeters = calfLengthInPixels / pixel_to_meter_ratio;

            String heightP_str = String.format("%.2f",heightInPixels);
            String calfP_str = String.format("%.2f",calfLengthInPixels);
            String thighP_str = String.format("%.2f",thighLengthInPixels);
            String height_str = String.format("%.2f",heightInMeters);
            String calf_str = String.format("%.2f",calfLengthInMeters);
            String thigh_str = String.format("%.2f",thighLengthInMeters);
            String fov_str = String.valueOf(field_of_vision);
            String ptm_str = String.format("%.2f",pixel_to_meter_ratio);
            String distP_str = String.format("%.2f",distance_pixels);
            String ss_str = String.format("%.2f",sensorSizeMM);
            String fc_str = String.format("%.2f",focal_length);
            String pv_w = String.valueOf(previewSize.getWidth());
            String pv_h = String.valueOf(previewSize.getHeight());
            String ss_w = String.valueOf(sensor_size.getWidth());
            String ss_h = String.valueOf(sensor_size.getHeight());


            String measureText ="sensor size : "+ ss_str +" mm\n" +
                    "focal length : "+ fc_str +" mm\n" +
                    "field of vision : "+ fov_str +" radians\n" +
                    "sensor size : "+ ss_w + "X " + ss_h + "\n"+
                    "preview Size : "+ pv_w +" X " + pv_h + "\n" +
                    "pixel to meters ratio : "+ ptm_str +"\n" +
                    "feet distance : "+ distP_str +" pixels\n" +
                    "Height in pixels: "+ heightP_str +" pixels\n" +
                    "Calf Length in pixels: "+ calfP_str +" pixels\n" +
                    "Thigh Length in pixels: "+ thighP_str +" pixels\n"+
                    "Height: "+ height_str +" metres\n" +
                    "Calf Length: "+ calf_str +" metres\n" +
                    "Thigh Length: "+ thigh_str +" metres\n";

            Intent intent = new Intent(MainActivity.this, MainActivity2.class);
            intent.putExtra("Text", measureText);
            startActivity(intent);


        }catch (Exception e){
            Log.d("PROCESS POSE", e.toString());
            Toast.makeText(MainActivity.this, "Pose Not Detected",Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
        }
    }
}