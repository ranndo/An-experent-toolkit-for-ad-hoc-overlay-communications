package jp.ac.ehime_u.cite.udptest;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
 * 常駐型サービスの基底クラス。
 * @author id:language_and_engineering
 *
 */
public class AODV_Service extends Service implements SensorEventListener
{
	// スレッド
	private Thread udpListenerThread;	// 受信スレッド
	private Thread routeManagerThread;	// ルート監視スレッド
	
	// ポート
	private final int port = 12345;
	
	// ルートテーブル
	private ArrayList<RouteTable> routeTable = new ArrayList<RouteTable>();
	
	// PATH_DISCOVERY_TIMEの間に受信したRREQの送信元とIDを記録
	private ArrayList<PastData> receiveRREQ_List = new ArrayList<PastData>();
	
	// 片方向リンク排除用アドレス一覧
	public ArrayList<BlackData> black_list = new ArrayList<BlackData>();
	public HashSet<byte[]> ack_demand_list = new HashSet<byte[]> ();
	
	// データベースへ様々な情報を記録
	private SQLiteDatabase log_db;
	private String MyIP;
	private byte[] myAddress;
	private String network_interface;
	
	// マルチスレッドの排他制御用オブジェクト
	public Object routeLock = new Object();
	public Object pastDataLock = new Object();
	public Object blackListLock = new Object();
	public Object ackDemandListLock = new Object();
	public Object RREQ_ID_Lock = new Object();
	public Object seqNumLock = new Object();
	public Object receivedProcessLock = new Object();
	public Object fileManagerLock = new Object();
	public Object fileReceivedManagerLock = new Object();
	public boolean routeCreateLock = false;
	
	// Bluetooth関連
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    // 加速度センサ
    private SensorManager sensorManager = null;
    // なす角計算に使ってる変数群
    private float oldx = 0;
    private float oldy = 0;
    private float oldz = 0;
    private double p,P = 1.0;
    public static final double SUB_RATE = 100000.0;
    
    // Message types sent from the BluetoothChatService Handler
    // DEVICE_**** is KEY_STRING
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_THREAD_NO = "device_thread";
    public static final int BLUETOOTH_MAX_SLAVE = 7;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
	// 様々なパラメータのデフォルト値を宣言
	public static final int ACTIVE_ROUTE_TIMEOUT = 3000; // [ms]
	public static final int ALLOWED_HELLO_LOSS = 2;
	public static final int HELLO_INTERVAL = 1000; // [ms]
	public static final int DELETE_PERIOD = (ACTIVE_ROUTE_TIMEOUT >= HELLO_INTERVAL) ? 5 * ACTIVE_ROUTE_TIMEOUT
			: 5 * HELLO_INTERVAL;
	public static final int LOCAL_ADD_TTL = 2;
	public static final int MY_ROUTE_TIMEOUT = 2 * ACTIVE_ROUTE_TIMEOUT;
	public static final int NET_DIAMETER = 35;
	public static final int MAX_REPAIR_TTL = (int) (0.3 * NET_DIAMETER);
	public static int MIN_REPAIR_TTL = -1; // 宛先ノードへ知られている最新のホップ数
	public static final int NODE_TRAVERSAL_TIME = 40; // [ms]
	public static final int NET_TRAVERSAL_TIME = 2 * NODE_TRAVERSAL_TIME
			* NET_DIAMETER;
	public static final int NEXT_HOP_WAIT = NODE_TRAVERSAL_TIME + 10;
	public static final int PATH_DISCOVERY_TIME = 2 * NET_TRAVERSAL_TIME;
	public static final int PERR_RATELIMIT = 10;
	public static final int RREQ_RETRIES = 2;
	public static final int RREQ_RATELIMIT = 10;
	public static final int BLACKLIST_TIMEOUT = RREQ_RETRIES * NET_TRAVERSAL_TIME;
	public static final int TIMEOUT_BUFFER = 2;
	public static final int TTL_START = 1;
	public static final int TTL_INCREMENT = 2;
	public static final int TTL_THRESHOLD = 7;
	public static int TTL_VALUE = 1; // IPヘッダ内の"TTL"フィールドの値
	public static int RING_TRAVERSAL_TIME = 2 * NODE_TRAVERSAL_TIME
			* (TTL_VALUE + TIMEOUT_BUFFER);
	public static int MAX_SEND_FILE_SIZE = 63*1024;
	public static int MAX_RESEND = 5;
	public static String BLOAD_CAST_ADDRESS = "255.255.255.255";
    
	// その他変数
	private int RREQ_ID = 0;
	private int seqNum = 0;
	private volatile boolean do_BroadCast = false; // 一定時間内に何かﾌﾞﾛｰﾄﾞｷｬｽﾄしたかどうか
	private int sendIntentId = 0;
    
	
	
    /**
     * サービスの定期実行の間隔をミリ秒で指定。
     * 処理が終了してから次に呼ばれるまでの時間。
     */
//    protected abstract long getIntervalMS();


    /**
     * 定期実行したいタスクの中身（１回分）
     * タスクの実行が完了したら，次回の実行計画を立てること。
     */
//    protected abstract void execTask();


    /**
     * 次回の実行計画を立てる。
     */
//    protected abstract void makeNextPlan();


    // ---------- 必須メンバ -----------
	private final SendManager.Stub mBinder = new SendManager.Stub() {

		// AODV上で送るデータの送信要請インタフェース
		public void SendMessage(String destination_address,
				String source_address, byte flag, String package_name,
				String intent_action, int intent_flags, String intent_type,
				String intent_scheme, List<String> intent_categories,
				Map dataMap) throws RemoteException {
			
			// **** OutputStreamを使えばかなりコード短縮可能 **** //
			// 送信すべきバッファの全体サイズを計測していく
			int total_length = 1+4+4+1;	//Type,宛先,送信元,フラグの各サイズを加算
			if(package_name != null){
				total_length++;
				total_length += package_name.length();
			}
			if(intent_action != null){
				total_length++;
				total_length += intent_action.length();
			}
			if(intent_flags != 0){
				total_length += 4;
			}
			if(intent_type != null){
				total_length++;
				total_length += intent_type.length();
			}
			if(intent_scheme != null){
				total_length++;
				total_length += intent_scheme.length();
			}
			if(intent_categories != null){
				total_length++;
				for(int i=0;i<intent_categories.size();i++){
					total_length++;
					total_length += intent_categories.get(i).length();
				}
			}
			if(dataMap != null){
				HashMap<String, byte[]> hashDataMap = (HashMap<String, byte[]>) dataMap;
				Set<Map.Entry<String, byte[]>> entrySet = hashDataMap.entrySet();
				Iterator<Map.Entry<String, byte[]>> it = entrySet.iterator();
			
				total_length ++;
				while(it.hasNext()){
					Map.Entry<String, byte[]> entry = it.next();
					total_length += 4 + entry.getKey().getBytes().length + 4 + entry.getValue().length;
				}
			}
			
			// 送信バッファの確保
			byte[] buffer = new byte[total_length];

			// バッファに各情報を割り当て
			buffer[0] =  5;	// Type=5とする
			System.arraycopy(getByteAddress(destination_address), 0, buffer, 1, 4);
			System.arraycopy(getByteAddress(source_address)		, 0, buffer, 5, 4);
			buffer[9] = flag;

			// フラグに応じて様々な情報を付加
			int index = 10;
			if(package_name != null){
				buffer[index] = (byte) package_name.length();
				index += 1;
				System.arraycopy(package_name.getBytes(), 0, buffer, index, package_name.length());
				index +=  package_name.length();
			}
			if(intent_action != null){
				buffer[index] = (byte) intent_action.length();
				index += 1;
				System.arraycopy(intent_action.getBytes(), 0, buffer, index, intent_action.length());
				index +=  intent_action.length();
			}
			if(intent_flags != 0){
				System.arraycopy(intToByte(intent_flags), 0, buffer, index, 4);
				index += 4;
			}
			if(intent_type != null){
				buffer[index] = (byte) intent_type.length();
				index += 1;
				System.arraycopy(intent_type.getBytes(), 0, buffer, index, intent_type.length());
				index +=  intent_type.length();
			}
			if(intent_scheme != null){
				buffer[index] = (byte) intent_scheme.length();
				index += 1;
				System.arraycopy(intent_scheme.getBytes(), 0, buffer, index, intent_scheme.length());
				index +=  intent_scheme.length();
			}
			if(intent_categories != null){
				buffer[index] = (byte) intent_categories.size();
				index += 1;
				for(int i=0;i<intent_categories.size();i++){
					buffer[index] = (byte) intent_categories.get(i).length();
					index += 1;
					System.arraycopy(intent_categories.get(i).getBytes(), 0, buffer, index, intent_categories.get(i).length());
					index +=  intent_categories.get(i).length();
				}
			}
			if(dataMap != null){
				HashMap<String, byte[]> hashDataMap = (HashMap<String, byte[]>) dataMap;
				Set<Map.Entry<String, byte[]>> entrySet = hashDataMap.entrySet();
				Iterator<Map.Entry<String, byte[]>> it = entrySet.iterator();
			
				buffer[index] = (byte) entrySet.size();
				index++;
				
				while(it.hasNext()){
					Map.Entry<String, byte[]> entry = it.next();
					byte[] keyName = entry.getKey().getBytes();
					byte[] thisData= entry.getValue();
					byte[] keyNameLength = intToByte(keyName.length);
					byte[] thisDataLength= intToByte(thisData.length);
					
					System.arraycopy(keyNameLength, 0, buffer, index, keyNameLength.length);
					index += keyNameLength.length;
					System.arraycopy(keyName, 0, buffer, index, keyName.length);
					index += keyName.length;
					System.arraycopy(thisDataLength, 0, buffer, index, thisDataLength.length);
					index += thisDataLength.length;
					System.arraycopy(thisData, 0, buffer, index, thisData.length);
					index += thisData.length;
				}
			}
			// 送信データ生成ここまで

			// 送信先への経路が存在するかチェック
			final int route_index = searchToAdd(getByteAddress(destination_address));
			// 経路が存在する場合、有効かどうかチェック
			boolean enableRoute = false; // 初期化
			if (route_index != -1) {
				if ( getRoute(route_index).stateFlag == 1 &&
						(getRoute(route_index).lifeTime > new Date().getTime())) {
					enableRoute = true;
				}
			}
			if(enableRoute){
				// 使用可能な経路がすでにある場合
				RouteTable route = getRoute(route_index);

				send(buffer, route.nextIpAdd);
			}
			else if(routeCreateLock == false)
			{
				routeCreateLock = true;
				// 経路がない場合

				// 自身のシーケンス番号をインクリメント
				seqNum++;

				// もし宛先がブロードキャストアドレスならExpandingRingSearchを行わない
				if( BLOAD_CAST_ADDRESS.equals(destination_address)){
					// *** RREQじゃなくて素のタイプ5メッセージを送るべきかな？ ***
//					// RREQ_IDをインクリメント
//					AODV_Activity.RREQ_ID++;
//
//					// 自分が送信したパケットを受信しないようにIDを登録
//					AODV_Activity.newPastRReq(AODV_Activity.RREQ_ID, getByteAddress(source_address));
//
//					try {
//						new RREQ().send(getByteAddress(destination_address),
//										getByteAddress(source_address),
//										false,
//										false,
//										true,
//										false,
//										true,
//										0,
//										AODV_Activity.seqNum,
//										AODV_Activity.RREQ_ID,
//										AODV_Activity.NET_DIAMETER,
//										12345,
//										"Bloadcast");
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
					routeCreateLock = false;
				}
				// ブロードキャストアドレスじゃない場合
				// ExpandingRingSearch
				else{
					Log.d("ServiceSearch","経路なし");

					// TTLを初期値または過去のホップ数+TTL_ｲﾝｸﾘﾒﾝﾄにセット
					// 宛先シーケンス番号(+未知フラグ)もまとめてセット
					final boolean flagU;
					final int seqValue;
					final int TTL_VALUE;

					// 無効経路が存在するなら、その情報を流用
					if (route_index != -1) {
						TTL_VALUE = getRoute(route_index).hopCount + TTL_INCREMENT;
						flagU = false;
						seqValue = getRoute(route_index).toSeqNum;
					}
					else{
						TTL_VALUE = TTL_START;
						flagU = true;
						seqValue = 0;
					}

					// ExpandingRingSearch
					final int ring_traversal_time = 2 * NODE_TRAVERSAL_TIME * (TTL_VALUE + TIMEOUT_BUFFER);
					final byte[] source_address_b = getByteAddress(source_address);
					final String source_address_S = source_address;
					final byte[] destination_address_b = getByteAddress(destination_address);
					final String destination_address_S = destination_address;
					final byte[] buffer_copy = buffer.clone();

					try {
						new Thread(new Runnable() {
							public void run() {

								boolean timer_stop = false;
								int TTL_VALUE_ = TTL_VALUE;
								int ring_traversal_time_ = ring_traversal_time;

								timer: while (true) {

									int index_new;
									byte[] myAdd = source_address_b;

									// Threadは必ずぴったり停止するとは限らないので、停止しなくても中の処理は実行されないようにする
									if (!timer_stop) {

										// 以下、定期処理の内容
										// 経路が完成した場合、ループを抜ける
										if ( (index_new = searchToAdd(destination_address_b)) != -1) {

											timer_stop = true;

											// メッセージの送信
											RouteTable route = getRoute(index_new);
											InetAddress next_hop_inet = null;
											send(buffer_copy, route.nextIpAdd);
										}

										// TTLが上限値なRREQを送信済みならループを抜ける
										else if (TTL_VALUE_ == (TTL_THRESHOLD + TTL_INCREMENT)) {
											timer_stop = true;
										}

										// TTLの微調整
										// 例えばINCREMENT2,THRESHOLD7のとき,TTLの変化は2->4->6->7(not 8)
										if (TTL_VALUE_ > TTL_THRESHOLD) {
											TTL_VALUE_ = TTL_THRESHOLD;
										}

										// RREQ_IDをインクリメント
										RREQ_ID++;

										// 自分が送信したパケットを受信しないようにIDを登録
										newPastRREQ(RREQ_ID, myAdd);

										// RREQの送信
										do_BroadCast = true;

										try {
											RREQ.send(destination_address_b,
															myAdd,
															false,
															false,
															false,
															false,
															flagU,
															seqValue,
															seqNum,
															RREQ_ID,
															TTL_VALUE_,
															null,
															null);
								        	writeLog(11,source_address_S,source_address_S,destination_address_S, 0,seqNum);
										} catch (Exception e) {
											e.printStackTrace();
										}

										// ちょっと強引な待機(本来はRREPが戻ってくれば待たなくていい時間も待っている)
										// 待ち時間をVALUEに合わせて更新
										ring_traversal_time_ = 2
												* NODE_TRAVERSAL_TIME
												* (TTL_VALUE_ + TIMEOUT_BUFFER);

										TTL_VALUE_ += TTL_INCREMENT;
									}

									// 指定の時間停止する
									try {
										Thread.sleep(ring_traversal_time_);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}

									// ループを抜ける処理
									if (timer_stop) {
										break timer;
									}
								}
								routeCreateLock = false;
								}
						}).start();

					} catch (Exception e) {
						routeCreateLock = false;
						e.printStackTrace();
					}


				}
			}//*/
		}

		@Override
		public void WriteLog(int state, String sourceAddress,
				String destinationAddress, int dataLength, String packageName)
				throws RemoteException {
			// TODO 自動生成されたメソッド・スタブ
			Date date_rint = new Date();
			SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
			
			LogDataBaseOpenHelper.insertLogTableDATA(log_db, state, MyIP, sourceAddress,
				destinationAddress, dataLength, packageName, sdf_rint.format(date_rint), network_interface);
		}

	};

	// 自身を返すBinder
	public class AODV_ServiceBinder extends Binder{
		AODV_Service getService(){
			return AODV_Service.this;
		}
	}
	
    @Override
    public IBinder onBind(Intent intent) {
    	// 同アプリからのBindなら，簡易アクセス用のBinderを返す
    	String packageName = intent.getPackage();
    	if(packageName != null){
    		if(packageName.equals(this.getPackageName())){
    			return new AODV_ServiceBinder();
    		}
    	}
    	
    	// 外アプリからのBindなら，StubBinderを返す
        return mBinder;
    }
    
	@Override
	public boolean onUnbind(Intent intent){
		return true;
	}
	
    /**
     * 常駐を開始
     */
/**    public BaseService startResident(Context context)
//    {
//        Intent intent = new Intent(context, this.getClass());
//        intent.putExtra("type", "start");
//        context.startService(intent);
//
//        return this;
//    }
**/

	@Override
	public void onCreate() {
		//Log.d("service","oncreate");
		StaticIpAddress sIp = new StaticIpAddress(this);
		MyIP = sIp.getStaticIp();
		myAddress = getByteAddress(MyIP);
		
		// スレッドが起動中でなければ
		if( udpListenerThread == null ){
			try {
				// 受信スレッドのインスタンスを作成
				UdpListener udp_listener = new UdpListener(12345, this);
				// スレッドを取得
				udpListenerThread = new Thread(udp_listener);
			} catch (SocketException e1) {
				e1.printStackTrace();
			}
			// 受信スレッドrun()
			udpListenerThread.start();
		}
		
		if( routeManagerThread == null){
			// 経路監視スレッドのインスタンスを作成
			try {
				RouteManager route_manager = new RouteManager(this);
				// スレッドを取得
				routeManagerThread = new Thread(route_manager);
			} catch (IOException e2) {
				e2.printStackTrace();
			}
			// 監視スレッドrun()
			routeManagerThread.start();
		}
		
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // if BT is enabled, start setup
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
            	setupChat();
                // Only if the state is STATE_NONE, do we know that we haven't started already
                if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                  // Start the Bluetooth chat services
                  mChatService.start();
                }
            }
        }
        
        // ログデータベースへ書き込み用。
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		network_interface = wifi.getConnectionInfo().getSSID();
		if(network_interface == null){
			network_interface = "other";
		}

		
		// ログデータベースの書き込み準備
		LogDataBaseOpenHelper DBhelper = new LogDataBaseOpenHelper(getApplicationContext());
		log_db = DBhelper.getWritableDatabase();
		
		// 被削除耐性
		setForeground(true);
	}

    @Override
    public int onStartCommand(Intent intent, int flags,int startId) {
    	return START_NOT_STICKY;
    }
    
	@Override
	public void onDestroy() {
		if (mChatService != null){
			mChatService.stop();
			sensorManager.unregisterListener(this);
		}
		// Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
	}
	
	// Bluetooth関連
    private void setupChat() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, this, myAddress);
        
        // 加速度センサの情報取得
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

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
	private byte[] intToByte(int num){

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
	public int byteToInt(byte[] num){

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

	/*
	 * ReceiveProcess関係の仕事代行
	 */
	// RREQの受信履歴の中で、古すぎるアドレスの削除
	public void removeOldRREQ_List(){
		if( !receiveRREQ_List.isEmpty() ){	// 空でなければ
			// リストの中で最も古いのは先頭の項目、その生存時間をチェック
			synchronized (pastDataLock) {
				if(receiveRREQ_List.get(0).lifeTime < new Date().getTime() ){
					receiveRREQ_List.remove(0);
				}
			}
		}
	}
	// 短い間のRREQ受信履歴中に、引数のID,アドレスのものが無いか検索
	public boolean RREQ_ContainCheck(int ID, byte[] Add) {

		synchronized (pastDataLock) {
			for (int i = 0; i < receiveRREQ_List.size(); i++) {
				if ((ID == receiveRREQ_List.get(i).RREQ_ID)
						&& Arrays.equals(Add, receiveRREQ_List.get(i).IpAdd)) {
					return true;
				}
			}
		}
		return false;
	}
	// 同時に参照が起こらないよう、リストに追加するメソッド
	public void newPastRREQ(int IDnum, byte[] FromIpAdd) {

		synchronized (pastDataLock) {
			receiveRREQ_List.add(new PastData(IDnum, FromIpAdd, new Date()
					.getTime() + PATH_DISCOVERY_TIME));
		}
	}
	// RouteTable(list)に宛先アドレス(Add)が含まれていないか検索する
	// 戻り値：リスト内で発見した位置、インデックス
	// 見つからない場合 -1を返す
	public int searchToAdd(byte[] Add) {
		synchronized (routeLock) {
			for (int i = 0; i < routeTable.size(); i++) {
				if (Arrays.equals((routeTable.get(i).toIpAdd), Add)) {
					return i;
				}
			}
		}
		return -1;
	}
	// seqNum
	public int getSeqNum(){
		synchronized (seqNumLock) {
			return seqNum;
		}
	}
	public void setSeqNum(int i){
		synchronized (seqNumLock) {
			seqNum = i;
		}
	}
	public void seqNumIncrement(){
		synchronized (seqNumLock) {
			seqNum++;
		}
	}
	// do_BroadCast
	public boolean getDoBroadcast(){
		return do_BroadCast;
	}
	public void setDoBroadcast(boolean b){
		do_BroadCast = b;
	}
	// RREQ_ID
	public int getRREQ_ID(){
		synchronized (RREQ_ID_Lock) {
			return RREQ_ID;
		}
	}
	public void setRREQ_ID(int id){
		synchronized (RREQ_ID_Lock) {
			RREQ_ID = id;
		}
	}
	public void RREQ_ID_Increment(){
		synchronized(RREQ_ID_Lock){
			RREQ_ID++;
		}
	}
	// MyIp(String)
	public String getMyIP(){
		return MyIP;
	}
	/*
	 * RouteTable関連の仕事代行
	 */
	// ルートテーブル用ロックオブジェクトを返す
	public Object getRouteLocker(){
		return routeLock;
	}
	// ルートテーブル中のi番目の要素を返す、排他制御
	public RouteTable getRoute(int index) {
		synchronized (routeLock) {
			
//			Log.d("RouteTable","tableSize = "+routeTable.size()+", index = "+index);
//			for(int i=0;i<routeTable.size();i++){
//				RouteTable route = routeTable.get(i);
//				Log.d("RouteTable","ToAddress:"+getStringByByteAddress(route.toIpAdd));
//				Log.d("RouteTable","NextAddress:"+getStringByByteAddress(route.nextIpAdd));
//				Log.d("RouteTable","State:"+route.stateFlag);
//				Log.d("RouteTable","BT_State:"+Boolean.toString(route.bluetoothFlag));
//			}
			
			return routeTable.get(index);
		}
	}
	// ルートテーブルに要素を追加する、排他制御
	public void addRoute(RouteTable route) {
		synchronized (routeLock) {
			routeTable.add(route);

			// 時刻取得
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10001, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
		}
	}
	// ルートテーブルの要素を削除する、排他制御
	public void removeRoute(int index) {
		synchronized (routeLock) {
			RouteTable route = routeTable.get(index);
			// 時刻取得
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);

			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10003, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
			routeTable.remove(index);
		}
	}
	// ルートテーブルの要素を上書きする、排他制御
	public void setRoute(int index, RouteTable route) {
		synchronized (routeLock) {
			RouteTable pre_route = routeTable.get(index);
			// 時刻取得
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);

			// 経路変更前と後を記録
			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10002, MyIP, getStringByByteAddress(pre_route.toIpAdd), (int)pre_route.hopCount, pre_route.toSeqNum
					, getStringByByteAddress(pre_route.nextIpAdd), (int)pre_route.stateFlag, (int)pre_route.lifeTime, sdf.format(date), network_interface);

			routeTable.set(index, route);

			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10002, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
		}
	}
	// ルートテーブルの要素数を得る
	public int getRouteSize(){
		return routeTable.size();
	}
	
	// ACK要求リストに要素を追加する、排他制御
	public void addAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			ack_demand_list.add(ip);
		}
	}
	// ACK要求リストに要素が含まれているか見る、排他制御
	public boolean containAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			return ack_demand_list.contains(ip);
		}
	}
	// ACK要求リストから要素を削除する、排他制御
	public void removeAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			ack_demand_list.remove(ip);
		}
	}
	
	// Blackリストに要素を追加する、排他制御
	public void addBlack(BlackData d){
		synchronized(blackListLock){
			black_list.add(d);
		}
	}
	public int getBlackListSize(){
		synchronized(blackListLock){
			return black_list.size();
		}
	}
	public BlackData getBlackData(int index){
		synchronized(blackListLock){
			return black_list.get(index);
		}
	}
	public void removeBlackData(int index){
		synchronized(blackListLock){
			black_list.remove(index);
		}
	}
	// BlackListに対して該当アドレスを検索
	public int searchInBlackList(byte[] add){
		synchronized(blackListLock){
			for(int i=0;i<black_list.size();i++){
				if( Arrays.equals( black_list.get(i).ip_add, add)){
					return i;
				}
			}
			return -1;
		}
	}
	
	public synchronized int getSendIntentId(){
		sendIntentId++;
		return sendIntentId;
	}
	
	public void subMoveSensorValue(double x){
		p -= x;
		if(p < 0)p = 0;
	}
	
	// 経路の自動修復
	public void localRepair(RouteTable route, byte[] myAdd,final AODV_Service mAODV_Service){
		
		// ***** RREQの送信 *****
		int TTL = MIN_REPAIR_TTL + LOCAL_ADD_TTL;
		
		route.toSeqNum++;
		RREQ_ID++;
		seqNum++;
		
		// 自分が送信したパケットを受信しないようにIDを登録
		newPastRREQ(RREQ_ID, myAdd);
		
		do_BroadCast = true;
		
		try {
			RREQ.send(route.toIpAdd, myAdd
					, false, false, false, false, false
					, route.toSeqNum, seqNum, RREQ_ID, TTL, null, mAODV_Service);
		} catch (Exception ex3) {
			ex3.printStackTrace();
		}
		
		// 経路探索期間が過ぎた後、経路が修復されたかチェック
		long waitTime = 2 * AODV_Service.NODE_TRAVERSAL_TIME * (TTL + AODV_Service.TIMEOUT_BUFFER);
		final byte[] toIp = route.toIpAdd;
		final RouteTable route_f = route;
		
		Timer mTimer = new Timer(true);
		mTimer.schedule( new TimerTask(){
				@Override
				public void run(){
					int index = searchToAdd(toIp);
					
					// 経路が追加されていて、かつホップ数が修復前以下なら、修復完了
					// それ以外の場合、RERRを送信する
					if(index == -1){
						new RERR().RERR_Sender(route_f,mAODV_Service);
						
						final byte[] destination_address = route_f.toIpAdd;
						appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
					}
					else{
						// ホップ数が増加した場合
						if(getRoute(index).hopCount > route_f.hopCount){
							new RERR().RERR_Sender(route_f,mAODV_Service);
						}
					}
				}
			}, waitTime);
	}
	
	// ルートテーブルに従った方法で送信を行う
	public void send(byte[] buffer, byte[] ipAddress){
		send(buffer, ipAddress, buffer.length);
	}
	public void send(byte[] buffer, byte[] ipAddress, int length){
		
		boolean btFlag = false;
		
		if(getStringByByteAddress(ipAddress).equals("255.255.255.255")){
			if(mChatService != null){
				btFlag = true;
			}
			else{
				btFlag = false;
			}
		}else{
			int index = searchToAdd(ipAddress);
			if(index != -1){
				RouteTable route = getRoute(index);
				btFlag = route.bluetoothFlag;
			}
		}
		
		if(btFlag){
			// BT ON
			appendMessageOnActivity("えええ...\n");
			// ブロードキャスト？
			if(getStringByByteAddress(ipAddress).equals("255.255.255.255")){
				mChatService.write(buffer);
			}else{
				// ユニキャスト
				mChatService.write(buffer, ipAddress);
			}
		}else{
			// BT OFF
			appendMessageOnActivity("ぷぷぷ...\n");
			// データグラムソケットを開く
			DatagramSocket soc = null;
			try {
				soc = new DatagramSocket();
			} catch (SocketException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

	        // UDPパケットを送信する先となるブロードキャストアドレス
	        InetSocketAddress remoteAddress =
	        			 new InetSocketAddress(getStringByByteAddress(ipAddress), 12345);

	        // UDPパケット
	        DatagramPacket sendPacket = null;
			try {
				sendPacket = new DatagramPacket(buffer, length, remoteAddress);
			} catch (SocketException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

	        // DatagramSocketインスタンスを生成して、UDPパケットを送信
	        try {
				soc.send(sendPacket);
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

	        //データグラムソケットを閉じる
	        soc.close();
		}
	}
	
	/*
	 * Activity関連の仕事代行（代行する必要ないけど...）
	 * Activityのメッセージ表示欄に表示してほしいStringを送りつける。
	 * Activityが起動していない場合、届かずにスルーされる？
	 */
	public void appendMessageOnActivity(String mes){
		Intent intent = new Intent(this.getString(R.string.AODV_ActivityReceiver));
		intent.putExtra(this.getString(R.string.AODV_ActivityKey), mes);
		sendBroadcast(intent);
	}
	
	/*
	 * LogDataBase関連の仕事代行
	 */
	public void writeLog(int type,String myIp,String sourceIp,String destinationAddress,int hopCount, int fromSeqNum){
		Date date_rint = new Date();
		SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		LogDataBaseOpenHelper.insertLogTableAODV(log_db, type, myIp, sourceIp,
				destinationAddress, hopCount, fromSeqNum, sdf_rint.format(date_rint), network_interface);
	}
	public void writeLogData(int type,String myIp,String sourceIp,String destinationAddress,int dataLength, String appName){
		Date date_rint = new Date();
		SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		LogDataBaseOpenHelper.insertLogTableDATA(log_db, type, myIp, sourceIp,
				destinationAddress, dataLength, appName, sdf_rint.format(date_rint), network_interface);
	}
	
	// lock
	public Object getReceivedProcessLock(){
		return receivedProcessLock;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double addValue = twoVectorAngle(event.values[0], event.values[1], event.values[2]);
            
            p += addValue;
            if(p > P){
            	if(!mBluetoothAdapter.isDiscovering())
            		mBluetoothAdapter.startDiscovery();
            	p = 0;
            }
        }
	}
	
    private double twoVectorAngle(float x,float y,float z){
    	if(oldx == 0 && oldy == 0 && oldz == 0){
        	oldx = x;
        	oldy = y;
        	oldz = z;
    		return 0;
    	}
        // なす角計算 arccos A・B / |A||B|
        // ・	:内積
        // |X|	:ベクトルの長さ(ノルム)]
        // return 0<= ? <=PI
    	double result = Math.acos((x*oldx + y*oldy + z*oldz) / (Math.sqrt(x*x + y*y + z*z) * Math.sqrt(oldx*oldx + oldy*oldy + oldz*oldz)));
    	oldx = x;
    	oldy = y;
    	oldz = z;
    	if(Double.isNaN(result))
    		return 0;
    	else
    		return result/Math.PI/2;
    }
	
    /**
     * サービスの次回の起動を予約
     */
//    public void scheduleNextTime() {
//
//        long now = System.currentTimeMillis();
//
//        // アラームをセット
//        PendingIntent alarmSender = PendingIntent.getService(
//            this,
//            0,
//            new Intent(this, this.getClass()),
//            0
//        );
//        AlarmManager am = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
//        am.set(
//            AlarmManager.RTC,
//            now + getIntervalMS(),
//            alarmSender
//        );
//        // 次回登録が完了
//
//    }


    /**
     * サービスの定期実行を解除し，サービスを停止
     */
//    public void stopResident(Context context)
//    {
//        // サービス名を指定
//        Intent intent = new Intent(context, this.getClass());
//
//        // アラームを解除
//        PendingIntent pendingIntent = PendingIntent.getService(
//            context,
//            0, // ここを-1にすると解除に成功しない
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT
//        );
//        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
//        alarmManager.cancel(pendingIntent);
//            // @see http://creadorgranoeste.blogspot.com/2011/06/alarmmanager.html
//
//        // サービス自体を停止
//        stopSelf();
//    }

//  @Override
//  protected long getIntervalMS() {
//      return 1000 * 10;
//  }
//
//
//  @Override
//  protected void execTask() {
//      activeService = this;
//
//
//      // ※もし毎回の処理が重い場合は，メインスレッドを妨害しないために
//      // ここから下を別スレッドで実行する。
//
//
//      // ログ出力（ここに定期実行したい処理を書く）
//      Log.d("hoge", "fuga");
//
//      // 次回の実行について計画を立てる
//      makeNextPlan();
//  }
//
//
//  @Override
//  public void makeNextPlan()
//  {
//      this.scheduleNextTime();
//  }


  /**
   * もし起動していたら，常駐を解除する
   */
//  public static void stopResidentIfActive(Context context) {
//      if( activeService != null )
//      {
//          activeService.stopResident(context);
//      }
//  }
}

