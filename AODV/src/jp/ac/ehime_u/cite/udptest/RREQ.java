package jp.ac.ehime_u.cite.udptest;
/* 未完成部分のキーワード「###」 */

import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import android.util.Log;

public class RREQ {
	// RREQメッセージのフィールド	以下[フォーマット上の位置(バイト)]、解説
//	byte type;		// [0] メッセージタイプ
//	byte flag;		// [1] 先頭5ビットのみフラグ(JRGDU)、残りは0
//					//	Joinフラグ：マルチキャスト用
//					//	Repairフラグ；マルチキャスト用
//					//	Gratuitous RREPフラグ : 宛先IPアドレスフィールドによって指定されたノードへ、Gratuitous RREPをユニキャストするかどうかを示す
//					//	Destination Onlyフラグ : 宛先ノードだけがこのRREQに対して返信することを示す
//					//	未知シーケンス番号 : 宛先シーケンス番号が知られていないことを示す
//	byte reserved;	// [2] 予約済み：0として送信され、使用しない
//	byte hopCount;	// [3] ホップ数
//	int RREQ_ID;	// [4-7] 送信元ノードのIPアドレスとともに受信した時、RREQを識別するためのシーケンス番号
//	byte[] toIpAdd;		// [8-11] あて先ノードのIPアドレス
//	int toSeqNum;		// [12-15] 宛先ノードへの経路において、送信元ノードによって過去の受信した最新のシーケンス番号
//	byte[] fromIpAdd;	// [16-19] 送信元ノードのIPアドレス
//	int fromSeqNum;		// [20-23] 送信元ノードへの経路において利用される現在のシーケンス番号
//	int timeToLive;		// [24-27] 生存時間TTL、中間ノードを残りいくつまで許すか
//	String message;		// [28-??]*通常では使用しない。ブロードキャストメッセージの場合、メッセージを付加

	// RREQメッセージの送信
	// 引数：送信先(String型)
	public static void send(byte[] destination_address, byte[] myAddress, boolean flagJ ,boolean flagR ,boolean flagG ,boolean flagD ,boolean flagU
			,int toSeq ,int fromSeq,int ID,int TTL,String text,AODV_Service binder) {

		// 各フィールドの初期化
		byte type = 1;	// RREQを示す
		// 各フラグを1バイトの先頭5ビットに納める
		byte flag =	(byte)(((flagJ)? (2<<6):0)	// 10000000
				|((flagR)? (2<<5):0)		// 01000000
				|((flagG)? (2<<4):0)		// 00100000
				|((flagD)? (2<<3):0)		// 00010000
				|((flagU)? (2<<2):0));		// 00001000
		byte reserved = 0;
		byte hopCount = 0;

		int RREQ_ID = ID;

		int toSeqNum = toSeq;

		int fromSeqNum = fromSeq;
		int timeToLive = TTL;
		String message = text;
		byte[] message_b = null;
		boolean bload_cast_flag = false;

		// UDPパケットに含めるデータ
		byte[] sendBuffer = null;

		// 最終宛先がブロードキャストアドレスなら
		if( Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), destination_address)){
			message_b = message.getBytes();
			sendBuffer = new byte[28+message_b.length];
			bload_cast_flag = true;
		}
		else{
			sendBuffer = new byte[28];
		}

		sendBuffer[0] = type;
		sendBuffer[1] = flag;
		sendBuffer[2] = reserved;
		sendBuffer[3] = hopCount;
		System.arraycopy(intToByte(RREQ_ID)   ,0,sendBuffer,4 ,4);
		System.arraycopy(destination_address  ,0,sendBuffer,8 ,4);
		System.arraycopy(intToByte(toSeqNum)  ,0,sendBuffer,12,4);
		System.arraycopy(myAddress			  ,0,sendBuffer,16,4);
		System.arraycopy(intToByte(fromSeqNum),0,sendBuffer,20,4);
		System.arraycopy(intToByte(timeToLive),0,sendBuffer,24,4);

		if(bload_cast_flag){
			System.arraycopy(message_b,0,sendBuffer,28,message_b.length);
		}
  
        if(binder != null){
    		binder.send(sendBuffer, RREQ.getByteAddress("255.255.255.255"));

            System.out.println("RREQメッセージを送信しました");	//###デバッグ用###
          
        	binder.writeLog(11,getStringByByteAddress(myAddress),getStringByByteAddress(myAddress),getStringByByteAddress(destination_address), 0,fromSeqNum);
        }
    }

	/***** RREQメッセージの転送 *****/
	public static void send2(byte[] data,AODV_Service binder){
		if(binder != null){
			binder.send(data, RREQ.getByteAddress("255.255.255.255"), 28);
		
	    	System.out.println("RREQメッセージを転送しました");	//###デバッグ用###
		}
	}

	// 受信したRREQメッセージが自身のノード宛のものか調べる
	// 引数：RREQメッセージ
	public static boolean isToMe(byte[] receiveBuffer, byte[] myAddress){
		// 宛先IPアドレスのコピー先を作成
		byte[] toIpAdd = new byte[4];

		// 該当部分を抜き出し
		System.arraycopy(receiveBuffer,8,toIpAdd,0,4);

		if(Arrays.equals(toIpAdd,myAddress))
				return true;
		else return false;
	}

	// RREQメッセージからJフィールドを返す
	public static boolean getFlagJ(byte[] RREQMes){
		if( (RREQMes[1]&(2<<6)) !=0)
			return true;
		else return false;
	}
	// RREQメッセージからRフィールドを返す
	public static boolean getFlagR(byte[] RREQMes){
		if( (RREQMes[1]&(2<<5)) !=0)
			return true;
		else return false;
	}
	// RREQメッセージからGフィールドを返す
	public static boolean getFlagG(byte[] RREQMes){
		if( (RREQMes[1]&(2<<4)) !=0)
			return true;
		else return false;
	}
	// RREQメッセージからDフィールドを返す
	public static boolean getFlagD(byte[] RREQMes){
		if( (RREQMes[1]&(2<<3)) !=0)
			return true;
		else return false;
	}
	// RREQメッセージからUフィールドを返す
	public static boolean getFlagU(byte[] RREQMes){
		if( (RREQMes[1]&(2<<2)) !=0)
			return true;
		else return false;
	}
	// RREQメッセージからhopCountフィールドを返す
	public static byte getHopCount(byte[] RREQMes){
		return RREQMes[3];
	}
	// RREQメッセージからRREQ_IDフィールドを返す
	public static int getRREQ_ID(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,4,buf,0,4);

		// int型に変換
		return byteToInt(buf);
	}
	// RREQメッセージからtoIpAddフィールドを返す
	public static byte[] getToIpAdd(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,8,buf,0,4);

		return buf;
	}
	// RREQメッセージからtoSeqNumフィールドを返す
	public static int getToSeqNum(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,12,buf,0,4);

		// int型に変換
		return byteToInt(buf);
	}
	// RREQメッセージからfromoIpAddフィールドを返す
	public static byte[] getFromIpAdd(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,16,buf,0,4);

		return buf;
	}
	// RREQメッセージからfromSeqNumフィールドを返す
	public static int getFromSeqNum(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,20,buf,0,4);

		// int型に変換
		return byteToInt(buf);
	}
	// RREQメッセージからTTLフィールドを返す
	public static int getTimeToLive(byte[] RREQMes){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,24,buf,0,4);

		// int型に変換
		return byteToInt(buf);
	}
	// RREQメッセージからメッセージフィールドを返す
	public static byte[] getMessage(byte[] RREQMes, int length){

		// 該当部分のbyte[]を抜き出し
		byte[] buf = new byte[length-28];
		System.arraycopy(RREQMes,28,buf,0,length-28);

		// int型に変換
		return buf;
	}

	// RREQメッセージの送信元シーケンス番号フィールドをセットして返す
	public static byte[] setFromSeqNum(byte[] RREQMes,int num){
		// 変更する番号をbyte[]型に
		byte[] seq = intToByte(num);

		// 送信元シーケンス番号の部分に上書き
		System.arraycopy(seq,0,RREQMes,20,4);
		return RREQMes;
	}
	// RREQメッセージのTTLをセットして返す
	public static byte[] setTimeToLive(byte[] RREQMes,int num){
		// 変更する番号をbyte[]型に
		byte[] TTL = intToByte(num);

		// 送信元シーケンス番号の部分に上書き
		System.arraycopy(TTL,0,RREQMes,24,4);
		return RREQMes;
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
	public static byte[] getByteAddress(String str){

		// 分割
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
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




}
