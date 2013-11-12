package jp.ac.ehime_u.cite.udptest;

import java.net.*;
import java.io.*;
import java.util.*;

// RREP��M����ACK��Ԃ�
// �Е��������N�̌��m�ɗp����
public class RREP_ACK {

	// �t�H�[�}�b�g [����]�̓o�C�g�񒆂̈ʒu������
	byte type;		// [0] 4:RREP_ACK������
	byte reserved;	// [1] ��o�C�g,�g�p����Ȃ�(�g���p)

	// RREP_ACK�̑��M
	// ����1: ACK��Ԃ�����m�[�h�̃A�h���X
	// ����2: port�ԍ�
	public void send(InetAddress destination_inet,int port){
		send(destination_inet.getAddress(), port);
	}
	public void send(byte[] address,int port){

		type = 4;
		reserved = 0;

		// ���M�o�C�g��
		byte[] send_buffer = new byte[2];

		send_buffer[0] = type;
		send_buffer[1] = reserved;

		SendByteArray.send(send_buffer, address);
        System.out.println("RREP_ACK���b�Z�[�W�𑗐M���܂���");	//###�f�o�b�O�p###
	}
}