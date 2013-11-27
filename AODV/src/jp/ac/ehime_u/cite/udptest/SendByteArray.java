//package jp.ac.ehime_u.cite.udptest;
//
//import java.io.IOException;
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.net.InetSocketAddress;
//import java.net.SocketException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.Locale;
//
//public class SendByteArray {
//	// ルートテーブルに従った方法で送信を行う
//	public static void send(byte[] buffer, byte[] ipAddress){
//		send(buffer, ipAddress, buffer.length);
//	}
//	public static void send(byte[] buffer, byte[] ipAddress, int length){
//		
//		boolean btFlag = false;
//		
//		if(AODV_Activity.getStringByByteAddress(ipAddress).equals("255.255.255.255")){
//			if(AODV_Activity.mChatService != null){
//				btFlag = true;
//			}
//			else{
//				btFlag = false;
//			}
//		}else{
//			int index = AODV_Activity.searchToAdd(ipAddress);
//			if(index != -1){
//				RouteTable route = AODV_Activity.getRoute(index);
//				btFlag = route.bluetoothFlag;
//			}
//		}
//		
//		if(btFlag){
//			// BT ON
//			// ブロードキャスト？
//			if(AODV_Activity.getStringByByteAddress(ipAddress).equals("255.255.255.255")){
//				AODV_Activity.mChatService.write(buffer);
//			}else{
//				// ユニキャスト
//				AODV_Activity.mChatService.write(buffer, ipAddress);
//			}
//		}else{
//			// BT OFF
//			// データグラムソケットを開く
//			DatagramSocket soc = null;
//			try {
//				soc = new DatagramSocket();
//			} catch (SocketException e) {
//				// TODO 自動生成された catch ブロック
//				e.printStackTrace();
//			}
//
//	        // UDPパケットを送信する先となるブロードキャストアドレス
//	        InetSocketAddress remoteAddress =
//	        			 new InetSocketAddress(AODV_Activity.getStringByByteAddress(ipAddress), 12345);
//
//	        // UDPパケット
//	        DatagramPacket sendPacket = null;
//			try {
//				sendPacket = new DatagramPacket(buffer, length, remoteAddress);
//			} catch (SocketException e) {
//				// TODO 自動生成された catch ブロック
//				e.printStackTrace();
//			}
//
//	        // DatagramSocketインスタンスを生成して、UDPパケットを送信
//	        try {
//				soc.send(sendPacket);
//			} catch (IOException e) {
//				// TODO 自動生成された catch ブロック
//				e.printStackTrace();
//			}
//
//	        //データグラムソケットを閉じる
//	        soc.close();
//		}
//	}
//	
//	// String型のアドレスをbyte[]型に変換
//	public static byte[] getByteAddress(String str){
//
//		// 分割
//		String[] s_bara = str.split("\\.");
//
//		byte[] b_bara = new byte[s_bara.length];
//		for(int i=0;i<s_bara.length;i++){
//			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
//		}
//		return b_bara;
//	}
//}
