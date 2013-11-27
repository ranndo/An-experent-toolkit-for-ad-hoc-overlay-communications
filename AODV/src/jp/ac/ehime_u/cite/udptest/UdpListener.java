package jp.ac.ehime_u.cite.udptest;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class UdpListener implements Runnable {

	Context context;

	// 受信用の配列やパケットなど
	private byte[] buffer = new byte[64*1024];
	private int port;
	private DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	private DatagramSocket socket;
	
	private byte[] my_address;
	private AODV_Service mAODV_Service;

	// コンストラクタ
	// 引数1:Handler	メインスレッドのハンドル(Handlerを使うことでUIスレッドの持つキューにジョブ登録ができる)
	// 引数2:TextView	受信結果を表示するTextView
	// 引数3:port_		ポート番号(受信)
	// 引数4:max_packets 記録する最大パケット数(受信可能回数)
	public UdpListener(int port_, Context context_) throws SocketException {
		port = port_;
		socket = new DatagramSocket(port);
		context = context_;
		
		StaticIpAddress sIp = new StaticIpAddress(context_);
		my_address = sIp.getStaticIpByte();
		
		mAODV_Service = null;
		Intent intent = new Intent(context, AODV_Service.class);
		intent.setPackage(context.getPackageName());
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void run() {
		while (true) {
			if(mAODV_Service != null){
				break;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
		}
		
		while (true) {
			try {
				socket.receive(packet); // blocking
				
				// 受信したデータを抽出	received_dataをreceiveBufferに変更したよ
				byte[] receiveBuffer = cut_byte_spare(packet.getData() ,packet.getLength());
				
				ReceiveProcess.process(receiveBuffer, packet.getAddress().getAddress(), false, my_address, context, mAODV_Service);
				
			} catch (IOException e) {

				e.printStackTrace();
			} finally{
				if(mAODV_Service != null)
					context.unbindService(connection);
			}
		}
	}

	// コネクション生成
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

	// バイト配列の取り出し（余分な部分の削除）
	byte[] cut_byte_spare(byte[] b,int size){

		byte[] slim_byte = new byte[size];
		System.arraycopy(b, 0, slim_byte, 0, size);

		return slim_byte;
	}
}