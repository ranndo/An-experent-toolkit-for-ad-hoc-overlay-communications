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
//	// ���[�g�e�[�u���ɏ]�������@�ő��M���s��
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
//			// �u���[�h�L���X�g�H
//			if(AODV_Activity.getStringByByteAddress(ipAddress).equals("255.255.255.255")){
//				AODV_Activity.mChatService.write(buffer);
//			}else{
//				// ���j�L���X�g
//				AODV_Activity.mChatService.write(buffer, ipAddress);
//			}
//		}else{
//			// BT OFF
//			// �f�[�^�O�����\�P�b�g���J��
//			DatagramSocket soc = null;
//			try {
//				soc = new DatagramSocket();
//			} catch (SocketException e) {
//				// TODO �����������ꂽ catch �u���b�N
//				e.printStackTrace();
//			}
//
//	        // UDP�p�P�b�g�𑗐M�����ƂȂ�u���[�h�L���X�g�A�h���X
//	        InetSocketAddress remoteAddress =
//	        			 new InetSocketAddress(AODV_Activity.getStringByByteAddress(ipAddress), 12345);
//
//	        // UDP�p�P�b�g
//	        DatagramPacket sendPacket = null;
//			try {
//				sendPacket = new DatagramPacket(buffer, length, remoteAddress);
//			} catch (SocketException e) {
//				// TODO �����������ꂽ catch �u���b�N
//				e.printStackTrace();
//			}
//
//	        // DatagramSocket�C���X�^���X�𐶐����āAUDP�p�P�b�g�𑗐M
//	        try {
//				soc.send(sendPacket);
//			} catch (IOException e) {
//				// TODO �����������ꂽ catch �u���b�N
//				e.printStackTrace();
//			}
//
//	        //�f�[�^�O�����\�P�b�g�����
//	        soc.close();
//		}
//	}
//	
//	// String�^�̃A�h���X��byte[]�^�ɕϊ�
//	public static byte[] getByteAddress(String str){
//
//		// ����
//		String[] s_bara = str.split("\\.");
//
//		byte[] b_bara = new byte[s_bara.length];
//		for(int i=0;i<s_bara.length;i++){
//			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
//		}
//		return b_bara;
//	}
//}
