package jp.ac.ehime_u.cite.remotecamera2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import jp.ac.ehime_u.cite.udptest.RINT;
import jp.ac.ehime_u.cite.udptest.SendManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

// 圧縮・送信インテントの生成スレッド
public class CompressThread extends Thread {

	byte[] data;
	int index;
	int format;
	int width, height;
	int[] strides;
	Context context;
	Semaphore sendLock;

	public CompressThread(byte[] data_, int index_, int format_, int width_,
			int height_, int[] strides_, Context context_, Semaphore semaphore) {
		data = data_;// .clone();
		index = index_;
		format = format_;
		width = width_;
		height = height_;
		if (strides != null)
			strides = strides_.clone();
		else
			strides = null;
		context = context_;
		sendLock = semaphore;
	}

	@Override
	public void run() {
		YuvImage yuvimage = new YuvImage(data, format, width, height, strides);

		// バッファ
		ByteArrayOutputStream byte_array_out_stream;
		byte_array_out_stream = new ByteArrayOutputStream();
		// Log.d("ServiceS","たしかに利用している2");
		yuvimage.compressToJpeg(new Rect(0, 0, width, height),
				CameraActivity.compress_ratio, byte_array_out_stream);
		if (byte_array_out_stream.size() <= 60000) {
			// サービスを使用し、画像データの送信
			byte flag = (byte) (RINT.FLAG_PACKAGE_NAME | RINT.FLAG_INTENT_ACTION);
			String package_name = context.getPackageName()
					+ ".ImageViewerActivity";
			String s_null = null;
			List<String> s_list = null;
			try {
				// Serviceを通して送信
				if (CameraActivity.sendManager != null) {
					CameraActivity.sendManager.SendMessage(
							CameraActivity.calling_address,
							CameraActivity.my_address, flag, package_name,
							Intent.ACTION_VIEW, 0, s_null, s_null, s_list,
							byte_array_out_stream.toByteArray());
					// AutoFocus.sendManager.Test();
					// Log.d("ServiceS","たしかに利用している");
				}
			} catch (RemoteException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			} finally {
				sendLock.release();
			}

			// // インテントの生成
			// Intent intent = new Intent();
			// intent.setAction(Intent.ACTION_SENDTO);
			// intent.setData(Uri.parse("connect:"+AutoFocus.calling_address));
			// intent.putExtra("TASK", name);
			// intent.putExtra("PACKAGE",context.getPackageName());
			// intent.putExtra("ID", AutoFocus.send_intent_id);
			// context.startActivity(intent);
		} else {
			// 圧縮率を低下
			CameraActivity.compress_ratio -= 5;
			sendLock.release();
		}

		// 生データの圧縮
		// 画像ファイルを作る場合
		// FileOutputStream f_out = null;
		// try {
		// f_out = context.openFileOutput(
		// context.getString(R.string.preview_filename)+index+".jpg"
		// ,Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
		//
		// yuvimage.compressToJpeg(new Rect(0,0,width,height),
		// CameraActivity.compress_ratio, f_out);
		//
		// // if(isYuv422())
		// // Log.d(getName(),"Yuv422" );
		// // else if(isYuv420())
		// // Log.d(getName(), "Yuv420");
		// } catch (FileNotFoundException e) {
		// // TODO 自動生成された catch ブロック
		// e.printStackTrace();
		// } finally {
		// if(f_out != null){
		// try {
		// f_out.close();
		// } catch (IOException e) {
		// // TODO 自動生成された catch ブロック
		// e.printStackTrace();
		// }
		// }
		// }

		// インテントの生成
		// Intent intent = new Intent();
		// intent.setAction(Intent.ACTION_SENDTO);
		// intent.setData(Uri.parse("connect:"+RemoteCameraActivity.calling_address));
		// intent.putExtra("TASK", name);
		// intent.putExtra("PACKAGE",context.getPackageName());
		// intent.putExtra("ID", RemoteCameraActivity.send_intent_id);
		// context.startActivity(intent);

	}

	public boolean isYuv422() {
		final int n_bytes_from_size = this.height * this.width * 2;
		return this.data.length == n_bytes_from_size;
	}

	public boolean isYuv420() {
		final int n_bytes_from_size = this.height * this.width / 2 * 3;
		return this.data.length == n_bytes_from_size;
	}

}
