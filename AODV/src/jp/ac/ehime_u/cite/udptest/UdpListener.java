package jp.ac.ehime_u.cite.udptest;


import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream.PutField;
import java.io.UnsupportedEncodingException;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import android.widget.ScrollView;

public class UdpListener implements Runnable {

	Handler handler;
	Context context;
	EditText editText;

	// 受信用の配列やパケットなど
	private byte[] buffer = new byte[64*1024];
	private int port;
	private DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	private DatagramSocket socket;

	// コンストラクタ
	// 引数1:Handler	メインスレッドのハンドル(Handlerを使うことでUIスレッドの持つキューにジョブ登録ができる)
	// 引数2:TextView	受信結果を表示するTextView
	// 引数3:port_		ポート番号(受信)
	// 引数4:max_packets 記録する最大パケット数(受信可能回数)
	public UdpListener(Handler handler_, EditText edit_text,
			int port_, int max_packets, Context context_) throws SocketException {
		port = port_;
		socket = new DatagramSocket(port);
		handler = handler_;
		editText = edit_text;
		context = context_;
	}

	@Override
	public void run() {
	label: while (true) {
			try {
				socket.receive(packet); // blocking

				// 受信したデータを抽出	received_dataをreceiveBufferに変更したよ
				byte[] receiveBuffer = cut_byte_spare(packet.getData() ,packet.getLength());
				
				AODV_Activity.receiveProcess.process(receiveBuffer, packet.getAddress().getAddress(), false);
				
			} catch (IOException e) {

				e.printStackTrace();
			}
		}
	}


	// バイト配列の取り出し（余分な部分の削除）
	byte[] cut_byte_spare(byte[] b,int size){

		byte[] slim_byte = new byte[size];
		System.arraycopy(b, 0, slim_byte, 0, size);

		return slim_byte;
	}
}