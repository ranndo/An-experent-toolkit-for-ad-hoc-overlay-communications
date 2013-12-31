package jp.ac.ehime_u.cite.remotecamera2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageViewerActivity extends Activity {

	// メンバ変数
	private static ImageView view;
	private static Bitmap bmp;
	private Context context;
	private Handler handler;

	// 親Activityから移行してきたメンバ変数 *要チェック*
	//private String calling_file_name;
	private String calling_address;
	protected static int select_image_no = 0;
	//protected static ArrayList<String> image_name_list = new ArrayList<String>();
	protected static ArrayList<String> address_list = new ArrayList<String>();
	protected static boolean draw_switch;

	// インテント制御用変数
	private static int prev_receive_intent_id = -1;
	private static String prev_receive_intent_package_name = null;
	protected int send_intent_id = 0;
	private static Semaphore semaphore = null;
	private static int trace_count;
	private static boolean trace_flag;
	byte[] image_data;
	ByteArrayInputStream in_stream;

	// 古いファイルの削除
	//protected static int DELETE_TIME; // [ms]

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		if(view == null){
			view = new ImageView(this);
			setContentView(view);
		}
		context = this;
		draw_switch = false;
		bmp = null;

		//DELETE_TIME = getResources().getInteger(R.integer.DELETE_TIME);
		if(semaphore == null)
			semaphore = new Semaphore(1);
		trace_count = 0;
		trace_flag = false;
		handler = new Handler();

		// 他画面から遷移したときはonNewIntentを通らないため、こちらで処理
		Intent intent = getIntent();
		if(intent!=null){
			// MAIN(通常のアプリ起動)でない方法で起動されているなら
			if(intent.getAction() != Intent.ACTION_MAIN){
				// CATEGORY_LAUNCHERが含まれてなければonNewIntent()を実行
				if(intent.getCategories() != null){
					if(intent.getCategories().contains(Intent.CATEGORY_LAUNCHER) != true)
						onNewIntent(intent);
				}else{
					onNewIntent(intent);
				}
			}
		}
	}

	@Override
	public void onNewIntent(Intent intent){
		super.onNewIntent(intent);

		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		Log.d("onNewIntent",sdf.format(date));

		// Trace
		if(semaphore.availablePermits() != 0){
			//trace_count ++;
			if((trace_flag == false) && (trace_count > 100) && (trace_count < 200)){
				Debug.startMethodTracing(String.valueOf(trace_count));
				trace_flag = true;
				trace_count = 0;
			}
		}

		String package_name = intent.getStringExtra("PACKAGE");
		int intent_id = intent.getIntExtra("ID", 0);

		final Uri uri = intent.getData();

		// 同インテントの多重処理防止 前回とパッケージ名またはIDが異なっていれば受理
		if( (package_name != prev_receive_intent_package_name)
				|| intent_id != prev_receive_intent_id){

			// 直前のパッケージ名,IDとして記録
			prev_receive_intent_package_name = package_name;
			prev_receive_intent_id = intent_id;

			calling_address = intent.getStringExtra("SOURCE_ADDRESS");

			//deleteOldFile();
			//moveFile();
			//deleteAodvFile(calling_file_name);

			// インテントの処理代行
			if(calling_address != null){

				int index_result_file;
				int index_result_add;

				// 既にリストに無いかチェック
				if((index_result_add = searchAddress(calling_address)) == -1){	// アドレスが無ければ
					// アドレスの記録
					address_list.add(calling_address);

					// 初アドレスなら，画面への表示
					if(address_list.size() == 1){
						// パーミットが利用可能な場合のみ実行
						if(semaphore.tryAcquire()){
							HashMap<String,byte[]> dataMap = (HashMap<String,byte[]>)intent.getSerializableExtra("HashMap");
							image_data = dataMap.get("DATA");
//							draw_switch = true;
							new Thread(new Runnable() {
								@Override
								public void run() {
									doDraw(handler);
								}
							}).start();
						}
					}
				}
				else{
					// 選択中の画像ならば
					if( index_result_add == select_image_no){
						// 開き直し
						if(semaphore.tryAcquire()){
//							draw_switch = true;
							HashMap<String,byte[]> dataMap = (HashMap<String,byte[]>)intent.getSerializableExtra("HashMap");
							image_data = dataMap.get("DATA");

							new Thread(new Runnable() {
								@Override
								public void run() {
									doDraw(handler);
								}
							}).start();
						}
					}
				}
			}
		}


	}

	@Override
	public void onResume(){
		super.onResume();

//		// 表示の更新
//		if( draw_switch){
//			draw_switch = false;
//
//			// パーミットが利用可能な時のみ実行
//			if(semaphore.tryAcquire()){
//				// 受理インテントが行列を起こさないようにスレッド数を制限してマルチスレッド処理
//				new Thread(new Runnable() {
//					@Override
//					public void run() {
//						doDraw(handler);
//					}
//				}).start();
//				semaphore.release();	// パーミットの解放
//			}
//		}
	}

//	@Override
//    public boolean onTouchEvent(MotionEvent event){
//		trace_count += 101;
//    	if( event.getAction() == MotionEvent.ACTION_DOWN){
//    		// 画面サイズ取得
//    		WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
//    		Display disp = wm.getDefaultDisplay();
//
//    		Log.d("event","X:"+event.getX()+",windowX:"+disp.getWidth());
//
//    		// 左半分をタッチしたなら
//    		if( event.getX() < disp.getWidth()/2 ){
//    			// 選択ナンバーをデクリメント
//    			if(select_image_no == 0){
//    				select_image_no = image_name_list.size()-1;
//    			}
//    			else{
//    				select_image_no--;
//    			}
//    		}
//    		// 右半分をタッチしたなら
//    		else{
//    			// 選択ナンバーをインクリメント
//    			select_image_no++;
//    			if(select_image_no >= image_name_list.size()){
//    				select_image_no = 0;
//    			}
//    		}
//    		// 表示更新
//    		doDraw(handler);
//    	}
//
//    	return true;
//	}

	// viewの更新処理
	private void doDraw(Handler handler){

		if(bmp != null){
			bmp.recycle();
			bmp = null;
		}

		in_stream = new ByteArrayInputStream(image_data);
		bmp = BitmapFactory.decodeStream(in_stream);

		handler.post(new Runnable() {

			@Override
			public void run() {
				view.setImageBitmap(bmp);
				//setContentView(view);
				setTitle(address_list.get(select_image_no));

				// stop tracing
				if(trace_flag)
					Debug.stopMethodTracing();
				semaphore.release();

			}
		});
	}

	// メニューの追加
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean ret = super.onCreateOptionsMenu(menu);

		menu.add(0, Menu.FIRST, Menu.NONE, "trace+101")
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, Menu.FIRST+1, Menu.NONE, "exit");

		return ret;
	}

	// メニューが押されたとき
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case Menu.FIRST:
//			// AODVにインテントを投げる
//			Intent intent = new Intent();
//			intent.setClassName("jp.ac.ehime_u.cite.udptest",
//					"jp.ac.ehime_u.cite.udptest.RouteActivity");
//			intent.setAction(Intent.ACTION_SENDTO);
//			intent.setData(Uri.parse("connect:" + calling_address));
//			intent.putExtra("TASK", "TASK:CameraCapture:STOP");
//			intent.putExtra("PACKAGE", "jp.ac.ehime_u.cite.remotecamera");
//			intent.putExtra("ID", send_intent_id);
//			startActivity(intent);
//
//			send_intent_id++;
			trace_count = 110;
			return true;
		case Menu.FIRST+1:
			finish();
			return true;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	// ファイル削除
//	private void deleteOldFile(){
//
//		// ファイル一覧の取得
//		File[] files = new File("/data/data/jp.ac.ehime_u.cite.remotecamera/files/").listFiles();
//
//		// 現在時刻
//		long now_time = new Date().getTime();
//
//		for(int i=0; i<files.length; i++){
//
//			// 画像Viewerに登録済みなら削除しない
//			if( searchFile(files[i].getName()) == -1){
//				// ディレクトリではなく、寿命であるファイルを検索
//				if(!files[i].isDirectory()){
//					long last_update_time = files[i].lastModified();
//
//					// 最終更新日時から削除時間以上の時間が過ぎていれば
//					if( (now_time - last_update_time) > DELETE_TIME){
//						deleteFile(files[i].getName());
//					}
//				}
//			}
//		}
//	}

	// ファイル移動 *udptest->remotecamera 用*
//	private void moveFile(){
//
//		// 変数宣言
//		Context aodv_c = null;
//
//		try {
//			// 読み込み元のコンテキスト作成
//			aodv_c = createPackageContext("jp.ac.ehime_u.cite.udptest",0);
//			// 読み込み元のファイルストリームを作成し、チャネルを取得
//			FileChannel in_channel = aodv_c.openFileInput(calling_file_name).getChannel();
//
//			// 同様に出力先
//			FileChannel out_channel= this.openFileOutput(calling_file_name, MODE_WORLD_READABLE | MODE_WORLD_WRITEABLE)
//								.getChannel();
//
//			// 転送
//			in_channel.transferTo(0, in_channel.size(), out_channel);
//
//			// クローズ
//			in_channel.close();
//			out_channel.close();
//
//		} catch (NameNotFoundException e) {
//			e.printStackTrace();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

    // 画像Viewer用 リストに既に存在するかチェック
//	private int searchFile(String name){
//		for(int i=0;i<image_name_list.size();i++){
//			if(image_name_list.get(i).equals(name)){
//				return i;
//			}
//		}
//		return -1;
//	}

	// 画像Viewer用 リストに既に存在するかチェック
	private int searchAddress(String address){
		for(int i=0;i<address_list.size();i++){
			if(address_list.get(i).equals(address)){
				return i;
			}
		}
		return -1;
	}

	// ファイル削除 *udptest内*
//	private void deleteAodvFile(String file_name){
//
//		// 削除命令インテントを発行
//		// AODVに明示的インテントを投げる
//        Intent intent = new Intent();
//        intent.setClassName(
//                "jp.ac.ehime_u.cite.udptest",
//                "jp.ac.ehime_u.cite.udptest.AODV_Activity");
//        intent.setAction(Intent.ACTION_DELETE);
//        intent.setData(Uri.parse("path:"+file_name));
//        intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.remotecamera");
//        intent.putExtra("ID", send_intent_id);
//        startActivity(intent);
//
//        send_intent_id++;
//	}


}
