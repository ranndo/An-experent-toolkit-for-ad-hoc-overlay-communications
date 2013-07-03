package jp.ac.ehime_u.cite.remotecamera2;

import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.util.Log;
import android.view.MotionEvent;

public class AutoFocusPreview extends CameraPreview {
	static int count = 0;

	AutoFocusPreview(Context context) {
		super(context);
	}

	void autoFocus() {
		camera.autoFocus(new AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, final Camera camera) {
				ShutterCallback shutter = new ShutterCallback() {
					@Override
					public void onShutter() {
						Log.d("TEST", "onShutter");
					}
				};
				PictureCallback raw = new PictureCallback() {
					@Override
					public void onPictureTaken(byte[] data, Camera camera) {
						Log.d("TEST", "onPictureTaken: raw: data=" + data);
					}
				};
				PictureCallback jpeg = new PictureCallback() {
					@Override
					public void onPictureTaken(byte[] data, final Camera camera) {
						Log.d("TEST", "onPictureTaken: jpeg: data=" + data);
						FileOutputStream fos = null;
						try {

							// 出力ファイルオープン
							fos = context.openFileOutput("test"+count+".jpg"
									,context.MODE_WORLD_READABLE | context.MODE_WORLD_WRITEABLE);
							count++;
							if(count==10)count=9;

							fos.write(data);
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (fos != null) {
								try {
									fos.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}


					}
				};
				camera.takePicture(shutter, raw, jpeg);
				new Thread() {
					@Override
					public void run() {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						camera.startPreview();

//				            Intent intent = new Intent();
//				            intent.setClassName(
//				                    "jp.ac.ehime_u.cite.image",
//				                    "jp.ac.ehime_u.cite.image.ImageTestActivity");
//				            //intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//
//				            context.startActivity(intent);
						autoFocus();

					}
				}.start();


			}
		});
	}

	void cancelAutoFocus() {
		camera.cancelAutoFocus();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		if(event.getAction() == MotionEvent.ACTION_DOWN)
			autoFocus();
		return true;
	}
}
