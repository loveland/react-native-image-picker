package com.imagepicker;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Patterns;
import android.util.Log;
import java.util.UUID;
import android.webkit.MimeTypeMap;
import android.content.pm.PackageManager;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.imagepicker.media.ImageConfig;
import com.imagepicker.permissions.PermissionUtils;
import com.imagepicker.permissions.OnImagePickerPermissionsCallback;
import com.imagepicker.utils.MediaUtils.ReadExifResult;
import com.imagepicker.utils.ReadableMapUtils;
import com.imagepicker.utils.RealPathUtil;
import com.imagepicker.utils.UI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;

import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.modules.core.PermissionAwareActivity;

import static com.imagepicker.utils.MediaUtils.*;
import static com.imagepicker.utils.MediaUtils.createNewFile;
import static com.imagepicker.utils.MediaUtils.getResizedImage;

@ReactModule(name = ImagePickerModule.NAME)
public class ImagePickerModule extends ReactContextBaseJavaModule
        implements ActivityEventListener
{
  public static final String NAME = "ImagePickerManager";

  public static final int DEFAULT_EXPLAINING_PERMISSION_DIALIOG_THEME = R.style.DefaultExplainingPermissionsTheme;

  public static final int REQUEST_LAUNCH_IMAGE_CAPTURE    = 13001;
  public static final int REQUEST_LAUNCH_IMAGE_LIBRARY    = 13002;
  public static final int REQUEST_LAUNCH_VIDEO_LIBRARY    = 13003;
  public static final int REQUEST_LAUNCH_VIDEO_CAPTURE    = 13004;
  public static final int REQUEST_PERMISSIONS_FOR_CAMERA  = 14001;
  public static final int REQUEST_PERMISSIONS_FOR_LIBRARY = 14002;

  private final ReactApplicationContext reactContext;
  private final int dialogThemeId;

  protected Callback callback;
  private Callback permissionRequestCallback;

  private ReadableMap options;
  protected Uri cameraCaptureURI;
  private Boolean noData = false;
  private Boolean pickVideo = false;
  private Boolean pickBoth = false;
  private ImageConfig imageConfig = new ImageConfig(null, null, 0, 0, 100, 0, false);

  @Deprecated
  private int videoQuality = 1;

  @Deprecated
  private int videoDurationLimit = 0;

  private ResponseHelper responseHelper = new ResponseHelper();
  private PermissionListener listener = new PermissionListener()
  {
    public boolean onRequestPermissionsResult(final int requestCode,
                                              @NonNull final String[] permissions,
                                              @NonNull final int[] grantResults)
    {
      boolean permissionsGranted = true;
      for (int i = 0; i < permissions.length; i++)
      {
        final boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
        permissionsGranted = permissionsGranted && granted;
      }

      if (callback == null || options == null)
      {
        return false;
      }

      if (!permissionsGranted)
      {
        responseHelper.invokeError(permissionRequestCallback, "Permissions weren't granted");
        return false;
      }

      switch (requestCode)
      {
        case REQUEST_PERMISSIONS_FOR_CAMERA:
          launchCamera(options, permissionRequestCallback);
          break;

        case REQUEST_PERMISSIONS_FOR_LIBRARY:
          break;

      }
      return true;
    }
  };

  public ImagePickerModule(ReactApplicationContext reactContext)
  {
    this(reactContext, DEFAULT_EXPLAINING_PERMISSION_DIALIOG_THEME);
  }

  public ImagePickerModule(ReactApplicationContext reactContext,
                           @StyleRes final int dialogThemeId)
  {
    super(reactContext);

    this.dialogThemeId = dialogThemeId;
    this.reactContext = reactContext;
    this.reactContext.addActivityEventListener(this);
  }

  @Override
  public String getName() {
    return NAME;
  }


  public void doOnCancel()
  {
    if (callback != null) {
      responseHelper.invokeCancel(callback);
      callback = null;
    }
  }

  public void launchCamera()
  {
    this.launchCamera(this.options, this.callback);
  }

  // NOTE: Currently not reentrant / doesn't support concurrent requests
  @ReactMethod
  public void launchCamera(final ReadableMap options, final Callback callback)
  {
      Log.i("Seth", "This is working2");
    permissionRequestCallback = callback;

    if (!isCameraAvailable())
    {
      responseHelper.invokeError(callback, "Camera not available");
      return;
    }

    final Activity currentActivity = getCurrentActivity();
    if (currentActivity == null)
    {
      responseHelper.invokeError(callback, "can't find current Activity");
      return;
    }

    this.callback = callback;
    this.options = options;

    if (!permissionsCheck(currentActivity, callback, REQUEST_PERMISSIONS_FOR_CAMERA))
    {
      return;
    }

    parseOptions(this.options);

    int requestCode;
    Intent cameraIntent;

      // specify photo
      requestCode = REQUEST_LAUNCH_IMAGE_CAPTURE;
      cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            Log.i("Seth", "Creating image");
      // the issue could be if the directory isn't there to write to. 

      /*
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        */
        File image_path = reactContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        final String image_filename = new StringBuilder("image-")
                .append(UUID.randomUUID().toString())
                .append(".jpg")
                .toString();
        File original= new File(image_path, image_filename);
        try {
            image_path.mkdirs();
            original.createNewFile();
            Log.i("Seth", "Image created");
        }
        catch(Exception e) {
            Log.i("Seth", "there was a problem creating image file");
        }
       
            //path = reactContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
      /*

        try
        {
            path.mkdirs();
            result.createNewFile();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            result = null;
        }

        return result;
    }
    */
      if (original.exists()) {
          Log.i("Seth", "original file exists");
      }
      else {
          Log.i("Seth", "original file does not exist");
      }
      imageConfig = imageConfig.withOriginalFile(original);

      if (imageConfig.original != null) {
        cameraCaptureURI = RealPathUtil.compatUriFromFile(reactContext, imageConfig.original);
      }else {
        responseHelper.invokeError(callback, "Couldn't get file path for photo");
        return;
      }
      if (cameraCaptureURI == null)
      {
        responseHelper.invokeError(callback, "Couldn't get file path for photo");
        return;
      }
      cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureURI);
      // specify photo

    if (cameraIntent.resolveActivity(reactContext.getPackageManager()) == null)
    {
        Log.i("Seth", "cameraIntent resolveActivity getPackageManager is null");
      responseHelper.invokeError(callback, "Cannot launch camera");
      return;
    }

    // Workaround for Android bug.
    // grantUriPermission also needed for KITKAT,
    // see https://code.google.com/p/android/issues/detail?id=76683
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
        Log.i("Seth", "on less than android system");
      responseHelper.invokeError(callback, "Cannot launch camera");
      List<ResolveInfo> resInfoList = reactContext.getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
      for (ResolveInfo resolveInfo : resInfoList) {
        String packageName = resolveInfo.activityInfo.packageName;
        reactContext.grantUriPermission(packageName, cameraCaptureURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }
    }
    try
    {
      Log.i("Seth", "beginning currentActivity cameraIntent");
      currentActivity.startActivityForResult(cameraIntent, requestCode);
    }
    catch (ActivityNotFoundException e)
    {
      e.printStackTrace();
      responseHelper.invokeError(callback, "Cannot launch camera");
    }
  }


  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      try {
      Log.i("Seth", "onActivityResult requestCode"+requestCode);
      Log.i("Seth", "onActivityResult resultCode"+resultCode);
    //robustness code
    if (passResult(requestCode))
    {
      return;
    }

    responseHelper.cleanResponse();

    // user cancel
    if (resultCode != Activity.RESULT_OK)
    {
      Log.i("Seth", "resultcode failure");
      removeUselessFiles(requestCode, imageConfig);
      responseHelper.invokeCancel(callback);
      callback = null;
      return;
    }

    Uri uri = null;
    switch (requestCode)
    {
      case REQUEST_LAUNCH_IMAGE_CAPTURE:
        uri = cameraCaptureURI;
        break;

    }

    final ReadExifResult result = readExifInterface(responseHelper, imageConfig);

    if (result.error != null)
    {
      removeUselessFiles(requestCode, imageConfig);
      responseHelper.invokeError(callback, result.error.getMessage());
      callback = null;
      return;
    }

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(imageConfig.original.getAbsolutePath(), options);
    int initialWidth = options.outWidth;
    int initialHeight = options.outHeight;
    updatedResultResponse(uri, imageConfig.original.getAbsolutePath());

      Log.i("Seth", "near useOriginal");
    // don't create a new file if contraint are respected
    if (imageConfig.useOriginal(initialWidth, initialHeight, result.currentRotation))
    {
      responseHelper.putInt("width", initialWidth);
      responseHelper.putInt("height", initialHeight);
      fileScan(reactContext, imageConfig.original.getAbsolutePath());
    }
    else
    {
      imageConfig = getResizedImage(reactContext, this.options, imageConfig, initialWidth, initialHeight, requestCode);
      if (imageConfig.resized == null)
      {
        removeUselessFiles(requestCode, imageConfig);
        responseHelper.putString("error", "Can't resize the image");
      }
      else
      {
        uri = Uri.fromFile(imageConfig.resized);
        BitmapFactory.decodeFile(imageConfig.resized.getAbsolutePath(), options);
        responseHelper.putInt("width", options.outWidth);
        responseHelper.putInt("height", options.outHeight);

        updatedResultResponse(uri, imageConfig.resized.getAbsolutePath());
        fileScan(reactContext, imageConfig.resized.getAbsolutePath());
      }
    }

      Log.i("Seth", "near saveToCameraRoll");
    if (imageConfig.saveToCameraRoll && requestCode == REQUEST_LAUNCH_IMAGE_CAPTURE)
    {
      final RolloutPhotoResult rolloutResult = rolloutPhotoFromCamera(imageConfig);

      if (rolloutResult.error == null)
      {
        imageConfig = rolloutResult.imageConfig;
        uri = Uri.fromFile(imageConfig.getActualFile());
        updatedResultResponse(uri, imageConfig.getActualFile().getAbsolutePath());
      }
      else
      {
        removeUselessFiles(requestCode, imageConfig);
        final String errorMessage = new StringBuilder("Error moving image to camera roll: ")
                .append(rolloutResult.error.getMessage()).toString();
        responseHelper.putString("error", errorMessage);
        return;
      }
    }

    responseHelper.invokeResponse(callback);
    callback = null;
    this.options = null;
      } catch (Exception e) {
      Log.i("Seth", "hit catch statement at the end");
            responseHelper.invokeError(callback, "Seth - Cannot launch camera");
            return;
      }

      Log.i("Seth", "reached bottom of file");
    responseHelper.invokeError(callback, "Seth - reached bottom of file");
  }

  public void invokeCustomButton(@NonNull final String action)
  {
    responseHelper.invokeCustomButton(this.callback, action);
  }

  @Override
  public void onNewIntent(Intent intent) { }

  public Context getContext()
  {
    return getReactApplicationContext();
  }

  public @StyleRes int getDialogThemeId()
  {
    return this.dialogThemeId;
  }

  public @NonNull Activity getActivity()
  {
    return getCurrentActivity();
  }


  private boolean passResult(int requestCode)
  {
    return callback == null || (cameraCaptureURI == null && requestCode == REQUEST_LAUNCH_IMAGE_CAPTURE)
            || (requestCode != REQUEST_LAUNCH_IMAGE_CAPTURE && requestCode != REQUEST_LAUNCH_IMAGE_LIBRARY
            && requestCode != REQUEST_LAUNCH_VIDEO_LIBRARY && requestCode != REQUEST_LAUNCH_VIDEO_CAPTURE);
  }

  private void updatedResultResponse(@Nullable final Uri uri,
                                     @NonNull final String path)
  {
    responseHelper.putString("uri", uri.toString());
    responseHelper.putString("path", path);

    if (!noData) {
      responseHelper.putString("data", getBase64StringFromFile(path));
    }

    putExtraFileInfo(path, responseHelper);
  }

  private boolean permissionsCheck(@NonNull final Activity activity,
                                   @NonNull final Callback callback,
                                   @NonNull final int requestCode)
  {
    final int writePermission = ActivityCompat
            .checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    final int cameraPermission = ActivityCompat
            .checkSelfPermission(activity, Manifest.permission.CAMERA);

    boolean permissionsGranted = false;

    switch (requestCode) {
      case REQUEST_PERMISSIONS_FOR_LIBRARY:
        permissionsGranted = writePermission == PackageManager.PERMISSION_GRANTED;
        break;
      case REQUEST_PERMISSIONS_FOR_CAMERA:
        permissionsGranted = cameraPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED;
        break;
    }

    if (!permissionsGranted)
    {
      final Boolean dontAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA);

      if (dontAskAgain)
      {
        final AlertDialog dialog = PermissionUtils
                .explainingDialog(this, options, new PermissionUtils.OnExplainingPermissionCallback()
                {
                  @Override
                  public void onCancel(WeakReference<ImagePickerModule> moduleInstance,
                                       DialogInterface dialogInterface)
                  {
                    final ImagePickerModule module = moduleInstance.get();
                    if (module == null)
                    {
                      return;
                    }
                    module.doOnCancel();
                  }

                  @Override
                  public void onReTry(WeakReference<ImagePickerModule> moduleInstance,
                                      DialogInterface dialogInterface)
                  {
                    final ImagePickerModule module = moduleInstance.get();
                    if (module == null)
                    {
                      return;
                    }
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", module.getContext().getPackageName(), null);
                    intent.setData(uri);
                    final Activity innerActivity = module.getActivity();
                    if (innerActivity == null)
                    {
                      return;
                    }
                    innerActivity.startActivityForResult(intent, 1);
                  }
                });
        if (dialog != null) {
          dialog.show();
        }
        return false;
      }
      else
      {
        String[] PERMISSIONS;
        PERMISSIONS = new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (activity instanceof ReactActivity)
        {
          ((ReactActivity) activity).requestPermissions(PERMISSIONS, requestCode, listener);
        }
        else if (activity instanceof PermissionAwareActivity) {
          ((PermissionAwareActivity) activity).requestPermissions(PERMISSIONS, requestCode, listener);
        }
        else if (activity instanceof OnImagePickerPermissionsCallback)
        {
          ((OnImagePickerPermissionsCallback) activity).setPermissionListener(listener);
          ActivityCompat.requestPermissions(activity, PERMISSIONS, requestCode);
        }
        else
        {
          final String errorDescription = new StringBuilder(activity.getClass().getSimpleName())
                  .append(" must implement ")
                  .append(OnImagePickerPermissionsCallback.class.getSimpleName())
                  .append(" or ")
                  .append(PermissionAwareActivity.class.getSimpleName())
                  .toString();
          throw new UnsupportedOperationException(errorDescription);
        }
        return false;
      }
    }
    return true;
  }

  private boolean isCameraAvailable() {
    return reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
      || reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
  }

  private @NonNull String getRealPathFromURI(@NonNull final Uri uri) {
    return RealPathUtil.getRealPathFromURI(reactContext, uri);
  }

  private String getBase64StringFromFile(String absoluteFilePath) {
    InputStream inputStream = null;
    try {
      inputStream = new FileInputStream(new File(absoluteFilePath));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    byte[] bytes;
    byte[] buffer = new byte[8192];
    int bytesRead;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    bytes = output.toByteArray();
    return Base64.encodeToString(bytes, Base64.NO_WRAP);
  }

  private void putExtraFileInfo(@NonNull final String path,
                                @NonNull final ResponseHelper responseHelper)
  {
    try {
      // size && filename
      File f = new File(path);
      responseHelper.putDouble("fileSize", f.length());
      responseHelper.putString("fileName", f.getName());
      // type
      String extension = MimeTypeMap.getFileExtensionFromUrl(path);
      String fileName = f.getName();
      if (extension != "") {
        responseHelper.putString("type", MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
      } else {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
          extension = fileName.substring(i+1);
          responseHelper.putString("type", MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void parseOptions(final ReadableMap options) {
    noData = false;
    if (options.hasKey("noData")) {
      noData = options.getBoolean("noData");
    }
    imageConfig = imageConfig.updateFromOptions(options);
    pickVideo = false;
    pickBoth = false;
    if (options.hasKey("mediaType") && options.getString("mediaType").equals("mixed")) {
      pickBoth = true;
    }
    if (options.hasKey("mediaType") && options.getString("mediaType").equals("video")) {
      pickVideo = true;
    }
    videoQuality = 1;
    if (options.hasKey("videoQuality") && options.getString("videoQuality").equals("low")) {
      videoQuality = 0;
    }
    videoDurationLimit = 0;
    if (options.hasKey("durationLimit")) {
      videoDurationLimit = options.getInt("durationLimit");
    }
  }
}
