package jp.ac.ehime_u.cite.remotecamera2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.util.Log;

// 圧縮・送信インテントの生成スレッド
public class CompressThread extends Thread {

	byte[] data;
	int index;
	int format;
	int width,height;
	int[] strides;
	Context context;

	public CompressThread(byte[] data_,int index_, int format_, int width_, int height_,int[] strides_,Context context_){
		data = data_.clone();
		index = index_;
		format = format_;
		width = width_;
		height = height_;
		if(strides != null)
			strides = strides_.clone();
		else	strides = null;
		context = context_;
	}

	@Override
	public void run(){
		YuvImage yuvimage = new YuvImage(data,format,width,height,strides);
		FileOutputStream f_out = null;

		// 生データの圧縮
		try {
			f_out = context.openFileOutput( context.getString(R.string.preview_filename)+index+".jpg"
					,Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);

			yuvimage.compressToJpeg(new Rect(0,0,width,height), 80, f_out);

//			if(isYuv422())
//				Log.d(getName(),"Yuv422" );
//			else if(isYuv420())
//				Log.d(getName(), "Yuv420");

		} catch (FileNotFoundException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		} finally {
			if(f_out != null){
				try {
					f_out.close();
				} catch (IOException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
			}
		}

		// インテントの生成
//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_SENDTO);
//        intent.setData(Uri.parse("connect:"+RemoteCameraActivity.calling_address));
//        intent.putExtra("TASK", name);
//        intent.putExtra("PACKAGE",context.getPackageName());
//        intent.putExtra("ID", RemoteCameraActivity.send_intent_id);
//        context.startActivity(intent);


	}

	public boolean isYuv422() {
		final int n_bytes_from_size = this.height
				* this.width * 2;
		return this.data.length == n_bytes_from_size;
	}


	public boolean isYuv420() {
		final int n_bytes_from_size = this.height
				* this.width / 2 * 3;
		return this.data.length == n_bytes_from_size;
	}

}
