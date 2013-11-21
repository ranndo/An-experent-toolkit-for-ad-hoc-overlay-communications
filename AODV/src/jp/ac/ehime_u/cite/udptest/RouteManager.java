package jp.ac.ehime_u.cite.udptest;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;



//一定時間ごとにHELLOメッセージの送信と、既存経路の生存時間をチェック
//接続切れがあった場合、RERRをPrecursorに送信する
//ついでに、DELETE時間がたった経路を削除する
public class RouteManager implements Runnable {
	
	RouteTable route;
	int port;
	byte[] myAddress;
	Context context;
	
	// Serviceとの連携用変数
	private AODV_Service mAODV_Service;
	private boolean bindState;
	
	public RouteManager(int port_,Context context_) throws IOException{
		port = port_;
		
		StaticIpAddress sIp = new StaticIpAddress(AODV_Activity.context);
		myAddress = sIp.getStaticIpByte();
		
		bindState = false;
		context.bindService( new Intent( context, AODV_Service.class), connection, Context.BIND_AUTO_CREATE);
	}
	public void stop(){
		if(bindState == true){
			context.unbindService(connection);
		}
	}
	
	// コネクション生成
	private ServiceConnection connection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO 自動生成されたメソッド・スタブ
			mAODV_Service = null;
			bindState = false;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// TODO 自動生成されたメソッド・スタブ
			mAODV_Service = ((AODV_Service.AODV_ServiceBinder)service).getService();
			bindState = true;
		}
	};
	
	@Override
	public void run() {
		while(true){
			if(!bindState){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
				continue;
			}
			
			// 経路が存在するならば
			if(mAODV_Service.getRouteSize() > 0){
				
				// 経路を維持するHELLOメッセージの送信
				// 一定時間内にブロードキャストしていれば実行しない
				if(mAODV_Service.getDoBroadcast() == false){
					try {
						new RREP().send(mAODV_Service.getSeqNum(), port, 
								AODV_Service.ALLOWED_HELLO_LOSS *
								AODV_Service.HELLO_INTERVAL
								, myAddress);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				
				// 既存経路の生存時間チェック
				for(int i=0;i<mAODV_Service.getRouteSize();i++){
					
					route = mAODV_Service.getRoute(i);
					long now = new Date().getTime();
					
					// 有効でない経路が削除される時間であるか
					if( (route.stateFlag != 1) && (route.lifeTime < now)){
						
						// 無効経路の削除
						mAODV_Service.removeRoute(i);
						
						// 削除によるiのズレを修正
						i--;
					}
					
					// 有効経路が無効経路になる時間（寿命）であるか
					else if( (route.stateFlag == 1) && (route.lifeTime < now)){
						
						// 無効化
						route.stateFlag = 2;
						route.lifeTime  = (now+AODV_Service.DELETE_PERIOD);
						route.toSeqNum++;
						
						// 上書き
						mAODV_Service.setRoute(i, route);
						
						// ローカルリペアを行えるホップ数か？
						if(route.hopCount <= AODV_Service.MAX_REPAIR_TTL){
							mAODV_Service.localRepair(route,port,myAddress,mAODV_Service);
						}
						else{
							// RERRの送信
							new RERR().RERR_Sender(route,port);
							
							final byte[] destination_address = route.toIpAdd;
							mAODV_Service.appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
						}
					}
				}
			}
			
			mAODV_Service.setDoBroadcast(false);
			
			try { // 一定時間ごとにループ処理、処理時間は無視？
				Thread.sleep(AODV_Service.HELLO_INTERVAL);
			} catch (InterruptedException ex2) {
				ex2.printStackTrace();
			}	
		}
	}
}
