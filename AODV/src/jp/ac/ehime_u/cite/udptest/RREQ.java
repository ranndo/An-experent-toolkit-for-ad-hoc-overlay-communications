package jp.ac.ehime_u.cite.udptest;
/* �����������̃L�[���[�h�u###�v */

import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import android.util.Log;

public class RREQ {
	// RREQ���b�Z�[�W�̃t�B�[���h	�ȉ�[�t�H�[�}�b�g��̈ʒu(�o�C�g)]�A���
//	byte type;		// [0] ���b�Z�[�W�^�C�v
//	byte flag;		// [1] �擪5�r�b�g�̂݃t���O(JRGDU)�A�c���0
//					//	Join�t���O�F�}���`�L���X�g�p
//					//	Repair�t���O�G�}���`�L���X�g�p
//					//	Gratuitous RREP�t���O : ����IP�A�h���X�t�B�[���h�ɂ���Ďw�肳�ꂽ�m�[�h�ցAGratuitous RREP�����j�L���X�g���邩�ǂ���������
//					//	Destination Only�t���O : ����m�[�h����������RREQ�ɑ΂��ĕԐM���邱�Ƃ�����
//					//	���m�V�[�P���X�ԍ� : ����V�[�P���X�ԍ����m���Ă��Ȃ����Ƃ�����
//	byte reserved;	// [2] �\��ς݁F0�Ƃ��đ��M����A�g�p���Ȃ�
//	byte hopCount;	// [3] �z�b�v��
//	int RREQ_ID;	// [4-7] ���M���m�[�h��IP�A�h���X�ƂƂ��Ɏ�M�������ARREQ�����ʂ��邽�߂̃V�[�P���X�ԍ�
//	byte[] toIpAdd;		// [8-11] ���Đ�m�[�h��IP�A�h���X
//	int toSeqNum;		// [12-15] ����m�[�h�ւ̌o�H�ɂ����āA���M���m�[�h�ɂ���ĉߋ��̎�M�����ŐV�̃V�[�P���X�ԍ�
//	byte[] fromIpAdd;	// [16-19] ���M���m�[�h��IP�A�h���X
//	int fromSeqNum;		// [20-23] ���M���m�[�h�ւ̌o�H�ɂ����ė��p����錻�݂̃V�[�P���X�ԍ�
//	int timeToLive;		// [24-27] ��������TTL�A���ԃm�[�h���c�肢���܂ŋ�����
//	String message;		// [28-??]*�ʏ�ł͎g�p���Ȃ��B�u���[�h�L���X�g���b�Z�[�W�̏ꍇ�A���b�Z�[�W��t��

	// RREQ���b�Z�[�W�̑��M
	// �����F���M��(String�^)
	public static void send(byte[] destination_address, byte[] myAddress, boolean flagJ ,boolean flagR ,boolean flagG ,boolean flagD ,boolean flagU
			,int toSeq ,int fromSeq,int ID,int TTL,String text,AODV_Service binder) {

		// �e�t�B�[���h�̏�����
		byte type = 1;	// RREQ������
		// �e�t���O��1�o�C�g�̐擪5�r�b�g�ɔ[�߂�
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

		// UDP�p�P�b�g�Ɋ܂߂�f�[�^
		byte[] sendBuffer = null;

		// �ŏI���悪�u���[�h�L���X�g�A�h���X�Ȃ�
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

            System.out.println("RREQ���b�Z�[�W�𑗐M���܂���");	//###�f�o�b�O�p###
          
        	binder.writeLog(11,getStringByByteAddress(myAddress),getStringByByteAddress(myAddress),getStringByByteAddress(destination_address), 0,fromSeqNum);
        }
    }

	/***** RREQ���b�Z�[�W�̓]�� *****/
	public static void send2(byte[] data,AODV_Service binder){
		if(binder != null){
			binder.send(data, RREQ.getByteAddress("255.255.255.255"), 28);
		
	    	System.out.println("RREQ���b�Z�[�W��]�����܂���");	//###�f�o�b�O�p###
		}
	}

	// ��M����RREQ���b�Z�[�W�����g�̃m�[�h���̂��̂����ׂ�
	// �����FRREQ���b�Z�[�W
	public static boolean isToMe(byte[] receiveBuffer, byte[] myAddress){
		// ����IP�A�h���X�̃R�s�[����쐬
		byte[] toIpAdd = new byte[4];

		// �Y�������𔲂��o��
		System.arraycopy(receiveBuffer,8,toIpAdd,0,4);

		if(Arrays.equals(toIpAdd,myAddress))
				return true;
		else return false;
	}

	// RREQ���b�Z�[�W����J�t�B�[���h��Ԃ�
	public static boolean getFlagJ(byte[] RREQMes){
		if( (RREQMes[1]&(2<<6)) !=0)
			return true;
		else return false;
	}
	// RREQ���b�Z�[�W����R�t�B�[���h��Ԃ�
	public static boolean getFlagR(byte[] RREQMes){
		if( (RREQMes[1]&(2<<5)) !=0)
			return true;
		else return false;
	}
	// RREQ���b�Z�[�W����G�t�B�[���h��Ԃ�
	public static boolean getFlagG(byte[] RREQMes){
		if( (RREQMes[1]&(2<<4)) !=0)
			return true;
		else return false;
	}
	// RREQ���b�Z�[�W����D�t�B�[���h��Ԃ�
	public static boolean getFlagD(byte[] RREQMes){
		if( (RREQMes[1]&(2<<3)) !=0)
			return true;
		else return false;
	}
	// RREQ���b�Z�[�W����U�t�B�[���h��Ԃ�
	public static boolean getFlagU(byte[] RREQMes){
		if( (RREQMes[1]&(2<<2)) !=0)
			return true;
		else return false;
	}
	// RREQ���b�Z�[�W����hopCount�t�B�[���h��Ԃ�
	public static byte getHopCount(byte[] RREQMes){
		return RREQMes[3];
	}
	// RREQ���b�Z�[�W����RREQ_ID�t�B�[���h��Ԃ�
	public static int getRREQ_ID(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,4,buf,0,4);

		// int�^�ɕϊ�
		return byteToInt(buf);
	}
	// RREQ���b�Z�[�W����toIpAdd�t�B�[���h��Ԃ�
	public static byte[] getToIpAdd(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,8,buf,0,4);

		return buf;
	}
	// RREQ���b�Z�[�W����toSeqNum�t�B�[���h��Ԃ�
	public static int getToSeqNum(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,12,buf,0,4);

		// int�^�ɕϊ�
		return byteToInt(buf);
	}
	// RREQ���b�Z�[�W����fromoIpAdd�t�B�[���h��Ԃ�
	public static byte[] getFromIpAdd(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,16,buf,0,4);

		return buf;
	}
	// RREQ���b�Z�[�W����fromSeqNum�t�B�[���h��Ԃ�
	public static int getFromSeqNum(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,20,buf,0,4);

		// int�^�ɕϊ�
		return byteToInt(buf);
	}
	// RREQ���b�Z�[�W����TTL�t�B�[���h��Ԃ�
	public static int getTimeToLive(byte[] RREQMes){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[4];
		System.arraycopy(RREQMes,24,buf,0,4);

		// int�^�ɕϊ�
		return byteToInt(buf);
	}
	// RREQ���b�Z�[�W���烁�b�Z�[�W�t�B�[���h��Ԃ�
	public static byte[] getMessage(byte[] RREQMes, int length){

		// �Y��������byte[]�𔲂��o��
		byte[] buf = new byte[length-28];
		System.arraycopy(RREQMes,28,buf,0,length-28);

		// int�^�ɕϊ�
		return buf;
	}

	// RREQ���b�Z�[�W�̑��M���V�[�P���X�ԍ��t�B�[���h���Z�b�g���ĕԂ�
	public static byte[] setFromSeqNum(byte[] RREQMes,int num){
		// �ύX����ԍ���byte[]�^��
		byte[] seq = intToByte(num);

		// ���M���V�[�P���X�ԍ��̕����ɏ㏑��
		System.arraycopy(seq,0,RREQMes,20,4);
		return RREQMes;
	}
	// RREQ���b�Z�[�W��TTL���Z�b�g���ĕԂ�
	public static byte[] setTimeToLive(byte[] RREQMes,int num){
		// �ύX����ԍ���byte[]�^��
		byte[] TTL = intToByte(num);

		// ���M���V�[�P���X�ԍ��̕����ɏ㏑��
		System.arraycopy(TTL,0,RREQMes,24,4);
		return RREQMes;
	}

	// int�^��byte[]�^�֕ϊ�
	public static byte[] intToByte(int num){

		// �o�C�g�z��ւ̏o�͂��s���X�g���[��
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		// �o�C�g�z��ւ̏o�͂��s���X�g���[����DataOutputStream�ƘA������
		DataOutputStream out = new DataOutputStream(bout);

		try{	// ���l���o��
			out.writeInt(num);
		}catch(Exception e){
				System.out.println(e);
		}

		// �o�C�g�z����o�C�g�X�g���[��������o��
		byte[] bytes = bout.toByteArray();
		return bytes;
	}

	// byte[]�^��int�^�֕ϊ�
	public static int byteToInt(byte[] num){

		int value = 0;
		// �o�C�g�z��̓��͂��s���X�g���[��
		ByteArrayInputStream bin = new ByteArrayInputStream(num);

		// DataInputStream�ƘA��
		DataInputStream in = new DataInputStream(bin);

		try{	// int��ǂݍ���
			value = in.readInt();
		}catch(IOException e){
			System.out.println(e);
		}
		return value;
	}

	// String�^�̃A�h���X��byte[]�^�ɕϊ�
	public static byte[] getByteAddress(String str){

		// ����
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}

	// IP�A�h���X(byte�z��)���當����(��:"127.0.0.1")�֕ϊ�
	public static String getStringByByteAddress(byte[] ip_address){

		if(ip_address.length != 4){
			return "Erorr_RouteIpAddress_is_not_correct";
		}

		// byte�𕄍����������ɕϊ�
		// ���Ȃ�+256
		int[] unsigned_b = new int[4];
		for(int i=0;i<4;i++){
			if(ip_address[i] >= 0){
				// 0�ȏ�Ȃ炻�̂܂�
				unsigned_b[i] = ip_address[i];
			}
			else{
				unsigned_b[i] = ip_address[i]+256;
			}
		}
		return unsigned_b[0]+"."+unsigned_b[1]+"."+unsigned_b[2]+"."+unsigned_b[3];
	}




}
