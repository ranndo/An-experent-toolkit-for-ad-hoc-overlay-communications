package jp.ac.ehime_u.cite.udptest;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/*
 * ���J��
 */
public class BluetoothService extends Service {

	private final IBinder mBinder = new BluetoothServiceBinder();
	
	@Override
	public void onCreate() {
		//Log.d("service","oncreate");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//Log.d("service","onstartcommand");
		return START_NOT_STICKY;
	}
	
	// ���g��Ԃ�Binder
	public class BluetoothServiceBinder extends Binder{
		BluetoothService getService(){
			return BluetoothService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO �����������ꂽ���\�b�h�E�X�^�u
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent){
		return true;
	}

	@Override
	public void onDestroy() {
		// Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
	}
}
