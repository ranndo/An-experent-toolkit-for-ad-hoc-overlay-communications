package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
 * �풓�^�T�[�r�X�̊��N���X�B
 * @author id:language_and_engineering
 *
 */
public class AODV_Service extends Service
{

    /**
     * �T�[�r�X�̒�����s�̊Ԋu���~���b�Ŏw��B
     * �������I�����Ă��玟�ɌĂ΂��܂ł̎��ԁB
     */
//    protected abstract long getIntervalMS();


    /**
     * ������s�������^�X�N�̒��g�i�P�񕪁j
     * �^�X�N�̎��s������������C����̎��s�v��𗧂Ă邱�ƁB
     */
//    protected abstract void execTask();


    /**
     * ����̎��s�v��𗧂Ă�B
     */
//    protected abstract void makeNextPlan();


    // ---------- �K�{�����o -----------
	private final SendManager.Stub mBinder = new SendManager.Stub() {

		// AODV��ő���f�[�^�̑��M�v���C���^�t�F�[�X
		public void SendMessage(String destination_address,
				String source_address, byte flag, String package_name,
				String intent_action, int intent_flags, String intent_type,
				String intent_scheme, List<String> intent_categories,
				byte[] data) throws RemoteException {

			// ���M���ׂ��o�b�t�@�̑S�̃T�C�Y���v�����Ă���
			int total_length = 1+4+4+1;	//Type,����,���M��,�t���O�̊e�T�C�Y�����Z
			if(package_name != null){
				total_length++;
				total_length += package_name.length();
			}
			if(intent_action != null){
				total_length++;
				total_length += intent_action.length();
			}
			if(intent_flags != 0){
				total_length += 4;
			}
			if(intent_type != null){
				total_length++;
				total_length += intent_type.length();
			}
			if(intent_scheme != null){
				total_length++;
				total_length += intent_scheme.length();
			}
			if(intent_categories != null){
				total_length++;
				for(int i=0;i<intent_categories.size();i++){
					total_length++;
					total_length += intent_categories.get(i).length();
				}
			}
			if(data != null){
				total_length += data.length;
			}
			// ���M�o�b�t�@�̊m��
			byte[] buffer = new byte[total_length];
Log.d("RINT_Create","total/data = "+total_length+"/"+data.length);
			// �o�b�t�@�Ɋe�������蓖��
			buffer[0] =  5;	// Type=5�Ƃ���
			System.arraycopy(getByteAddress(destination_address), 0, buffer, 1, 4);
			System.arraycopy(getByteAddress(source_address)		, 0, buffer, 5, 4);
			buffer[9] = flag;

			// �t���O�ɉ����ėl�X�ȏ���t��
			int index = 10;
			if(package_name != null){
				buffer[index] = (byte) package_name.length();
				index += 1;
				System.arraycopy(package_name.getBytes(), 0, buffer, index, package_name.length());
				index +=  package_name.length();
			}
			if(intent_action != null){
				buffer[index] = (byte) intent_action.length();
				index += 1;
				System.arraycopy(intent_action.getBytes(), 0, buffer, index, intent_action.length());
				index +=  intent_action.length();
			}
			if(intent_flags != 0){
				System.arraycopy(intToByte(intent_flags), 0, buffer, index, 4);
				index += 4;
			}
			if(intent_type != null){
				buffer[index] = (byte) intent_type.length();
				index += 1;
				System.arraycopy(intent_type.getBytes(), 0, buffer, index, intent_type.length());
				index +=  intent_type.length();
			}
			if(intent_scheme != null){
				buffer[index] = (byte) intent_scheme.length();
				index += 1;
				System.arraycopy(intent_scheme.getBytes(), 0, buffer, index, intent_scheme.length());
				index +=  intent_scheme.length();
			}
			if(intent_categories != null){
				buffer[index] = (byte) intent_categories.size();
				index += 1;
				for(int i=0;i<intent_categories.size();i++){
					buffer[index] = (byte) intent_categories.get(i).length();
					index += 1;
					System.arraycopy(intent_categories.get(i).getBytes(), 0, buffer, index, intent_categories.get(i).length());
					index +=  intent_categories.get(i).length();
				}
			}
			System.arraycopy(data, 0, buffer, index, data.length);
			// ���M�f�[�^���������܂�

			// ���M��ւ̌o�H�����݂��邩�`�F�b�N
			final int route_index = AODV_Activity.searchToAdd(getByteAddress(destination_address));
			// �o�H�����݂���ꍇ�A�L�����ǂ����`�F�b�N
			boolean enableRoute = false; // ������
			Log.d("ServiceSearch","z");
			if (route_index != -1) {
				if ( AODV_Activity.getRoute(route_index).stateFlag == 1 &&
						(AODV_Activity.getRoute(route_index).lifeTime > new Date().getTime())) {
					enableRoute = true;
				}
			}
			if(enableRoute){
				// �g�p�\�Ȍo�H�����łɂ���ꍇ
				RouteTable route = AODV_Activity.getRoute(route_index);

				SendByteArray.send(buffer, route.nextIpAdd);
			}
			else{Log.d("ServiceSearch","X");
				// �o�H���Ȃ��ꍇ

				// ���g�̃V�[�P���X�ԍ����C���N�������g
				AODV_Activity.seqNum++;

				// �������悪�u���[�h�L���X�g�A�h���X�Ȃ�ExpandingRingSearch���s��Ȃ�
				if( AODV_Activity.BLOAD_CAST_ADDRESS.equals(destination_address)){
					// *** RREQ����Ȃ��đf�̃^�C�v5���b�Z�[�W�𑗂�ׂ����ȁH ***
//					// RREQ_ID���C���N�������g
//					AODV_Activity.RREQ_ID++;
//
//					// ���������M�����p�P�b�g����M���Ȃ��悤��ID��o�^
//					AODV_Activity.newPastRReq(AODV_Activity.RREQ_ID, getByteAddress(source_address));
//
//					try {
//						new RREQ().send(getByteAddress(destination_address),
//										getByteAddress(source_address),
//										false,
//										false,
//										true,
//										false,
//										true,
//										0,
//										AODV_Activity.seqNum,
//										AODV_Activity.RREQ_ID,
//										AODV_Activity.NET_DIAMETER,
//										12345,
//										"Bloadcast");
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
				}
				// �u���[�h�L���X�g�A�h���X����Ȃ��ꍇ
				// ExpandingRingSearch
				else{
					Log.d("ServiceSearch","�o�H�Ȃ�");

					// TTL�������l�܂��͉ߋ��̃z�b�v��+TTL_�ݸ���ĂɃZ�b�g
					// ����V�[�P���X�ԍ�(+���m�t���O)���܂Ƃ߂ăZ�b�g
					final boolean flagU;
					final int seqValue;
					final int TTL_VALUE;

					// �����o�H�����݂���Ȃ�A���̏��𗬗p
					if (route_index != -1) {
						TTL_VALUE = AODV_Activity.getRoute(route_index).hopCount + AODV_Activity.TTL_INCREMENT;
						flagU = false;
						seqValue = AODV_Activity.getRoute(route_index).toSeqNum;
					}
					else{
						TTL_VALUE = AODV_Activity.TTL_START;
						flagU = true;
						seqValue = 0;
					}

					// ExpandingRingSearch
					final int ring_traversal_time = 2 * AODV_Activity.NODE_TRAVERSAL_TIME * (TTL_VALUE + AODV_Activity.TIMEOUT_BUFFER);
					final byte[] source_address_b = getByteAddress(source_address);
					final byte[] destination_address_b = getByteAddress(destination_address);
					final byte[] buffer_copy = buffer.clone();

					try {
						new Thread(new Runnable() {
							public void run() {

								boolean timer_stop = false;
								int TTL_VALUE_ = TTL_VALUE;
								int ring_traversal_time_ = ring_traversal_time;

								timer: while (true) {

									int index_new;
									byte[] myAdd = source_address_b;

									// Thread�͕K���҂������~����Ƃ͌���Ȃ��̂ŁA��~���Ȃ��Ă����̏����͎��s����Ȃ��悤�ɂ���
									if (!timer_stop) {

										// �ȉ��A��������̓��e
										// �o�H�����������ꍇ�A���[�v�𔲂���
										if ( (index_new = AODV_Activity.searchToAdd(destination_address_b)) != -1) {

											timer_stop = true;

											// ���b�Z�[�W�̑��M
											RouteTable route = AODV_Activity.getRoute(index_new);
											InetAddress next_hop_inet = null;
											SendByteArray.send(buffer_copy, route.nextIpAdd);
											
											try {
												File file = getFileStreamPath("imagePacket.txt");
												if(!file.exists()){
													FileOutputStream out = openFileOutput("imagePacket.txt",MODE_PRIVATE);
													out.write(buffer_copy);
													out.close();
												}
											} catch (FileNotFoundException e) {
												e.printStackTrace();
											} catch (IOException e) {
												e.printStackTrace();
											}
										}

										// TTL������l��RREQ�𑗐M�ς݂Ȃ烋�[�v�𔲂���
										else if (TTL_VALUE_ == (AODV_Activity.TTL_THRESHOLD + AODV_Activity.TTL_INCREMENT)) {
											timer_stop = true;
										}

										// TTL�̔�����
										// �Ⴆ��INCREMENT2,THRESHOLD7�̂Ƃ�,TTL�̕ω���2->4->6->7(not 8)
										if (TTL_VALUE_ > AODV_Activity.TTL_THRESHOLD) {
											TTL_VALUE_ = AODV_Activity.TTL_THRESHOLD;
										}

										// RREQ_ID���C���N�������g
										AODV_Activity.RREQ_ID++;

										// ���������M�����p�P�b�g����M���Ȃ��悤��ID��o�^
										AODV_Activity.newPastRReq(AODV_Activity.RREQ_ID, myAdd);

										// RREQ�̑��M
										AODV_Activity.do_BroadCast = true;

										try {
											new RREQ().send(destination_address_b,
															myAdd,
															false,
															false,
															false,
															false,
															flagU,
															seqValue,
															AODV_Activity.seqNum,
															AODV_Activity.RREQ_ID,
															TTL_VALUE_,
															12345,
															null);
										} catch (Exception e) {
											e.printStackTrace();
										}

										// ������Ƌ����ȑҋ@(�{����RREP���߂��Ă���Α҂��Ȃ��Ă������Ԃ��҂��Ă���)
										// �҂����Ԃ�VALUE�ɍ��킹�čX�V
										ring_traversal_time_ = 2
												* AODV_Activity.NODE_TRAVERSAL_TIME
												* (TTL_VALUE_ + AODV_Activity.TIMEOUT_BUFFER);

										TTL_VALUE_ += AODV_Activity.TTL_INCREMENT;
									}

									// �w��̎��Ԓ�~����
									try {
										Thread.sleep(ring_traversal_time_);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}

									// ���[�v�𔲂��鏈��
									if (timer_stop) {
										break timer;
									}
								}
							}
						}).start();

					} catch (Exception e) {
						e.printStackTrace();
					}


				}
			}//*/
			Date date_rint = new Date();
			SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
			if((RINT.FLAG_PACKAGE_NAME & flag) != 0)
				LogDataBaseOpenHelper.insertLogTableDATA(AODV_Activity.log_db, 51, AODV_Activity.MyIP, source_address,
						destination_address, data.length, package_name, sdf_rint.format(date_rint), AODV_Activity.network_interface);
			else
				LogDataBaseOpenHelper.insertLogTableDATA(AODV_Activity.log_db, 51, AODV_Activity.MyIP, source_address,
						destination_address, data.length, package_name, sdf_rint.format(date_rint), AODV_Activity.network_interface);
		}

		@Override
		public void Test() throws RemoteException {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			Log.d("AODV_Service","ToastStart");
			//Toast.makeText(getApplicationContext(), "Service", Toast.LENGTH_LONG).show();
			Log.d("AODV_Service","ToastEnd");
		}


	};

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    // ---------- �T�[�r�X�̃��C�t�T�C�N�� -----------


    /**
     * �풓���J�n
     */
/**    public BaseService startResident(Context context)
//    {
//        Intent intent = new Intent(context, this.getClass());
//        intent.putExtra("type", "start");
//        context.startService(intent);
//
//        return this;
//    }
**/


    @Override
    public void onStart(Intent intent, int startId) {

        // �T�[�r�X�N�����̏����B
        // �T�[�r�X�N�����ɌĂԂƕ�����R�[�����꓾��B��������d�N���͂��Ȃ�
        // @see http://d.hatena.ne.jp/rso/20110911

        super.onStart(intent, startId);

        // �^�X�N�����s
//        execTask();

        // NOTE: �����Ŏ���̎��s�v��𒀎��I�ɃR�[�����Ă��Ȃ����R�́C
        // �^�X�N���񓯊��̏ꍇ�����邩��B
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

	// int�^��byte[]�^�֕ϊ�
	private byte[] intToByte(int num){

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


    /**
     * �T�[�r�X�̎���̋N����\��
     */
//    public void scheduleNextTime() {
//
//        long now = System.currentTimeMillis();
//
//        // �A���[�����Z�b�g
//        PendingIntent alarmSender = PendingIntent.getService(
//            this,
//            0,
//            new Intent(this, this.getClass()),
//            0
//        );
//        AlarmManager am = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
//        am.set(
//            AlarmManager.RTC,
//            now + getIntervalMS(),
//            alarmSender
//        );
//        // ����o�^������
//
//    }


    /**
     * �T�[�r�X�̒�����s���������C�T�[�r�X���~
     */
//    public void stopResident(Context context)
//    {
//        // �T�[�r�X�����w��
//        Intent intent = new Intent(context, this.getClass());
//
//        // �A���[��������
//        PendingIntent pendingIntent = PendingIntent.getService(
//            context,
//            0, // ������-1�ɂ���Ɖ����ɐ������Ȃ�
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT
//        );
//        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
//        alarmManager.cancel(pendingIntent);
//            // @see http://creadorgranoeste.blogspot.com/2011/06/alarmmanager.html
//
//        // �T�[�r�X���̂��~
//        stopSelf();
//    }

//  @Override
//  protected long getIntervalMS() {
//      return 1000 * 10;
//  }
//
//
//  @Override
//  protected void execTask() {
//      activeService = this;
//
//
//      // ����������̏������d���ꍇ�́C���C���X���b�h��W�Q���Ȃ����߂�
//      // �������牺��ʃX���b�h�Ŏ��s����B
//
//
//      // ���O�o�́i�����ɒ�����s�����������������j
//      Log.d("hoge", "fuga");
//
//      // ����̎��s�ɂ��Čv��𗧂Ă�
//      makeNextPlan();
//  }
//
//
//  @Override
//  public void makeNextPlan()
//  {
//      this.scheduleNextTime();
//  }


  /**
   * �����N�����Ă�����C�풓����������
   */
//  public static void stopResidentIfActive(Context context) {
//      if( activeService != null )
//      {
//          activeService.stopResident(context);
//      }
//  }
}

