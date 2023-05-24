package com.myapp.posedetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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

        // calculate Field of vision
        sensorSizeMM = Math.sqrt(Math.pow(sensor_size.getWidth(), 2) + Math.pow(sensor_size.getHeight(), 2));
        field_of_vision = 2 * Math.atan2(sensorSizeMM / 2, focal_length);

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
            //PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
            //PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
            PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
            PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
            PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
            PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
            PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
            //PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
            PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
            PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
            Log.d("PROCESS POSE", "PROCESS POSE FUNCTION IS RUNNING");
            //Log.d("right ankle:","right ankle: "+ rightAnkle);

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

            // Calculate the user's height for kameez
            assert leftShoulder != null;
            double heightInPixels = sqrt(Math.pow((leftKnee.getPosition().x - leftShoulder.getPosition().x),2) + Math.pow((leftKnee.getPosition().y - leftShoulder.getPosition().y),2));
            double heightInMeters = heightInPixels / pixel_to_meter_ratio;


            // Calculate the users chest length
            double chestInPixels = sqrt(Math.pow((leftShoulder.getPosition().x - rightShoulder.getPosition().x),2) + Math.pow((leftShoulder.getPosition().y - rightShoulder.getPosition().y),2));;
            double chestInMeters = chestInPixels / pixel_to_meter_ratio;

            // Calculate the users left arm length
            double armleftInPixels = sqrt(Math.pow((leftWrist.getPosition().x - leftShoulder.getPosition().x),2) + Math.pow((leftWrist.getPosition().y - leftShoulder.getPosition().y),2));;
            double armleftInMeters = armleftInPixels / pixel_to_meter_ratio;

            // Calculate the users right arm length
            double armrightInPixels = sqrt(Math.pow((rightWrist.getPosition().x - rightShoulder.getPosition().x),2) + Math.pow((rightWrist.getPosition().y - rightShoulder.getPosition().y),2));;
            double armrightInMeters = armrightInPixels / pixel_to_meter_ratio;

            // Calculate the users leg length
            double legleftInPixels = sqrt(Math.pow((leftAnkle.getPosition().x - leftHip.getPosition().x),2) + Math.pow((leftAnkle.getPosition().y - leftHip.getPosition().y),2));;;
            double legleftInMeters = legleftInPixels / pixel_to_meter_ratio;

            // Calculate the users leg length
            double legrightInPixels = sqrt(Math.pow((rightAnkle.getPosition().x - rightHip.getPosition().x),2) + Math.pow((rightAnkle.getPosition().y - rightHip.getPosition().y),2));;;
            double legrightInMeters = legrightInPixels / pixel_to_meter_ratio;

            // Calculate the users hip length
            double hipInPixels = sqrt(Math.pow((leftHip.getPosition().x - rightHip.getPosition().x),2) + Math.pow((leftHip.getPosition().y - rightHip.getPosition().y),2));;;;
            double hipInMeters = hipInPixels / pixel_to_meter_ratio;

            String chestP_str = String.format("%.2f",chestInPixels);
            String chest_str = String.format("%.2f",chestInMeters);
            String armleftP_str = String.format("%.2f",armleftInPixels);
            String armleft_str = String.format("%.2f",armleftInMeters);
            String armrightP_str = String.format("%.2f",armrightInPixels);
            String armright_str = String.format("%.2f",armrightInMeters);
            String legleftP_str = String.format("%.2f",legleftInPixels);
            String legleft_str = String.format("%.2f",legleftInMeters);
            String legrightP_str = String.format("%.2f",legrightInPixels);
            String legright_str = String.format("%.2f",legrightInMeters);
            String hipP_str = String.format("%.2f",hipInPixels);
            String hip_str = String.format("%.2f",hipInMeters);
            String heightP_str = String.format("%.2f",heightInPixels);
            String height_str = String.format("%.2f",heightInMeters);


            String measureText =

                            "chest in pixels: "+ chestP_str +" pixels\n" +
                            "chest in meters: "+ chest_str +" meters\n" +
                            "arm left in pixels: "+ armleftP_str +" pixels\n" +
                            "arm left in meters: "+ armleft_str +" meters\n" +
                            "arm right in pixels: "+ armrightP_str +" pixels\n" +
                            "arm right in meters: "+ armright_str +" meters\n" +
                            "leg left in pixels: "+ legrightP_str +" pixels\n" +
                            "leg left in meters: "+ legleft_str +" meters\n" +
                            "leg right in pixels: "+ legleftP_str +" pixels\n" +
                            "leg right in meters: "+ legright_str +" meters\n" +
                            "hip in pixels: "+ hipP_str +" pixels\n" +
                            "hip in meters: "+ hip_str +" meters\n" +
                             "Height in pixels: "+ heightP_str +" pixels\n" +
                             "Height: "+ height_str +" metres\n" ;

            //Initialise the Intent and Start the activity

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