package com.myapp.posedetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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

import static java.lang.Math.atan;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    LinearLayout cameraButton, galleryButton;
    CameraView cameraViewPose;
    ProgressBar progressBar;
    // get the sensor size and focal length
    CameraManager manager;
    String cameraId;

    private double sensor_size;
    private double focal_length;

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
        sensor_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE).getWidth();;
        focal_length = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0];;

        Log.d("sensor_size: ", String.valueOf(sensor_size));
        Log.d("focal_length: ", String.valueOf(focal_length));

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
                                Toast.makeText(MainActivity.this, "Pose Tespit Edilemedi",Toast.LENGTH_SHORT).show();
                            }
                        });
    }

    // Pose Detect
    String angleText;
    String measureText;

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


            //Measurements
            assert rightAnkle != null;
            double rAnklex = rightAnkle.getPosition().x;
            assert leftAnkle != null;
            double lAnklex = leftAnkle.getPosition().x;
            double rAnkley = rightAnkle.getPosition().y;
            double lAnkley = leftAnkle.getPosition().y;
            double distance_pixels = sqrt(Math.pow((rAnklex - lAnklex),2) + Math.pow((rAnkley - lAnkley),2));
            double distance_from_camera = 2.0;

            // calculate Field of vision

            double field_of_vision = 2 * atan(sensor_size / (2 * focal_length));
            double pixel_to_meter_ratio = distance_pixels / (2 * distance_from_camera * tan(field_of_vision / 2));

            // Calculate the user's height
            assert leftShoulder != null;
            double heightInPixels = leftShoulder.getPosition().y - leftAnkle.getPosition().y;
            double heightInMeters = heightInPixels * pixel_to_meter_ratio;

            // Calculate the user's thigh length
            assert leftHip != null;
            assert leftKnee != null;
            double thighLengthInPixels = leftHip.getPosition().y - leftKnee.getPosition().y;
            double thighLengthInMeters = thighLengthInPixels * pixel_to_meter_ratio;

            // Calculate the user's calf length
            double calfLengthInPixels = leftKnee.getPosition().y - leftAnkle.getPosition().y;
            double calfLengthInMeters = calfLengthInPixels * pixel_to_meter_ratio;

            measureText = "Height: "+heightInMeters+"\n" +
                    "Calf Length: "+calfLengthInMeters+"\n" +
                    "Thigh Length: "+thighLengthInMeters+"\n";

            Intent intent = new Intent(MainActivity.this, MainActivity2.class);
            intent.putExtra("Text", measureText);
            startActivity(intent);


        }catch (Exception e){
            Log.d("PROCESS POSE", e.toString());
            Toast.makeText(MainActivity.this, "Pose Not Detected",Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    // Pose Draw
    private void DisplayAll(float lShoulderX, float lShoulderY, float rShoulderX, float rShoulderY,
                            float lElbowX, float lElbowY, float rElbowX, float rElbowY,
                            float lWristX, float lWristY, float rWristX, float rWristY,
                            float lHipX, float lHipY, float rHipX, float rHipY,
                            float lKneeX, float lKneeY, float rKneeX, float rKneeY,
                            float lAnkleX, float lAnkleY, float rAnkleX, float rAnkleY) {

        /*Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        float strokeWidth = 4.0f;
        paint.setStrokeWidth(strokeWidth);

        Bitmap drawBitmap = Bitmap.createBitmap(resizedBitmap.getWidth(), resizedBitmap.getHeight(), resizedBitmap.getConfig());

        Canvas canvas =new Canvas(drawBitmap);
        canvas.drawBitmap(resizedBitmap, 0f, 0f, null);
        canvas.drawLine(lShoulderX, lShoulderY, rShoulderX, rShoulderY, paint);
        canvas.drawLine(rShoulderX, rShoulderY, rElbowX, rElbowY, paint);
        canvas.drawLine(rElbowX, rElbowY, rWristX, rWristY, paint);
        canvas.drawLine(lShoulderX, lShoulderY, lElbowX, lElbowY, paint);
        canvas.drawLine(lElbowX, lElbowY, lWristX, lWristY, paint);
        canvas.drawLine(rShoulderX, rShoulderY, rHipX, rHipY, paint);
        canvas.drawLine(lShoulderX, lShoulderY, lHipX, lHipY, paint);
        canvas.drawLine(lHipX, lHipY, rHipX, rHipY, paint);
        canvas.drawLine(rHipX, rHipY, rKneeX, rKneeY, paint);
        canvas.drawLine(lHipX, lHipY, lKneeX, lKneeY, paint);
        canvas.drawLine(rKneeX, rKneeY, rAnkleX, rAnkleY, paint);
        canvas.drawLine(lKneeX, lKneeY, lAnkleX, lAnkleY, paint);

        Singleton singleton = Singleton.getInstance();
        singleton.setMyImage(drawBitmap);
        */
        Intent intent = new Intent(MainActivity.this, MainActivity2.class);
        intent.putExtra("Text", measureText);

        startActivity(intent);

    }

}