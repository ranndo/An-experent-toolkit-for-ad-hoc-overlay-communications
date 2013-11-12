package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
public class AODV_Service extends Service
{

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
				byte[] data) throws RemoteException {

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
			if(data != null){
				total_length += data.length;
			}
			// 送信バッファの確保
			byte[] buffer = new byte[total_length];
Log.d("RINT_Create","total/data = "+total_length+"/"+data.length);
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
			System.arraycopy(data, 0, buffer, index, data.length);
			// 送信データ生成ここまで

			// 送信先への経路が存在するかチェック
			final int route_index = AODV_Activity.searchToAdd(getByteAddress(destination_address));
			// 経路が存在する場合、有効かどうかチェック
			boolean enableRoute = false; // 初期化
			Log.d("ServiceSearch","z");
			if (route_index != -1) {
				if ( AODV_Activity.getRoute(route_index).stateFlag == 1 &&
						(AODV_Activity.getRoute(route_index).lifeTime > new Date().getTime())) {
					enableRoute = true;
				}
			}
			if(enableRoute){
				// 使用可能な経路がすでにある場合
				RouteTable route = AODV_Activity.getRoute(route_index);

				SendByteArray.send(buffer, route.nextIpAdd);
			}
			else{Log.d("ServiceSearch","X");
				// 経路がない場合

				// 自身のシーケンス番号をインクリメント
				AODV_Activity.seqNum++;

				// もし宛先がブロードキャストアドレスならExpandingRingSearchを行わない
				if( AODV_Activity.BLOAD_CAST_ADDRESS.equals(destination_address)){
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
						TTL_VALUE = AODV_Activity.getRoute(route_index).hopCount + AODV_Activity.TTL_INCREMENT;
						flagU = false;
						seqValue = AODV_Activity.getRoute(route_index).toSeqNum;
					}
					else{
						TTL_VALUE = AODV_Activity.TTL_START;
						flagU = true;
						seqValue = 0;
					}

					// ExpandingRingSearch
					final int ring_traversal_time = 2 * AODV_Activity.NODE_TRAVERSAL_TIME * (TTL_VALUE + AODV_Activity.TIMEOUT_BUFFER);
					final byte[] source_address_b = getByteAddress(source_address);
					final byte[] destination_address_b = getByteAddress(destination_address);
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
										if ( (index_new = AODV_Activity.searchToAdd(destination_address_b)) != -1) {

											timer_stop = true;

											// メッセージの送信
											RouteTable route = AODV_Activity.getRoute(index_new);
											InetAddress next_hop_inet = null;
											SendByteArray.send(buffer_copy, route.nextIpAdd);
											
											try {
												File file = getFileStreamPath("imagePacket.txt");
												if(!file.exists()){
													FileOutputStream out = openFileOutput("imagePacket.txt",MODE_PRIVATE);
													out.write(buffer_copy);
													out.close();
												}
											} catch (FileNotFoundException e) {
												e.printStackTrace();
											} catch (IOException e) {
												e.printStackTrace();
											}
										}

										// TTLが上限値なRREQを送信済みならループを抜ける
										else if (TTL_VALUE_ == (AODV_Activity.TTL_THRESHOLD + AODV_Activity.TTL_INCREMENT)) {
											timer_stop = true;
										}

										// TTLの微調整
										// 例えばINCREMENT2,THRESHOLD7のとき,TTLの変化は2->4->6->7(not 8)
										if (TTL_VALUE_ > AODV_Activity.TTL_THRESHOLD) {
											TTL_VALUE_ = AODV_Activity.TTL_THRESHOLD;
										}

										// RREQ_IDをインクリメント
										AODV_Activity.RREQ_ID++;

										// 自分が送信したパケットを受信しないようにIDを登録
										AODV_Activity.newPastRReq(AODV_Activity.RREQ_ID, myAdd);

										// RREQの送信
										AODV_Activity.do_BroadCast = true;

										try {
											new RREQ().send(destination_address_b,
															myAdd,
															false,
															false,
															false,
															false,
															flagU,
															seqValue,
															AODV_Activity.seqNum,
															AODV_Activity.RREQ_ID,
															TTL_VALUE_,
															12345,
															null);
										} catch (Exception e) {
											e.printStackTrace();
										}

										// ちょっと強引な待機(本来はRREPが戻ってくれば待たなくていい時間も待っている)
										// 待ち時間をVALUEに合わせて更新
										ring_traversal_time_ = 2
												* AODV_Activity.NODE_TRAVERSAL_TIME
												* (TTL_VALUE_ + AODV_Activity.TIMEOUT_BUFFER);

										TTL_VALUE_ += AODV_Activity.TTL_INCREMENT;
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
							}
						}).start();

					} catch (Exception e) {
						e.printStackTrace();
					}


				}
			}//*/
			Date date_rint = new Date();
			SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
			if((RINT.FLAG_PACKAGE_NAME & flag) != 0)
				LogDataBaseOpenHelper.insertLogTableDATA(AODV_Activity.log_db, 51, AODV_Activity.MyIP, source_address,
						destination_address, data.length, package_name, sdf_rint.format(date_rint), AODV_Activity.network_interface);
			else
				LogDataBaseOpenHelper.insertLogTableDATA(AODV_Activity.log_db, 51, AODV_Activity.MyIP, source_address,
						destination_address, data.length, package_name, sdf_rint.format(date_rint), AODV_Activity.network_interface);
		}

		@Override
		public void Test() throws RemoteException {
			// TODO 自動生成されたメソッド・スタブ
			Log.d("AODV_Service","ToastStart");
			//Toast.makeText(getApplicationContext(), "Service", Toast.LENGTH_LONG).show();
			Log.d("AODV_Service","ToastEnd");
		}


	};

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    // ---------- サービスのライフサイクル -----------


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
    public void onStart(Intent intent, int startId) {

        // サービス起動時の処理。
        // サービス起動中に呼ぶと複数回コールされ得る。しかし二重起動はしない
        // @see http://d.hatena.ne.jp/rso/20110911

        super.onStart(intent, startId);

        // タスクを実行
//        execTask();

        // NOTE: ここで次回の実行計画を逐次的にコールしていない理由は，
        // タスクが非同期の場合があるから。
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

