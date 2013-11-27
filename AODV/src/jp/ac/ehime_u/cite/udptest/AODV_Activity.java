package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AODV_Activity extends Activity {

	// メンバ変数
	// EditTextクラス(ip入力やボタン押下などユーザーの入力を処理するため)
	private EditText editTextSrc;
	private EditText editTextSrcPort;
	static EditText editTextDest;
	private EditText editTextDestPort;
	private static EditText editTextToBeSent;
	private EditText text_view_received;
	
	public static EditText testtext;

	public static Context context;
	
	private BroadcastReceiverAODV_Activity receiver = new BroadcastReceiverAODV_Activity();

	private AODV_Service mAODV_Service;

	// インテントの多重処理制御
	private static String prev_receive_package_name = null;
	private static int prev_receive_intent_id = -1;

	private boolean timer_stop = false;	//ExpandingRingSerchを終了するためのもの

	/** Called when the activity is first created. */
	@Override
	// オーバーライド(親･子クラスでメソッドが競合したとき子クラスの宣言で上書き)
	// ボタンなどが表示される前の初期化処理など
	// onCreateメソッドをオーバーライドとして記述していく
	public void onCreate(Bundle savedInstanceState) {
		// onCreateをオーバーライドする場合、スーパークラスのメソッドを呼び出す必要がある
		super.onCreate(savedInstanceState);
		
		// 起動時にソフトキーボードの立ち上がりを防ぐ
		this.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// アクティビティにビューグループを追加する
		setContentView(R.layout.main);

		// IDに対応するViewを取得、型が異なるのでキャスト
		editTextDest = (EditText) findViewById(R.id.editTextDest);
		editTextDestPort = (EditText) findViewById(R.id.editTextDestPort);
		editTextSrc = (EditText) findViewById(R.id.editTextSrc);
		editTextSrcPort = (EditText) findViewById(R.id.editTextSrcPort);
		editTextToBeSent = (EditText) findViewById(R.id.editTextToBeSent);

		StaticIpAddress sIp = new StaticIpAddress(this);
		editTextSrc.setText(sIp.getStaticIp());

		// 受信ログ用のTextView、同様にIDから取得
		//final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);
		text_view_received = (EditText) findViewById(R.id.textViewReceived);
		context = this;
		mAODV_Service = null;
		
		// ファイルオープン、同様にIDから取得しクリックイベントを追加
		Button buttonFileOpen = (Button) findViewById(R.id.buttonFileOpen);

		buttonFileOpen.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//SelectFile();
			}
		});

		// 送信Button、同様にIDから取得
		Button buttonSend = (Button) findViewById(R.id.buttonSend);

		// クリック処理、匿名クラス(その場限りの名前の無いクラス)を利用
		// ボタン毎に、ビューを意識せずに処理を記述できる
		buttonSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// editTextから送信先IP(String)、送信元(String)、port(int)の取得
				String destination_address = editTextDest.getText().toString();
				String source_address = editTextSrc.getText().toString();
				final int destination_port = Integer.parseInt(editTextDestPort
						.getText().toString());

				byte[] destination_address_b = RREQ.getByteAddress(destination_address);
				byte[] source_address_b	= RREQ.getByteAddress(source_address);

				// 送信先への経路が存在するかチェック
				final int index = mAODV_Service.searchToAdd(destination_address_b);

				// 経路が存在する場合、有効かどうかチェック
				boolean enableRoute = false; // 初期化

				if (index != -1) {
					if ( mAODV_Service.getRoute(index).stateFlag == 1 &&
							(mAODV_Service.getRoute(index).lifeTime > new Date().getTime())) {
						enableRoute = true;
					}
				}

				// ********* 経路が既に存在する場合 *******
				if (enableRoute) {
					// メッセージの送信
					sendMessage(mAODV_Service.getRoute(index).nextIpAdd, mAODV_Service.getRoute(index).hopCount, destination_port
							, destination_address_b, source_address_b, context);

					// 送信したことを表示
					text_view_received.append(editTextToBeSent.getText().toString()
							+ "-->" + destination_address+"\n");
				}
				// *********** 経路が存在しない場合 ***********
				else {
					text_view_received.append("Try Connect...\n");

					// 経路作成
					routeCreate(destination_address, source_address, destination_port, index
							, text_view_received, context);
				}
			}
		});


		// 送信Clear、同様にIDから取得
		Button buttonClear = (Button) findViewById(R.id.buttonClear);

		buttonClear.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				text_view_received.setText("");
			}
		});


		// ルートテーブル表示ボタン
		Button buttonShowRouteTable = (Button) findViewById(R.id.buttonShowRouteTable);

		buttonShowRouteTable.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// ルートテーブルの表示
				if(mAODV_Service.getRouteSize() == 0){
					text_view_received.append("Route_NotFound\n");
				}
				else{
					RouteTable route;

					text_view_received.append("ToIp,NextIp,Hop,Enable\n");
					for(int i=0;i<mAODV_Service.getRouteSize();i++){
						// i番目の経路を取得
						route = mAODV_Service.getRoute(i);

						text_view_received.append( getStringByByteAddress(route.toIpAdd) +",");
						text_view_received.append( getStringByByteAddress(route.nextIpAdd) +",");
						text_view_received.append( route.hopCount +",");

						if(route.stateFlag == 1)
							text_view_received.append("OK1,");
						else
							text_view_received.append("NG"+route.stateFlag+",");
						
						if(route.bluetoothFlag)
							text_view_received.append("BT\n");
						else
							text_view_received.append("Wi-Fi\n");
					}

					text_view_received.append(mAODV_Service.getRouteSize()+" RouteFound\n");

					text_view_received.setSelection(text_view_received.getText().length());
				}
//				if(mChatService != null){
//					String s = mChatService.showConnection();
//					if(s != null){
//						text_view_received.append(s);
//						text_view_received.setSelection(text_view_received.getText().length());
//					}
//					else{
//						text_view_received.append("BTnotConnect\n");
//						text_view_received.setSelection(text_view_received.getText().length());
//					}
//				}
			}
		});
		
		// スタートサービス
		startService(new Intent(context, AODV_Service.class));


		// 他画面から遷移したときはonNewIntentを通らないため、こちらで処理
		Intent intent = getIntent();
		if(intent!=null){
			// MAIN(通常のアプリ起動)でない方法で起動されているなら
			if(intent.getAction() != Intent.ACTION_MAIN){
				onNewIntent(intent);
			}
		}
	}

	// Intentの受け取り処理
	@Override
	protected void onNewIntent(Intent intent){
		super.onNewIntent(intent);

		// Intentからパッケージ名,ID取得
		String package_name = intent.getStringExtra("PACKAGE");
		int intent_id = intent.getIntExtra("ID", 0);

		Log.d("intent",package_name+"-"+intent_id);

		// 同インテントの多重処理防止 パッケージ名またはIDが異なっていれば受理
		if( (package_name != prev_receive_package_name)
				|| intent_id != prev_receive_intent_id){

			// 直前のパッケージ,IDとして記録
			prev_receive_package_name = package_name;
			prev_receive_intent_id = intent_id;

			// 起動方法のチェック 暗黙的インテント:SENDTOで起動されていれば
			if(Intent.ACTION_SENDTO.equals(intent.getAction())){
				final Uri uri = intent.getData();
				String task = intent.getStringExtra("TASK");

				// schemeが"connect"なら
				if("connect".equals(uri.getScheme()) && mAODV_Service != null){
					// 変数内の値が消えている場合がある？
					//editTextDest = (EditText)findViewById(R.id.editTextDest);
					//editTextToBeSent = (EditText)findViewById(R.id.editTextToBeSent);

					editTextDest.setText(uri.getEncodedSchemeSpecificPart());
					editTextToBeSent.setText(task);

					// 自動送信を試みる
					// editTextから送信先IP(String)、送信元(String)、port(int)の取得
					String destination_address = editTextDest.getText().toString();
					String source_address = editTextSrc.getText().toString();
					final int destination_port = Integer.parseInt(editTextDestPort
							.getText().toString());

					byte[] destination_address_b = RREQ.getByteAddress(destination_address);
					byte[] source_address_b	= RREQ.getByteAddress(source_address);

					// UIの出力先を取得
					//final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);

					// 送信先への経路が存在するかチェック
					final int index = mAODV_Service.searchToAdd(destination_address_b);

					// 経路が存在する場合、有効かどうかチェック
					boolean enableRoute = false; // 初期化

					if (index != -1) {
						if ( mAODV_Service.getRoute(index).stateFlag == 1 &&
								(mAODV_Service.getRoute(index).lifeTime > new Date().getTime())) {
							enableRoute = true;
						}
					}

					Context etc_context = context;
					// ファイルオープン用に他パッケージアプリのコンテキストを取得
					if( package_name != null ){
						try {
							etc_context = createPackageContext(package_name,0);
						} catch (NameNotFoundException e) {
							e.printStackTrace();
						}
					}

					// ********* 経路が既に存在する場合 *******
					if (enableRoute) {
						// メッセージの送信
						sendMessage(mAODV_Service.getRoute(index).nextIpAdd, mAODV_Service.getRoute(index).hopCount, destination_port
								, destination_address_b, source_address_b, etc_context);

						// 送信したことを表示
						text_view_received.append(editTextToBeSent.getText().toString()
								+ "-->" + destination_address+"\n");
					}
					// *********** 経路が存在しない場合 ***********
					else {
						text_view_received.append("Try Connect...\n");

						// 経路作成
						routeCreate(destination_address, source_address, destination_port, index
								, text_view_received, etc_context);
					}
				}
			}

			// 起動方法のチェック 暗黙的インテント:DELETEで起動されていれば
			if(Intent.ACTION_DELETE.equals(intent.getAction())){
				final Uri uri = intent.getData();

				if("path".equals(uri.getScheme())){
					deleteFile(uri.getEncodedSchemeSpecificPart());
				}
			}
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// bindService
		mAODV_Service = null;
		Intent intent = new Intent(context, AODV_Service.class);
		intent.setPackage(context.getPackageName());
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		
		// setListener
		IntentFilter filter = new IntentFilter(getString(R.string.AODV_ActivityReceiver));
		registerReceiver(receiver, filter);
		//Log.d("onResume()","onresume()");
	}
	


	// ルート作成＋メッセージ送信
	public void routeCreate(String destination_address, String source_address, final int destination_port
			, int search_result, EditText output_view, final Context context_){

		Log.d("debug", "Send__Start");

		// 送信先,送信元IPアドレスのbyte[]化
		final byte[] destination_address_b = RREQ.getByteAddress(destination_address);

		final byte[] source_address_b = RREQ.getByteAddress(source_address);

		// 検索結果を利用
		int index = search_result;

		// 画面出力先
		final EditText text_view_received = output_view;

		// 自身のシーケンス番号をインクリメント
		mAODV_Service.seqNumIncrement();

		// もし宛先がブロードキャストアドレスならExpandingRingSearchを行わない
		if( AODV_Service.BLOAD_CAST_ADDRESS.equals(destination_address)){

			// メッセージ抽出
			String text = editTextToBeSent.getText().toString();

			// RREQ_IDをインクリメント
			mAODV_Service.RREQ_ID_Increment();

			// 自分が送信したパケットを受信しないようにIDを登録
			mAODV_Service.newPastRREQ(mAODV_Service.getRREQ_ID(), source_address_b);

			// RREQの送信
			mAODV_Service.setDoBroadcast(true);

			try {
				RREQ.send(	destination_address_b,
							source_address_b,
							false,
							false,
							true,
							false,
							true,
							0,
							mAODV_Service.getSeqNum(),
							mAODV_Service.getRREQ_ID(),
							AODV_Service.NET_DIAMETER,
							text,
							mAODV_Service);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 宛先が通常のIPアドレスなら
		else{
			// TTLを初期値または過去のホップ数+TTL_ｲﾝｸﾘﾒﾝﾄにセット
			// 宛先シーケンス番号(+未知フラグ)もまとめてセット
			final boolean flagU;
			final int seqValue;

			// 無効経路が存在するなら、その情報を流用
			if (index != -1) {
				AODV_Service.TTL_VALUE = mAODV_Service.getRoute(index).hopCount + AODV_Service.TTL_INCREMENT;
				flagU = false;
				seqValue = mAODV_Service.getRoute(index).toSeqNum;
			}
			else{
				AODV_Service.TTL_VALUE = AODV_Service.TTL_START;
				flagU = true;
				seqValue = 0;
			}

			// ExpandingRingSearch
			timer_stop = false;		//timer_stopを初期化
			final Handler mHandler = new Handler();

			AODV_Service.RING_TRAVERSAL_TIME = 2 * AODV_Service.NODE_TRAVERSAL_TIME * (AODV_Service.TTL_VALUE + AODV_Service.TIMEOUT_BUFFER);

			try {
				new Thread(new Runnable() {
					public void run() {
						timer: while (true) {

							mHandler.post(new Runnable() {
								public void run() {
									int index_new;
									byte[] myAdd = source_address_b;

									// Threadは必ずぴったり停止するとは限らないので、停止しなくても中の処理は実行されないようにする
									if (!timer_stop) {

										// 以下、定期処理の内容
										// 経路が完成した場合、ループを抜ける
										if ( (index_new = mAODV_Service.searchToAdd(destination_address_b)) != -1) {
											text_view_received
													.append("Route_Create_Success!!\n");

											timer_stop = true;

											// メッセージの送信
											RouteTable rt = mAODV_Service.getRoute(index_new);
											sendMessage(rt.nextIpAdd, rt.hopCount,
													destination_port, rt.toIpAdd, myAdd, context_);

											// 送信したことを表示
											text_view_received.append(editTextToBeSent.getText().toString()
													+ "-->" + getStringByByteAddress(rt.toIpAdd)+"\n");
										}

										// TTLが上限値なRREQを送信済みならループを抜ける
										else if (AODV_Service.TTL_VALUE == (AODV_Service.TTL_THRESHOLD + AODV_Service.TTL_INCREMENT)) {
											text_view_received
													.append("Failed\n");
											timer_stop = true;
										}

										// TTLの微調整
										// 例えばINCREMENT2,THRESHOLD7のとき,TTLの変化は2->4->6->7(not
										// 8)
										if (AODV_Service.TTL_VALUE > AODV_Service.TTL_THRESHOLD) {
											AODV_Service.TTL_VALUE = AODV_Service.TTL_THRESHOLD;
										}

										// RREQ_IDをインクリメント
										mAODV_Service.RREQ_ID_Increment();

										// 自分が送信したパケットを受信しないようにIDを登録
										mAODV_Service.newPastRREQ(mAODV_Service.getRREQ_ID(), myAdd);

										// RREQの送信
										mAODV_Service.setDoBroadcast(true);

										try {
											RREQ.send(destination_address_b,
														myAdd,
														false,
														false,
														false,
														false,
														flagU,
														seqValue,
														mAODV_Service.getSeqNum(),
														mAODV_Service.getRREQ_ID(),
														AODV_Service.TTL_VALUE,
														null,
														mAODV_Service);
										} catch (Exception e) {
											e.printStackTrace();
										}

										// ちょっと強引な待機(本来はRREPが戻ってくれば待たなくていい時間も待っている)
										// 待ち時間をVALUEに合わせて更新
										AODV_Service.RING_TRAVERSAL_TIME = 2
												* AODV_Service.NODE_TRAVERSAL_TIME
												* (AODV_Service.TTL_VALUE + AODV_Service.TIMEOUT_BUFFER);

										AODV_Service.TTL_VALUE += AODV_Service.TTL_INCREMENT;
									}

								}

							});
							// 指定の時間停止する
							try {
								Thread.sleep(AODV_Service.RING_TRAVERSAL_TIME);
							} catch (InterruptedException e) {
							}

							// ループを抜ける処理
							if (timer_stop) {
								break timer;
							}
						}
					}
				}).start();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

//	public static	SelectFileDialog	_dlgSelectFile;
//
//
//	private	void	SelectFile()
//	{
//		//ここで画面回転を固定すべき（画面が固定されていないなら）
//
//		_dlgSelectFile = new SelectFileDialog(context,new Handler(),editTextToBeSent);
//		_dlgSelectFile.Show("/data/data/jp.ac.ehime_u.cite.udptest/files/");
//	}
	
	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onPause()
	{
//		if(_dlgSelectFile != null)
//			_dlgSelectFile.onPause();
		// unbindService
		unbindService(connection);
		// unsetListener
		unregisterReceiver(receiver);
		super.onPause();
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	public void onDestroy(){
		super.onDestroy();
	}
	
	// bindコネクション
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO 自動生成されたメソッド・スタブ
			mAODV_Service = null;
		}
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// TODO 自動生成されたメソッド・スタブ
			mAODV_Service = ((AODV_Service.AODV_ServiceBinder)service).getService();
		}
	};
	
	// Service->Android 通信用
	public class BroadcastReceiverAODV_Activity extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO 自動生成されたメソッド・スタブ
			String mes = intent.getStringExtra(context.getString(R.string.AODV_ActivityKey));
			text_view_received.append(mes);
			text_view_received.setSelection(text_view_received.getText().toString().length());
		}

	}


	// メニューの追加
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean ret = super.onCreateOptionsMenu(menu);

		menu.add(0 , Menu.FIRST , Menu.NONE
				, getString(R.string.Bluetooth)).setIcon(android.R.drawable.ic_menu_crop);
		menu.add(0 , Menu.FIRST + 1 ,Menu.NONE
				, "CALL"+getString(R.string.menu_finish)).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0 , Menu.FIRST + 2 ,Menu.NONE
				, "Image");

		return ret;
	}

	// メニューが押されたとき
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        // ルートテーブルメニューが押されたとき
        case Menu.FIRST:
            //別のActivityを起動させる
//            Intent intent = new Intent();
//
//            String package_name = "jp.ac.ehime_u.cite.remotecamera.ImageViewerActivity";
//            String class_name = package_name.substring(0, package_name.lastIndexOf('.'));
//            Log.d("Test",package_name + " / " +class_name);
//            intent.setAction(Intent.ACTION_VIEW);
//            intent.setClassName(
//                    "jp.ac.ehime_u.cite.remotecamera",
//                    "jp.ac.ehime_u.cite.remotecamera.ImageViewerActivity");
//            startActivity(intent);
            
            // Bluetooth.on/off
            // If the adapter is null, then Bluetooth is not supported
//            if (mBluetoothAdapter == null) {
//                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
//            }
//            else{
//	            // If BT is not on, request that it be enabled.
//	            // setupChat() will then be called during onActivityResult
//	            if (!mBluetoothAdapter.isEnabled()) {
//	                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//	                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
//	            // Otherwise, BT to OFF.
//	            // End BTService
//	            } else {
//	            	if (mChatService != null) mChatService.stop();
//	                mBluetoothAdapter.disable();
//	            }
//            }
            
        	return true;
        // 終了メニューが押されたとき
        case Menu.FIRST + 1:

//            Intent intent1 = new Intent();
//            intent1.setAction(Intent.ACTION_CALL);
//            intent1.setData(Uri.parse("CameraCapture:0_300_200_122.11.1.1"));	// TASK:〇の部分をセット
//            intent1.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
//            intent1.putExtra("ID", 1);
//            AODV_Activity.context.startActivity(intent1);
            //Activity終了
            //this.moveTaskToBack(true);

        	//udpListenerThread.destroy();
//            Intent intent1 = new Intent();
//            intent1.setAction(Intent.ACTION_CALL);
//            intent1.setData(Uri.parse("CameraCapture:0_300_200_133.11.34.16"));	// TASK:〇の部分をセット
//            intent1.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
//            intent1.putExtra("ID", 100);
//            AODV_Activity.context.startActivity(intent1);

        	finish();

            return true;
        case Menu.FIRST + 2:
        	// 簡易テスト用
            StringBuilder builder = new StringBuilder(1000);
            for(int i=0;i<1000;i++)builder.append('b');
            final String text = builder.toString();
            editTextToBeSent.setText(text);
            text_view_received.append(editTextToBeSent.getText().length()+"\n");
        
        	break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Log.d(this.getLocalClassName(), "onActivityResult " + resultCode);
//        switch (requestCode) {
//        case REQUEST_ENABLE_BT:
//            // When the request to enable Bluetooth returns
//            if (resultCode == Activity.RESULT_OK) {
//                // Bluetooth is now enabled, so set up a chat session
//                setupChat();
//                
//                // Only if the state is STATE_NONE, do we know that we haven't started already
//                if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
//                  // Start the Bluetooth chat services
//                  mChatService.start();
//                }
//            } else {
//                // User did not enable Bluetooth or an error occured
//                Log.d(this.getLocalClassName(), "BT not enabled");
//                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
    

	// ログの表示用EditTextのサイズを画面サイズに合わせて動的に決定
	// OnCreate()ではまだViewがレイアウトが初期化されていないため？
	// Viewサイズなどの取得が不可
/*	@Override
//	public void onWindowFocusChanged(boolean hasFocus) {
//		super.onWindowFocusChanged(hasFocus);
//
//		// receivedのY座標を取得 * タイトルバー,ステータスバーの下が0扱い *
//		final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);
//		final int received_top = text_view_received.getTop();
//
//		// Clearのサイズを取得
//		final Button clear_button = (Button) findViewById(R.id.buttonClear);
//		final int clear_height = clear_button.getHeight();
//
//		// 画面サイズを取得 *タイトルバー,ステータスバー含む*
//		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
//		Display display = wm.getDefaultDisplay();
//		final int display_height = display.getHeight();
//
//		Log.d("gamen_size","height:"+display_height+",wifth"+display.getWidth());
//
//		// タイトル+ステータスバーのサイズを50と仮定、不完全な動的決定
//		text_view_received.setHeight(display_height - received_top
//				- clear_height - 50);
//	}
*/


	// 送信先IPをローカルファイルに保存
	private void save_ip_to_local(String s) {

		try {
			OutputStream out = openFileOutput("ip.txt", MODE_APPEND
					| MODE_PRIVATE);
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,
					"UTF-8"));
			writer.append(s + "\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}




	// 送信textの先頭に、AODVとは無関係であるメッセージタイプ0を挿入する
	// 以下はフォーマット、[数字]は配列内の位置を示す
	// [0]		:メッセージタイプ0
	// [1-4]	:宛先アドレス
	// [5-8]	:送信元アドレス
	// [9-?]	:データ
	private byte[] addMessageTypeString(byte[] message,byte[] toIPAddress,byte[] my_address) {
		byte[] new_message = new byte[1 + 4 + 4 + message.length];

		new_message[0] = 0; // メッセージタイプ0
		System.arraycopy(toIPAddress, 0, new_message, 1, 4);
		System.arraycopy(my_address, 0, new_message, 1+4, 4);
		System.arraycopy(message, 0, new_message, 1+4+4, message.length);

		return new_message;
	}

	// ファイル送信用タイプ10やその他制御データを付加する
	// 以下はフォーマット、[数字]は配列内の位置を示す
	// [0]		: メッセージタイプ10
	// [1-4]	: 宛先アドレス
	// [5-8]	: 送信元アドレス
	// [9-12]	: 分割した何番目のデータか	* このメソッドでは代入しない *
	// [13-16]	: いくつに分割したか
	// [17-20]	: ファイル名のサイズ
	// [21-??]	: ファイル名(可変長)
	// [??-??]	: ファイルデータ(可変長,最大63K[byte]) * このメソッドでは代入しない *
	private byte[] addMessageTypeFile(int fileSize,byte[] toIPAddress,byte[] my_address,
			String fileName,int step){

		byte[] fileName_b = fileName.getBytes();	// ファイル名をbyte化
		byte[] fileName_size_b = intToByte(fileName_b.length);	// byte型ファイル名のサイズをbyte化
		byte[] step_b = intToByte(step);			// 分割数をbyte化

		byte[] new_message = new byte[21 + fileName_b.length + fileSize];

		new_message[0] = 10;	// メッセージタイプ10
		System.arraycopy(toIPAddress, 0, new_message, 1, 4);
		System.arraycopy(my_address, 0, new_message, 5, 4);
		// [9-12] 分割した何番目のデータか を代入しない
		System.arraycopy(step_b, 0, new_message, 13, 4);
		System.arraycopy(fileName_size_b, 0, new_message, 17, 4);
		System.arraycopy(fileName_b, 0, new_message, 21, fileName_b.length);
		// [??-??] ファイルデータを代入しない

		return new_message;
	}

	public void sendMessage(byte[] destination_next_hop_address_b, int hop_count, int destination_port
			, byte[] destination_address_b, byte[] source_address_b, Context context_){

		//editTextToBeSent = (EditText)findViewById(R.id.editTextToBeSent);
		final String text = editTextToBeSent.getText().toString();
		int index;
		
//		try{
//			// 古すぎる送信データを削除
//			while( (index=searchLifeTimeEmpty()) != -1){
//				try {
//					AODV_Activity.file_manager.get(index).file_in.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//				try {
//					AODV_Activity.file_manager.get(index).file.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//				AODV_Activity.file_manager.remove(index);
//			}
//			// ファイルが送信中なら送信中止
//			if(searchProgress(text, destination_address_b) != null){
//				Log.d("FILE_SEND","this_file_sending_now");
//			}
//			else{
//				// ファイルオープン
//				FileManager	files = new FileManager(text, destination_address_b,
//							source_address_b, context_);
//
//				// Log.d 開始時間
//				Date date = new Date();
//				SimpleDateFormat sdf = new SimpleDateFormat("MMdd'_'HHmmss");
//
//				String log = "time:"+sdf.format(date);
//				Log.d("SEND_T",log);
//
//				// 分割パケットの最初の１つを送信
//				files.fileSend(source_address_b, destination_next_hop_address_b, destination_port);
//
//				// 過程を保持 *分割後,パケットが1つのみでも再送が有りうるので必要*
//				files.add();
//
//				// タイムアウトを起動
//				final int time = 2 * NODE_TRAVERSAL_TIME * (hop_count + TIMEOUT_BUFFER);
//				final int step = files.file_next_no;
//				final byte[] data = files.buffer;
//				final byte[] dest_next_hop_add = destination_next_hop_address_b;
//				final int port_ = destination_port;
//				final String name = files.file_name;
//				final byte[] dest_add = files.destination_address;
//
//				final Handler mHandler = new Handler();
//
//				try {
//					new Thread(new Runnable() {
//
//						int wait_time = time;
//						int resend_count = 0;
//						int prev_step = step;
//						byte[] buffer = data;
//						byte[] destination_next_hop_address_b = dest_next_hop_add;
//						int port = port_;
//						String file_name = name;
//						byte[] destination_address = dest_add;
//
//						// 再送処理
//						public void run() {
//							timer: while (true) {
//
//								mHandler.post(new Runnable() {
//									public void run() {
//										SendByteArray.send(buffer, destination_next_hop_address_b);
//									}
//
//								});
//								// 指定の時間停止する
//								try {
//									Thread.sleep(wait_time);
//								} catch (InterruptedException e) {
//								}
//
//								resend_count++;
//
//								// ループを抜ける処理
//								if (resend_count == MAX_RESEND) {
//									break timer;
//								}
//								FileManager files = searchProgress(file_name, destination_address);
//								if(files == null){
//									break timer;
//								}
//								else{
//									if( files.file_next_no != prev_step){
//										break timer;
//									}
//								}
//
//							}
//						}
//					}).start();
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		} catch (FileNotFoundException e){
			// ファイルが開けない場合
			// **********テキストメッセージ:type0として送信*********

			// メッセージタイプ0,宛先アドレス,送信元アドレス,メッセージIDを先頭に付加
			byte[] buffer = addMessageTypeString(text.getBytes(),
					destination_address_b, source_address_b);

			// 送信try
			mAODV_Service.send(buffer, destination_next_hop_address_b);
//		}


	}

	// ファイル名,宛先が等しい経過を返す
	// 存在しない場合はnullを返す
//	public static FileManager searchProgress(String name,byte[] dest_add){
//		synchronized(AODV_Activity.fileManagerLock){
//			for(int i=0; i<AODV_Activity.file_manager.size();i++){
//				if( name.equals(AODV_Activity.file_manager.get(i).file_name)
//						&& Arrays.equals(dest_add, AODV_Activity.file_manager.get(i).destination_address)){
//					return AODV_Activity.file_manager.get(i);
//				}
//			}
//
//			return null;
//		}
//	}

	// 生存時間が寿命であるindexを返す
	// 存在しない場合は-1
//	public static int searchLifeTimeEmpty(){
//		synchronized(AODV_Activity.fileManagerLock){
//			long now = new Date().getTime();
//			for(int i=0; i<AODV_Activity.file_manager.size();i++){
//				if( AODV_Activity.file_manager.get(i).life_time < now ){
//					return i;
//				}
//			}
//			return -1;
//		}
//	}

	// IPアドレス(byte配列)から文字列(例:"127.0.0.1")へ変換
	public static String getStringByByteAddress(byte[] ip_address){

		if(ip_address.length != 4){
			return "Erorr_RouteIpAddress_is_not_correct";
		}

		// byteを符号無し整数に変換
		// 負なら+256
		int[] unsigned_b = new int[4];
		for(int i=0;i<4;i++){
			if(ip_address[i] >= 0){
				// 0以上ならそのまま
				unsigned_b[i] = ip_address[i];
			}
			else{
				unsigned_b[i] = ip_address[i]+256;
			}
		}
		return unsigned_b[0]+"."+unsigned_b[1]+"."+unsigned_b[2]+"."+unsigned_b[3];
	}


	// int型をbyte[]型へ変換
	public static byte[] intToByte(int num){

		// バイト配列への出力を行うストリーム
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		// バイト配列への出力を行うストリームをDataOutputStreamと連結する
		DataOutputStream out = new DataOutputStream(bout);

		try{	// 数値を出力
			out.writeInt(num);
		}catch(Exception e){
				System.out.println(e);
		}

		// バイト配列をバイトストリームから取り出す
		byte[] bytes = bout.toByteArray();
		return bytes;
	}

	// byte[]型をint型へ変換
	public static int byteToInt(byte[] num){

		int value = 0;
		// バイト配列の入力を行うストリーム
		ByteArrayInputStream bin = new ByteArrayInputStream(num);

		// DataInputStreamと連結
		DataInputStream in = new DataInputStream(bin);

		try{	// intを読み込み
			value = in.readInt();
		}catch(IOException e){
			System.out.println(e);
		}
		return value;
	}

	// String型のアドレスをbyte[]型に変換
	public byte[] getByteAddress(String str){

		// 分割
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}
	
	// Log.d
	public static void logD(String mes){
//		try {
//			FileOutputStream out = context.openFileOutput("LogD.txt",MODE_PRIVATE | MODE_APPEND);
//			out.write((mes+System.getProperty("line.separator")).getBytes());
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	public static void logD(byte[] data){
//		try {
//			FileOutputStream out = context.openFileOutput("LogD.txt",MODE_PRIVATE | MODE_APPEND);
//			out.write(data);
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	public static void logD(byte[] data,String name){
//		try {
//			FileOutputStream out = context.openFileOutput(name,MODE_PRIVATE | MODE_APPEND);
//			out.write(data);
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
}