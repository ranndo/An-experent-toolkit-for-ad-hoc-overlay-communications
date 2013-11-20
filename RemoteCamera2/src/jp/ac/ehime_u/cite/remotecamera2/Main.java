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

	// メンバ変数
	// EditTextクラス(ip入力やボタン押下などユーザーの入力を処理するため)
	private EditText editTextSrc;
	private EditText editTextSrcPort;
	private EditText editTextDest;
	private EditText editTextDefaultPictureName;
	private EditText editTextPictureSizeWidth;
	private EditText editTextPictureSizeHeight;
	private CheckBox checkBoxWatching;

//	private String calling_file_name;

	// インテント制御用変数
	protected static int send_intent_id = 0;

	// ファイル関連
//	protected static String file_name;
//	protected static String file_path;

	// 子Activityに渡すコンテキスト
	protected static Context context;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		context = this;

		// 起動時にソフトキーボードの立ち上がりを防ぐ
		this.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// アクティビティにビューグループを追加する
		setContentView(R.layout.main);

		// IDに対応するViewを取得、型が異なるのでキャスト
		editTextDest = (EditText) findViewById(R.id.editTextDest);
		editTextSrc = (EditText) findViewById(R.id.editTextSrc);
		editTextSrcPort = (EditText) findViewById(R.id.editTextSrcPort);
		editTextDefaultPictureName = (EditText) findViewById(R.id.editTextDefaultFileName);
		editTextPictureSizeWidth = (EditText) findViewById(R.id.editTextPictureSizeWidth);
		editTextPictureSizeHeight = (EditText) findViewById(R.id.editTextPictureSizeHeight);
		checkBoxWatching = (CheckBox) findViewById(R.id.checkBoxWatching);

		// 自身のアドレスを取得
		StaticIpAddress sIp = new StaticIpAddress(this);
		editTextSrc.setText(sIp.getStaticIp());

		// デフォルト写真ファイル名を取得
		// 過去に設定した名前があるならそれを継続利用 *ローカルファイルを利用
		editTextDefaultPictureName.setText(getPreDefaultFileName(editTextSrc.getText().toString()));


		// ファイル名を設定時にローカルファイルに保存するイベントを登録
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

		// 要求写真サイズに自身のサイズを設定(初期化)
		WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
		Display disp = wm.getDefaultDisplay();

		// 横向きを基本に設定 X>Y
		if(disp.getHeight() > disp.getWidth()){
			// 縦横逆にセット
			editTextPictureSizeHeight.setText(String.valueOf(disp.getWidth()));
			editTextPictureSizeWidth.setText(String.valueOf(disp.getHeight()));
		}
		else{	// 横幅のほうが広いディスプレイならそのまま
			editTextPictureSizeHeight.setText(String.valueOf(disp.getHeight()));
			editTextPictureSizeWidth.setText(String.valueOf(disp.getWidth()));
		}

		// 送信Button、同様にIDから取得
		Button buttonSend = (Button) findViewById(R.id.buttonSend);

		// クリック処理を登録
		// AODVに接続要求・写真要求を投げる
		buttonSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				// 宛先の文字列を取得
				String destination_s = editTextDest.getText().toString();

				// 写真サイズを取得
				String size_width = editTextPictureSizeWidth.getText().toString();
				String size_height= editTextPictureSizeHeight.getText().toString();

				// 暗黙的インテントを投げる
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
            intent1.setData(Uri.parse("CameraCapture:1_1280_720_133.21.3.1"));	// TASK:〇の部分をセット
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
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

			try {
				OutputStream o = c1.openFileOutput("test.jpg",MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE);
				o.write(1);
				o.close();

				c1.openFileInput("test.jpg");
				Toast.makeText(this, "みつかる", Toast.LENGTH_LONG).show();

			} catch (FileNotFoundException e) {
				// TODO 自動生成された catch ブロック

			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}

            //finish();
		default:
			rc = super.onOptionsItemSelected(item);
			break;
		}
		return rc;
	}


	// 以前のデフォルトネームを返す
	// 無ければ自身のアドレスを利用
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


    // デフォルトネームとしてローカルファイルに保持
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

    // WatchingLoopのチェック取得
    // 戻り値：String型で"1"(オン),"0"(オフ)
    private String getWatchingLoopCheck(){
    	if(checkBoxWatching.isChecked()){
    		return "1";
    	}
    	return "0";
    }
}
