package jp.ac.ehime_u.cite.remotecamera2;

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
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.ac.ehime_u.cite.udptest.StaticIpAddress;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class Main extends Activity {
	private static final int MENU_AUTOFOCUS = Menu.FIRST + 1;

	// �����o�ϐ�
	// EditText�N���X(ip���͂�{�^�������Ȃǃ��[�U�[�̓��͂��������邽��)
	private EditText editTextSrc;
	private EditText editTextSrcPort;
	private EditText editTextDest;
	private EditText editTextDefaultPictureName;
	private EditText editTextPictureSizeWidth;
	private EditText editTextPictureSizeHeight;
	private CheckBox checkBoxWatching;

//	private String calling_file_name;

	// �C���e���g����p�ϐ�
	protected static int send_intent_id = 0;

	// �t�@�C���֘A
//	protected static String file_name;
//	protected static String file_path;

	// �qActivity�ɓn���R���e�L�X�g
	protected static Context context;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		context = this;

		// �N�����Ƀ\�t�g�L�[�{�[�h�̗����オ���h��
		this.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// �A�N�e�B�r�e�B�Ƀr���[�O���[�v��ǉ�����
		setContentView(R.layout.main);

		// ID�ɑΉ�����View���擾�A�^���قȂ�̂ŃL���X�g
		editTextDest = (EditText) findViewById(R.id.editTextDest);
		editTextSrc = (EditText) findViewById(R.id.editTextSrc);
		editTextSrcPort = (EditText) findViewById(R.id.editTextSrcPort);
		editTextDefaultPictureName = (EditText) findViewById(R.id.editTextDefaultFileName);
		editTextPictureSizeWidth = (EditText) findViewById(R.id.editTextPictureSizeWidth);
		editTextPictureSizeHeight = (EditText) findViewById(R.id.editTextPictureSizeHeight);
		checkBoxWatching = (CheckBox) findViewById(R.id.checkBoxWatching);

		// ���g�̃A�h���X���擾
		StaticIpAddress sIp = new StaticIpAddress(this);
		editTextSrc.setText(sIp.getStaticIp());

		// �f�t�H���g�ʐ^�t�@�C�������擾
		// �ߋ��ɐݒ肵�����O������Ȃ炻����p�����p *���[�J���t�@�C���𗘗p
		editTextDefaultPictureName.setText(getPreDefaultFileName(editTextSrc.getText().toString()));


		// �t�@�C������ݒ莞�Ƀ��[�J���t�@�C���ɕۑ�����C�x���g��o�^
		editTextDefaultPictureName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
				setPreDefaultFileName(editTextDefaultPictureName.getText().toString());
			}
		});

		// �v���ʐ^�T�C�Y�Ɏ��g�̃T�C�Y��ݒ�(������)
		WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
		Display disp = wm.getDefaultDisplay();

		// ����������{�ɐݒ� X>Y
		if(disp.getHeight() > disp.getWidth()){
			// �c���t�ɃZ�b�g
			editTextPictureSizeHeight.setText(String.valueOf(disp.getWidth()));
			editTextPictureSizeWidth.setText(String.valueOf(disp.getHeight()));
		}
		else{	// �����̂ق����L���f�B�X�v���C�Ȃ炻�̂܂�
			editTextPictureSizeHeight.setText(String.valueOf(disp.getHeight()));
			editTextPictureSizeWidth.setText(String.valueOf(disp.getWidth()));
		}

		// ���MButton�A���l��ID����擾
		Button buttonSend = (Button) findViewById(R.id.buttonSend);

		// �N���b�N������o�^
		// AODV�ɐڑ��v���E�ʐ^�v���𓊂���
		buttonSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// ����̕�������擾
				String destination_s = editTextDest.getText().toString();

				// �ʐ^�T�C�Y���擾
				String size_width = editTextPictureSizeWidth.getText().toString();
				String size_height= editTextPictureSizeHeight.getText().toString();

				// �ÖٓI�C���e���g�𓊂���
	            Intent intent = new Intent();
	            intent.setAction(Intent.ACTION_SENDTO);
	            intent.setData(Uri.parse("connect:"+destination_s));
	            intent.putExtra("TASK", "TASK:CameraCapture:"+getWatchingLoopCheck()+"_"
	            		+size_width+"_"+size_height);
	            intent.putExtra("PACKAGE","jp.ac.ehime_u.cite.remotecamera");
	            intent.putExtra("ID", send_intent_id);

	            startActivity(intent);
			}
		});
    }

    @Override
    public void onDestroy(){
    	super.onDestroy();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_AUTOFOCUS, Menu.NONE, "service_test");
		menu.add(Menu.NONE, MENU_AUTOFOCUS+1, Menu.NONE, "image_view");
		menu.add(Menu.NONE, MENU_AUTOFOCUS+2, Menu.NONE, "END");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean rc = true;
		switch (item.getItemId()) {
		case MENU_AUTOFOCUS:



			break;

		case MENU_AUTOFOCUS+1:

            Intent intent1 = new Intent();
            intent1.setClassName(
                    "jp.ac.ehime_u.cite.remotecamera2",
                    "jp.ac.ehime_u.cite.remotecamera2.ImageViewerActivity");
            intent1.setAction(Intent.ACTION_CALL);
            intent1.setData(Uri.parse("CameraCapture:1_1280_720_133.21.3.1"));	// TASK:�Z�̕������Z�b�g
            intent1.putExtra("PACKAGE","jp.ac.ehime_u.cite.udptest");
            intent1.putExtra("ID", send_intent_id);
            startActivity(intent1);
            send_intent_id++;

			break;
		case MENU_AUTOFOCUS+2:
			Context c1 = null;
			try {
				c1 =  createPackageContext("jp.ac.ehime_u.cite.image",0);
			} catch (NameNotFoundException e) {
				// TODO �����������ꂽ catch �u���b�N
				e.printStackTrace();
			}

			try {
				OutputStream o = c1.openFileOutput("test.jpg",MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE);
				o.write(1);
				o.close();

				c1.openFileInput("test.jpg");
				Toast.makeText(this, "�݂���", Toast.LENGTH_LONG).show();

			} catch (FileNotFoundException e) {
				// TODO �����������ꂽ catch �u���b�N

			} catch (IOException e) {
				// TODO �����������ꂽ catch �u���b�N
				e.printStackTrace();
			}

            //finish();
		default:
			rc = super.onOptionsItemSelected(item);
			break;
		}
		return rc;
	}


	// �ȑO�̃f�t�H���g�l�[����Ԃ�
	// ������Ύ��g�̃A�h���X�𗘗p
    private CharSequence getPreDefaultFileName(String ip_address) {

    	try {
			InputStream in = openFileInput("fileName.txt");
			BufferedReader reader = new BufferedReader(new InputStreamReader(in,"UTF-8"));
			String s = reader.readLine();

			reader.close();
			in.close();
			return s;

		} catch (Exception e) {	// IOException+FileNotFoundException+...
			return ip_address.replaceAll("\\.", "_");
		}
	}


    // �f�t�H���g�l�[���Ƃ��ă��[�J���t�@�C���ɕێ�
    private void setPreDefaultFileName(String name){
		try {
			OutputStream out = openFileOutput("fileName.txt",MODE_PRIVATE);
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
			writer.write(name);

			writer.close();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    // WatchingLoop�̃`�F�b�N�擾
    // �߂�l�FString�^��"1"(�I��),"0"(�I�t)
    private String getWatchingLoopCheck(){
    	if(checkBoxWatching.isChecked()){
    		return "1";
    	}
    	return "0";
    }
}
