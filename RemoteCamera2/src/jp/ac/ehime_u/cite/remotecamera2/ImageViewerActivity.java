package jp.ac.ehime_u.cite.remotecamera2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageViewerActivity extends Activity {

	// �����o�ϐ�
	private static ImageView view;
	private static Bitmap bmp;
	private Context context;
	private Handler handler;

	// �eActivity����ڍs���Ă��������o�ϐ� *�v�`�F�b�N*
	//private String calling_file_name;
	private String calling_address;
	protected static int select_image_no = 0;
	//protected static ArrayList<String> image_name_list = new ArrayList<String>();
	protected static ArrayList<String> address_list = new ArrayList<String>();
	protected static boolean draw_switch;

	// �C���e���g����p�ϐ�
	private static int prev_receive_intent_id = -1;
	private static String prev_receive_intent_package_name = null;
	protected int send_intent_id = 0;
	private static Semaphore semaphore = null;
	private static int trace_count;
	private static boolean trace_flag;
	byte[] image_data;
	ByteArrayInputStream in_stream;

	// �Â��t�@�C���̍폜
	//protected static int DELETE_TIME; // [ms]

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		if(view == null){
			view = new ImageView(this);
			setContentView(view);
		}
		context = this;
		draw_switch = false;
		bmp = null;

		//DELETE_TIME = getResources().getInteger(R.integer.DELETE_TIME);
		if(semaphore == null)
			semaphore = new Semaphore(1);
		trace_count = 0;
		trace_flag = false;
		handler = new Handler();

		// ����ʂ���J�ڂ����Ƃ���onNewIntent��ʂ�Ȃ����߁A������ŏ���
		Intent intent = getIntent();
		if(intent!=null){
			// MAIN(�ʏ�̃A�v���N��)�łȂ����@�ŋN������Ă���Ȃ�
			if(intent.getAction() != Intent.ACTION_MAIN){
				// CATEGORY_LAUNCHER���܂܂�ĂȂ����onNewIntent()�����s
				if(intent.getCategories() != null){
					if(intent.getCategories().contains(Intent.CATEGORY_LAUNCHER) != true)
						onNewIntent(intent);
				}else{
					onNewIntent(intent);
				}
			}
		}
	}

	@Override
	public void onNewIntent(Intent intent){
		super.onNewIntent(intent);

		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss SSS", Locale.JAPANESE);
		Log.d("onNewIntent",sdf.format(date));

		// Trace
		if(semaphore.availablePermits() != 0){
			//trace_count ++;
			if((trace_flag == false) && (trace_count > 100) && (trace_count < 200)){
				Debug.startMethodTracing(String.valueOf(trace_count));
				trace_flag = true;
				trace_count = 0;
			}
		}

		String package_name = intent.getStringExtra("PACKAGE");
		int intent_id = intent.getIntExtra("ID", 0);

		final Uri uri = intent.getData();

		// ���C���e���g�̑��d�����h�~ �O��ƃp�b�P�[�W���܂���ID���قȂ��Ă���Ύ�
		if( (package_name != prev_receive_intent_package_name)
				|| intent_id != prev_receive_intent_id){

			// ���O�̃p�b�P�[�W��,ID�Ƃ��ċL�^
			prev_receive_intent_package_name = package_name;
			prev_receive_intent_id = intent_id;

			calling_address = intent.getStringExtra("SOURCE_ADDRESS");

			//deleteOldFile();
			//moveFile();
			//deleteAodvFile(calling_file_name);

			// �C���e���g�̏�����s
			if(calling_address != null){

				int index_result_file;
				int index_result_add;

				// ���Ƀ��X�g�ɖ������`�F�b�N
				if((index_result_add = searchAddress(calling_address)) == -1){	// �A�h���X���������
					// �A�h���X�̋L�^
					address_list.add(calling_address);

					// ���A�h���X�Ȃ�C��ʂւ̕\��
					if(address_list.size() == 1){
						// �p�[�~�b�g�����p�\�ȏꍇ�̂ݎ��s
						if(semaphore.tryAcquire()){
							HashMap<String,byte[]> dataMap = (HashMap<String,byte[]>)intent.getSerializableExtra("HashMap");
							image_data = dataMap.get("DATA");
//							draw_switch = true;
							new Thread(new Runnable() {
								@Override
								public void run() {
									doDraw(handler);
								}
							}).start();
						}
					}
				}
				else{
					// �I�𒆂̉摜�Ȃ��
					if( index_result_add == select_image_no){
						// �J������
						if(semaphore.tryAcquire()){
//							draw_switch = true;
							HashMap<String,byte[]> dataMap = (HashMap<String,byte[]>)intent.getSerializableExtra("HashMap");
							image_data = dataMap.get("DATA");

							new Thread(new Runnable() {
								@Override
								public void run() {
									doDraw(handler);
								}
							}).start();
						}
					}
				}
			}
		}


	}

	@Override
	public void onResume(){
		super.onResume();

//		// �\���̍X�V
//		if( draw_switch){
//			draw_switch = false;
//
//			// �p�[�~�b�g�����p�\�Ȏ��̂ݎ��s
//			if(semaphore.tryAcquire()){
//				// �󗝃C���e���g���s����N�����Ȃ��悤�ɃX���b�h���𐧌����ă}���`�X���b�h����
//				new Thread(new Runnable() {
//					@Override
//					public void run() {
//						doDraw(handler);
//					}
//				}).start();
//				semaphore.release();	// �p�[�~�b�g�̉��
//			}
//		}
	}

//	@Override
//    public boolean onTouchEvent(MotionEvent event){
//		trace_count += 101;
//    	if( event.getAction() == MotionEvent.ACTION_DOWN){
//    		// ��ʃT�C�Y�擾
//    		WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
//    		Display disp = wm.getDefaultDisplay();
//
//    		Log.d("event","X:"+event.getX()+",windowX:"+disp.getWidth());
//
//    		// ���������^�b�`�����Ȃ�
//    		if( event.getX() < disp.getWidth()/2 ){
//    			// �I���i���o�[���f�N�������g
//    			if(select_image_no == 0){
//    				select_image_no = image_name_list.size()-1;
//    			}
//    			else{
//    				select_image_no--;
//    			}
//    		}
//    		// �E�������^�b�`�����Ȃ�
//    		else{
//    			// �I���i���o�[���C���N�������g
//    			select_image_no++;
//    			if(select_image_no >= image_name_list.size()){
//    				select_image_no = 0;
//    			}
//    		}
//    		// �\���X�V
//    		doDraw(handler);
//    	}
//
//    	return true;
//	}

	// view�̍X�V����
	private void doDraw(Handler handler){

		if(bmp != null){
			bmp.recycle();
			bmp = null;
		}

		in_stream = new ByteArrayInputStream(image_data);
		bmp = BitmapFactory.decodeStream(in_stream);

		handler.post(new Runnable() {

			@Override
			public void run() {
				view.setImageBitmap(bmp);
				//setContentView(view);
				setTitle(address_list.get(select_image_no));

				// stop tracing
				if(trace_flag)
					Debug.stopMethodTracing();
				semaphore.release();

			}
		});
	}

	// ���j���[�̒ǉ�
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean ret = super.onCreateOptionsMenu(menu);

		menu.add(0, Menu.FIRST, Menu.NONE, "trace+101")
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, Menu.FIRST+1, Menu.NONE, "exit");

		return ret;
	}

	// ���j���[�������ꂽ�Ƃ�
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case Menu.FIRST:
//			// AODV�ɃC���e���g�𓊂���
//			Intent intent = new Intent();
//			intent.setClassName("jp.ac.ehime_u.cite.udptest",
//					"jp.ac.ehime_u.cite.udptest.RouteActivity");
//			intent.setAction(Intent.ACTION_SENDTO);
//			intent.setData(Uri.parse("connect:" + calling_address));
//			intent.putExtra("TASK", "TASK:CameraCapture:STOP");
//			intent.putExtra("PACKAGE", "jp.ac.ehime_u.cite.remotecamera");
//			intent.putExtra("ID", send_intent_id);
//			startActivity(intent);
//
//			send_intent_id++;
			trace_count = 110;
			return true;
		case Menu.FIRST+1:
			finish();
			return true;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	// �t�@�C���폜
//	private void deleteOldFile(){
//
//		// �t�@�C���ꗗ�̎擾
//		File[] files = new File("/data/data/jp.ac.ehime_u.cite.remotecamera/files/").listFiles();
//
//		// ���ݎ���
//		long now_time = new Date().getTime();
//
//		for(int i=0; i<files.length; i++){
//
//			// �摜Viewer�ɓo�^�ς݂Ȃ�폜���Ȃ�
//			if( searchFile(files[i].getName()) == -1){
//				// �f�B���N�g���ł͂Ȃ��A�����ł���t�@�C��������
//				if(!files[i].isDirectory()){
//					long last_update_time = files[i].lastModified();
//
//					// �ŏI�X�V��������폜���Ԉȏ�̎��Ԃ��߂��Ă����
//					if( (now_time - last_update_time) > DELETE_TIME){
//						deleteFile(files[i].getName());
//					}
//				}
//			}
//		}
//	}

	// �t�@�C���ړ� *udptest->remotecamera �p*
//	private void moveFile(){
//
//		// �ϐ��錾
//		Context aodv_c = null;
//
//		try {
//			// �ǂݍ��݌��̃R���e�L�X�g�쐬
//			aodv_c = createPackageContext("jp.ac.ehime_u.cite.udptest",0);
//			// �ǂݍ��݌��̃t�@�C���X�g���[�����쐬���A�`���l�����擾
//			FileChannel in_channel = aodv_c.openFileInput(calling_file_name).getChannel();
//
//			// ���l�ɏo�͐�
//			FileChannel out_channel= this.openFileOutput(calling_file_name, MODE_WORLD_READABLE | MODE_WORLD_WRITEABLE)
//								.getChannel();
//
//			// �]��
//			in_channel.transferTo(0, in_channel.size(), out_channel);
//
//			// �N���[�Y
//			in_channel.close();
//			out_channel.close();
//
//		} catch (NameNotFoundException e) {
//			e.printStackTrace();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

    // �摜Viewer�p ���X�g�Ɋ��ɑ��݂��邩�`�F�b�N
//	private int searchFile(String name){
//		for(int i=0;i<image_name_list.size();i++){
//			if(image_name_list.get(i).equals(name)){
//				return i;
//			}
//		}
//		return -1;
//	}

	// �摜Viewer�p ���X�g�Ɋ��ɑ��݂��邩�`�F�b�N
	private int searchAddress(String address){
		for(int i=0;i<address_list.size();i++){
			if(address_list.get(i).equals(address)){
				return i;
			}
		}
		return -1;
	}

	// �t�@�C���폜 *udptest��*
//	private void deleteAodvFile(String file_name){
//
//		// �폜���߃C���e���g�𔭍s
//		// AODV�ɖ����I�C���e���g�𓊂���
//        Intent intent = new Intent();
//        intent.setClassName(
//                "jp.ac.ehime_u.cite.udptest",
//                "jp.ac.ehime_u.cite.udptest.AODV_Activity");
//        intent.setAction(Intent.ACTION_DELETE);
//        intent.setData(Uri.parse("path:"+file_name));
//        intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.remotecamera");
//        intent.putExtra("ID", send_intent_id);
//        startActivity(intent);
//
//        send_intent_id++;
//	}


}
