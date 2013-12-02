package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class ReceiveProcess {
	// ��M�������𑀍삷�邽�߃C���X�^���X�����Ă���
	public static RREP RRep = new RREP();
	public static RERR RErr = new RERR();
//	public static FSEND FSend = new FSEND();
//	public static FREQ FReq = new FREQ();

	// �t�@�C����M
//	public static ArrayList<FileReceivedManager> file_received_manager = new ArrayList<FileReceivedManager>();
	
	// ��M����
	public static void process(byte[] receiveBuffer,byte[] preHopAddress, boolean bluetoothFlag, byte[] my_address, Context context, AODV_Service mAODV_Service){
		boolean bload_cast_flag = false;
		
		Log.d("debug_receive","length:"+receiveBuffer.length);
		
		switch (receiveBuffer[0]) {

		case 1:	// RREQ
			// RREQ�̎�M�����̒��ŁA�Â�����A�h���X�̍폜
			mAODV_Service.removeOldRREQ_List();

			// ��M�������X�g�̒��̏��ƁARREQ_ID,���M������v����΃��b�Z�[�W�𖳎�
			if( mAODV_Service.RREQ_ContainCheck( RREQ.getRREQ_ID(receiveBuffer), RREQ.getFromIpAdd(receiveBuffer))){
				// "�d������RREQ���b�Z�[�W�̂��ߖ������܂�\n");
				break;
			}

			// ����ɁABlackList�ɑ΂��Ă����l�ɌÂ�����A�h���X�̍폜
			if( mAODV_Service.getBlackListSize() > 0 ){	// ��łȂ����
				// �e�������Ԃ��`�F�b�N
				for(int i=0;i<mAODV_Service.getBlackListSize();i++){
					if(mAODV_Service.getBlackData(i).life_time < new Date().getTime()){
						mAODV_Service.removeBlackData(i);
					}
				}
			}

			// �u���b�N���X�g�ɑO�z�b�v�m�[�h�̃A�h���X���܂܂�Ă���΁A���b�Z�[�W�𖳎�
			if( mAODV_Service.searchInBlackList(preHopAddress) != -1){
				// �u���b�N���X�g(�Е��������N)�����RREQ���b�Z�[�W�̂��ߖ������܂�
				break;
			}

			// �z�b�v��+1,
			receiveBuffer[3]++;

			// DB�֋L�^
			mAODV_Service.writeLog(13, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer)),
					AODV_Service.getStringByByteAddress(RREQ.getToIpAdd(receiveBuffer)), receiveBuffer[3], RREQ.getFromSeqNum(receiveBuffer));

			// ��M����RREQ���b�Z�[�W�̏���List�ɒǉ�
			// ������RREQ_ID,���M���A�h���X
			mAODV_Service.newPastRREQ(RREQ.getRREQ_ID(receiveBuffer),RREQ.getFromIpAdd(receiveBuffer));


			// �t�o�H���A�������ԒZ�߂ŋL�^
			// �������Ԃ́A���Ɍo�H������Ȃ炻�̐������Ԃ����̂܂܁A�܂���MinimalLifeTime�̑傫���ق��ɃZ�b�g
			int index;
			long life;
			long MinimalLifeTime = new Date().getTime()+ 2*AODV_Service.NET_TRAVERSAL_TIME
				- 2 * RREQ.getHopCount(receiveBuffer) * AODV_Service.NODE_TRAVERSAL_TIME;
			byte state_flag;

			if( (index = mAODV_Service.searchToAdd(RREQ.getFromIpAdd(receiveBuffer))) != -1){
				life = (mAODV_Service.getRoute(index).lifeTime > MinimalLifeTime)? mAODV_Service.getRoute(index).lifeTime:MinimalLifeTime;
			}
			else{
				life = MinimalLifeTime;
			}

			// �V�[�P���X�ԍ������l�ɁA�����o�H�̒l�Ǝ�MRREQ���̒l���r���č����ق�
			// �܂��A�����o�H�̂ق��������ꍇ�A��M����RREQ���̒l�����̒l�ɕύX
			if(index != -1){
				if(mAODV_Service.getRoute(index).toSeqNum > RREQ.getFromSeqNum(receiveBuffer)){
    				receiveBuffer = RREQ.setFromSeqNum(receiveBuffer, mAODV_Service.getRoute(index).toSeqNum);
				}
			}

			// �o�H��Ԃ͊����o�H������Ȃ炻�̂܂܁A�Ȃ���Έꎞ�o�H
			if(index != -1){
				state_flag = mAODV_Service.getRoute(index).stateFlag;
			}
			else if(RREQ.getFlagG(receiveBuffer)){	// ### G�t���O�������Ă���Ȃ琳���o�H�Ƃ��č쐬 ###
				state_flag = 1;
			}
			else{
				state_flag = 5;
			}

			Log.d("AODV_type1","\t�t�o�H�쐬:"+AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer))+
					"��"+"("+AODV_Service.getStringByByteAddress(preHopAddress)+"�o�R)\n");
			// ���ɋt�o�H������Ȃ�㏑��set�A�Ȃ���Βǉ�add
			if(index != -1){
				// �o�H��Ԃ͊����̂܂�
				mAODV_Service.setRoute(index, new RouteTable( RREQ.getFromIpAdd(receiveBuffer), RREQ.getFromSeqNum(receiveBuffer)
    					, true, state_flag, bluetoothFlag, RREQ.getHopCount(receiveBuffer), preHopAddress, life
    					, new HashSet<byte[]>() ));
			}
			else{
				// �o�H��Ԃ͈ꎞ�I�Ȍo�H(5)
				mAODV_Service.addRoute( new RouteTable( RREQ.getFromIpAdd(receiveBuffer), RREQ.getFromSeqNum(receiveBuffer)
    					, true, state_flag, bluetoothFlag, RREQ.getHopCount(receiveBuffer), preHopAddress, life
    					, new HashSet<byte[]>() ));
			}

			// �u���[�h�L���X�g���b�Z�[�W�H
			bload_cast_flag = Arrays.equals(RREQ.getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS)
					, RREQ.getToIpAdd(receiveBuffer));

			// RREQ���b�Z�[�W�̓��e�`�F�b�N
			if(RREQ.isToMe(receiveBuffer, my_address) || bload_cast_flag){

				// RREQ:�������̃��b�Z�[�W �܂��� �u���[�h�L���X�g���b�Z�[�W�̂Ƃ�
				Log.d("AODV_type1","RREP��O�z�b�v�m�[�h" + preHopAddress + "�Ƀ��j�L���X�g���܂�");

				// �ԐM�O�ɁA�V�[�P���X�ԍ��̍X�V
				if( RREQ.getToSeqNum(receiveBuffer) == mAODV_Service.getSeqNum()+1){
					mAODV_Service.setSeqNum(mAODV_Service.getSeqNum()+1);
				}

				// RREP�̕ԐM
				RRep.reply(preHopAddress, RREQ.getFromIpAdd(receiveBuffer), my_address
						,(byte)0 ,mAODV_Service.getSeqNum() , AODV_Service.MY_ROUTE_TIMEOUT, mAODV_Service);

			}
			if(!RREQ.isToMe(receiveBuffer, my_address) || bload_cast_flag ){

				// RREQ:�������łȂ����b�Z�[�W �܂��� �u���[�h�L���X�g���b�Z�[�W�̂Ƃ�
				Log.d("AODV_type1", "RREQ:�������̃��b�Z�[�W�ł͂���܂���");

				// ����܂ł̗L���o�H�������Ă��邩����
				index = mAODV_Service.searchToAdd(RREQ.getToIpAdd(receiveBuffer));

				// �o�H��m���Ă��āAD�t���O���I�t�Ȃ�
				if( (index != -1) && (!RREQ.getFlagD(receiveBuffer))){
					if( mAODV_Service.getRoute(index).stateFlag == 1
							&& mAODV_Service.getRoute(index).toSeqNum > RREQ.getToSeqNum(receiveBuffer)
							&& !RREQ.getFlagD(receiveBuffer)){

    					// �o�H�����m�Ȓ��ԃm�[�h�Ȃ̂ŁARREP��ԐM");

    					// �o�H��precursorList�ɒǉ�
    					// �܂����o�H�̍X�V
    					// ���o�H��precursorList�ɁA�t�o�H�̎��z�b�v��ǉ��B�G���[����RERR��`����m�[�h
    					// ���ł�List�Ɋ܂܂�Ă��Ă��AHashSet�ɂ��d���͔F�߂��Ȃ��̂�OK
    					RouteTable route = mAODV_Service.getRoute(index);		// ��U���X�g����o��
    					route.preList.add(preHopAddress);		// ��������
    					mAODV_Service.setRoute(index, route);		// ���X�g�ɏ㏑��

    					// ���ɋt�o�H�̍X�V
    					// �t�o�H��precursorList�ɁA���o�H�̎��z�b�v��ǉ��B�G���[����RERR��`����m�[�h
    					int index2 = mAODV_Service.searchToAdd(RREQ.getFromIpAdd(receiveBuffer));
    					route = mAODV_Service.getRoute(index2);
    					route.preList.add(mAODV_Service.getRoute(index).nextIpAdd);


    					// G�t���O���Z�b�g����Ă���΁A�t�o�H�̏�Ԃ�L���ɂ��A�t�����o�H���m��
    					// �܂��A����m�[�h�ɂ�RREP���K�v
    					// (�ǂ�����o�����Ɍo�H���m�����邽�߂ɗp����)
    					if(RREQ.getFlagG(receiveBuffer)){

    						// ����͏��o�H�̎��z�b�v
    						InetAddress str = null;
							try {
								str = InetAddress.getByAddress(mAODV_Service.getRoute(index).nextIpAdd);
							} catch (UnknownHostException e) {
								// TODO �����������ꂽ catch �u���b�N
								e.printStackTrace();
							}

    						// �L����
    						route.stateFlag =1;

    						// G_RREP�̑��M
        					RRep.reply(str, RREQ.getToIpAdd(receiveBuffer),
        							RREQ.getFromIpAdd(receiveBuffer),
        							mAODV_Service.getRoute(index).hopCount, RREQ.getFromSeqNum(receiveBuffer),
        							(int)(mAODV_Service.getRoute(index).lifeTime - new Date().getTime()), mAODV_Service);
    					}

    					// ���X�g�ɏ㏑��
    					mAODV_Service.setRoute(index2, route);

    					// RREP�̕ԐM
    					RRep.reply(preHopAddress, RREQ.getFromIpAdd(receiveBuffer),
    							RREQ.getToIpAdd(receiveBuffer),
    							mAODV_Service.getRoute(index).hopCount, mAODV_Service.getRoute(index).toSeqNum,
    							(int)(mAODV_Service.getRoute(index).lifeTime - new Date().getTime()), mAODV_Service);

					}
				}
				// =����܂ł̗L���o�H�������Ă��Ȃ��ꍇ�A�܂��͎����Ă���D�t���O���I���̏ꍇ
				else{
					// TTL�����炷�^�C�~���O�͂��̌�ɂ��Ă���̂ŁA��r��1�ȉ�
					// ���ԕς��Ă��ʂɂ����C������
					if(RREQ.getTimeToLive(receiveBuffer)<=1){
						Log.d("AODV_type1","TTL��0�ȉ��Ȃ̂œ]�����܂���");
					}
					else{
						// �����𖞂����΁A���p���邽�߃u���[�h�L���X�g
						// ������TTL--;
						Log.d("AODV_type1","RREQ���b�Z�[�W��]��");
						receiveBuffer = RREQ.setTimeToLive(receiveBuffer, RREQ.getTimeToLive(receiveBuffer)-1);

						RREQ.send2(receiveBuffer,mAODV_Service);

						mAODV_Service.writeLog(12, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RREQ.getFromIpAdd(receiveBuffer)),
								AODV_Service.getStringByByteAddress(RREQ.getToIpAdd(receiveBuffer)), receiveBuffer[3], RREQ.getFromSeqNum(receiveBuffer));
	        		}
				}
			}

			// �u���[�h�L���X�g���b�Z�[�W�łȂ����,RREQ�̏����݂̂ŏI��
			if(!bload_cast_flag){
				break;
			}
		// �u���[�h�L���X�g���b�Z�[�W�Ȃ�A��������case0�̏���
    	case 0: // �ʐM���肩��̃��b�Z�[�W
			Log.d("AODV_type0","receive0");
    		// �������̃��b�Z�[�W�Ȃ�
    		if( isToMe(receiveBuffer,my_address) || bload_cast_flag){
    			Log.d("AODV_type0","To_me_message");

    			// "���M��:�����Ă����e�L�X�g"��View�ɒǉ�
    			final String src = AODV_Service.getStringByByteAddress(getAddressSrc(receiveBuffer));
    			final String mes = new String(getMessage(receiveBuffer, receiveBuffer.length));

    			// final String s2= "prev_hop_is:"+AODV_Activity.getStringByByteAddress(cAddr.getAddress());
    			
    			mAODV_Service.appendMessageOnActivity(src+":"+mes+"\n\r");

				// �ÖٓI�C���e���g�𓊂���v������"TASK:"�Ȃ�
				if(mes.startsWith("TASK:")){
		            Intent intent = new Intent();
		            intent.setAction(Intent.ACTION_CALL);
		            intent.setData(Uri.parse(mes.replaceFirst("TASK:", "")+"_"+src));	// TASK:�Z�̕������Z�b�g
		            intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
		            intent.putExtra("ID", mAODV_Service.getSendIntentId());
		            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		            context.startActivity(intent);
				}
    		}
    		else{	// ���z�b�v�֓]��
    			// ����܂ł̗L���o�H�������Ă��邩����
				index = mAODV_Service.searchToAdd(getAddressDest(receiveBuffer));

				Log.d("AODV_type0", "deliver_message");

				// �o�H��m���Ă���
				if(index != -1){
					// �L���Ȍo�H�Ȃ�
					if(mAODV_Service.getRoute(index).stateFlag == 1){

						Log.d("AODV_type0", "delivery_start");
						// ���z�b�v�ւ��̂܂ܓ]��
						mAODV_Service.send(cut_byte_spare(receiveBuffer,receiveBuffer.length), mAODV_Service.getRoute(index).nextIpAdd);

	    				Log.d("AODV_type0", "delivery_end");
						break;
					}
				}
				// �L���Ȍo�H�������Ă��Ȃ��ꍇ

				// RERR�̑��M
				RErr.RERR_Sender(mAODV_Service.getRoute(index),mAODV_Service);

    		}

    		break;

		case 2:	//RREP���󂯎�����ꍇ


			// ��M�f�[�^�̃T�C�Y
			int mesLength = receiveBuffer.length;

			// �������g�����RREP�Ȃ疳��
			byte[] local_address = RREQ.getByteAddress("127.0.0.1");

			if( Arrays.equals(RRep.getToIpAdd(receiveBuffer,mesLength),local_address)
					|| Arrays.equals(RRep.getToIpAdd(receiveBuffer,mesLength), my_address)){
				break;
			}

			// Ack�t���O���I���Ȃ�RREP_ACK��Ԃ�
			// Hello���b�Z�[�W�̏ꍇ��false
			if( RRep.getFlagA(receiveBuffer, mesLength)){
				RREP_ACK.send(preHopAddress, mAODV_Service);
			}


			// �z�b�v��++
			receiveBuffer = RRep.hopCountInc(receiveBuffer, mesLength);

			mAODV_Service.writeLog(23, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RRep.getFromIpAdd(receiveBuffer, mesLength, my_address)),
					AODV_Service.getStringByByteAddress(RRep.getToIpAdd(receiveBuffer, mesLength)), RRep.getHopCount(receiveBuffer, mesLength),
					RRep.toSeqNum);

			// RREP�𑗐M���Ă����O�m�[�h��
			// �����̌o�H�̎��z�b�v�Ȃ�A���̌o�H�̐������Ԃ��X�V
			// �i�o�H�̏�Ԃ��L���܂��͖����̂Ƃ��j
			for(int i=0;i<mAODV_Service.getRouteSize();i++){
				RouteTable route = mAODV_Service.getRoute(i);
				if( Arrays.equals((route.nextIpAdd) , preHopAddress)
						&& (route.stateFlag == 1 || route.stateFlag == 2)){
					//Log.d("AODV_RREP","LifeTime before:"+route.lifeTime);

					// ���݂̐������ԂƁAHELLO�̎����r���đ傫�����ɍX�V
					if(route.lifeTime < (AODV_Service.ALLOWED_HELLO_LOSS * AODV_Service.HELLO_INTERVAL)){
						route.lifeTime = AODV_Service.ALLOWED_HELLO_LOSS * AODV_Service.HELLO_INTERVAL;
					}
					// ��Ԃ�L����
					route.stateFlag = 1;

					//Log.d("AODV_RREP","LifeTime after:"+route.lifeTime);
					// �㏑��
					mAODV_Service.setRoute(i,route);
				}
			}

			// HELLO���b�Z�[�W�Ȃ琶�����Ԃ��������邾���ł悢(�Е��������N�Ή�)
			if(RRep.isHelloMessage(mesLength)){
				break;
			}

			// ���o�H�iRREQ���M���ˈ���j�����݂��邩�ǂ�������
			int index2 = mAODV_Service.searchToAdd(RRep.getToIpAdd(receiveBuffer,mesLength));

			// ���݂��Ȃ��ꍇ�A���o�H�̍쐬
			if( index2 == -1 ){
				mAODV_Service.addRoute( new RouteTable( RRep.getToIpAdd(receiveBuffer,mesLength), RRep.getToSeqNum(receiveBuffer,mesLength)
    					, true, (byte)1, bluetoothFlag, RRep.getHopCount(receiveBuffer,mesLength), preHopAddress
    					, RRep.getLifeTime(receiveBuffer,mesLength) + (new Date().getTime())
    					, new HashSet<byte[]>() ));

    			// �o�H���ǉ�����܂���"
			}
			// ���o�H�����݂���ꍇ
			else{
				// �ȉ��̂����ꂩ�̏����𖞂����Ă���ꍇ�A�o�H���X�V����
				// 1.�����o�H�̃V�[�P���X�ԍ��������ł���ƋL�^����Ă���
				// 2.RREP�̈���V�[�P���X�ԍ��������o�H�̔ԍ��ł���A�L��
				// 3.�V�[�P���X�ԍ��������������o�H�������ł���
				// 4.�V�[�P���X�ԍ����������z�b�v���������o�H����������
				if(	(mAODV_Service.getRoute(index2).validToSeqNumFlag == false)
					||(RRep.getHopCount(receiveBuffer, mesLength) > mAODV_Service.getRoute(index2).toSeqNum)
					||( (mAODV_Service.getRoute(index2).stateFlag != 1)
							&&(RRep.getHopCount(receiveBuffer, mesLength) == mAODV_Service.getRoute(index2).toSeqNum))
					||( (RRep.getHopCount(receiveBuffer, mesLength) < mAODV_Service.getRoute(index2).hopCount)
							&&(RRep.getHopCount(receiveBuffer, mesLength) == mAODV_Service.getRoute(index2).toSeqNum)))
				{
					// ���o�H�̏㏑��
					mAODV_Service.setRoute(index2, new RouteTable( RRep.getToIpAdd(receiveBuffer,mesLength), RRep.getToSeqNum(receiveBuffer,mesLength)
    					, true, (byte)1, bluetoothFlag, RRep.getHopCount(receiveBuffer,mesLength), preHopAddress
    					, RRep.getLifeTime(receiveBuffer,mesLength) + (new Date().getTime())
    					, new HashSet<byte[]>() ));
				}
			}

			if(RRep.isToMe(receiveBuffer,mesLength,my_address)){
				// RREP:���M���h�o�A�h���X�������ł�
			}
			else{
				// RREP:���M���h�o�A�h���X�������ł͂���܂���

				// ���o�H������index2���Č����A�X�V
				index2 = mAODV_Service.searchToAdd(RRep.getToIpAdd(receiveBuffer,mesLength));
				// RREQ�����m�[�h�ւ̌o�H�i�t�o�H�j������
				int index3 = mAODV_Service.searchToAdd(RRep.getFromIpAdd(receiveBuffer,mesLength,my_address));
				// ���o�H��PrecursorList�ɁA�t�o�H�ւ̎��z�b�v��ǉ�
				// ���ł�List�Ɋ܂܂�Ă��Ă��AHashSet�ɂ��d���͔F�߂��Ȃ��̂�OK
				RouteTable route = mAODV_Service.getRoute(index2);		// ��U���X�g����o��
				route.preList.add(mAODV_Service.getRoute(index3).nextIpAdd);	// ��������
				mAODV_Service.setRoute(index2, route);					// ���X�g�ɏ㏑��

				// �t�o�H��PrecursorList�ɁA���o�H�̎��z�b�v��ǉ�
				// �܂��A�������Ԃ��X�V
				route = mAODV_Service.getRoute(index3);					// ��U���X�g����o��
				route.preList.add(mAODV_Service.getRoute(index2).nextIpAdd);	// ��������

				// ���݂̐������Ԃƌ��ݎ���+ACTIVE_ROUTE_TIMEOUT�̍ő�l���ɃZ�b�g
				if(route.lifeTime < (new Date().getTime()+AODV_Service.ACTIVE_ROUTE_TIMEOUT)){
					route.lifeTime = new Date().getTime()+AODV_Service.ACTIVE_ROUTE_TIMEOUT;
				}
				mAODV_Service.setRoute(index3, route);					// ���X�g�ɏ㏑��

				// RREP��O�z�b�v�m�[�h�ɓ]��
				// �O�z�b�v�m�[�h�͋t�o�H�̎��z�b�v�Ɠ���
				try {
					RRep.reply2(receiveBuffer, InetAddress.getByAddress(mAODV_Service.getRoute(index3).nextIpAdd),mAODV_Service);
				} catch (UnknownHostException e) {
					// TODO �����������ꂽ catch �u���b�N
					e.printStackTrace();
				}

				mAODV_Service.writeLog(22, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(RRep.getFromIpAdd(receiveBuffer, mesLength, my_address)),
						AODV_Service.getStringByByteAddress(RRep.getToIpAdd(receiveBuffer, mesLength)), RRep.getHopCount(receiveBuffer, mesLength),
    					RRep.toSeqNum);
			}
			break;

		case 3:	// RERR���󂯎�����ꍇ

			// "���̃��b�Z�[�W��RERR�ł�\n");

			// �o�H�̌���
			index = mAODV_Service.searchToAdd(RErr.getIpAdd(receiveBuffer));

			// �o�H�����݂���Ȃ�
			if(index != -1){
				// ���X�g����o���A�t�B�[���h�̍X�V
				RouteTable route = mAODV_Service.getRoute(index);

				// �L���o�H�Ȃ�
				if(route.stateFlag == 1){

    				route.stateFlag = 2;	// ������
    				route.lifeTime  = (new Date().getTime()+AODV_Service.DELETE_PERIOD);	// �폜���Ԃ̐ݒ�
    				if(route.validToSeqNumFlag){	// �V�[�P���X�ԍ����L���Ȃ�
    					route.toSeqNum = RErr.getSeqNum(receiveBuffer);		// ���ݽ�ԍ����X�V
    				}

    				// �ǂ݂������ꏊ�ɏ㏑��
    				mAODV_Service.setRoute(index, route);

					// ���[�J�����y�A���s����z�b�v�����H
					if(route.hopCount <= AODV_Service.MAX_REPAIR_TTL){
						mAODV_Service.localRepair(route,my_address,mAODV_Service);
					}
					else{
						// RERR�̑��M
						new RERR().RERR_Sender(route,mAODV_Service);

						final byte[] destination_address = route.toIpAdd;
						
						mAODV_Service.appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
					}
				}
			}

			break;

		case 4:	// RREP_ACK���󂯎�����ꍇ

			// ACK�v�����X�g��ACK���M���m�[�h�̃A�h���X���c���Ă����
			if(mAODV_Service.containAckDemand(preHopAddress)){
				// ���X�g����폜
				mAODV_Service.removeAckDemand(preHopAddress);
			}

			// �o�H�̌���
			index = mAODV_Service.searchToAdd(preHopAddress);

			// �o�H������Ȃ�
			if(index != -1){
				RouteTable route = mAODV_Service.getRoute(index);

				// ### �ꎞ�o�H��L���o�H�ɂ��A�o�����o�H���m�� ###
				// ### �Е��������N�����݂��Ă���ꍇ�ADELETE_PERIOD�ԁA�s���Ȍo�H���ƂȂ� ###
				if(route.stateFlag == 5){
					route.stateFlag = 1;
					mAODV_Service.setRoute(index, route);
				}
			}

			break;

		case 5: // (�f�[�^�t��)�C���e���g���s���b�Z�[�W���󂯎�����ꍇ
			Log.d("AODV_type5","receive5");
    		// �������̃��b�Z�[�W?
			RINT rint = new RINT(receiveBuffer);
    		boolean to_me_message = Arrays.equals(my_address, rint.getDestinationAddress())
    								||Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress());

			if((RINT.FLAG_PACKAGE_NAME & rint.flag) != 0)
				mAODV_Service.writeLogData(53, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
						AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, rint.package_name);
			else
				mAODV_Service.writeLogData(53, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
						AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, null);
    		if(to_me_message){
    			Log.d("AODV_type5","To_me_message");

    			// "���M��:�����Ă����e�L�X�g"��View�ɒǉ�
//    			final String src = AODV_Activity.getStringByByteAddress(getAddressSrc(receiveBuffer));
//    			final String mes = new String(getMessage(receiveBuffer, packet.getLength()));
//
//    			// final String s2= "prev_hop_is:"+AODV_Activity.getStringByByteAddress(cAddr.getAddress());
//
//				handler.post(new Runnable() {
//					@Override
//					public void run() {
//						editText.append(src+":"+mes+"\n\r");
//						editText.setSelection(editText.getText().toString().length());
//					}
//				});
   				// RINT�ɏ]���C���e���g�𐶐�
	            Intent intent = new Intent();

	            if(rint.getPackageName() != null){
	            	//Log.d("AODV_type5","P "+ rint.getPackageName());
	            	// �p�b�P�[�W��.�N���X������N���X������菜���C�p�b�P�[�W���𓾂�
	            	intent.setClassName( rint.getPackageName().substring(0, rint.getPackageName().lastIndexOf('.'))
	            			,rint.getPackageName());
	            }
	            if(rint.getIntentAction() != null){
	            	//Log.d("AODV_type5","A");
	            	//intent.setAction(rint.getIntentAction());
	            }
	            if(rint.getIntentFlags() != 0){
	            	//Log.d("AODV_type5","F");
	            	intent.setFlags(rint.getIntentFlags());
	            }
	            if(rint.getIntentType() != null){
	            	//Log.d("AODV_type5","T");
	            	intent.setType(rint.getIntentType());
	            }
	            if(rint.getIntentScheme() != null){
	            	//Log.d("AODV_type5","S");
	            	intent.setData(Uri.parse(rint.getIntentScheme()));
	            }
	            if(rint.getIntentCategories() != null){
	            	//Log.d("AODV_type5","C");
	            	List<String> categories_list = rint.getIntentCategories();
	            	for(Iterator<String> ite = categories_list.iterator(); ite.hasNext();){
	            		intent.addCategory(ite.next());
	            	}
	            }
	            if(rint.getData() != null){
	            	//Log.d("AODV_type5","D");
	            	intent.putExtra("DATA", rint.getData());
	            }

	            intent.putExtra("SOURCE_ADDRESS", AODV_Service.getStringByByteAddress(rint.getSourceAddress()));
	            intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
	            intent.putExtra("ID", mAODV_Service.getSendIntentId());
	            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	            context.startActivity(intent);
    		}

    		boolean hop_message = (!Arrays.equals(my_address, rint.getDestinationAddress()))
								|| Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress());
    		if(hop_message){	// ���z�b�v�֓]��
    			// ����܂ł̗L���o�H�������Ă��邩����
				index = mAODV_Service.searchToAdd(rint.getDestinationAddress());

				Log.d("AODV_type5", "deliver_message");

				// �o�H��m���Ă���
				if(index != -1){
					// �L���Ȍo�H�Ȃ�
					if(mAODV_Service.getRoute(index).stateFlag == 1){

						Log.d("AODV_type5", "delivery_start");
						// ���z�b�v�ւ��̂܂ܓ]��
						mAODV_Service.send(receiveBuffer, mAODV_Service.getRoute(index).nextIpAdd);

						if((RINT.FLAG_PACKAGE_NAME & rint.flag) != 0)
							mAODV_Service.writeLogData(52, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
									AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, rint.package_name);
						else
							mAODV_Service.writeLogData(52, AODV_Service.getStringByByteAddress(my_address), AODV_Service.getStringByByteAddress(rint.source_address),
									AODV_Service.getStringByByteAddress(rint.destination_address), rint.data.length, null);
	    				Log.d("AODV_type5", "delivery_end");
						break;
					}
				}
				// �L���Ȍo�H�������Ă��Ȃ��ꍇ

				// RERR�̑��M
				if(!Arrays.equals(getByteAddress(AODV_Service.BLOAD_CAST_ADDRESS), rint.getDestinationAddress()))
					new RERR().RERR_Sender(mAODV_Service.getRoute(index),mAODV_Service);
    		}

			break;

/*    	case 10: // �����t�@�C�����MFSEND
			Log.d("�����t�@�C��FSEND","get");
    		// �������̃��b�Z�[�W�Ȃ�
    		if( FSend.isToMe(receiveBuffer,my_address)){

    			final int packet_seq = FSend.getStepNo(receiveBuffer);		// �p�P�b�g������̔ԍ�(���Ԗڂ̃p�P�b�g��)
    			final int packet_total = FSend.getStepTotal(receiveBuffer);	// �p�P�b�g������

    			int file_name_length = FSend.getFileNameLength(receiveBuffer);	// �t�@�C����(byte)�̒���
    			final String file_name = FSend.getFileName(receiveBuffer, file_name_length);	// �t�@�C����

    			Log.d("debug_FSEND", "receive"+packet_seq+"/"+packet_total+":"+file_name);

    			int r_index;
    			FileReceivedManager frm = new FileReceivedManager(1, null, null);

    			// �Â������M�f�[�^���폜
    			while( (r_index = removeDisconnectedFile()) != -1){	// �Â��f�[�^��������胋�[�v
    				frm = frm.get(r_index);

    				try {
						frm.out.close();
					} catch (IOException e) {
						// TODO �����������ꂽ catch �u���b�N
						e.printStackTrace();
					}
    				try {
						frm.file.close();
					} catch (IOException e) {
						// TODO �����������ꂽ catch �u���b�N
						e.printStackTrace();
					}
    				AODV_Activity.context.deleteFile(frm.file_name);
    				file_received_manager.remove(r_index);
    				Log.d("FILE","no_move_file_removed_for_long_time");
    			}

    			// ������M���̃t�@�C��������
    			r_index = searchSameNameFile(file_name);

    			// �ŏ��̃p�P�b�g�Ȃ�t�@�C���I�[�v��
    			if(packet_seq == 1 && r_index == -1){
    				try {
    					// �V�K�C���X�^���X�Ƃ��Ċe�t�B�[���h�̏�����
    					frm.file_name = file_name;
    					frm.file = AODV_Activity.context.openFileOutput(file_name,
    							AODV_Activity.context.MODE_WORLD_READABLE
    							| AODV_Activity.context.MODE_WORLD_WRITEABLE);
    					frm.out = new BufferedOutputStream(frm.file);
    					frm.receive_file_next_no = 1;
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;

					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
    			}
    			else if(r_index != -1){
    				// �����̏������f�[�^�𒊏o
    				frm = frm.get(r_index);
    			}else{
    				break;
    			}

    			// �����������Ŏ�M�����Ȃ�t�@�C���Ƀf�[�^����������
    			if(packet_seq == frm.receive_file_next_no){
    				Log.d("debug_FSEND", "this_written");
    				try {
						frm.out.write(FSend.getFileData(receiveBuffer, file_name_length, receiveBuffer.length));
					} catch (IOException e) {
						// TODO �����������ꂽ catch �u���b�N
						e.printStackTrace();
					}

    				frm.receive_file_next_no++;

    				if( (((packet_seq*100)/packet_total) %10) == 0){
			    		handler.post(new Runnable() {
			    			@Override
			    			public void run() {
			    				// 10%���ƂɌo�߂��o��
			    				editText.append(file_name+":\t"+((packet_seq*100)/packet_total)+"% received\n");
			    				editText.setSelection(editText.getText().toString().length());
			    			}
	    				});
    				}
    			}

    			// ���̃p�P�b�g�𑗐M���ɗv��
    			// �ŏI�p�P�b�g��M��ł��I���ʒm�̂��߂ɕK�v
	    		if(frm.file != null)
	    			FReq.file_req(preHopAddress, my_address, FSend.getAddressSrc(receiveBuffer), frm.receive_file_next_no, file_name, port);

    			// ����ɁA��M�p�P�b�g���Ō�̃p�P�b�g�Ȃ�f�[�^�������݂��I��
    			if(frm.receive_file_next_no == (packet_total+1)){
    				
    				try {
    					frm.out.flush();
        				frm.out.close();
						frm.file.close();
					} catch (IOException e) {
						// TODO �����������ꂽ catch �u���b�N
						e.printStackTrace();
					}

    				frm.out = null;
    				frm.file = null;

    				// �폜
    				if(r_index != -1){ // ���p�P�b�g=�ŏI�p�P�b�g�łȂ����
    					frm.remove(r_index);
    				}

    				if( file_name.endsWith(".jpg") || file_name.endsWith(".jpeg")){
	    				// �����摜�t�@�C���Ȃ�ÖٓI�C���e���g�𓊂���
	    	            Intent intent = new Intent();
	    	            intent.setAction(Intent.ACTION_VIEW);
	    	            intent.setData(Uri.parse("Files:"+frm.file_name));
	    	            intent.putExtra("SOURCE_ADDRESS",
	    	            		AODV_Activity.getStringByByteAddress(FSend.getAddressSrc(receiveBuffer)));
	    	            intent.putExtra("PACKAGE", "jp.ac.ehime_u.cite.udptest");
	    	            intent.putExtra("ID", send_intent_id);
	    	            AODV_Activity.context.startActivity(intent);

	    	            send_intent_id++;
    				}
    			}
    			else{
    				if(r_index == -1){	// ���p�P�b�g�Ȃ�add
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;
    					frm.add();
    				}
    				else{
    					frm.life_time = new Date().getTime() + AODV_Activity.ACTIVE_ROUTE_TIMEOUT*2;
    					frm.set(r_index);
    				}
    			}
    		}
    		else{	// ���z�b�v�֓]��
    			// ����܂ł̗L���o�H�������Ă��邩����
				int index1 = AODV_Activity.searchToAdd(FSend.getAddressDest(receiveBuffer));

				// �o�H��m���Ă���
				if(index1 != -1){
					// �L���Ȍo�H�Ȃ�
					if(AODV_Activity.getRoute(index1).stateFlag == 1){

						// ���z�b�v�ւ��̂܂ܓ]��
						sendMessage(receiveBuffer, AODV_Activity.getRoute(index1).nextIpAdd);

						break;
					}
				}
				// �L���Ȍo�H�������Ă��Ȃ��ꍇ

				// RERR�̑��M?
				RouteManager.RERR_Sender(AODV_Activity.getRoute(index1),port);
    		}

    		break;

    	case 11: // �����t�@�C���v��FREQ

    		// ###

    		// �������̃��b�Z�[�W�Ȃ�
    		if( FReq.isToMe(receiveBuffer,my_address)){

    			// ���M���܂ł̗L���o�H�������Ă��邩����
				int index1 = AODV_Activity.searchToAdd(FReq.getAddressSrc(receiveBuffer));

				// �o�H��m���Ă���
				if(index1 != -1){
					final RouteTable route = AODV_Activity.getRoute(index1);

					// �L���Ȍo�H�Ȃ�
					if(route.stateFlag == 1){

						// �v���t�@�C����,�V�[�P���X�ԍ�,���M���𔲂��o��
						String file_name = FReq.getFileName(receiveBuffer, receiveBuffer.length);
						int req_no = FReq.getStepNextNo(receiveBuffer);
						byte[] source_address = FReq.getAddressSrc(receiveBuffer);

						// �ߋ��̑��M�o�߂�����
						FileManager files = AODV_Activity.searchProgress(file_name, source_address);

						if(files == null){ // ����܂łɑ��M�L�^���Ȃ��Ƃ�
							// �t�@�C���I�[�v��
							try {
								files = new FileManager(file_name,source_address,my_address,AODV_Activity.context);
							} catch (FileNotFoundException e) {
								// TODO �����������ꂽ catch �u���b�N
								e.printStackTrace();
							}
						}

						// ���p�P�b�g�̏����ԍ�����v����ꍇ
						if(files.file_next_no == req_no){

							// �����Ō�̃p�P�b�g�̉����ʒm�Ȃ�
							if(files.total_step < req_no){
								// �t�@�C���N���[�Y����
								if( files.file_in != null){
									try {
										files.file_in.close();
									} catch (IOException e) {
										// TODO �����������ꂽ catch �u���b�N
										e.printStackTrace();
									}
								}
								if( files.file != null){
									try {
										files.file.close();
									} catch (IOException e) {
										// TODO �����������ꂽ catch �u���b�N
										e.printStackTrace();
									}
								}
								files.remove();
					    		handler.post(new Runnable() {
					    			@Override
					    			public void run() {
					    				// 10%���ƂɌo�߂��o��
					    				editText.append("fileSend_completed!\n");
					    				editText.setSelection(editText.getText().toString().length());
					    			}
			    				});



			    				Date date = new Date();
			    				SimpleDateFormat sdf = new SimpleDateFormat("MMdd'_'HHmmss");

			    				String log = "time:"+sdf.format(date);
			    				Log.d("SEND_T",log);
							}
							else{	// �������M�̓r���Ȃ�
								// ���M����
								files.fileSend(my_address, route.nextIpAdd, port);

								// �o�߂��㏑��
								files.set();

								// �đ�����
								final FileManager f_copy = files;

								try {
									new Thread(new Runnable() {

										int wait_time = 2 * AODV_Activity.NODE_TRAVERSAL_TIME
											* (route.hopCount + AODV_Activity.TIMEOUT_BUFFER);
										int resend_count = 0;
										int prev_step = f_copy.file_next_no;
										byte[] buffer = f_copy.buffer;
										byte[] destination_next_hop_address_b = route.nextIpAdd;
										int port_ = port;
										String file_name = f_copy.file_name;
										byte[] destination_address = f_copy.destination_address;

										// �đ�����
										public void run() {
											timer: while (true) {

//												handler.post(new Runnable() {
//													public void run() {

														// ���Mtry
														SendByteArray.send(buffer, destination_next_hop_address_b);

//													}

//												});
												// �w��̎��Ԓ�~����
												try {
													Thread.sleep(wait_time);
												} catch (InterruptedException e) {
												}

												resend_count++;

												// ���[�v�𔲂��鏈��
												if (resend_count == AODV_Activity.MAX_RESEND) {
													break timer;
												}
												FileManager files = AODV_Activity.searchProgress(file_name, destination_address);
												if(files == null){
													break timer;
												}
												else{
													if( files.file_next_no != prev_step){
														break timer;
													}
												}

											}
										}
									}).start();

								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}


						break;
					}
				}
				// �L���Ȍo�H�������Ă��Ȃ��ꍇ
				else{

				}
    		}
    		else{	// ���z�b�v�֓]��
    			// ����܂ł̗L���o�H�������Ă��邩����
				int index1 = AODV_Activity.searchToAdd(FReq.getAddressDest(receiveBuffer));

				// �o�H��m���Ă���
				if(index1 != -1){
					// �L���Ȍo�H�Ȃ�
					if(AODV_Activity.getRoute(index1).stateFlag == 1){

						// ���z�b�v�ւ��̂܂ܓ]��
						sendMessage(receiveBuffer, AODV_Activity.getRoute(index1).nextIpAdd);

						break;
					}
				}
				// �L���Ȍo�H�������Ă��Ȃ��ꍇ

				// RERR�̑��M?
				RouteManager.RERR_Sender(AODV_Activity.getRoute(index1),port);
    		}

    		break;*/
    		
		}
	}
	
	// �o�C�g�z��̎��o���i�]���ȕ����̍폜�j
	private static byte[] cut_byte_spare(byte[] b,int size){

		byte[] slim_byte = new byte[size];
		System.arraycopy(b, 0, slim_byte, 0, size);

		return slim_byte;
	}

	// ��M�t�@�C������
	// ��M���̃f�[�^�Ƀt�@�C�������������̂����邩����
	// �������-1��Ԃ�
//	public int searchSameNameFile(String name){
//		synchronized(AODV_Activity.fileReceivedManagerLock){
//			for(int i=0;i<file_received_manager.size();i++){
//				if( file_received_manager.get(i).file_name.equals(name)){
//					return i;
//				}
//			}
//		}
//		return -1;
//	}
	// �����ԉ������̖�����M�t�@�C����index��Ԃ�
//	public int removeDisconnectedFile(){
//		synchronized(AODV_Activity.fileReceivedManagerLock){
//
//			long now = new Date().getTime();
//			for(int i=0;i<file_received_manager.size();i++){
//				if( file_received_manager.get(i).life_time < now){
//					return i;
//				}
//			}
//		}
//		return -1;
//	}

	/***** ���b�Z�[�W0:�e�L�X�g���b�Z�[�W�p�֐� ******/
	// ���b�Z�[�W0�����������H
	private static boolean isToMe(byte[] receiveBuffer,byte[] myAddress){
		if(receiveBuffer[0] != 0){
			return false;
		}

		// ����IP�A�h���X�̃R�s�[����쐬
		byte[] toIpAdd = new byte[4];

		// �Y�������𔲂��o��
		System.arraycopy(receiveBuffer,1,toIpAdd,0,4);

		if(Arrays.equals(toIpAdd,myAddress))
				return true;
		else return false;
	}

	// ���b�Z�[�W0�̒����父��A�h���X�𔲂��o��
	private static byte[] getAddressDest(byte[] receiveBuffer){
		byte[] add = new byte[4];

		// �Y�������𔲂��o��
		System.arraycopy(receiveBuffer, 1, add, 0, 4);

		return add;
	}

	// ���b�Z�[�W0�̒����瑗�M���A�h���X�𔲂��o��
	private static byte[] getAddressSrc(byte[] receiveBuffer){
		if(receiveBuffer[0] == 1){
			return RREQ.getFromIpAdd(receiveBuffer);
		}

		byte[] add = new byte[4];

		// �Y�������𔲂��o��
		System.arraycopy(receiveBuffer, 5, add, 0, 4);

		return add;
	}

	// ���b�Z�[�W0�̒�����`���f�[�^�𔲂��o��
	private static byte[] getMessage(byte[] receiveBuffer,int length){
		if(receiveBuffer[0] == 1){
			return RREQ.getMessage(receiveBuffer, length);
		}


		// ����IP�A�h���X�̃R�s�[����쐬
		byte[] message = new byte[length-9];

		// �Y�������𔲂��o��
		System.arraycopy(receiveBuffer,9,message,0,length-9);

		return message;
	}

	// byte[]�^��int�^�֕ϊ�
	private static int byteToInt(byte[] num){

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
	private static  byte[] getByteAddress(String str){

		// ����
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}
}
