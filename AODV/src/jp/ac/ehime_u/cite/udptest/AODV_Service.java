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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.example.intenttester_s.AODV_Service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
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
	// �X���b�h
	private Thread udpListenerThread;	// ��M�X���b�h
	private Thread routeManagerThread;	// ���[�g�Ď��X���b�h
	private boolean timer_stop = false;	//ExpandingRingSerch���I�����邽�߂̂���
	
	// ���[�g�e�[�u��
	private ArrayList<RouteTable> routeTable = new ArrayList<RouteTable>();
	
	// PATH_DISCOVERY_TIME�̊ԂɎ�M����RREQ�̑��M����ID���L�^
	private ArrayList<PastData> receiveRREQ_List = new ArrayList<PastData>();
	
	// �Е��������N�r���p�A�h���X�ꗗ
	public ArrayList<BlackData> black_list = new ArrayList<BlackData>();
	public HashSet<byte[]> ack_demand_list = new HashSet<byte[]> ();
	
	// �f�[�^�x�[�X�֗l�X�ȏ����L�^
	private SQLiteDatabase log_db;
	private String MyIP;
	private String network_interface;
	
	// �}���`�X���b�h�̔r������p�I�u�W�F�N�g
	public Object routeLock = new Object();
	public Object pastDataLock = new Object();
	public Object blackListLock = new Object();
	public Object ackDemandListLock = new Object();
	public Object fileManagerLock = new Object();
	public Object fileReceivedManagerLock = new Object();
	
	// Bluetooth�֘A
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    // Message types sent from the BluetoothChatService Handler
    // DEVICE_**** is KEY_STRING
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_THREAD_NO = "device_thread";
    public static final int BLUETOOTH_MAX_SLAVE = 7;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
	// �l�X�ȃp�����[�^�̃f�t�H���g�l��錾
	public static final int ACTIVE_ROUTE_TIMEOUT = 3000; // [ms]
	public static final int ALLOWED_HELLO_LOSS = 2;
	public static final int HELLO_INTERVAL = 1000; // [ms]
	public static final int DELETE_PERIOD = (ACTIVE_ROUTE_TIMEOUT >= HELLO_INTERVAL) ? 5 * ACTIVE_ROUTE_TIMEOUT
			: 5 * HELLO_INTERVAL;
	public static final int LOCAL_ADD_TTL = 2;
	public static final int MY_ROUTE_TIMEOUT = 2 * ACTIVE_ROUTE_TIMEOUT;
	public static final int NET_DIAMETER = 35;
	public static final int MAX_REPAIR_TTL = (int) (0.3 * NET_DIAMETER);
	public static int MIN_REPAIR_TTL = -1; // ����m�[�h�֒m���Ă���ŐV�̃z�b�v��
	public static final int NODE_TRAVERSAL_TIME = 40; // [ms]
	public static final int NET_TRAVERSAL_TIME = 2 * NODE_TRAVERSAL_TIME
			* NET_DIAMETER;
	public static final int NEXT_HOP_WAIT = NODE_TRAVERSAL_TIME + 10;
	public static final int PATH_DISCOVERY_TIME = 2 * NET_TRAVERSAL_TIME;
	public static final int PERR_RATELIMIT = 10;
	public static final int RREQ_RETRIES = 2;
	public static final int RREQ_RATELIMIT = 10;
	public static final int BLACKLIST_TIMEOUT = RREQ_RETRIES * NET_TRAVERSAL_TIME;
	public static final int TIMEOUT_BUFFER = 2;
	public static final int TTL_START = 1;
	public static final int TTL_INCREMENT = 2;
	public static final int TTL_THRESHOLD = 7;
	public static int TTL_VALUE = 1; // IP�w�b�_����"TTL"�t�B�[���h�̒l
	public static int RING_TRAVERSAL_TIME = 2 * NODE_TRAVERSAL_TIME
			* (TTL_VALUE + TIMEOUT_BUFFER);
	public static int MAX_SEND_FILE_SIZE = 63*1024;
	public static int MAX_RESEND = 5;
	public static String BLOAD_CAST_ADDRESS = "255.255.255.255";
    
	// ���̑��ϐ�
	private int RREQ_ID = 0;
	private int seqNum = 0;
	private boolean do_BroadCast = false; // ��莞�ԓ��ɉ�����۰�޷��Ă������ǂ���
	
    
	// �t�@�C�����M
	private ArrayList<FileManager> file_manager = new ArrayList<FileManager>();
	
	
	
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

	// ���g��Ԃ�Binder
	public class AODV_ServiceBinder extends Binder{
		AODV_Service getService(){
			return AODV_Service.this;
		}
	}
	
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
	@Override
	public boolean onUnbind(Intent intent){
		return true;
	}
	
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
	public void onCreate() {
		//Log.d("service","oncreate");
		StaticIpAddress sIp = new StaticIpAddress(this);
		MyIP = sIp.getStaticIp();
		
		// �X���b�h���N�����łȂ����
		if( udpListenerThread == null ){
			try {
				// ��M�X���b�h�̃C���X�^���X���쐬
				UdpListener udp_listener = new UdpListener(12345, this);
				// �X���b�h���擾
				udpListenerThread = new Thread(udp_listener);
			} catch (SocketException e1) {
				e1.printStackTrace();
			}
			// ��M�X���b�hrun()
			udpListenerThread.start();
		}
		
		if( routeManagerThread == null){
			// �o�H�Ď��X���b�h�̃C���X�^���X���쐬
			try {
				RouteManager route_manager = new RouteManager(12345);
				// �X���b�h���擾
				routeManagerThread = new Thread(route_manager);
			} catch (IOException e2) {
				e2.printStackTrace();
			}
			// �Ď��X���b�hrun()
			routeManagerThread.start();
		}

	}

    @Override
    public int onStartCommand(Intent intent, int flags,int startId) {
    	return START_NOT_STICKY;
    }
    
	@Override
	public void onDestroy() {
		// Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
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

	/*
	 * ReceiveProcess�֌W�̎d����s
	 */
	// RREQ�̎�M�����̒��ŁA�Â�����A�h���X�̍폜
	public void removeOldRREQ_List(){
		if( !receiveRREQ_List.isEmpty() ){	// ��łȂ����
			// ���X�g�̒��ōł��Â��̂͐擪�̍��ځA���̐������Ԃ��`�F�b�N
			synchronized (pastDataLock) {
				if(receiveRREQ_List.get(0).lifeTime < new Date().getTime() ){
					receiveRREQ_List.remove(0);
				}
			}
		}
	}
	// �Z���Ԃ�RREQ��M���𒆂ɁA������ID,�A�h���X�̂��̂�����������
	public boolean RREQ_ContainCheck(int ID, byte[] Add) {

		synchronized (pastDataLock) {
			for (int i = 0; i < receiveRREQ_List.size(); i++) {
				if ((ID == receiveRREQ_List.get(i).RREQ_ID)
						&& Arrays.equals(Add, receiveRREQ_List.get(i).IpAdd)) {
					return true;
				}
			}
		}
		return false;
	}
	// �����ɎQ�Ƃ��N����Ȃ��悤�A���X�g�ɒǉ����郁�\�b�h
	public void newPastRReq(int IDnum, byte[] FromIpAdd) {

		synchronized (pastDataLock) {
			receiveRREQ_List.add(new PastData(IDnum, FromIpAdd, new Date()
					.getTime() + PATH_DISCOVERY_TIME));
		}
	}
	// RouteTable(list)�Ɉ���A�h���X(Add)���܂܂�Ă��Ȃ�����������
	// �߂�l�F���X�g���Ŕ��������ʒu�A�C���f�b�N�X
	// ������Ȃ��ꍇ -1��Ԃ�
	public int searchToAdd(byte[] Add) {
		synchronized (routeLock) {
			for (int i = 0; i < routeTable.size(); i++) {
				if (Arrays.equals((routeTable.get(i).toIpAdd), Add)) {
					return i;
				}
			}
		}
		return -1;
	}
	// seqNum
	public int getSeqNum(){
		return seqNum;
	}
	public void setSeqNum(int i){
		seqNum = i;
	}
	// do_BroadCast
	public boolean getDoBroadcast(){
		return do_BroadCast;
	}
	public void setDoBroadcast(boolean b){
		do_BroadCast = b;
	}
	// RREQ_ID
	public int getRREQ_ID(){
		return RREQ_ID;
	}
	public void setRREQ_ID(int id){
		RREQ_ID = id;
	}
	// MyIp(String)
	public String getMyIP(){
		return MyIP;
	}
	/*
	 * RouteTable�֘A�̎d����s
	 */
	// ���[�g�e�[�u������i�Ԗڂ̗v�f��Ԃ��A�r������
	public RouteTable getRoute(int index) {
		synchronized (routeLock) {
			return routeTable.get(index);
		}
	}
	// ���[�g�e�[�u���ɗv�f��ǉ�����A�r������
	public void addRoute(RouteTable route) {
		synchronized (routeLock) {
			routeTable.add(route);

			// �����擾
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10001, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
		}
	}
	// ���[�g�e�[�u���̗v�f���폜����A�r������
	public void removeRoute(int index) {
		synchronized (routeLock) {
			RouteTable route = routeTable.get(index);
			// �����擾
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);

			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10003, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
			routeTable.remove(index);
		}
	}
	// ���[�g�e�[�u���̗v�f���㏑������A�r������
	public void setRoute(int index, RouteTable route) {
		synchronized (routeLock) {
			RouteTable pre_route = routeTable.get(index);
			// �����擾
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);

			// �o�H�ύX�O�ƌ���L�^
			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10002, MyIP, getStringByByteAddress(pre_route.toIpAdd), (int)pre_route.hopCount, pre_route.toSeqNum
					, getStringByByteAddress(pre_route.nextIpAdd), (int)pre_route.stateFlag, (int)pre_route.lifeTime, sdf.format(date), network_interface);

			routeTable.set(index, route);

			LogDataBaseOpenHelper.insertLogTableROUTE(log_db, 10002, MyIP, getStringByByteAddress(route.toIpAdd), (int)route.hopCount, route.toSeqNum
					, getStringByByteAddress(route.nextIpAdd), (int)route.stateFlag, (int)route.lifeTime, sdf.format(date), network_interface);
		}
	}
	// ���[�g�e�[�u���̗v�f���𓾂�
	public int getRouteSize(){
		return routeTable.size();
	}
	
	// ACK�v�����X�g�ɗv�f��ǉ�����A�r������
	public void addAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			ack_demand_list.add(ip);
		}
	}
	// ACK�v�����X�g�ɗv�f���܂܂�Ă��邩����A�r������
	public boolean containAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			return ack_demand_list.contains(ip);
		}
	}
	// ACK�v�����X�g����v�f���폜����A�r������
	public void removeAckDemand(byte[] ip){
		synchronized(ackDemandListLock){
			ack_demand_list.remove(ip);
		}
	}
	
	// Black���X�g�ɗv�f��ǉ�����A�r������
	public void addBlack(BlackData d){
		synchronized(blackListLock){
			black_list.add(d);
		}
	}
	public int getBlackListSize(){
		synchronized(blackListLock){
			return black_list.size();
		}
	}
	public BlackData getBlackData(int index){
		synchronized(blackListLock){
			return black_list.get(index);
		}
	}
	public void removeBlackData(int index){
		synchronized(blackListLock){
			black_list.remove(index);
		}
	}
	
	// �o�H�̎����C��
	public void localRepair(RouteTable route, final int port, byte[] myAdd,final AODV_Service mAODV_Service){
		
		// ***** RREQ�̑��M *****
		int TTL = MIN_REPAIR_TTL + LOCAL_ADD_TTL;
		
		route.toSeqNum++;
		RREQ_ID++;
		seqNum++;
		
		// ���������M�����p�P�b�g����M���Ȃ��悤��ID��o�^
		newPastRReq(RREQ_ID, myAdd);
		
		do_BroadCast = true;
		
		try {
			RREQ.send(route.toIpAdd, myAdd
					, false, false, false, false, false
					, route.toSeqNum, seqNum, RREQ_ID, TTL, port, null, mAODV_Service);
		} catch (Exception ex3) {
			ex3.printStackTrace();
		}
		
		// �o�H�T�����Ԃ��߂�����A�o�H���C�����ꂽ���`�F�b�N
		long waitTime = 2 * AODV_Service.NODE_TRAVERSAL_TIME * (TTL + AODV_Service.TIMEOUT_BUFFER);
		final byte[] toIp = route.toIpAdd;
		final RouteTable route_f = route;
		
		Timer mTimer = new Timer(true);
		mTimer.schedule( new TimerTask(){
				@Override
				public void run(){
					int index = searchToAdd(toIp);
					
					// �o�H���ǉ�����Ă��āA���z�b�v�����C���O�ȉ��Ȃ�A�C������
					// ����ȊO�̏ꍇ�ARERR�𑗐M����
					if(index == -1){
						new RERR().RERR_Sender(route_f,port);
						
						final byte[] destination_address = route_f.toIpAdd;
						mAODV_Service.appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
					}
					else{
						// �z�b�v�������������ꍇ
						if(getRoute(index).hopCount > route_f.hopCount){
							new RERR().RERR_Sender(route_f,port);
						}
					}
				}
			}, waitTime);
	}
	/*
	 * Activity�֘A�̎d����s�i��s����K�v�Ȃ�����...�j
	 * Activity�̃��b�Z�[�W�\�����ɕ\�����Ăق���String�𑗂����B
	 * Activity���N�����Ă��Ȃ��ꍇ�A�͂����ɃX���[�����H
	 */
	public void appendMessageOnActivity(String mes){
		Intent intent = new Intent(this.getString(R.string.AODV_ActivityReceiver));
		intent.putExtra(this.getString(R.string.AODV_ActivityKey), mes);
		sendBroadcast(intent);
	}
	
	/*
	 * LogDataBase�֘A�̎d����s
	 */
	public void writeLog(int type,String myIp,String sourceIp,String destinationAddress,int hopCount, int fromSeqNum){
		Date date_rint = new Date();
		SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		LogDataBaseOpenHelper.insertLogTableAODV(log_db, type, myIp, sourceIp,
				destinationAddress, hopCount, fromSeqNum, sdf_rint.format(date_rint), network_interface);
	}
	public void writeLogData(int type,String myIp,String sourceIp,String destinationAddress,int dataLength, String appName){
		Date date_rint = new Date();
		SimpleDateFormat sdf_rint = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		LogDataBaseOpenHelper.insertLogTableDATA(log_db, type, myIp, sourceIp,
				destinationAddress, dataLength, appName, sdf_rint.format(date_rint), network_interface);
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

