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

/* �ӂ���Wifi_IpAddress���g�������ꍇ�́AgetStaticIp��Wifi��on�Ȃ炻��������Ƃ�A�݂����ȏ�����ǉ�����Ƃ悢�B */
/* ���CWifi����Ip�擾�̃R�[�h���R�����g�A�E�g�Ŏc���Ă����B */
public class StaticIpAddress {
	Context context;
//	private final String text_name = "staticIp.txt";
	
	public StaticIpAddress(Context context_){
		context = context_;
	}
	
	// �Ȃ񂩓K����"192.168.1**.1**"�����[�J���t�@�C���ɏ������݁B
	// �������A����ID���g���ďd����*����*������悤�ɂ���B
//	public void refreshStaticIp(){
//		String ipAddress;
//		String subIp1,subIp2;
//		
//		// IMEI:�[���Ɋ��蓖�Ă�ꂽ�ԍ��B���ʂɕ֗��B
//		// ���ӓ_
//		// 1.�p�[�~�b�V�������K�v
//		// 2.�u�g�ѓd�b�[���v�łȂ��Ǝg���Ȃ��B�g�ы@�\���Ȃ��ꍇ���A�E�g
//		TelephonyManager mTelephonyMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
//		String imsi = mTelephonyMgr.getSubscriberId();
//		if(imsi == null){
//			Date date = new Date();
//			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//			imsi = sdf.format(date);
//		}
//		// **.01.**�Ƃ��Ȃ�ƍ���̂ŁA100��Łc�B
//		int length = imsi.length();
//		subIp1 = "1"+imsi.substring(length-4, length-3);
//		subIp2 = "1"+imsi.substring(length-2);
//		
//		ipAddress = "192.168." + subIp1 + "." + subIp2;
//		
//		// ���[�J���t�@�C���ɏ������߁B
//		try {
//			OutputStream out = context.openFileOutput(text_name,Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);
//			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
//			writer.append(ipAddress);
//			writer.close();
//		} catch (FileNotFoundException e) {
//			// TODO �����������ꂽ catch �u���b�N
//			e.printStackTrace();
//		} catch (UnsupportedEncodingException e) {
//			// TODO �����������ꂽ catch �u���b�N
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
//			// TODO �����������ꂽ catch �u���b�N
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO �����������ꂽ catch �u���b�N
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
	
	// String�^�̃A�h���X��byte[]�^�ɕϊ�
	public byte[] getByteAddress(String str){

		// ����
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}
	
	/*
	 * 	// ���g��IP�A�h���X���擾
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

	            //127.0.0.1��0.0.0.0�ȊO�̃A�h���X�����������炻���Ԃ�
	            if(b1 && b2 && b3){
	                return address;
	            }
	        }
	    }

	    return "127.0.0.1";
	}
	 */
}
