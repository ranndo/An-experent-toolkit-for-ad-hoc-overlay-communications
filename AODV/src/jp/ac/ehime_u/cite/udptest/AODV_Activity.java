package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AODV_Activity extends Activity {

	// �����o�ϐ�
	// EditText�N���X(ip���͂�{�^�������Ȃǃ��[�U�[�̓��͂��������邽��)
	private EditText editTextSrc;
	private EditText editTextSrcPort;
	static EditText editTextDest;
	private EditText editTextDestPort;
	private static EditText editTextToBeSent;
	private EditText text_view_received;
	
	public static EditText testtext;

	public static Context context;
	
	private BroadcastReceiverAODV_Activity receiver = new BroadcastReceiverAODV_Activity();

	private AODV_Service mAODV_Service;

	// �C���e���g�̑��d��������
	private static String prev_receive_package_name = null;
	private static int prev_receive_intent_id = -1;

	private boolean timer_stop = false;	//ExpandingRingSerch���I�����邽�߂̂���

	/** Called when the activity is first created. */
	@Override
	// �I�[�o�[���C�h(�e��q�N���X�Ń��\�b�h�����������Ƃ��q�N���X�̐錾�ŏ㏑��)
	// �{�^���Ȃǂ��\�������O�̏����������Ȃ�
	// onCreate���\�b�h���I�[�o�[���C�h�Ƃ��ċL�q���Ă���
	public void onCreate(Bundle savedInstanceState) {
		// onCreate���I�[�o�[���C�h����ꍇ�A�X�[�p�[�N���X�̃��\�b�h���Ăяo���K�v������
		super.onCreate(savedInstanceState);
		
		// �N�����Ƀ\�t�g�L�[�{�[�h�̗����オ���h��
		this.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// �A�N�e�B�r�e�B�Ƀr���[�O���[�v��ǉ�����
		setContentView(R.layout.main);

		// ID�ɑΉ�����View���擾�A�^���قȂ�̂ŃL���X�g
		editTextDest = (EditText) findViewById(R.id.editTextDest);
		editTextDestPort = (EditText) findViewById(R.id.editTextDestPort);
		editTextSrc = (EditText) findViewById(R.id.editTextSrc);
		editTextSrcPort = (EditText) findViewById(R.id.editTextSrcPort);
		editTextToBeSent = (EditText) findViewById(R.id.editTextToBeSent);

		StaticIpAddress sIp = new StaticIpAddress(this);
		editTextSrc.setText(sIp.getStaticIp());

		// ��M���O�p��TextView�A���l��ID����擾
		//final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);
		text_view_received = (EditText) findViewById(R.id.textViewReceived);
		context = this;
		mAODV_Service = null;
		
		// �t�@�C���I�[�v���A���l��ID����擾���N���b�N�C�x���g��ǉ�
		Button buttonFileOpen = (Button) findViewById(R.id.buttonFileOpen);

		buttonFileOpen.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//SelectFile();
			}
		});

		// ���MButton�A���l��ID����擾
		Button buttonSend = (Button) findViewById(R.id.buttonSend);

		// �N���b�N�����A�����N���X(���̏����̖��O�̖����N���X)�𗘗p
		// �{�^�����ɁA�r���[���ӎ������ɏ������L�q�ł���
		buttonSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// editText���瑗�M��IP(String)�A���M��(String)�Aport(int)�̎擾
				String destination_address = editTextDest.getText().toString();
				String source_address = editTextSrc.getText().toString();
				final int destination_port = Integer.parseInt(editTextDestPort
						.getText().toString());

				byte[] destination_address_b = RREQ.getByteAddress(destination_address);
				byte[] source_address_b	= RREQ.getByteAddress(source_address);

				// ���M��ւ̌o�H�����݂��邩�`�F�b�N
				final int index = mAODV_Service.searchToAdd(destination_address_b);

				// �o�H�����݂���ꍇ�A�L�����ǂ����`�F�b�N
				boolean enableRoute = false; // ������

				if (index != -1) {
					if ( mAODV_Service.getRoute(index).stateFlag == 1 &&
							(mAODV_Service.getRoute(index).lifeTime > new Date().getTime())) {
						enableRoute = true;
					}
				}

				// ********* �o�H�����ɑ��݂���ꍇ *******
				if (enableRoute) {
					// ���b�Z�[�W�̑��M
					sendMessage(mAODV_Service.getRoute(index).nextIpAdd, mAODV_Service.getRoute(index).hopCount, destination_port
							, destination_address_b, source_address_b, context);

					// ���M�������Ƃ�\��
					text_view_received.append(editTextToBeSent.getText().toString()
							+ "-->" + destination_address+"\n");
				}
				// *********** �o�H�����݂��Ȃ��ꍇ ***********
				else {
					text_view_received.append("Try Connect...\n");

					// �o�H�쐬
					routeCreate(destination_address, source_address, destination_port, index
							, text_view_received, context);
				}
			}
		});


		// ���MClear�A���l��ID����擾
		Button buttonClear = (Button) findViewById(R.id.buttonClear);

		buttonClear.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				text_view_received.setText("");
			}
		});


		// ���[�g�e�[�u���\���{�^��
		Button buttonShowRouteTable = (Button) findViewById(R.id.buttonShowRouteTable);

		buttonShowRouteTable.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// ���[�g�e�[�u���̕\��
				if(mAODV_Service.getRouteSize() == 0){
					text_view_received.append("Route_NotFound\n");
				}
				else{
					RouteTable route;

					text_view_received.append("ToIp,NextIp,Hop,Enable\n");
					for(int i=0;i<mAODV_Service.getRouteSize();i++){
						// i�Ԗڂ̌o�H���擾
						route = mAODV_Service.getRoute(i);

						text_view_received.append( getStringByByteAddress(route.toIpAdd) +",");
						text_view_received.append( getStringByByteAddress(route.nextIpAdd) +",");
						text_view_received.append( route.hopCount +",");

						if(route.stateFlag == 1)
							text_view_received.append("OK1,");
						else
							text_view_received.append("NG"+route.stateFlag+",");
						
						if(route.bluetoothFlag)
							text_view_received.append("BT\n");
						else
							text_view_received.append("Wi-Fi\n");
					}

					text_view_received.append(mAODV_Service.getRouteSize()+" RouteFound\n");

					text_view_received.setSelection(text_view_received.getText().length());
				}
//				if(mChatService != null){
//					String s = mChatService.showConnection();
//					if(s != null){
//						text_view_received.append(s);
//						text_view_received.setSelection(text_view_received.getText().length());
//					}
//					else{
//						text_view_received.append("BTnotConnect\n");
//						text_view_received.setSelection(text_view_received.getText().length());
//					}
//				}
			}
		});
		
		// �X�^�[�g�T�[�r�X
		startService(new Intent(context, AODV_Service.class));


		// ����ʂ���J�ڂ����Ƃ���onNewIntent��ʂ�Ȃ����߁A������ŏ���
		Intent intent = getIntent();
		if(intent!=null){
			// MAIN(�ʏ�̃A�v���N��)�łȂ����@�ŋN������Ă���Ȃ�
			if(intent.getAction() != Intent.ACTION_MAIN){
				onNewIntent(intent);
			}
		}
	}

	// Intent�̎󂯎�菈��
	@Override
	protected void onNewIntent(Intent intent){
		super.onNewIntent(intent);

		// Intent����p�b�P�[�W��,ID�擾
		String package_name = intent.getStringExtra("PACKAGE");
		int intent_id = intent.getIntExtra("ID", 0);

		Log.d("intent",package_name+"-"+intent_id);

		// ���C���e���g�̑��d�����h�~ �p�b�P�[�W���܂���ID���قȂ��Ă���Ύ�
		if( (package_name != prev_receive_package_name)
				|| intent_id != prev_receive_intent_id){

			// ���O�̃p�b�P�[�W,ID�Ƃ��ċL�^
			prev_receive_package_name = package_name;
			prev_receive_intent_id = intent_id;

			// �N�����@�̃`�F�b�N �ÖٓI�C���e���g:SENDTO�ŋN������Ă����
			if(Intent.ACTION_SENDTO.equals(intent.getAction())){
				final Uri uri = intent.getData();
				String task = intent.getStringExtra("TASK");

				// scheme��"connect"�Ȃ�
				if("connect".equals(uri.getScheme()) && mAODV_Service != null){
					// �ϐ����̒l�������Ă���ꍇ������H
					//editTextDest = (EditText)findViewById(R.id.editTextDest);
					//editTextToBeSent = (EditText)findViewById(R.id.editTextToBeSent);

					editTextDest.setText(uri.getEncodedSchemeSpecificPart());
					editTextToBeSent.setText(task);

					// �������M�����݂�
					// editText���瑗�M��IP(String)�A���M��(String)�Aport(int)�̎擾
					String destination_address = editTextDest.getText().toString();
					String source_address = editTextSrc.getText().toString();
					final int destination_port = Integer.parseInt(editTextDestPort
							.getText().toString());

					byte[] destination_address_b = RREQ.getByteAddress(destination_address);
					byte[] source_address_b	= RREQ.getByteAddress(source_address);

					// UI�̏o�͐���擾
					//final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);

					// ���M��ւ̌o�H�����݂��邩�`�F�b�N
					final int index = mAODV_Service.searchToAdd(destination_address_b);

					// �o�H�����݂���ꍇ�A�L�����ǂ����`�F�b�N
					boolean enableRoute = false; // ������

					if (index != -1) {
						if ( mAODV_Service.getRoute(index).stateFlag == 1 &&
								(mAODV_Service.getRoute(index).lifeTime > new Date().getTime())) {
							enableRoute = true;
						}
					}

					Context etc_context = context;
					// �t�@�C���I�[�v���p�ɑ��p�b�P�[�W�A�v���̃R���e�L�X�g���擾
					if( package_name != null ){
						try {
							etc_context = createPackageContext(package_name,0);
						} catch (NameNotFoundException e) {
							e.printStackTrace();
						}
					}

					// ********* �o�H�����ɑ��݂���ꍇ *******
					if (enableRoute) {
						// ���b�Z�[�W�̑��M
						sendMessage(mAODV_Service.getRoute(index).nextIpAdd, mAODV_Service.getRoute(index).hopCount, destination_port
								, destination_address_b, source_address_b, etc_context);

						// ���M�������Ƃ�\��
						text_view_received.append(editTextToBeSent.getText().toString()
								+ "-->" + destination_address+"\n");
					}
					// *********** �o�H�����݂��Ȃ��ꍇ ***********
					else {
						text_view_received.append("Try Connect...\n");

						// �o�H�쐬
						routeCreate(destination_address, source_address, destination_port, index
								, text_view_received, etc_context);
					}
				}
			}

			// �N�����@�̃`�F�b�N �ÖٓI�C���e���g:DELETE�ŋN������Ă����
			if(Intent.ACTION_DELETE.equals(intent.getAction())){
				final Uri uri = intent.getData();

				if("path".equals(uri.getScheme())){
					deleteFile(uri.getEncodedSchemeSpecificPart());
				}
			}
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// bindService
		mAODV_Service = null;
		Intent intent = new Intent(context, AODV_Service.class);
		intent.setPackage(context.getPackageName());
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		
		// setListener
		IntentFilter filter = new IntentFilter(getString(R.string.AODV_ActivityReceiver));
		registerReceiver(receiver, filter);
		//Log.d("onResume()","onresume()");
	}
	


	// ���[�g�쐬�{���b�Z�[�W���M
	public void routeCreate(String destination_address, String source_address, final int destination_port
			, int search_result, EditText output_view, final Context context_){

		Log.d("debug", "Send__Start");

		// ���M��,���M��IP�A�h���X��byte[]��
		final byte[] destination_address_b = RREQ.getByteAddress(destination_address);

		final byte[] source_address_b = RREQ.getByteAddress(source_address);

		// �������ʂ𗘗p
		int index = search_result;

		// ��ʏo�͐�
		final EditText text_view_received = output_view;

		// ���g�̃V�[�P���X�ԍ����C���N�������g
		mAODV_Service.seqNumIncrement();

		// �������悪�u���[�h�L���X�g�A�h���X�Ȃ�ExpandingRingSearch���s��Ȃ�
		if( AODV_Service.BLOAD_CAST_ADDRESS.equals(destination_address)){

			// ���b�Z�[�W���o
			String text = editTextToBeSent.getText().toString();

			// RREQ_ID���C���N�������g
			mAODV_Service.RREQ_ID_Increment();

			// ���������M�����p�P�b�g����M���Ȃ��悤��ID��o�^
			mAODV_Service.newPastRREQ(mAODV_Service.getRREQ_ID(), source_address_b);

			// RREQ�̑��M
			mAODV_Service.setDoBroadcast(true);

			try {
				RREQ.send(	destination_address_b,
							source_address_b,
							false,
							false,
							true,
							false,
							true,
							0,
							mAODV_Service.getSeqNum(),
							mAODV_Service.getRREQ_ID(),
							AODV_Service.NET_DIAMETER,
							text,
							mAODV_Service);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// ���悪�ʏ��IP�A�h���X�Ȃ�
		else{
			// TTL�������l�܂��͉ߋ��̃z�b�v��+TTL_�ݸ���ĂɃZ�b�g
			// ����V�[�P���X�ԍ�(+���m�t���O)���܂Ƃ߂ăZ�b�g
			final boolean flagU;
			final int seqValue;

			// �����o�H�����݂���Ȃ�A���̏��𗬗p
			if (index != -1) {
				AODV_Service.TTL_VALUE = mAODV_Service.getRoute(index).hopCount + AODV_Service.TTL_INCREMENT;
				flagU = false;
				seqValue = mAODV_Service.getRoute(index).toSeqNum;
			}
			else{
				AODV_Service.TTL_VALUE = AODV_Service.TTL_START;
				flagU = true;
				seqValue = 0;
			}

			// ExpandingRingSearch
			timer_stop = false;		//timer_stop��������
			final Handler mHandler = new Handler();

			AODV_Service.RING_TRAVERSAL_TIME = 2 * AODV_Service.NODE_TRAVERSAL_TIME * (AODV_Service.TTL_VALUE + AODV_Service.TIMEOUT_BUFFER);

			try {
				new Thread(new Runnable() {
					public void run() {
						timer: while (true) {

							mHandler.post(new Runnable() {
								public void run() {
									int index_new;
									byte[] myAdd = source_address_b;

									// Thread�͕K���҂������~����Ƃ͌���Ȃ��̂ŁA��~���Ȃ��Ă����̏����͎��s����Ȃ��悤�ɂ���
									if (!timer_stop) {

										// �ȉ��A��������̓��e
										// �o�H�����������ꍇ�A���[�v�𔲂���
										if ( (index_new = mAODV_Service.searchToAdd(destination_address_b)) != -1) {
											text_view_received
													.append("Route_Create_Success!!\n");

											timer_stop = true;

											// ���b�Z�[�W�̑��M
											RouteTable rt = mAODV_Service.getRoute(index_new);
											sendMessage(rt.nextIpAdd, rt.hopCount,
													destination_port, rt.toIpAdd, myAdd, context_);

											// ���M�������Ƃ�\��
											text_view_received.append(editTextToBeSent.getText().toString()
													+ "-->" + getStringByByteAddress(rt.toIpAdd)+"\n");
										}

										// TTL������l��RREQ�𑗐M�ς݂Ȃ烋�[�v�𔲂���
										else if (AODV_Service.TTL_VALUE == (AODV_Service.TTL_THRESHOLD + AODV_Service.TTL_INCREMENT)) {
											text_view_received
													.append("Failed\n");
											timer_stop = true;
										}

										// TTL�̔�����
										// �Ⴆ��INCREMENT2,THRESHOLD7�̂Ƃ�,TTL�̕ω���2->4->6->7(not
										// 8)
										if (AODV_Service.TTL_VALUE > AODV_Service.TTL_THRESHOLD) {
											AODV_Service.TTL_VALUE = AODV_Service.TTL_THRESHOLD;
										}

										// RREQ_ID���C���N�������g
										mAODV_Service.RREQ_ID_Increment();

										// ���������M�����p�P�b�g����M���Ȃ��悤��ID��o�^
										mAODV_Service.newPastRREQ(mAODV_Service.getRREQ_ID(), myAdd);

										// RREQ�̑��M
										mAODV_Service.setDoBroadcast(true);

										try {
											RREQ.send(destination_address_b,
														myAdd,
														false,
														false,
														false,
														false,
														flagU,
														seqValue,
														mAODV_Service.getSeqNum(),
														mAODV_Service.getRREQ_ID(),
														AODV_Service.TTL_VALUE,
														null,
														mAODV_Service);
										} catch (Exception e) {
											e.printStackTrace();
										}

										// ������Ƌ����ȑҋ@(�{����RREP���߂��Ă���Α҂��Ȃ��Ă������Ԃ��҂��Ă���)
										// �҂����Ԃ�VALUE�ɍ��킹�čX�V
										AODV_Service.RING_TRAVERSAL_TIME = 2
												* AODV_Service.NODE_TRAVERSAL_TIME
												* (AODV_Service.TTL_VALUE + AODV_Service.TIMEOUT_BUFFER);

										AODV_Service.TTL_VALUE += AODV_Service.TTL_INCREMENT;
									}

								}

							});
							// �w��̎��Ԓ�~����
							try {
								Thread.sleep(AODV_Service.RING_TRAVERSAL_TIME);
							} catch (InterruptedException e) {
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

	}

//	public static	SelectFileDialog	_dlgSelectFile;
//
//
//	private	void	SelectFile()
//	{
//		//�����ŉ�ʉ�]���Œ肷�ׂ��i��ʂ��Œ肳��Ă��Ȃ��Ȃ�j
//
//		_dlgSelectFile = new SelectFileDialog(context,new Handler(),editTextToBeSent);
//		_dlgSelectFile.Show("/data/data/jp.ac.ehime_u.cite.udptest/files/");
//	}
	
	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onPause()
	{
//		if(_dlgSelectFile != null)
//			_dlgSelectFile.onPause();
		// unbindService
		unbindService(connection);
		// unsetListener
		unregisterReceiver(receiver);
		super.onPause();
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	public void onDestroy(){
		super.onDestroy();
	}
	
	// bind�R�l�N�V����
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			mAODV_Service = null;
		}
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			mAODV_Service = ((AODV_Service.AODV_ServiceBinder)service).getService();
		}
	};
	
	// Service->Android �ʐM�p
	public class BroadcastReceiverAODV_Activity extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			String mes = intent.getStringExtra(context.getString(R.string.AODV_ActivityKey));
			text_view_received.append(mes);
			text_view_received.setSelection(text_view_received.getText().toString().length());
		}

	}


	// ���j���[�̒ǉ�
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean ret = super.onCreateOptionsMenu(menu);

		menu.add(0 , Menu.FIRST , Menu.NONE
				, getString(R.string.Bluetooth)).setIcon(android.R.drawable.ic_menu_crop);
		menu.add(0 , Menu.FIRST + 1 ,Menu.NONE
				, "CALL"+getString(R.string.menu_finish)).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0 , Menu.FIRST + 2 ,Menu.NONE
				, "Image");

		return ret;
	}

	// ���j���[�������ꂽ�Ƃ�
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        // ���[�g�e�[�u�����j���[�������ꂽ�Ƃ�
        case Menu.FIRST:
            //�ʂ�Activity���N��������
//            Intent intent = new Intent();
//
//            String package_name = "jp.ac.ehime_u.cite.remotecamera.ImageViewerActivity";
//            String class_name = package_name.substring(0, package_name.lastIndexOf('.'));
//            Log.d("Test",package_name + " / " +class_name);
//            intent.setAction(Intent.ACTION_VIEW);
//            intent.setClassName(
//                    "jp.ac.ehime_u.cite.remotecamera",
//                    "jp.ac.ehime_u.cite.remotecamera.ImageViewerActivity");
//            startActivity(intent);
            
            // Bluetooth.on/off
            // If the adapter is null, then Bluetooth is not supported
//            if (mBluetoothAdapter == null) {
//                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
//            }
//            else{
//	            // If BT is not on, request that it be enabled.
//	            // setupChat() will then be called during onActivityResult
//	            if (!mBluetoothAdapter.isEnabled()) {
//	                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//	                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
//	            // Otherwise, BT to OFF.
//	            // End BTService
//	            } else {
//	            	if (mChatService != null) mChatService.stop();
//	                mBluetoothAdapter.disable();
//	            }
//            }
            
        	return true;
        // �I�����j���[�������ꂽ�Ƃ�
        case Menu.FIRST + 1:

//            Intent intent1 = new Intent();
//            intent1.setAction(Intent.ACTION_CALL);
//            intent1.setData(Uri.parse("CameraCapture:0_300_200_122.11.1.1"));	// TASK:�Z�̕������Z�b�g
//            intent1.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
//            intent1.putExtra("ID", 1);
//            AODV_Activity.context.startActivity(intent1);
            //Activity�I��
            //this.moveTaskToBack(true);

        	//udpListenerThread.destroy();
//            Intent intent1 = new Intent();
//            intent1.setAction(Intent.ACTION_CALL);
//            intent1.setData(Uri.parse("CameraCapture:0_300_200_133.11.34.16"));	// TASK:�Z�̕������Z�b�g
//            intent1.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
//            intent1.putExtra("ID", 100);
//            AODV_Activity.context.startActivity(intent1);

        	finish();

            return true;
        case Menu.FIRST + 2:
        	// �ȈՃe�X�g�p
            StringBuilder builder = new StringBuilder(1000);
            for(int i=0;i<1000;i++)builder.append('b');
            final String text = builder.toString();
            editTextToBeSent.setText(text);
            text_view_received.append(editTextToBeSent.getText().length()+"\n");
        
        	break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Log.d(this.getLocalClassName(), "onActivityResult " + resultCode);
//        switch (requestCode) {
//        case REQUEST_ENABLE_BT:
//            // When the request to enable Bluetooth returns
//            if (resultCode == Activity.RESULT_OK) {
//                // Bluetooth is now enabled, so set up a chat session
//                setupChat();
//                
//                // Only if the state is STATE_NONE, do we know that we haven't started already
//                if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
//                  // Start the Bluetooth chat services
//                  mChatService.start();
//                }
//            } else {
//                // User did not enable Bluetooth or an error occured
//                Log.d(this.getLocalClassName(), "BT not enabled");
//                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
    

	// ���O�̕\���pEditText�̃T�C�Y����ʃT�C�Y�ɍ��킹�ē��I�Ɍ���
	// OnCreate()�ł͂܂�View�����C�A�E�g������������Ă��Ȃ����߁H
	// View�T�C�Y�Ȃǂ̎擾���s��
/*	@Override
//	public void onWindowFocusChanged(boolean hasFocus) {
//		super.onWindowFocusChanged(hasFocus);
//
//		// received��Y���W���擾 * �^�C�g���o�[,�X�e�[�^�X�o�[�̉���0���� *
//		final EditText text_view_received = (EditText) findViewById(R.id.textViewReceived);
//		final int received_top = text_view_received.getTop();
//
//		// Clear�̃T�C�Y���擾
//		final Button clear_button = (Button) findViewById(R.id.buttonClear);
//		final int clear_height = clear_button.getHeight();
//
//		// ��ʃT�C�Y���擾 *�^�C�g���o�[,�X�e�[�^�X�o�[�܂�*
//		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
//		Display display = wm.getDefaultDisplay();
//		final int display_height = display.getHeight();
//
//		Log.d("gamen_size","height:"+display_height+",wifth"+display.getWidth());
//
//		// �^�C�g��+�X�e�[�^�X�o�[�̃T�C�Y��50�Ɖ���A�s���S�ȓ��I����
//		text_view_received.setHeight(display_height - received_top
//				- clear_height - 50);
//	}
*/


	// ���M��IP�����[�J���t�@�C���ɕۑ�
	private void save_ip_to_local(String s) {

		try {
			OutputStream out = openFileOutput("ip.txt", MODE_APPEND
					| MODE_PRIVATE);
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,
					"UTF-8"));
			writer.append(s + "\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}




	// ���Mtext�̐擪�ɁAAODV�Ƃ͖��֌W�ł��郁�b�Z�[�W�^�C�v0��}������
	// �ȉ��̓t�H�[�}�b�g�A[����]�͔z����̈ʒu������
	// [0]		:���b�Z�[�W�^�C�v0
	// [1-4]	:����A�h���X
	// [5-8]	:���M���A�h���X
	// [9-?]	:�f�[�^
	private byte[] addMessageTypeString(byte[] message,byte[] toIPAddress,byte[] my_address) {
		byte[] new_message = new byte[1 + 4 + 4 + message.length];

		new_message[0] = 0; // ���b�Z�[�W�^�C�v0
		System.arraycopy(toIPAddress, 0, new_message, 1, 4);
		System.arraycopy(my_address, 0, new_message, 1+4, 4);
		System.arraycopy(message, 0, new_message, 1+4+4, message.length);

		return new_message;
	}

	// �t�@�C�����M�p�^�C�v10�₻�̑�����f�[�^��t������
	// �ȉ��̓t�H�[�}�b�g�A[����]�͔z����̈ʒu������
	// [0]		: ���b�Z�[�W�^�C�v10
	// [1-4]	: ����A�h���X
	// [5-8]	: ���M���A�h���X
	// [9-12]	: �����������Ԗڂ̃f�[�^��	* ���̃��\�b�h�ł͑�����Ȃ� *
	// [13-16]	: �����ɕ���������
	// [17-20]	: �t�@�C�����̃T�C�Y
	// [21-??]	: �t�@�C����(�ϒ�)
	// [??-??]	: �t�@�C���f�[�^(�ϒ�,�ő�63K[byte]) * ���̃��\�b�h�ł͑�����Ȃ� *
	private byte[] addMessageTypeFile(int fileSize,byte[] toIPAddress,byte[] my_address,
			String fileName,int step){

		byte[] fileName_b = fileName.getBytes();	// �t�@�C������byte��
		byte[] fileName_size_b = intToByte(fileName_b.length);	// byte�^�t�@�C�����̃T�C�Y��byte��
		byte[] step_b = intToByte(step);			// ��������byte��

		byte[] new_message = new byte[21 + fileName_b.length + fileSize];

		new_message[0] = 10;	// ���b�Z�[�W�^�C�v10
		System.arraycopy(toIPAddress, 0, new_message, 1, 4);
		System.arraycopy(my_address, 0, new_message, 5, 4);
		// [9-12] �����������Ԗڂ̃f�[�^�� �������Ȃ�
		System.arraycopy(step_b, 0, new_message, 13, 4);
		System.arraycopy(fileName_size_b, 0, new_message, 17, 4);
		System.arraycopy(fileName_b, 0, new_message, 21, fileName_b.length);
		// [??-??] �t�@�C���f�[�^�������Ȃ�

		return new_message;
	}

	public void sendMessage(byte[] destination_next_hop_address_b, int hop_count, int destination_port
			, byte[] destination_address_b, byte[] source_address_b, Context context_){

		//editTextToBeSent = (EditText)findViewById(R.id.editTextToBeSent);
		final String text = editTextToBeSent.getText().toString();
		int index;
		
//		try{
//			// �Â����鑗�M�f�[�^���폜
//			while( (index=searchLifeTimeEmpty()) != -1){
//				try {
//					AODV_Activity.file_manager.get(index).file_in.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//				try {
//					AODV_Activity.file_manager.get(index).file.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//				AODV_Activity.file_manager.remove(index);
//			}
//			// �t�@�C�������M���Ȃ瑗�M���~
//			if(searchProgress(text, destination_address_b) != null){
//				Log.d("FILE_SEND","this_file_sending_now");
//			}
//			else{
//				// �t�@�C���I�[�v��
//				FileManager	files = new FileManager(text, destination_address_b,
//							source_address_b, context_);
//
//				// Log.d �J�n����
//				Date date = new Date();
//				SimpleDateFormat sdf = new SimpleDateFormat("MMdd'_'HHmmss");
//
//				String log = "time:"+sdf.format(date);
//				Log.d("SEND_T",log);
//
//				// �����p�P�b�g�̍ŏ��̂P�𑗐M
//				files.fileSend(source_address_b, destination_next_hop_address_b, destination_port);
//
//				// �ߒ���ێ� *������,�p�P�b�g��1�݂̂ł��đ����L�肤��̂ŕK�v*
//				files.add();
//
//				// �^�C���A�E�g���N��
//				final int time = 2 * NODE_TRAVERSAL_TIME * (hop_count + TIMEOUT_BUFFER);
//				final int step = files.file_next_no;
//				final byte[] data = files.buffer;
//				final byte[] dest_next_hop_add = destination_next_hop_address_b;
//				final int port_ = destination_port;
//				final String name = files.file_name;
//				final byte[] dest_add = files.destination_address;
//
//				final Handler mHandler = new Handler();
//
//				try {
//					new Thread(new Runnable() {
//
//						int wait_time = time;
//						int resend_count = 0;
//						int prev_step = step;
//						byte[] buffer = data;
//						byte[] destination_next_hop_address_b = dest_next_hop_add;
//						int port = port_;
//						String file_name = name;
//						byte[] destination_address = dest_add;
//
//						// �đ�����
//						public void run() {
//							timer: while (true) {
//
//								mHandler.post(new Runnable() {
//									public void run() {
//										SendByteArray.send(buffer, destination_next_hop_address_b);
//									}
//
//								});
//								// �w��̎��Ԓ�~����
//								try {
//									Thread.sleep(wait_time);
//								} catch (InterruptedException e) {
//								}
//
//								resend_count++;
//
//								// ���[�v�𔲂��鏈��
//								if (resend_count == MAX_RESEND) {
//									break timer;
//								}
//								FileManager files = searchProgress(file_name, destination_address);
//								if(files == null){
//									break timer;
//								}
//								else{
//									if( files.file_next_no != prev_step){
//										break timer;
//									}
//								}
//
//							}
//						}
//					}).start();
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		} catch (FileNotFoundException e){
			// �t�@�C�����J���Ȃ��ꍇ
			// **********�e�L�X�g���b�Z�[�W:type0�Ƃ��đ��M*********

			// ���b�Z�[�W�^�C�v0,����A�h���X,���M���A�h���X,���b�Z�[�WID��擪�ɕt��
			byte[] buffer = addMessageTypeString(text.getBytes(),
					destination_address_b, source_address_b);

			// ���Mtry
			mAODV_Service.send(buffer, destination_next_hop_address_b);
//		}


	}

	// �t�@�C����,���悪�������o�߂�Ԃ�
	// ���݂��Ȃ��ꍇ��null��Ԃ�
//	public static FileManager searchProgress(String name,byte[] dest_add){
//		synchronized(AODV_Activity.fileManagerLock){
//			for(int i=0; i<AODV_Activity.file_manager.size();i++){
//				if( name.equals(AODV_Activity.file_manager.get(i).file_name)
//						&& Arrays.equals(dest_add, AODV_Activity.file_manager.get(i).destination_address)){
//					return AODV_Activity.file_manager.get(i);
//				}
//			}
//
//			return null;
//		}
//	}

	// �������Ԃ������ł���index��Ԃ�
	// ���݂��Ȃ��ꍇ��-1
//	public static int searchLifeTimeEmpty(){
//		synchronized(AODV_Activity.fileManagerLock){
//			long now = new Date().getTime();
//			for(int i=0; i<AODV_Activity.file_manager.size();i++){
//				if( AODV_Activity.file_manager.get(i).life_time < now ){
//					return i;
//				}
//			}
//			return -1;
//		}
//	}

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
	public byte[] getByteAddress(String str){

		// ����
		String[] s_bara = str.split("\\.");

		byte[] b_bara = new byte[s_bara.length];
		for(int i=0;i<s_bara.length;i++){
			b_bara[i] = (byte)Integer.parseInt(s_bara[i]);
		}
		return b_bara;
	}
	
	// Log.d
	public static void logD(String mes){
//		try {
//			FileOutputStream out = context.openFileOutput("LogD.txt",MODE_PRIVATE | MODE_APPEND);
//			out.write((mes+System.getProperty("line.separator")).getBytes());
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	public static void logD(byte[] data){
//		try {
//			FileOutputStream out = context.openFileOutput("LogD.txt",MODE_PRIVATE | MODE_APPEND);
//			out.write(data);
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	public static void logD(byte[] data,String name){
//		try {
//			FileOutputStream out = context.openFileOutput(name,MODE_PRIVATE | MODE_APPEND);
//			out.write(data);
//			out.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
}