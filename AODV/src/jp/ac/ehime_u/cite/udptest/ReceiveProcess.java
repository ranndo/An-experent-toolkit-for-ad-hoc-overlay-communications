package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class ReceiveProcess {
	// 受信した情報を操作するためインスタンス化しておく
	public static RREP RRep = new RREP();
	public static RERR RErr = new RERR();
//	public static FSEND FSend = new FSEND();
//	public static FREQ FReq = new FREQ();

	// ファイル受信
//	public static ArrayList<FileReceivedManager> file_received_manager = new ArrayList<FileReceivedManager>();
	
	// 受信処理
	public static void process(byte[] receiveBuffer,byte[] preHopAddress, boolean bluetoothFlag, byte[] my_address, Context context, AODV_Service mAODV_Service){
		boolean bload_cast_flag = false;
		
		Log.d("debug_receive","length:"+receiveBuffer.length);
		
		switch (receiveBuffer[0]) {

		case 1:	// RREQ
			// RREQの受信履歴の中で、古すぎるアドレスの削除
			mAODV_Service.removeOldRREQ_List();

			// 受信履歴リストの中の情報と、RREQ_ID,送信元が一致すればメッセージを無視
			if( mAODV_Service.RREQ_ContainCheck( RREQ.getRREQ_ID(receiveBuffer), RREQ.getFromIpAdd(receiveBuffer))){
				// "重複したRREQメッセージのため無視します\n");
				break;
			}

			// さらに、BlackListに対しても同様に古すぎるアドレスの削除
			if( mAODV_Service.getBlackListSize() > 0 ){	// 空でなければ
				// 各生存時間をチェック
				for(int i=0;i<mAODV_Service.getBlackListSize();i++){
					if(mAODV_Service.getBlackData(i).life_time < new Date().getTime()){
						mAODV_Service.removeBlackData(i);
					}
				}
			}

			// ブラックリストに前ホップノードのアドレスが含まれていれば、メッセージを無視
			if( mAODV_Service.searchInBlackList(preHopAddress) != -1){
				// ブラックリスト(片方向リンク)からのRREQメッセージのため無視します
				break;
			}

			// ホップ数+1,
			receiveBuffer[3]++;

			// DBへ記録
			mAODV_Service.writeLog(13, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer)),
					AODV_Service.getStringByByteAddress(RREQ.getToIpAdd(receiveBuffer)), receiveBuffer[3], RREQ.getFromSeqNum(receiveBuffer));

			// 受信したRREQメッセージの情報をListに追加
			// 引数はRREQ_ID,送信元アドレス
			mAODV_Service.newPastRREQ(RREQ.getRREQ_ID(receiveBuffer),RREQ.getFromIpAdd(receiveBuffer));


			// 逆経路を、生存時間短めで記録
			// 生存時間は、既に経路があるならその生存時間をそのまま、またはMinimalLifeTimeの大きいほうにセット
			int index;
			long life;
			long MinimalLifeTime = new Date().getTime()+ 2*AODV_Service.NET_TRAVERSAL_TIME
				- 2 * RREQ.getHopCount(receiveBuffer) * AODV_Service.NODE_TRAVERSAL_TIME;
			byte state_flag;

			if( (index = mAODV_Service.searchToAdd(RREQ.getFromIpAdd(receiveBuffer))) != -1){
				life = (mAODV_Service.getRoute(index).lifeTime > MinimalLifeTime)? mAODV_Service.getRoute(index).lifeTime:MinimalLifeTime;
			}
			else{
				life = MinimalLifeTime;
			}

			// シーケンス番号も同様に、既存経路の値と受信RREQ中の値を比較して高いほう
			// また、既存経路のほうが高い場合、受信したRREQ中の値をその値に変更
			if(index != -1){
				if(mAODV_Service.getRoute(index).toSeqNum > RREQ.getFromSeqNum(receiveBuffer)){
    				receiveBuffer = RREQ.setFromSeqNum(receiveBuffer, mAODV_Service.getRoute(index).toSeqNum);
				}
			}

			// 経路状態は既存経路があるならそのまま、なければ一時経路
			if(index != -1){
				state_flag = mAODV_Service.getRoute(index).stateFlag;
			}
			else if(RREQ.getFlagG(receiveBuffer)){	// ### Gフラグが入っているなら正当経路として作成 ###
				state_flag = 1;
			}
			else{
				state_flag = 5;
			}

			Log.d("AODV_type1","\t逆経路作成:"+AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer))+
					"宛"+"("+AODV_Service.getStringByByteAddress(preHopAddress)+"経由)\n");
			// 既に逆経路があるなら上書きset、なければ追加add
			if(index != -1){
				// 経路状態は既存のまま
				mAODV_Service.setRoute(index, new RouteTable( RREQ.getFromIpAdd(receiveBuffer), RREQ.getFromSeqNum(receiveBuffer)
    					, true, state_flag, bluetoothFlag, RREQ.getHopCount(receiveBuffer), preHopAddress, life
    					, new HashSet<byte[]>() ));
			}
			else{
				// 経路状態は一時的な経路(5)
				mAODV_Service.addRoute( new RouteTable( RREQ.getFromIpAdd(receiveBuffer), RREQ.getFromSeqNum(receiveBuffer)
    					, true, state_flag, bluetoothFlag, RREQ.getHopCount(receiveBuffer), preHopAddress, life
    					, new HashSet<byte[]>() ));
			}

			// ブロードキャストメッセージ？
			bload_cast_flag = Arrays.equals(RREQ.getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS)
					, RREQ.getToIpAdd(receiveBuffer));

			// RREQメッセージの内容チェック
			if(RREQ.isToMe(receiveBuffer, my_address) || bload_cast_flag){

				// RREQ:自分宛のメッセージ または ブロードキャストメッセージのとき
				Log.d("AODV_type1","RREPを前ホップノード" + preHopAddress + "にユニキャストします");

				// 返信前に、シーケンス番号の更新
				if( RREQ.getToSeqNum(receiveBuffer) == mAODV_Service.getSeqNum()+1){
					mAODV_Service.setSeqNum(mAODV_Service.getSeqNum()+1);
				}

				// RREPの返信
				RRep.reply(preHopAddress, RREQ.getFromIpAdd(receiveBuffer), my_address
						,(byte)0 ,mAODV_Service.getSeqNum() , AODV_Service.MY_ROUTE_TIMEOUT, mAODV_Service);

			}
			if(!RREQ.isToMe(receiveBuffer, my_address) || bload_cast_flag ){

				// RREQ:自分宛でないメッセージ または ブロードキャストメッセージのとき
				Log.d("AODV_type1", "RREQ:自分宛のメッセージではありません");

				// 宛先までの有効経路を持っているか検索
				index = mAODV_Service.searchToAdd(RREQ.getToIpAdd(receiveBuffer));

				// 経路を知っていて、Dフラグがオフなら
				if( (index != -1) && (!RREQ.getFlagD(receiveBuffer))){
					if( mAODV_Service.getRoute(index).stateFlag == 1
							&& mAODV_Service.getRoute(index).toSeqNum > RREQ.getToSeqNum(receiveBuffer)
							&& !RREQ.getFlagD(receiveBuffer)){

    					// 経路が既知な中間ノードなので、RREPを返信");

    					// 経路のprecursorListに追加
    					// まず順経路の更新
    					// 順経路のprecursorListに、逆経路の次ホップを追加。エラー時にRERRを伝えるノード
    					// すでにListに含まれていても、HashSetにより重複は認められないのでOK
    					RouteTable route = mAODV_Service.getRoute(index);		// 一旦リストから出す
    					route.preList.add(preHopAddress);		// 書き加え
    					mAODV_Service.setRoute(index, route);		// リストに上書き

    					// 次に逆経路の更新
    					// 逆経路のprecursorListに、順経路の次ホップを追加。エラー時にRERRを伝えるノード
    					int index2 = mAODV_Service.searchToAdd(RREQ.getFromIpAdd(receiveBuffer));
    					route = mAODV_Service.getRoute(index2);
    					route.preList.add(mAODV_Service.getRoute(index).nextIpAdd);


    					// Gフラグがセットされていれば、逆経路の状態を有効にし、逆方向経路を確立
    					// また、宛先ノードにもRREPが必要
    					// (どちらも双方向に経路を確立するために用いる)
    					if(RREQ.getFlagG(receiveBuffer)){

    						// 宛先は順経路の次ホップ
    						InetAddress str = null;
							try {
								str = InetAddress.getByAddress(mAODV_Service.getRoute(index).nextIpAdd);
							} catch (UnknownHostException e) {
								// TODO 自動生成された catch ブロック
								e.printStackTrace();
							}

    						// 有効化
    						route.stateFlag =1;

    						// G_RREPの送信
        					RRep.reply(str, RREQ.getToIpAdd(receiveBuffer),
        							RREQ.getFromIpAdd(receiveBuffer),
        							mAODV_Service.getRoute(index).hopCount, RREQ.getFromSeqNum(receiveBuffer),
        							(int)(mAODV_Service.getRoute(index).lifeTime - new Date().getTime()), mAODV_Service);
    					}

    					// リストに上書き
    					mAODV_Service.setRoute(index2, route);

    					// RREPの返信
    					RRep.reply(preHopAddress, RREQ.getFromIpAdd(receiveBuffer),
    							RREQ.getToIpAdd(receiveBuffer),
    							mAODV_Service.getRoute(index).hopCount, mAODV_Service.getRoute(index).toSeqNum,
    							(int)(mAODV_Service.getRoute(index).lifeTime - new Date().getTime()), mAODV_Service);

					}
				}
				// =宛先までの有効経路を持っていない場合、または持っていてDフラグがオンの場合
				else{
					// TTLを減らすタイミングはこの後にしているので、比較は1以下
					// 順番変えても別にいい気もする
					if(RREQ.getTimeToLive(receiveBuffer)<=1){
						Log.d("AODV_type1","TTLが0以下なので転送しません");
					}
					else{
						// 条件を満たせば、中継するためブロードキャスト
						// 引数のTTL--;
						Log.d("AODV_type1","RREQメッセージを転送");
						receiveBuffer = RREQ.setTimeToLive(receiveBuffer, RREQ.getTimeToLive(receiveBuffer)-1);

						RREQ.send2(receiveBuffer,mAODV_Service);

						mAODV_Service.writeLog(12, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer)),
								AODV_Service.getStringByByteAddress(RREQ.getToIpAdd(receiveBuffer)), receiveBuffer[3], RREQ.getFromSeqNum(receiveBuffer));
	        		}
				}
			}

			// ブロードキャストメッセージでなければ,RREQの処理のみで終了
			if(!bload_cast_flag){
				break;
			}
		// ブロードキャストメッセージなら、引き続きcase0の処理
    	case 0: // 通信相手からのメッセージ
			Log.d("AODV_type0","receive0");
    		// 自分宛のメッセージなら
    		if( isToMe(receiveBuffer,my_address) || bload_cast_flag){
    			Log.d("AODV_type0","To_me_message");

    			// "送信元:送られてきたテキスト"をViewに追加
    			final String src = AODV_Service.getStringByByteAddress(getAddressSrc(receiveBuffer));
    			final String mes = new String(getMessage(receiveBuffer, receiveBuffer.length));

    			// final String s2= "prev_hop_is:"+AODV_Activity.getStringByByteAddress(cAddr.getAddress());
    			
    			mAODV_Service.appendMessageOnActivity(src+":"+mes+"\n\r");

				// 暗黙的インテントを投げる要求文字"TASK:"なら
				if(mes.startsWith("TASK:")){
		            Intent intent = new Intent();
		            intent.setAction(Intent.ACTION_CALL);
		            intent.setData(Uri.parse(mes.replaceFirst("TASK:", "")+"_"+src));	// TASK:〇の部分をセット
		            intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
		            intent.putExtra("ID", mAODV_Service.getSendIntentId());
		            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		            context.startActivity(intent);
				}
    		}
    		else{	// 次ホップへ転送
    			// 宛先までの有効経路を持っているか検索
				index = mAODV_Service.searchToAdd(getAddressDest(receiveBuffer));

				Log.d("AODV_type0", "deliver_message");

				// 経路を知っていて
				if(index != -1){
					// 有効な経路なら
					if(mAODV_Service.getRoute(index).stateFlag == 1){

						Log.d("AODV_type0", "delivery_start");
						// 次ホップへそのまま転送
						mAODV_Service.send(cut_byte_spare(receiveBuffer,receiveBuffer.length), mAODV_Service.getRoute(index).nextIpAdd);

	    				Log.d("AODV_type0", "delivery_end");
						break;
					}
				}
				// 有効な経路を持っていない場合

				// RERRの送信
				RErr.RERR_Sender(mAODV_Service.getRoute(index),mAODV_Service);

    		}

    		break;

		case 2:	//RREPを受け取った場合


			// 受信データのサイズ
			int mesLength = receiveBuffer.length;

			// 自分自身からのRREPなら無視
			byte[] local_address = RREQ.getByteAddress("127.0.0.1");

			if( Arrays.equals(RRep.getToIpAdd(receiveBuffer,mesLength),local_address)
					|| Arrays.equals(RRep.getToIpAdd(receiveBuffer,mesLength), my_address)){
				break;
			}

			// AckフラグがオンならRREP_ACKを返す
			// Helloメッセージの場合はfalse
			if( RRep.getFlagA(receiveBuffer, mesLength)){
				RREP_ACK.send(preHopAddress, mAODV_Service);
			}


			// ホップ数++
			receiveBuffer = RRep.hopCountInc(receiveBuffer, mesLength);

			mAODV_Service.writeLog(23, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RRep.getFromIpAdd(receiveBuffer, mesLength, my_address)),
					AODV_Service.getStringByByteAddress(RRep.getToIpAdd(receiveBuffer, mesLength)), RRep.getHopCount(receiveBuffer, mesLength),
					RRep.toSeqNum);

			// RREPを送信してきた前ノードが
			// 何かの経路の次ホップなら、その経路の生存時間を更新
			// （経路の状態が有効または無効のとき）
			for(int i=0;i<mAODV_Service.getRouteSize();i++){
				RouteTable route = mAODV_Service.getRoute(i);
				if( Arrays.equals((route.nextIpAdd) , preHopAddress)
						&& (route.stateFlag == 1 || route.stateFlag == 2)){
					//Log.d("AODV_RREP","LifeTime before:"+route.lifeTime);

					// 現在の生存時間と、HELLOの式を比較して大きい方に更新
					if(route.lifeTime < (AODV_Service.ALLOWED_HELLO_LOSS * AODV_Service.HELLO_INTERVAL)){
						route.lifeTime = AODV_Service.ALLOWED_HELLO_LOSS * AODV_Service.HELLO_INTERVAL;
					}
					// 状態を有効に
					route.stateFlag = 1;

					//Log.d("AODV_RREP","LifeTime after:"+route.lifeTime);
					// 上書き
					mAODV_Service.setRoute(i,route);
				}
			}

			// HELLOメッセージなら生存時間を延長するだけでよい(片方向リンク対応)
			if(RRep.isHelloMessage(mesLength)){
				break;
			}

			// 順経路（RREQ送信元⇒宛先）が存在するかどうか検索
			int index2 = mAODV_Service.searchToAdd(RRep.getToIpAdd(receiveBuffer,mesLength));

			// 存在しない場合、順経路の作成
			if( index2 == -1 ){
				mAODV_Service.addRoute( new RouteTable( RRep.getToIpAdd(receiveBuffer,mesLength), RRep.getToSeqNum(receiveBuffer,mesLength)
    					, true, (byte)1, bluetoothFlag, RRep.getHopCount(receiveBuffer,mesLength), preHopAddress
    					, RRep.getLifeTime(receiveBuffer,mesLength) + (new Date().getTime())
    					, new HashSet<byte[]>() ));

    			// 経路が追加されました"
			}
			// 順経路が存在する場合
			else{
				// 以下のいずれかの条件を満たしている場合、経路を更新する
				// 1.既存経路のシーケンス番号が無効であると記録されている
				// 2.RREPの宛先シーケンス番号＞既存経路の番号であり、有効
				// 3.シーケンス番号が等しく既存経路が無効である
				// 4.シーケンス番号が等しくホップ数が既存経路よりも小さい
				if(	(mAODV_Service.getRoute(index2).validToSeqNumFlag == false)
					||(RRep.getHopCount(receiveBuffer, mesLength) > mAODV_Service.getRoute(index2).toSeqNum)
					||( (mAODV_Service.getRoute(index2).stateFlag != 1)
							&&(RRep.getHopCount(receiveBuffer, mesLength) == mAODV_Service.getRoute(index2).toSeqNum))
					||( (RRep.getHopCount(receiveBuffer, mesLength) < mAODV_Service.getRoute(index2).hopCount)
							&&(RRep.getHopCount(receiveBuffer, mesLength) == mAODV_Service.getRoute(index2).toSeqNum)))
				{
					// 順経路の上書き
					mAODV_Service.setRoute(index2, new RouteTable( RRep.getToIpAdd(receiveBuffer,mesLength), RRep.getToSeqNum(receiveBuffer,mesLength)
    					, true, (byte)1, bluetoothFlag, RRep.getHopCount(receiveBuffer,mesLength), preHopAddress
    					, RRep.getLifeTime(receiveBuffer,mesLength) + (new Date().getTime())
    					, new HashSet<byte[]>() ));
				}
			}

			if(RRep.isToMe(receiveBuffer,mesLength,my_address)){
				// RREP:送信元ＩＰアドレスが自分です
			}
			else{
				// RREP:送信元ＩＰアドレスが自分ではありません

				// 順経路を示すindex2を再検索、更新
				index2 = mAODV_Service.searchToAdd(RRep.getToIpAdd(receiveBuffer,mesLength));
				// RREQ生成ノードへの経路（逆経路）を検索
				int index3 = mAODV_Service.searchToAdd(RRep.getFromIpAdd(receiveBuffer,mesLength,my_address));
				// 順経路のPrecursorListに、逆経路への次ホップを追加
				// すでにListに含まれていても、HashSetにより重複は認められないのでOK
				RouteTable route = mAODV_Service.getRoute(index2);		// 一旦リストから出す
				route.preList.add(mAODV_Service.getRoute(index3).nextIpAdd);	// 書き加え
				mAODV_Service.setRoute(index2, route);					// リストに上書き

				// 逆経路のPrecursorListに、順経路の次ホップを追加
				// また、生存時間も更新
				route = mAODV_Service.getRoute(index3);					// 一旦リストから出す
				route.preList.add(mAODV_Service.getRoute(index2).nextIpAdd);	// 書き加え

				// 現在の生存時間と現在時刻+ACTIVE_ROUTE_TIMEOUTの最大値側にセット
				if(route.lifeTime < (new Date().getTime()+AODV_Service.ACTIVE_ROUTE_TIMEOUT)){
					route.lifeTime = new Date().getTime()+AODV_Service.ACTIVE_ROUTE_TIMEOUT;
				}
				mAODV_Service.setRoute(index3, route);					// リストに上書き

				// RREPを前ホップノードに転送
				// 前ホップノードは逆経路の次ホップと同一
				try {
					RRep.reply2(receiveBuffer, InetAddress.getByAddress(mAODV_Service.getRoute(index3).nextIpAdd),mAODV_Service);
				} catch (UnknownHostException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}

				mAODV_Service.writeLog(22, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RRep.getFromIpAdd(receiveBuffer, mesLength, my_address)),
						AODV_Service.getStringByByteAddress(RRep.getToIpAdd(receiveBuffer, mesLength)), RRep.getHopCount(receiveBuffer, mesLength),
    					RRep.toSeqNum);
			}
			break;

		case 3:	// RERRを受け取った場合

			// "このメッセージはRERRです\n");

			// 経路の検索
			index = mAODV_Service.searchToAdd(RErr.getIpAdd(receiveBuffer));

			// 経路が存在するなら
			if(index != -1){
				// リストから出し、フィールドの更新
				RouteTable route = mAODV_Service.getRoute(index);

				// 有効経路なら
				if(route.stateFlag == 1){

    				route.stateFlag = 2;	// 無効化
    				route.lifeTime  = (new Date().getTime()+AODV_Service.DELETE_PERIOD);	// 削除時間の設定
    				if(route.validToSeqNumFlag){	// シーケンス番号が有効なら
    					route.toSeqNum = RErr.getSeqNum(receiveBuffer);		// ｼｰｹﾝｽ番号も更新
    				}

    				// 読みだした場所に上書き
    				mAODV_Service.setRoute(index, route);

					// ローカルリペアを行えるホップ数か？
					if(route.hopCount <= AODV_Service.MAX_REPAIR_TTL){
						mAODV_Service.localRepair(route,my_address,mAODV_Service);
					}
					else{
						// RERRの送信
						new RERR().RERR_Sender(route,mAODV_Service);

						final byte[] destination_address = route.toIpAdd;
						
						mAODV_Service.appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
					}
				}
			}

			break;

		case 4:	// RREP_ACKを受け取った場合

			// ACK要求リストにACK送信元ノードのアドレスが残っていれば
			if(mAODV_Service.containAckDemand(preHopAddress)){
				// リストから削除
				mAODV_Service.removeAckDemand(preHopAddress);
			}

			// 経路の検索
			index = mAODV_Service.searchToAdd(preHopAddress);

			// 経路があるなら
			if(index != -1){
				RouteTable route = mAODV_Service.getRoute(index);

				// ### 一時経路を有効経路にし、双方向経路を確立 ###
				// ### 片方向リンクが混在している場合、DELETE_PERIOD間、不正な経路情報となる ###
				if(route.stateFlag == 5){
					route.stateFlag = 1;
					mAODV_Service.setRoute(index, route);
				}
			}

			break;

		case 5: // (データ付き)インテント発行メッセージを受け取った場合
			Log.d("AODV_type5","receive5");
    		// 自分宛のメッセージ?
			RINT rint = new RINT(receiveBuffer);
    		boolean to_me_message = Arrays.equals(my_address, rint.getDestinationAddress())
    								||Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress());

			if((RINT.FLAG_PACKAGE_NAME & rint.flag) != 0)
				mAODV_Service.writeLogData(53, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
						AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, rint.package_name);
			else
				mAODV_Service.writeLogData(53, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
						AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, null);
    		if(to_me_message){
    			Log.d("AODV_type5","To_me_message");

    			// "送信元:送られてきたテキスト"をViewに追加
//    			final String src = AODV_Activity.getStringByByteAddress(getAddressSrc(receiveBuffer));
//    			final String mes = new String(getMessage(receiveBuffer, packet.getLength()));
//
//    			// final String s2= "prev_hop_is:"+AODV_Activity.getStringByByteAddress(cAddr.getAddress());
//
//				handler.post(new Runnable() {
//					@Override
//					public void run() {
//						editText.append(src+":"+mes+"\n\r");
//						editText.setSelection(editText.getText().toString().length());
//					}
//				});
   				// RINTに従いインテントを生成
	            Intent intent = new Intent();

	            if(rint.getPackageName() != null){
	            	//Log.d("AODV_type5","P "+ rint.getPackageName());
	            	// パッケージ名.クラス名からクラス名を取り除き，パッケージ名を得る
	            	intent.setClassName( rint.getPackageName().substring(0, rint.getPackageName().lastIndexOf('.'))
	            			,rint.getPackageName());
	            }
	            if(rint.getIntentAction() != null){
	            	//Log.d("AODV_type5","A");
	            	//intent.setAction(rint.getIntentAction());
	            }
	            if(rint.getIntentFlags() != 0){
	            	//Log.d("AODV_type5","F");
	            	intent.setFlags(rint.getIntentFlags());
	            }
	            if(rint.getIntentType() != null){
	            	//Log.d("AODV_type5","T");
	            	intent.setType(rint.getIntentType());
	            }
	            if(rint.getIntentScheme() != null){
	            	//Log.d("AODV_type5","S");
	            	intent.setData(Uri.parse(rint.getIntentScheme()));
	            }
	            if(rint.getIntentCategories() != null){
	            	//Log.d("AODV_type5","C");
	            	List<String> categories_list = rint.getIntentCategories();
	            	for(Iterator<String> ite = categories_list.iterator(); ite.hasNext();){
	            		intent.addCategory(ite.next());
	            	}
	            }
	            if(rint.getData() != null){
	            	//Log.d("AODV_type5","D");
	            	intent.putExtra("DATA", rint.getData());
	            }

	            intent.putExtra("SOURCE_ADDRESS", AODV_Service.getStringByByteAddress(rint.getSourceAddress()));
	            intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
	            intent.putExtra("ID", mAODV_Service.getSendIntentId());
	            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	            context.startActivity(intent);
    		}

    		boolean hop_message = (!Arrays.equals(my_address, rint.getDestinationAddress()))
								|| Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress());
    		if(hop_message){	// 次ホップへ転送
    			// 宛先までの有効経路を持っているか検索
				index = mAODV_Service.searchToAdd(rint.getDestinationAddress());

				Log.d("AODV_type5", "deliver_message");

				// 経路を知っていて
				if(index != -1){
					// 有効な経路なら
					if(mAODV_Service.getRoute(index).stateFlag == 1){

						Log.d("AODV_type5", "delivery_start");
						// 次ホップへそのまま転送
						mAODV_Service.send(receiveBuffer, mAODV_Service.getRoute(index).nextIpAdd);

						if((RINT.FLAG_PACKAGE_NAME & rint.flag) != 0)
							mAODV_Service.writeLogData(52, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
									AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, rint.package_name);
						else
							mAODV_Service.writeLogData(52, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
									AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, null);
	    				Log.d("AODV_type5", "delivery_end");
						break;
					}
				}
				// 有効な経路を持っていない場合

				// RERRの送信
				if(!Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress()))
					new RERR().RERR_Sender(mAODV_Service.getRoute(index),mAODV_Service);
    		}

			break;

/*    	case 10: // 分割ファイル送信FSEND
			Log.d("分割ファイルFSEND","get");
    		// 自分宛のメッセージなら
    		if( FSend.isToMe(receiveBuffer,my_address)){

    			final int packet_seq = FSend.getStepNo(receiveBuffer);		// パケット分割後の番号(何番目のパケットか)
    			final int packet_total = FSend.getStepTotal(receiveBuffer);	// パケット分割数

    			int file_name_length = FSend.getFileNameLength(receiveBuffer);	// ファイル名(byte)の長さ
    			final String file_name = FSend.getFileName(receiveBuffer, file_name_length);	// ファイル名

    			Log.d("debug_FSEND", "receive"+packet_seq+"/"+packet_total+":"+file_name);

    			int r_index;
    			FileReceivedManager frm = new FileReceivedManager(1, null, null);

    			// 古すぎる受信データを削除
    			while( (r_index = removeDisconnectedFile()) != -1){	// 古いデータがある限りループ
    				frm = frm.get(r_index);

    				try {
						frm.out.close();
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
    				try {
						frm.file.close();
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
    				AODV_Activity.context.deleteFile(frm.file_name);
    				file_received_manager.remove(r_index);
    				Log.d("FILE","no_move_file_removed_for_long_time");
    			}

    			// 分割受信中のファイルか検索
    			r_index = searchSameNameFile(file_name);

    			// 最初のパケットならファイルオープン
    			if(packet_seq == 1 && r_index == -1){
    				try {
    					// 新規インスタンスとして各フィールドの初期化
    					frm.file_name = file_name;
    					frm.file = AODV_Activity.context.openFileOutput(file_name,
    							AODV_Activity.context.MODE_WORLD_READABLE
    							| AODV_Activity.context.MODE_WORLD_WRITEABLE);
    					frm.out = new BufferedOutputStream(frm.file);
    					frm.receive_file_next_no = 1;
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;

					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
    			}
    			else if(r_index != -1){
    				// 既存の処理中データを抽出
    				frm = frm.get(r_index);
    			}else{
    				break;
    			}

    			// 正しい順序で受信したならファイルにデータを書き込む
    			if(packet_seq == frm.receive_file_next_no){
    				Log.d("debug_FSEND", "this_written");
    				try {
						frm.out.write(FSend.getFileData(receiveBuffer, file_name_length, receiveBuffer.length));
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}

    				frm.receive_file_next_no++;

    				if( (((packet_seq*100)/packet_total) %10) == 0){
			    		handler.post(new Runnable() {
			    			@Override
			    			public void run() {
			    				// 10%ごとに経過を出力
			    				editText.append(file_name+":\t"+((packet_seq*100)/packet_total)+"% received\n");
			    				editText.setSelection(editText.getText().toString().length());
			    			}
	    				});
    				}
    			}

    			// 次のパケットを送信元に要求
    			// 最終パケット受信後でも終了通知のために必要
	    		if(frm.file != null)
	    			FReq.file_req(preHopAddress, my_address, FSend.getAddressSrc(receiveBuffer), frm.receive_file_next_no, file_name, port);

    			// さらに、受信パケットが最後のパケットならデータ書き込みを終了
    			if(frm.receive_file_next_no == (packet_total+1)){
    				
    				try {
    					frm.out.flush();
        				frm.out.close();
						frm.file.close();
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}

    				frm.out = null;
    				frm.file = null;

    				// 削除
    				if(r_index != -1){ // 初パケット=最終パケットでなければ
    					frm.remove(r_index);
    				}

    				if( file_name.endsWith(".jpg") || file_name.endsWith(".jpeg")){
	    				// もし画像ファイルなら暗黙的インテントを投げる
	    	            Intent intent = new Intent();
	    	            intent.setAction(Intent.ACTION_VIEW);
	    	            intent.setData(Uri.parse("Files:"+frm.file_name));
	    	            intent.putExtra("SOURCE_ADDRESS",
	    	            		AODV_Activity.getStringByByteAddress(FSend.getAddressSrc(receiveBuffer)));
	    	            intent.putExtra("PACKAGE", "jp.ac.ehime_u.cite.udptest");
	    	            intent.putExtra("ID", send_intent_id);
	    	            AODV_Activity.context.startActivity(intent);

	    	            send_intent_id++;
    				}
    			}
    			else{
    				if(r_index == -1){	// 初パケットならadd
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;
    					frm.add();
    				}
    				else{
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;
    					frm.set(r_index);
    				}
    			}
    		}
    		else{	// 次ホップへ転送
    			// 宛先までの有効経路を持っているか検索
				int index1 = AODV_Activity.searchToAdd(FSend.getAddressDest(receiveBuffer));

				// 経路を知っていて
				if(index1 != -1){
					// 有効な経路なら
					if(AODV_Activity.getRoute(index1).stateFlag == 1){

						// 次ホップへそのまま転送
						sendMessage(receiveBuffer, AODV_Activity.getRoute(index1).nextIpAdd);

						break;
					}
				}
				// 有効な経路を持っていない場合

				// RERRの送信?
				RouteManager.RERR_Sender(AODV_Activity.getRoute(index1),port);
    		}

    		break;

    	case 11: // 分割ファイル要求FREQ

    		// ###

    		// 自分宛のメッセージなら
    		if( FReq.isToMe(receiveBuffer,my_address)){

    			// 送信元までの有効経路を持っているか検索
				int index1 = AODV_Activity.searchToAdd(FReq.getAddressSrc(receiveBuffer));

				// 経路を知っていて
				if(index1 != -1){
					final RouteTable route = AODV_Activity.getRoute(index1);

					// 有効な経路なら
					if(route.stateFlag == 1){

						// 要求ファイル名,シーケンス番号,送信元を抜き出し
						String file_name = FReq.getFileName(receiveBuffer, receiveBuffer.length);
						int req_no = FReq.getStepNextNo(receiveBuffer);
						byte[] source_address = FReq.getAddressSrc(receiveBuffer);

						// 過去の送信経過を検索
						FileManager files = AODV_Activity.searchProgress(file_name, source_address);

						if(files == null){ // これまでに送信記録がないとき
							// ファイルオープン
							try {
								files = new FileManager(file_name,source_address,my_address,AODV_Activity.context);
							} catch (FileNotFoundException e) {
								// TODO 自動生成された catch ブロック
								e.printStackTrace();
							}
						}

						// 次パケットの順序番号が一致する場合
						if(files.file_next_no == req_no){

							// もし最後のパケットの応答通知なら
							if(files.total_step < req_no){
								// ファイルクローズ処理
								if( files.file_in != null){
									try {
										files.file_in.close();
									} catch (IOException e) {
										// TODO 自動生成された catch ブロック
										e.printStackTrace();
									}
								}
								if( files.file != null){
									try {
										files.file.close();
									} catch (IOException e) {
										// TODO 自動生成された catch ブロック
										e.printStackTrace();
									}
								}
								files.remove();
					    		handler.post(new Runnable() {
					    			@Override
					    			public void run() {
					    				// 10%ごとに経過を出力
					    				editText.append("fileSend_completed!\n");
					    				editText.setSelection(editText.getText().toString().length());
					    			}
			    				});



			    				Date date = new Date();
			    				SimpleDateFormat sdf = new SimpleDateFormat("MMdd'_'HHmmss");

			    				String log = "time:"+sdf.format(date);
			    				Log.d("SEND_T",log);
							}
							else{	// 分割送信の途中なら
								// 送信処理
								files.fileSend(my_address, route.nextIpAdd, port);

								// 経過を上書き
								files.set();

								// 再送処理
								final FileManager f_copy = files;

								try {
									new Thread(new Runnable() {

										int wait_time = 2 * AODV_Activity.NODE_TRAVERSAL_TIME
											* (route.hopCount + AODV_Activity.TIMEOUT_BUFFER);
										int resend_count = 0;
										int prev_step = f_copy.file_next_no;
										byte[] buffer = f_copy.buffer;
										byte[] destination_next_hop_address_b = route.nextIpAdd;
										int port_ = port;
										String file_name = f_copy.file_name;
										byte[] destination_address = f_copy.destination_address;

										// 再送処理
										public void run() {
											timer: while (true) {

//												handler.post(new Runnable() {
//													public void run() {

														// 送信try
														SendByteArray.send(buffer, destination_next_hop_address_b);

//													}

//												});
												// 指定の時間停止する
												try {
													Thread.sleep(wait_time);
												} catch (InterruptedException e) {
												}

												resend_count++;

												// ループを抜ける処理
												if (resend_count == AODV_Activity.MAX_RESEND) {
													break timer;
												}
												FileManager files = AODV_Activity.searchProgress(file_name, destination_address);
												if(files == null){
													break timer;
												}
												else{
													if( files.file_next_no != prev_step){
														break timer;
													}
												}

											}
										}
									}).start();

								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}


						break;
					}
				}
				// 有効な経路を持っていない場合
				else{

				}
    		}
    		else{	// 次ホップへ転送
    			// 宛先までの有効経路を持っているか検索
				int index1 = AODV_Activity.searchToAdd(FReq.getAddressDest(receiveBuffer));

				// 経路を知っていて
				if(index1 != -1){
					// 有効な経路なら
					if(AODV_Activity.getRoute(index1).stateFlag == 1){

						// 次ホップへそのまま転送
						sendMessage(receiveBuffer, AODV_Activity.getRoute(index1).nextIpAdd);

						break;
					}
				}
				// 有効な経路を持っていない場合

				// RERRの送信?
				RouteManager.RERR_Sender(AODV_Activity.getRoute(index1),port);
    		}

    		break;*/
    		
		}
	}
	
	// バイト配列の取り出し（余分な部分の削除）
	private static byte[] cut_byte_spare(byte[] b,int size){

		byte[] slim_byte = new byte[size];
		System.arraycopy(b, 0, slim_byte, 0, size);

		return slim_byte;
	}

	// 受信ファイル検索
	// 受信中のデータにファイル名が同じものがあるか検索
	// 無ければ-1を返す
//	public int searchSameNameFile(String name){
//		synchronized(AODV_Activity.fileReceivedManagerLock){
//			for(int i=0;i<file_received_manager.size();i++){
//				if( file_received_manager.get(i).file_name.equals(name)){
//					return i;
//				}
//			}
//		}
//		return -1;
//	}
	// 長時間音沙汰の無い受信ファイルのindexを返す
//	public int removeDisconnectedFile(){
//		synchronized(AODV_Activity.fileReceivedManagerLock){
//
//			long now = new Date().getTime();
//			for(int i=0;i<file_received_manager.size();i++){
//				if( file_received_manager.get(i).life_time < now){
//					return i;
//				}
//			}
//		}
//		return -1;
//	}

	/***** メッセージ0:テキストメッセージ用関数 ******/
	// メッセージ0が自分宛か？
	private static boolean isToMe(byte[] receiveBuffer,byte[] myAddress){
		if(receiveBuffer[0] != 0){
			return false;
		}

		// 宛先IPアドレスのコピー先を作成
		byte[] toIpAdd = new byte[4];

		// 該当部分を抜き出し
		System.arraycopy(receiveBuffer,1,toIpAdd,0,4);

		if(Arrays.equals(toIpAdd,myAddress))
				return true;
		else return false;
	}

	// メッセージ0の中から宛先アドレスを抜き出す
	private static byte[] getAddressDest(byte[] receiveBuffer){
		byte[] add = new byte[4];

		// 該当部分を抜き出し
		System.arraycopy(receiveBuffer, 1, add, 0, 4);

		return add;
	}

	// メッセージ0の中から送信元アドレスを抜き出す
	private static byte[] getAddressSrc(byte[] receiveBuffer){
		if(receiveBuffer[0] == 1){
			return RREQ.getFromIpAdd(receiveBuffer);
		}

		byte[] add = new byte[4];

		// 該当部分を抜き出し
		System.arraycopy(receiveBuffer, 5, add, 0, 4);

		return add;
	}

	// メッセージ0の中から伝送データを抜き出す
	private static byte[] getMessage(byte[] receiveBuffer,int length){
		if(receiveBuffer[0] == 1){
			return RREQ.getMessage(receiveBuffer, length);
		}


		// 宛先IPアドレスのコピー先を作成
		byte[] message = new byte[length-9];

		// 該当部分を抜き出し
		System.arraycopy(receiveBuffer,9,message,0,length-9);

		return message;
	}

	// byte[]型をint型へ変換
	private static int byteToInt(byte[] num){

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
	private static  byte[] getByteAddress(String str){

		// 分割
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}
}
