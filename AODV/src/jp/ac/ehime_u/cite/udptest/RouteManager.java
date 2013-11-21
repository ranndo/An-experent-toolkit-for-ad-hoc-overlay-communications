package jp.ac.ehime_u.cite.udptest;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;



//��莞�Ԃ��Ƃ�HELLO���b�Z�[�W�̑��M�ƁA�����o�H�̐������Ԃ��`�F�b�N
//�ڑ��؂ꂪ�������ꍇ�ARERR��Precursor�ɑ��M����
//���łɁADELETE���Ԃ��������o�H���폜����
public class RouteManager implements Runnable {
	
	RouteTable route;
	int port;
	byte[] myAddress;
	Context context;
	
	// Service�Ƃ̘A�g�p�ϐ�
	private AODV_Service mAODV_Service;
	private boolean bindState;
	
	public RouteManager(int port_,Context context_) throws IOException{
		port = port_;
		
		StaticIpAddress sIp = new StaticIpAddress(AODV_Activity.context);
		myAddress = sIp.getStaticIpByte();
		
		bindState = false;
		context.bindService( new Intent( context, AODV_Service.class), connection, Context.BIND_AUTO_CREATE);
	}
	public void stop(){
		if(bindState == true){
			context.unbindService(connection);
		}
	}
	
	// �R�l�N�V��������
	private ServiceConnection connection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			mAODV_Service = null;
			bindState = false;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// TODO �����������ꂽ���\�b�h�E�X�^�u
			mAODV_Service = ((AODV_Service.AODV_ServiceBinder)service).getService();
			bindState = true;
		}
	};
	
	@Override
	public void run() {
		while(true){
			if(!bindState){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO �����������ꂽ catch �u���b�N
					e.printStackTrace();
				}
				continue;
			}
			
			// �o�H�����݂���Ȃ��
			if(mAODV_Service.getRouteSize() > 0){
				
				// �o�H���ێ�����HELLO���b�Z�[�W�̑��M
				// ��莞�ԓ��Ƀu���[�h�L���X�g���Ă���Ύ��s���Ȃ�
				if(mAODV_Service.getDoBroadcast() == false){
					try {
						new RREP().send(mAODV_Service.getSeqNum(), port, 
								AODV_Service.ALLOWED_HELLO_LOSS *
								AODV_Service.HELLO_INTERVAL
								, myAddress);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				
				// �����o�H�̐������ԃ`�F�b�N
				for(int i=0;i<mAODV_Service.getRouteSize();i++){
					
					route = mAODV_Service.getRoute(i);
					long now = new Date().getTime();
					
					// �L���łȂ��o�H���폜����鎞�Ԃł��邩
					if( (route.stateFlag != 1) && (route.lifeTime < now)){
						
						// �����o�H�̍폜
						mAODV_Service.removeRoute(i);
						
						// �폜�ɂ��i�̃Y�����C��
						i--;
					}
					
					// �L���o�H�������o�H�ɂȂ鎞�ԁi�����j�ł��邩
					else if( (route.stateFlag == 1) && (route.lifeTime < now)){
						
						// ������
						route.stateFlag = 2;
						route.lifeTime  = (now+AODV_Service.DELETE_PERIOD);
						route.toSeqNum++;
						
						// �㏑��
						mAODV_Service.setRoute(i, route);
						
						// ���[�J�����y�A���s����z�b�v�����H
						if(route.hopCount <= AODV_Service.MAX_REPAIR_TTL){
							mAODV_Service.localRepair(route,port,myAddress,mAODV_Service);
						}
						else{
							// RERR�̑��M
							new RERR().RERR_Sender(route,port);
							
							final byte[] destination_address = route.toIpAdd;
							mAODV_Service.appendMessageOnActivity(AODV_Service.getStringByByteAddress(destination_address)+" disconnected\n");
						}
					}
				}
			}
			
			mAODV_Service.setDoBroadcast(false);
			
			try { // ��莞�Ԃ��ƂɃ��[�v�����A�������Ԃ͖����H
				Thread.sleep(AODV_Service.HELLO_INTERVAL);
			} catch (InterruptedException ex2) {
				ex2.printStackTrace();
			}	
		}
	}
}
