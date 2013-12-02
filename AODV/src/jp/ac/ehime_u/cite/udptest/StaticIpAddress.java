package jp.ac.ehime_u.cite.udptest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

/* ふつうのWifi_IpAddressが使いたい場合は、getStaticIpにWifiがonならそっちからとる、みたいな処理を追加するとよい。 */
/* 旧，WifiからIp取得のコードをコメントアウトで残しておく。 */
public class StaticIpAddress {
	Context context;
//	private final String text_name = "staticIp.txt";
	
	public StaticIpAddress(Context context_){
		context = context_;
	}
	
	// なんか適当に"192.168.1**.1**"をローカルファイルに書き込み。
	// ただし、識別IDを使って重複を*多分*避けるようにする。
//	public void refreshStaticIp(){
//		String ipAddress;
//		String subIp1,subIp2;
//		
//		// IMEI:端末に割り当てられた番号。識別に便利。
//		// 注意点
//		// 1.パーミッションが必要
//		// 2.「携帯電話端末」でないと使えない。携帯機能がない場合がアウト
//		TelephonyManager mTelephonyMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
//		String imsi = mTelephonyMgr.getSubscriberId();
//		if(imsi == null){
//			Date date = new Date();
//			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//			imsi = sdf.format(date);
//		}
//		// **.01.**とかなると困るので、100台で…。
//		int length = imsi.length();
//		subIp1 = "1"+imsi.substring(length-4, length-3);
//		subIp2 = "1"+imsi.substring(length-2);
//		
//		ipAddress = "192.168." + subIp1 + "." + subIp2;
//		
//		// ローカルファイルに書き込め。
//		try {
//			OutputStream out = context.openFileOutput(text_name,Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);
//			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
//			writer.append(ipAddress);
//			writer.close();
//		} catch (FileNotFoundException e) {
//			// TODO 自動生成された catch ブロック
//			e.printStackTrace();
//		} catch (UnsupportedEncodingException e) {
//			// TODO 自動生成された catch ブロック
//			e.printStackTrace();
//		}
//	}
//	
	public String getStaticIp(){
//		String str = null;
//		try {
//			InputStream in = context.openFileInput(text_name);
//			BufferedReader reader = new BufferedReader(new InputStreamReader(in,"UTF-8"));
//			
//			str = reader.readLine();
//			reader.close();
//		} catch (FileNotFoundException e) {
//			refreshStaticIp();
//			str = getStaticIp();
//		} catch (UnsupportedEncodingException e) {
//			// TODO 自動生成された catch ブロック
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO 自動生成された catch ブロック
//			e.printStackTrace();
//		}
//		return str;
		
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		byte[] byteMacAddress = getByteFromMacAddress(wifiInfo.getMacAddress());
		
		return String.valueOf(byteMacAddress[0]+128) +"."+String.valueOf(byteMacAddress[1]+128)
				+ "." + String.valueOf(byteMacAddress[2]+128) + "." + String.valueOf(byteMacAddress[3]+128);
	}
	
	private byte[] getByteFromMacAddress(String mac){
		String[] parts = mac.split(":");
		byte[] byteMac = new byte[parts.length];
		for(int i=0; i<parts.length; i++){
			byteMac[i] = (byte)Integer.parseInt(parts[i],16);
		}
		return byteMac;
	}
	
	public byte[] getStaticIpByte(){
		String str = getStaticIp();
		return getByteAddress(str);
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
	 * 	// 自身のIPアドレスを取得
	public static String getIPAddress() throws IOException{
	    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

	    String regex = "[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}";
	    Pattern pattern = Pattern.compile(regex);

	    while(interfaces.hasMoreElements()){
	        NetworkInterface network = interfaces.nextElement();
	        Enumeration<InetAddress> addresses = network.getInetAddresses();

	        while(addresses.hasMoreElements()){
	            String address = addresses.nextElement().getHostAddress();
	            Matcher matcher = pattern.matcher(address);

	            Boolean b1 = !("127.0.0.1".equals(address));
	            Boolean b2 = !("0.0.0.0".equals(address));
	            Boolean b3 = matcher.find();

	            //127.0.0.1と0.0.0.0以外のアドレスが見つかったらそれを返す
	            if(b1 && b2 && b3){
	                return address;
	            }
	        }
	    }

	    return "127.0.0.1";
	}
	 */
}
