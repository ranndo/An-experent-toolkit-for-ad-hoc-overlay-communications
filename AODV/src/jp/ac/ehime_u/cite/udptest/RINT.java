package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;

import android.util.Log;

// RemoteIntent_withData
public class RINT {

	/*** ���b�Z�[�W�t�H�[�}�b�g [����]***/
	byte type;	// [0] ���b�Z�[�W�^�C�v 5���g�p
	byte[] destination_address;	// [1-4]	����A�h���X
	byte[] source_address;		// [5-8]	���M���A�h���X
	byte flag;					// [9]		�e�p�����[�^�̗L���t���O
	String package_name;
	String intent_action;
	int intent_flags;
	String intent_type;
	String intent_scheme;
	List<String> intent_categories;
	byte[] data;				// [??-??]	�f�[�^

	// �萔
	public static final byte FLAG_PACKAGE_NAME	= 0x01;
	public static final byte FLAG_INTENT_ACTION = 0x02;
	public static final byte FLAG_INTENT_FLAGS	= 0x04;
	public static final byte FLAG_INTENT_TYPE   = 0x08;
	public static final byte FLAG_INTENT_SCHEME = 0x10;
	public static final byte FLAG_INTENT_CATEGORIES = 0x20;
	public static final byte FLAG_RTT = 0x40;
	public static final byte FLAG_PUT = (byte)0x80;

	// ��M�o�b�t�@�̃f�R�[�h
	public RINT(){}
	public RINT(byte[] receive_data){
		type = 5;

		destination_address = new byte[4];	// ����A�h���X�̔����o��
		System.arraycopy(receive_data,1,destination_address,0,4);

		source_address = new byte[4];	// ���M���A�h���X�̔����o��
		System.arraycopy(receive_data,5,source_address,0,4);

		flag = receive_data[9];
		// �t���O���
		boolean flag_package_name 		= ((flag & FLAG_PACKAGE_NAME) != 0)? true:false;
		boolean flag_intent_action 		= ((flag & FLAG_INTENT_ACTION) != 0)? true:false;
		boolean flag_intent_flags 		= ((flag & FLAG_INTENT_FLAGS) != 0)? true:false;
		boolean flag_intent_type		= ((flag & FLAG_INTENT_TYPE) != 0)? true:false;
		boolean flag_intent_scheme 		= ((flag & FLAG_INTENT_SCHEME) != 0)? true:false;
		boolean flag_intent_categories	= ((flag & FLAG_INTENT_CATEGORIES) != 0)? true:false;
		boolean flag_rtt 				= ((flag & FLAG_RTT) != 0)? true:false;

		// Log.d
//		Log.d("RINT","flag_package_name "+ flag_package_name);
//		Log.d("RINT","flag_intent_action "+ flag_intent_action);
//		Log.d("RINT","flag_intent_flags "+ flag_intent_flags);
//		Log.d("RINT","flag_intent_types "+ flag_intent_type);q
//		Log.d("RINT","flag_intent_scheme "+ flag_intent_scheme);
//		Log.d("RINT","flag_intent_categories "+ flag_intent_categories);
//		Log.d("RINT","flag_log "+ flag_log);

		// �t���O�ɏ]���Ċe�p�����[�^���擾
		int index = 10;
		byte next_length;
		byte[] b_string;

		if(flag_package_name){
			next_length = receive_data[index];	// ��������ǂݍ���
			index++;

			b_string = new byte[next_length];	// ��U�Cbyte�^�ɂȂ��Ă��镶�����ǂݍ���
			System.arraycopy(receive_data, index, b_string, 0, next_length);
			index += next_length;

			package_name = new String(b_string);// ������ɖ߂�
		}else{
			package_name = null;
		}

		if(flag_intent_action){
			next_length = receive_data[index];	// ��������ǂݍ���
			index++;

			b_string = new byte[next_length];	// ��U�Cbyte�^�ɂȂ��Ă��镶�����ǂݍ���
			System.arraycopy(receive_data, index, b_string, 0, next_length);
			index += next_length;
			
			intent_action = new String(b_string);// ������ɖ߂�
			
		}else{
			intent_action = null;
		}

		if(flag_intent_flags){
			b_string = new byte[4];
			System.arraycopy(receive_data, index, b_string, 0, 4);
			index += 4;

			// byte[4] -> int
			intent_flags = byteToInt(b_string);
		}else{
			intent_flags = 0;
		}

		if(flag_intent_type){
			next_length = receive_data[index];	// ��������ǂݍ���
			index++;

			b_string = new byte[next_length];	// ��U�Cbyte�^�ɂȂ��Ă��镶�����ǂݍ���
			System.arraycopy(receive_data, index, b_string, 0, next_length);
			index += next_length;

			intent_type = new String(b_string);// ������ɖ߂�
		}else{
			intent_type = null;
		}

		if(flag_intent_scheme){
			next_length = receive_data[index];	// ��������ǂݍ���
			index++;

			b_string = new byte[next_length];	// ��U�Cbyte�^�ɂȂ��Ă��镶�����ǂݍ���
			System.arraycopy(receive_data, index, b_string, 0, next_length);
			index += next_length;

			intent_scheme = new String(b_string);// ������ɖ߂�
		}else{
			intent_scheme = null;
		}

		if(flag_intent_categories){
			// �J�e�S�������擾
			byte number_of_categories = receive_data[index];
			index++;

			for(int i=0;i<number_of_categories;i++){
				next_length = receive_data[index];	// ��������ǂݍ���
				index++;

				b_string = new byte[next_length];	// ��U�Cbyte�^�ɂȂ��Ă��镶�����ǂݍ���
				System.arraycopy(receive_data, index, b_string, 0, next_length);
				index += next_length;

				intent_categories.add(new String(b_string));// ������ɖ߂�
			}
		}else{
			intent_categories = null;
		}

		// data���t������Ă���Ȃ�
		if(receive_data.length > index){
			data = new byte[receive_data.length - index];
			System.arraycopy(receive_data, index, data, 0, data.length);
		}else{
			data = null;
		}

	}

	// *** ��������get���\�b�h ***
	protected byte[] getDestinationAddress(){
		return destination_address;
	}
	protected byte[] getSourceAddress(){
		return source_address;
	}
	protected byte getFlag(){
		return flag;
	}
	protected boolean getFlagRTT(){
		return (flag & FLAG_RTT) != 0 ;
	}
	public String getPackageName(){
		return package_name;
	}
	public String getIntentAction(){
		return intent_action;
	}
	public int getIntentFlags(){
		return intent_flags;
	}
	protected String getIntentType(){
		return intent_type;
	}
	protected String getIntentScheme(){
		return intent_scheme;
	}
	protected List<String> getIntentCategories(){
		return intent_categories;
	}
	protected byte[] getData(){
		return data;
	}


	// int�^��byte[]�^�֕ϊ�
	public byte[] intToByte(int num){

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
	public int byteToInt(byte[] num){

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
}
