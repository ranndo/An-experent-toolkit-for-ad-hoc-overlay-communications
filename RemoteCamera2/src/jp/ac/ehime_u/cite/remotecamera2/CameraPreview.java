package jp.ac.ehime_u.cite.remotecamera2;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jp.ac.ehime_u.cite.udptest.SendManager;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import android.graphics.ImageFormat;

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	protected Context context;
	private SurfaceHolder holder;
	protected Camera camera;
	int i;
	int count;
	protected int preview_width;
	protected int preview_height;
	protected int preview_format;

	CameraPreview(Context context) {
		super(context);
		this.context = context;
		holder = getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		i = 0;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d("TEST", "surfaceCreated");
		if (camera == null) {
			try {
				camera = Camera.open();

			} catch (RuntimeException e) {
				((Activity)context).finish();
				Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
			}
		}
		if (camera != null) {
			Log.d("TEST", "onP");
			camera.setPreviewCallback(new PreviewCallback() {
				@Override
				public void onPreviewFrame(byte[] data, Camera camera) {
					Log.d("TEST", "onPreviewFrame: preview: data=" + data);
				}
			});
			camera.setOneShotPreviewCallback(new PreviewCallback() {
				@Override
				public void onPreviewFrame(byte[] data, Camera camera) {
					Log.d("TEST", "onPreviewFrame: short preview: data=" + data);
				}
			});
			camera.setErrorCallback(new ErrorCallback() {
				@Override
				public void onError(int error, Camera camera) {
					Log.d("TEST", "onError: error=" + error);
				}
			});
		}
		try {
			camera.setPreviewDisplay(holder);
		} catch (IOException e) {
			camera.release();
			camera = null;
			((Activity)context).finish();
			Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d("TEST", "surfaceChanged");
		if (camera == null) {
			((Activity)context).finish();
		} else {
			camera.stopPreview();
//			setPictureFormat(format);
//			setPreviewSize(width, height);
//			setPictureSize(2048,1536);	// 写真サイズ
			camera.startPreview();
			
			preview_format = 0;
			count = 0;
			
			camera.setPreviewCallback(new PreviewCallback() {
				@Override
				public void onPreviewFrame(byte[] data, Camera camera) {
					Log.d("TEST", "onPreviewFrame: preview: data=" + data);

//					Parameters p = camera.getParameters();
//					Size size = p.getPreviewSize();
					if(preview_format == 0){
						Parameters p = camera.getParameters();
						preview_format = p.getPreviewFormat();
						preview_width  = p.getPreviewSize().width;
						preview_height = p.getPreviewSize().height;
						Log.d("Format",""+preview_format);
					}
					
					//Log.d("Size",size.width+":"+size.height);
//					i++;
/* 圧縮画像チェック.jpg */
//					if(i==2000){
//						camera.stopPreview();
//						
//						FileOutputStream f_out = null;
//						try {
//							YuvImage yuvimage = new YuvImage(data, preview_format, preview_width, preview_height,null);
////							YuvImage yuvimage = new YuvImage(data, p.getPreviewFormat(), size.width, size.height,null);
//							f_out = context.openFileOutput( "last"+i%100+".jpg"
//									,Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
//				
//							yuvimage.compressToJpeg(new Rect(0,0, preview_width, preview_height), CameraActivity.compress_ratio, f_out);
////							yuvimage.compressToJpeg(new Rect(0,0, size.width, size.height), CameraActivity.compress_ratio, f_out);
//							
////							if(isYuv422())
////								Log.d(getName(),"Yuv422" );
////							else if(isYuv420())
////								Log.d(getName(), "Yuv420");
//						} catch (FileNotFoundException e) {
//							// TODO 自動生成された catch ブロック
//							e.printStackTrace();
//						} finally {
//							if(f_out != null){
//								try {
//									f_out.close();
//								} catch (IOException e) {
//									// TODO 自動生成された catch ブロック
//									e.printStackTrace();
//								}
//							}
//						}
//					}
					
/* 1s間のトレーシング */
//					if(i==31){
//						Debug.startMethodTracing("/data/data/jp.ac.ehime_u.cite.remotecamera2/files/ossoi.trace");
//						//i=1999;
//					}
//					i++;
//					if(i==100)
					new CompressThread(data, i, preview_format,
							preview_width, preview_height, null, context).start();
					
/* 1s間のトレーシング */
//					if(i==30){
//						new Thread(new Runnable() {
//							
//							@Override
//							public void run() {
//								try {
//									Thread.sleep(500);
//									Debug.stopMethodTracing();
//									Log.d("Count",""+count);
//								} catch (InterruptedException e) {
//									// TODO 自動生成された catch ブロック
//									e.printStackTrace();
//								}
//							}
//						}).start();
//					}
				}
			});
//			
//			new Thread(new Runnable() {
//				
//				@Override
//				public void run() {
//					Timer timer = new Timer(false);
//					timer.schedule(new TimerTask() {
//						
//						@Override
//						public void run() {
//							// 1sごとにonPreviewFrameのフレームレートをログる
//							Log.d("FrameRate", Integer.toString(i));
//						}
//					}, 1000, 1000);
//				}
//			}).start();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d("TEST", "surfaceDestroyed");
		if (camera != null) {
			camera.stopPreview();
//			camera.release();
//			camera = null;
		}
	}

	protected void setPictureFormat(int format) {
		try {
			 Log.d("debug","int2:"+format);
			Camera.Parameters params = camera.getParameters();
			List<Integer> supported = params.getSupportedPictureFormats();
			if (supported != null) {  Log.d("debug","int:"+format);
				for (int f : supported) { Log.d("debug","f:"+f);
					if (f == format) {
						params.setPreviewFormat(format);
						camera.setParameters(params);
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void setPreviewSize(int width, int height) {
		Camera.Parameters params = camera.getParameters();
		List<Camera.Size> supported = params.getSupportedPreviewSizes();
		if (supported != null) {
			for (Camera.Size size : supported) {
				if (size.width <= width && size.height <= height) {
					params.setPreviewSize(size.width, size.height);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setPictureSize(int width, int height) {
		Camera.Parameters params = camera.getParameters();
		List<Camera.Size> supported = params.getSupportedPictureSizes();
		if (supported != null) {
			for (Camera.Size size : supported) {	Log.d("pic.size","sizex:"+size.width+",y:"+size.height);
				if (size.width == width && size.height == height) {
					params.setPictureSize(size.width, size.height);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setAntibanding(String antibanding) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedAntibanding();
		if (supported != null) {
			for (String ab : supported) {
				if (ab.equals(antibanding)) {
					params.setAntibanding(antibanding);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setColorEffect(String effect) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedColorEffects();
		if (supported != null) {
			for (String e : supported) {
				if (e.equals(effect)) {
					params.setColorEffect(effect);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setFlashMode(String flash_mode) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedFlashModes();
		if (supported != null) {
			for (String fm : supported) {
				if (fm.equals(flash_mode)) {
					params.setFlashMode(flash_mode);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setFocusMode(String focus_mode) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedFocusModes();
		if (supported != null) {
			for (String fm : supported) {
				if (fm.equals(focus_mode)) {
					params.setFocusMode(focus_mode);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setSceneMode(String scene_mode) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedSceneModes();
		if (supported != null) {
			for (String sm : supported) {
				if (sm.equals(scene_mode)) {
					params.setSceneMode(scene_mode);
					camera.setParameters(params);
					break;
				}
			}
		}
	}

	protected void setWhiteBalance(String white_balance) {
		Camera.Parameters params = camera.getParameters();
		List<String> supported = params.getSupportedWhiteBalance();
		if (supported != null) {
			for (String wb : supported) {
				if (wb.equals(white_balance)) {
					params.setWhiteBalance(white_balance);
					camera.setParameters(params);
					break;
				}
			}
		}
	}
//	@Override
//	public boolean onTouchEvent(MotionEvent event) {
//		autoFocus();
//
//		return true;
//	}
//	void cancelAutoFocus() {
//		camera.cancelAutoFocus();
//	}
//
//	void autoFocus() {
//		camera.autoFocus(new AutoFocusCallback() {
//			@Override
//			public void onAutoFocus(boolean success, final Camera camera) {
//				ShutterCallback shutter = new ShutterCallback() {
//					@Override
//					public void onShutter() {
//						Log.d("TEST", "onShutter");
//					}
//				};
//				PictureCallback raw = new PictureCallback() {
//					@Override
//					public void onPictureTaken(byte[] data, Camera camera) {
//						Log.d("TEST", "onPictureTaken: raw: data=" + data);
//					}
//				};
//				PictureCallback jpeg = new PictureCallback() {
//					@Override
//					public void onPictureTaken(byte[] data, Camera camera) {
//						Log.d("TEST", "onPictureTaken: jpeg: data=" + data);
//
//
//					    //savePicture(rotated);
//
//
//						FileOutputStream fos = null;
//						try {
//							// 出力ファイルオープン
//							Log.d("name","test.jpeg");
//							fos = context.openFileOutput("test.jpg"
//									,context.MODE_WORLD_READABLE | context.MODE_WORLD_WRITEABLE);
//
//							fos.write(data);
//						} catch (IOException e) {
//							e.printStackTrace();
//						} finally {
//							if (fos != null) {
//								try {
//									fos.close();
//								} catch (IOException e) {
//									e.printStackTrace();
//								}
//							}
//						}
//					}
//				};
//				camera.takePicture(shutter, raw, jpeg);
//				new Thread() {
//					@Override
//					public void run() {
//						try {
//							Thread.sleep(3000);
//						} catch (InterruptedException e) {
//						}
//						camera.startPreview();
//					}
//				}.start();
//
//			}
//		});
//	}
}
