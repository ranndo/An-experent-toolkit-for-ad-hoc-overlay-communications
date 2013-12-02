package jp.ac.ehime_u.cite.remotecamera2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.intenttester_r.ServiceInterface;

import jp.ac.ehime_u.cite.udptest.SendManager;
import jp.ac.ehime_u.cite.udptest.StaticIpAddress;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class CameraActivity extends Activity {
	private static final int MENU_AUTOFOCUS = Menu.FIRST + 1;
	private static CameraPreview view = null;
	protected static int order_size_x;
	protected static int order_size_y;
	protected static boolean do_capture;

	// インテント制御用変数
	private static int prev_receive_intent_id = -1;
	private static String prev_receive_intent_package_name = null;
	protected static int send_intent_id = 0;

	// 移してきたメンバ変数
	protected static String calling_address;
	protected static String my_address;
	protected static int compress_ratio;
	static SendManager sendManager = null;
	ServiceInterface sr = null;
	protected static int frame_rate_count;
	
	Timer observer_timer;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("TEST", "AutoFopcus#onCreate()");
//		frame_rate_count = 0;

		do_capture = false;
		compress_ratio = 80;	// 圧縮率の初期値

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

//		Intent it = getIntent();
//		order_size_x = it.getIntExtra("SIZE_X", 0);
//		order_size_y = it.getIntExtra("SIZE_Y", 0);

		// カメラ起動
		if(view == null){
			view = new CameraPreview(this);
			setContentView(view);
		}
		// Serviceにbindする
		Intent s_intent = new Intent(SendManager.class.getName());
		Boolean bind = bindService(s_intent, serviceConnection, BIND_AUTO_CREATE);
		if(bind){
			Log.d("BindService","bind成功");
		}else{
			Log.d("BindService","Bind失敗");
		}
		// 他画面から遷移したときはonNewIntentを通らないため、こちらで処理
		Intent intent = getIntent();
		if(intent!=null){
			// MAIN(通常のアプリ起動)でない方法で起動されているなら
			if(intent.getAction() != Intent.ACTION_MAIN){
				onNewIntent(intent);
			}
		}
		
//		// フレームレートオブザーバー
//		new Thread(new Runnable() {
//			
//			@Override
//			public void run() {
//				observer_timer = new Timer(false);
//				observer_timer.scheduleAtFixedRate(new TimerTask() {
//					
//					@Override
//					public void run() {
//						// TODO 自動生成されたメソッド・スタブ
//						Log.d("AutoFocusActivity","FrameRate := "+frame_rate_count);
////						final int pre = frame_rate_count;
////						
////						handler.post(new Runnable() {
////							
////							@Override
////							public void run() {
////								// TODO 自動生成されたメソッド・スタブ
////								setTitle(String.valueOf(pre));
////							}
////						});
//						frame_rate_count = 0;
//					}
//				}, 1000, 1000);
//			}
//		}).start();

	}

	@Override
	public void onNewIntent(Intent intent){
		super.onNewIntent(intent);
		Log.d("TEST", "AutoFopcus#onNewIntent()");

		StaticIpAddress sIp = new StaticIpAddress(this);
		my_address = sIp.getStaticIp();

		String package_name = intent.getStringExtra("PACKAGE");
		int intent_id = intent.getIntExtra("ID", 0);

		// 同インテントの多重処理防止 前回とパッケージ名またはIDが異なっていれば受理
		if( (package_name != prev_receive_intent_package_name)
				|| intent_id != prev_receive_intent_id){

			// 直前のパッケージ名,IDとして記録
			prev_receive_intent_package_name = package_name;
			prev_receive_intent_id = intent_id;

			final Uri uri = intent.getData();

			// schemeが"CameraCapture"なら
			if("CameraCapture".equals(uri.getScheme())){

				// "CameraCapture:"以降を取得
				String call_data = uri.getEncodedSchemeSpecificPart();


				// 先頭から順に切り出し
				String[] call_data_list = call_data.split("_");

				// [0]:フラグ
				// [1]:要求横サイズ
				// [2]:要求縦サイズ
				// [3]:送信元(返信用)
				calling_address = call_data_list[3].toString();
			}
		}
	}

	@Override
	public void onResume(){
		super.onResume();

		Log.d("TEST", "AutoFopcus#onResume()");
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();

		// unbind
		if(sendManager != null){
			unbindService(serviceConnection);
			Log.d("onDestroy","unbindservice");
		}
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			sendManager = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			sendManager = SendManager.Stub.asInterface(service);
			Log.d("ServiceS","sendManager初期化OK!");
		}
	};


	// メニューの追加
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean ret = super.onCreateOptionsMenu(menu);
		menu.add(0, Menu.FIRST, Menu.NONE, "send");

		return ret;
	}

	// メニューが押されたとき
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case Menu.FIRST:
			//finish();
			return true;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
