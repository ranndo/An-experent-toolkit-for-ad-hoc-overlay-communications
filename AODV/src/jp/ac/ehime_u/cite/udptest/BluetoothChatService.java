/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.ac.ehime_u.cite.udptest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BluetoothChatService{
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChatMulti";

    // Member fields
    private final BluetoothAdapter mAdapter;
//    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private ArrayList<ConnectedDeviceManager> mConnThreads;
    
    // add to Sample by Student
    private AutoConnectingThread mAutoConnectingThread;
    
    /**
     * A bluetooth piconet can support up to 7 connections. This array holds 7 unique UUIDs.
     * When attempting to make a connection, the UUID on the client must match one that the server
     * is listening for. When accepting incoming connections server listens for all 7 UUIDs. 
     * When trying to form an outgoing connection, the client tries each UUID one at a time. 
     */
//    private ArrayList<UUID> mUuids;
    private ArrayList<UUID> mUuids;
    private boolean[] connectState;
    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    // fragment data id
    public static final int FRAGMENT_MTU = 1008;
    public static final byte FRAGMENT_DATA = 103;
    public static final int FRAGMENT_HEADER_SIZE = 10;
    public static final int FRAGMENT_HASH_SIZE = 16;
    
    // static ip exchange
    public static final byte STATIC_IP_REQUEST	= 101;
    public static final byte STATIC_IP_DATA		= 102;
    
    
    private Context context;
    private AODV_Service mAODV_Service;
    private byte[] my_address;
    
    // synchronized
    private Object mConnThreadsLock = new Object();
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothChatService(Context context_, AODV_Service binder, byte[] myAddress) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
//        mHandler = handler;
        mConnThreads = new ArrayList<ConnectedDeviceManager>();
        
        mUuids = new ArrayList<UUID>();
        // 7 randomly-generated UUIDs. These must match on both server and client.
        // mUuids.add(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        
        mUuids.add(UUID.fromString("b7746a40-c758-4868-aa19-7ac6b3475dfc"));
        mUuids.add(UUID.fromString("2d64189d-5a2c-4511-a074-77f199fd0834"));
        mUuids.add(UUID.fromString("e442e09a-51f3-4a7b-91cb-f638491d1412"));
        mUuids.add(UUID.fromString("a81d6504-4536-49ee-a475-7d96d09439e4"));
        mUuids.add(UUID.fromString("aa91eab1-d8ad-448e-abdb-95ebba4a9b55"));
        mUuids.add(UUID.fromString("4d34da73-d0a4-4f40-ac38-917e0a9dee97"));
        mUuids.add(UUID.fromString("5e14d4df-9c8a-4db7-81e4-c937564c86e0"));
        
        connectState = new boolean[7];
        for(int i=0;i<connectState.length;i++)connectState[i] = false;
        
        context = context_;
        mAODV_Service = binder;
        my_address = myAddress;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
//        mHandler.obtainMessage(AODV_Activity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
        
        // Start the thread to auto connection
        if (mAutoConnectingThread == null) {
        	mAutoConnectingThread = new AutoConnectingThread();
        	mAutoConnectingThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("ConnectTry\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Create a new thread and attempt to connect to each UUID one-by-one.    
        for (int i = 0; i < 7; i++) {
        	if(connectState[i] == false){
				try {

					mConnectThread = new ConnectThread(device, mUuids.get(i));
					mConnectThread.start();
					setState(STATE_CONNECTING);
				} catch (Exception e) {
				}
        	}
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device,UUID uuid) {
        if (D) Log.d(TAG, "connected");

        //Commented out all the cancellations of existing threads, since we want multiple connections.
        /*
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
         */
        
        // Start the thread to manage the connection and perform transmissions
        synchronized(mConnThreadsLock){
        	Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
	        while(it.hasNext()){
	        	ConnectedDeviceManager m = it.next();
	        	if(m.getMacAddress().equals(device.getAddress())){
	        		// return; // 既存の接続を残す
	        		// 既存の接続を削除して，新しい接続の登録を続ける
	        		m.getConnectedThread().cancel();
	        		UUID u = m.getUuid();
	        		for(int j=0;j<mUuids.size();j++){
	        			if(u.compareTo(mUuids.get(j)) == 0){
	        				connectState[j] = false;
	        			}
	        		}
	        		mConnThreads.remove(it);
	        		break;
	        	}
	        }
        }
        
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        // Add each connected thread to an array
        ConnectedDeviceManager connectedDevice = new ConnectedDeviceManager();
        connectedDevice.setConnectedThread(mConnectedThread);
        connectedDevice.setMacAddress(device.getAddress());
        connectedDevice.setDeviceName(device.getName());
        connectedDevice.setUuid(uuid);
        
        synchronized(mConnThreadsLock){
        	mConnThreads.add(connectedDevice);
        }
        mConnectedThread = null;

//        // Send the name of the connected device back to the UI Activity
//        Message msg = mHandler.obtainMessage(AODV_Activity.MESSAGE_DEVICE_NAME);
//        Bundle bundle = new Bundle();
//        bundle.putString(AODV_Activity.DEVICE_NAME, device.getName());
//        bundle.putString(AODV_Activity.DEVICE_ADDRESS, device.getAddress());
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    	
//    	synchronized(mConnThreadsLock){
//	    	for(int i=0;i<mConnThreads.size();i++){
//	    		ConnectedDeviceManager m = mConnThreads.get(i);
//	    		final int i_ = i;
//	    		if(m.getConnectedThread() == null){
//	    			mHandler.post(new Runnable() {
//	    				@Override
//	    				public void run() {
//	    					// TODO 自動生成されたメソッド・スタブ
//	    					EditText text_view_received = AODV_Activity.testtext;
//	    					text_view_received.append("mConnThreads"+i_+":null\n");
//	    					text_view_received.setSelection(text_view_received.getText().length());
//	    				}
//	    			});
//	    		}else{
//	    			mHandler.post(new Runnable() {
//	    				@Override
//	    				public void run() {
//	    					// TODO 自動生成されたメソッド・スタブ
//	    					EditText text_view_received = AODV_Activity.testtext;
//	    					text_view_received.append("mConnThreads"+i_+":NotNull\n");
//	    					text_view_received.setSelection(text_view_received.getText().length());
//	    				}
//	    			});
//	    		}
//	    	}
//    	}
//    	
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("connected!\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
//        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}	/* 全スレッド止めないと意味ないような… */
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        if (mAutoConnectingThread != null) {mAutoConnectingThread.cancel(); mAutoConnectingThread = null;}
        
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
//    public void write(final byte[] out){
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("Write255\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});
//    	new Thread(new Runnable() {
//			
//			@Override
//			public void run() {
//				write_in(out);
//			}
//		}).start();
//    }
//    public void write(final byte[] out, final byte[] ipAddress){
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("Write xxx\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});
//    	new Thread(new Runnable() {
//			@Override
//			public void run() {
//				write_in(out,ipAddress);
//			}
//		}).start();
//    }
    public void write(byte[] out) {
    	// When writing, try to write out to all connected threads 
    	synchronized(mConnThreadsLock){
    		Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
	    	while(it.hasNext()){
	    		try {
	    			ConnectedDeviceManager m = it.next();
	                // Create temporary object
	                ConnectedThread r;
	                // Synchronize a copy of the ConnectedThread
                    //if (mState != STATE_CONNECTED) return;
                    r = m.getConnectedThread();
	                // Perform the write unsynchronized
	                if(out.length > FRAGMENT_MTU){
	                	Thread f = new fragment_write(r, out);		/**** フラグメント処理が無駄に複数回実行されている...要修正 ****/
	                	f.start();
	                }
	                else
	                	r.write(out);
	    		} catch (Exception e) {  
	    		}
	    	}
    	}
    }
    public void write(byte[] out,byte[] ipAddress) {
    	// When writing, try to write out to all connected threads 
    	synchronized(mConnThreadsLock){
    		Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
	    	while(it.hasNext()) {
	    		ConnectedDeviceManager m = it.next();
	    		if(m.getIpAddress() != null)
		    		if(m.getIpAddress().equals(ipAddress)){
			    		try {
			                // Create temporary object
			                ConnectedThread r;
			                // Synchronize a copy of the ConnectedThread
		                    //if (mState != STATE_CONNECTED) return;
		                    r = m.getConnectedThread();
			                
			                // Perform the write unsynchronized
			                if(out.length > FRAGMENT_MTU){
			                	Thread f = new fragment_write(r, out);
			                	f.start();
			                }
			                else
			                	r.write(out);
			                break;
			    		} catch (Exception e) {    			
			    		}
		    		}
	    	}
    	}
    }
    
    /*
     * フラグメント化
     * 
     * 
     * サイズ数がFRAGMENT_MTUより大きければ、分割し、ヘッダを付加して分割送信する。
     * 更に、データの末尾にMD5ハッシュ値を追加する。この値はデータの一部として扱う。別にすると面倒なので。
     * 結合は受信ノードが直ちに行う。ハッシュ値を再計算し、データの末尾の値と比較する。
     * 
     * param:ipAddress == null -> broadcast
     */
    private class fragment_write extends Thread{
    	ConnectedThread r;
    	byte[] out;
        public fragment_write(ConnectedThread r_, byte[] out_) {
        	r = r_;
        	out = out_;
        }

        public void run() {
        	int length = out.length + FRAGMENT_HASH_SIZE;
        	r.fragmentId++;
        	int dataId = r.fragmentId;
        	byte sequenceNo = 0;
        	MessageDigest digest = null;
    		try {
    			digest = java.security.MessageDigest.getInstance("MD5");
    		} catch (NoSuchAlgorithmException e) {
    			// TODO 自動生成された catch ブロック
    			e.printStackTrace();
    		}
        	
        	// MD5 calculate
        	digest.update(out);
        	byte[] hash = digest.digest();
        	
        	// byte[]型に変換
        	byte[] byteLength = AODV_Activity.intToByte(length);
        	byte[] byteDataId = AODV_Activity.intToByte(dataId);
        	
        	// 分割して送信
        	int dataSize = FRAGMENT_MTU-FRAGMENT_HEADER_SIZE;
        	for(int i=0;i<length;i+=dataSize){
        		if(!r.isAlive())
        			break;
        		
        		sequenceNo++;
        		
        		int thisSize = ((i+dataSize) > length)? length-i:dataSize; 
        		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(thisSize);
        		
        		// ヘッダーを追加
        		outputStream.write(FRAGMENT_DATA);
        		outputStream.write(sequenceNo);
        		outputStream.write(byteDataId, 0, byteDataId.length);
        		outputStream.write(byteLength, 0, byteLength.length);
        		// データを追加
        		/*
        		 * 全体として起こりうるぶつ切りの場合分けは、
    				1.[データの途中まで] [データの残り+ハッシュ値]
    				2.[データすべて] [ハッシュ値]
    				3.[データの終わりまで+ハッシュ値の途中まで] [ハッシュ値の残り] 
    			 * の3通り。
        		 */
        		// 送信データがハッシュ部分も取り込む場合
        		if(i+thisSize > length-FRAGMENT_HASH_SIZE){
        			
        			// 今回で送り終える場合
        			if(i+thisSize == length){
        				
        				// ケースA.ハッシュ値のみでいい
    					// 2,3の最終パケットを担う。
        				if(thisSize <= FRAGMENT_HASH_SIZE){
        					outputStream.write(hash, FRAGMENT_HASH_SIZE-thisSize, thisSize);
        				}
        				
        				// ケースB.データ+ハッシュ値
    					// 1の最終パケット。
        				else{
        					outputStream.write(out, i, thisSize-FRAGMENT_HASH_SIZE);
        					outputStream.write(hash, 0, FRAGMENT_HASH_SIZE);
        				}
        			}
        			
        			// 今回で送り終えない場合
    				// ケースC.データ+ハッシュ値の途中まで
    				// 3の最終直前パケット。
        			else{
        				outputStream.write(out, i, length-FRAGMENT_HASH_SIZE-i);
        				outputStream.write(hash, 0, thisSize-(length-FRAGMENT_HASH_SIZE-i));
        			}
        		}
        		
        		// 送信データがデータ部分だけで構成される場合
        		else{
        			outputStream.write(out, i, thisSize);
        		}
        		// 送信
        		if(!r.isAlive())
        			break;
        		r.write(outputStream.toByteArray());
//        		AODV_Activity.logD(outputStream.toByteArray(), "SendAfterFragment.dat");
        	}
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);
        // Commented out, because when trying to connect to all 7 UUIDs, failures will occur
        // for each that was tried and unsuccessful, resulting in multiple failure toasts.
        /*
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        */
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("ConnectionFailed\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        if(mConnThreads.isEmpty())
        	setState(STATE_LISTEN);
        else{
        	setState(STATE_CONNECTED);
        }

        // Send a failure message back to the Activity
//        Message msg = mHandler.obtainMessage(AODV_Activity.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString("toast", "Device connection was lost");
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);
//		mHandler.post(new Runnable() {
//			@Override
//			public void run() {
//				// TODO 自動生成されたメソッド・スタブ
//				EditText text_view_received = AODV_Activity.testtext;
//				text_view_received.append("ConnectionLost\n");
//				text_view_received.setSelection(text_view_received.getText().length());
//			}
//		});
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until canceled).
     */
    private class AcceptThread extends Thread {
    	BluetoothServerSocket serverSocket = null;
        
        public AcceptThread() {
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            BluetoothSocket socket = null;
    		
            try {
            	// Listen for all 7 UUIDs
            	for (int i = 0; i < 7; i++) {
            		if(connectState[i] == false){
	            		serverSocket = mAdapter.listenUsingRfcommWithServiceRecord(NAME, mUuids.get(i));
	                    socket = serverSocket.accept();
	                    if (socket != null) {
		                    connected(socket, socket.getRemoteDevice(), mUuids.get(i));
		                    connectState[i] = true;
	                    }	                    
            		}
            	}
            } catch (IOException e) {
                Log.e(TAG, "accept() failed", e);
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private UUID tempUuid;

        public ConnectThread(BluetoothDevice device, UUID uuidToTry) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            tempUuid = uuidToTry;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(uuidToTry);        	
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");
//    		mHandler.post(new Runnable() {
//    			@Override
//    			public void run() {
//    				// TODO 自動生成されたメソッド・スタブ
//    				EditText text_view_received = AODV_Activity.testtext;
//    				text_view_received.append("ConnectThreadRun()\n");
//    				text_view_received.setSelection(text_view_received.getText().length());
//    			}
//    		});
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
            	if (tempUuid.toString().contentEquals(mUuids.get(6).toString())) {
                    connectionFailed();
            	}
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                BluetoothChatService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, tempUuid);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Thread ipRequestThread;
        private boolean ipAddressFlag;
        
        // Fragment
        ByteArrayOutputStream outputStream = null;
        byte sequenceNo = -1;
        byte[] dataLength = null;
        byte[] dataNo = null;
        
        // fragment data id
        private int fragmentId;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            ipAddressFlag = false;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            
            fragmentId = 0;
        }

        public void run() {
//    		mHandler.post(new Runnable() {
//    			@Override
//    			public void run() {
//    				EditText text_view_received = AODV_Activity.testtext;
//    				text_view_received.append("ConnectedThreadRun()\n");
//    				text_view_received.setSelection(text_view_received.getText().length());
//    			}
//    		});
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[65536];
            Log.i(TAG, "buffer:"+buffer.length);
            int bytes;
            
            byte[] preHopAddress = null;	// 前ホップノードのアドレス(当メッセージの送信元)
            
            // IPアドレスを要求するスレッドを生成
            ipRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while(true){
						if(ipAddressFlag)break;
						
                		byte[] request = new byte[1];
                		request[0] = STATIC_IP_REQUEST;
                		write(request);
                		
                		try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							break;
						}
					}
				}
			});
            ipRequestThread.start();
            
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                	if(mmInStream.available() > 0){
	                    bytes = mmInStream.read(buffer);
	                    mAODV_Service.subMoveSensorValue(bytes / AODV_Service.SUB_RATE);
	                    
	                    Log.d(TAG,"バイト数:"+bytes);
	                    //mAODV_Service.appendMessageOnActivity("type:"+buffer[0]+",length:"+bytes+"\n");
	                    
	                    // BT受信処理
	                    // STATIC_IP_REQUESTならIpAddressを送る
	                    if(bytes == 1 && buffer[0] == STATIC_IP_REQUEST){
	                        // Ipアドレスを通知
	                    	byte[] ipNotification = new byte[5];
	                    	ipNotification[0] = STATIC_IP_DATA;
	                    	StaticIpAddress sIp = new StaticIpAddress(context);
	                    	System.arraycopy(sIp.getStaticIpByte(), 0, ipNotification, 1, 4);
	                    	this.write(ipNotification);
	                    	//mAODV_Service.appendMessageOnActivity("\n"+sIp.getStaticIpByte()[0]+"\n");
	                    }
	                    else{
		                    if(!ipAddressFlag){
		                    	if(buffer[0] == STATIC_IP_DATA){
		                    		// アドレスの保管
		                    		synchronized (mConnThreadsLock) {
		                    			Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
		                        		while(it.hasNext()){
		                        			ConnectedDeviceManager manager = it.next();
		                        			if(manager.getMacAddress().equals(mmSocket.getRemoteDevice().getAddress())){
		                        				preHopAddress = new byte[4];
		                        				System.arraycopy(buffer, 1, preHopAddress, 0, 4);
		                        				manager.setIpAddress(preHopAddress);
		                        				ipAddressFlag = true;
		                        				Log.d("IPaddressReceive","True");
		                        				break;
		                        			}
		                        		}
									}
		                    		mAODV_Service.appendMessageOnActivity("\nBT_IPget!\n");
		                    	}
		                    	else{
		                    		// まだIpAddressが届いてないので催促する制御メッセージの送信
//		                    		byte[] request = new byte[1];
//		                    		request[0] = -1;
//		                    		this.write(request);
		                    	}
		                    }
		                    else{
		                    	// 受信処理
		                    	byte[] cutBuffer = new byte[bytes];
		                    	System.arraycopy(buffer, 0, cutBuffer, 0, bytes);
		                    	
	                    		if(bytes > FRAGMENT_MTU){
	                    			for(int size=0;size<bytes;size+=FRAGMENT_MTU){
	                    				byte[] bufferMtuSize = new byte[FRAGMENT_MTU];
		                    			System.arraycopy(cutBuffer, size, bufferMtuSize, 0, (size+FRAGMENT_MTU > bytes)? bytes-size:FRAGMENT_MTU);
		                    			
		                    			// 先頭バイトがFRAGMENT_DATAならフラグメント化されているパケット
		                    			if(bufferMtuSize[0] == FRAGMENT_DATA){
		                    				receiveData(bufferMtuSize, preHopAddress);
		                    			}
		                    			else{
//		                    				synchronized(mAODV_Service.getReceivedProcessLock()){
		                    					ReceiveProcess.process(bufferMtuSize, preHopAddress, true, my_address, context, mAODV_Service);
//		                    				}
		                    			}
	                    			}
	                    		}
	                    		else{
	                    			// 先頭バイトがFRAGMENT_DATAならフラグメント化されているパケット
	                    			if(cutBuffer[0] == FRAGMENT_DATA){
//	                    				AODV_Activity.logD(cutBuffer);
//	                    				final byte no = cutBuffer[1];
//	                    				byte[] a = {cutBuffer[6],cutBuffer[7],cutBuffer[8],cutBuffer[9]};
//	                    				final int datalength = AODV_Activity.byteToInt(a);
//	            	            		mHandler.post(new Runnable() {
//	            	            			@Override
//	            	            			public void run() {
//	            	            				// TODO 自動生成されたメソッド・スタブ
//	            	            				EditText text_view_received = AODV_Activity.testtext;
//	            	            				text_view_received.append(" FramentSequence:"+no+","+datalength+"/"+outputStream.size() +"\n");
//	            	            				text_view_received.setSelection(text_view_received.getText().length());
//	            	            			}
//	            	            		});
	                    				receiveData(cutBuffer, preHopAddress);
	                    			}
	                    			else{
//	                    				synchronized(mAODV_Service.getReceivedProcessLock()){
	                    					ReceiveProcess.process(cutBuffer, preHopAddress, true, my_address, context, mAODV_Service);
//	                    				}
	                    			}
	                    		}
		                    }
		                    
		                    // Send the obtained bytes to the UI Activity
		//                    mHandler.obtainMessage(AODV_Activity.MESSAGE_READ, bytes, -1, buffer)
		//                            .sendToTarget();
	                    }
                	}
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    mAODV_Service.appendMessageOnActivity("\ndisconnected@1\n");
//            		mHandler.post(new Runnable() {
//            			@Override
//            			public void run() {
//            				// TODO 自動生成されたメソッド・スタブ
//            				EditText text_view_received = AODV_Activity.testtext;
//            				text_view_received.append("ConnecedThread_IOexcep\n");
//            				text_view_received.setSelection(text_view_received.getText().length());
//            			}
//            		});
                    //e.printStackTrace();
                    // ArrayListから自分の削除
            		synchronized (mConnThreadsLock) {
            			Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
                		while(it.hasNext()){
                			ConnectedDeviceManager m = it.next();
                			if(m.getConnectedThread().getId() == this.getId()){
                				for(int j=0;j<7;j++){
                					if(m.getUuid().compareTo(mUuids.get(j)) == 0){
                						connectState[j] = false;
                						break;
                					}
                				}
                				mConnThreads.remove(it);
                				setState(STATE_LISTEN);
//                				AODV_Activity.logD("ここ通ってるよ！\n");
                				break;
                			}
                		}
					}
            		connectionLost();
                    if(ipRequestThread != null)
        				if(ipRequestThread.isAlive()){
        					ipRequestThread.interrupt();
        					ipRequestThread = null;
        				}
                    break;
                } catch (NoSuchAlgorithmException e) {
					// TODO 自動生成された catch ブロック
                    if(ipRequestThread != null)
        				if(ipRequestThread.isAlive()){
        					ipRequestThread.interrupt();
        					ipRequestThread = null;
        				}
					e.printStackTrace();
				}
            }
            if(ipRequestThread != null)
				if(ipRequestThread.isAlive()){
					ipRequestThread.interrupt();
					ipRequestThread = null;
				}
        }
        
        // フラグメント化されたデータの受け取り口
        private void receiveData(byte[] cutBuffer, byte[] preHopAddress) throws NoSuchAlgorithmException, IOException{
        	// ヘッダー情報抜き出し
    		byte thisSequenceNo = cutBuffer[1];
    		byte[] thisDataId = new byte[4];
    		byte[] thisDataLength = new byte[4];
    		System.arraycopy(cutBuffer, 2, thisDataId, 0, 4);
    		System.arraycopy(cutBuffer, 6, thisDataLength, 0, 4);
    		
//    		AODV_Activity.logD(cutBuffer, "ReceiveBeforeCombine"+thisSequenceNo+".dat");
    		
//    		final byte a = cutBuffer[1];
//    		
//    		mHandler.post(new Runnable() {
//    			@Override
//    			public void run() {
//    				// TODO 自動生成されたメソッド・スタブ
//    				EditText text_view_received = AODV_Activity.testtext;
//    				text_view_received.append(a+"っ"+sequenceNo+"\n");
//    				text_view_received.setSelection(text_view_received.getText().length());
//    			}
//    		});
    		
//    		if(thisSequenceNo == 2){
//    			if(outputStream == null){
//    				AODV_Activity.logD("out".getBytes(), "outNull.dat");
//    			}
//    			if(!Arrays.equals(dataLength,thisDataLength)){
//    				AODV_Activity.logD("out".getBytes(), "NotEqualLength.dat");
//    			}
//    			if(!Arrays.equals(dataNo,thisDataId)){
//    				AODV_Activity.logD("out".getBytes(), "NotEqualId.dat");
//    			}
//    		}
    		 		
    		// それまでの続きか、あるいは新しいフラグメントデータか識別
    		// if 受け取り先バッファが未準備なら，新しいデータ
    		// || IDなりサイズなりが異なっていれば新しいデータ
    		if(outputStream == null || !Arrays.equals(dataLength,thisDataLength) || !Arrays.equals(dataNo,thisDataId)){
    			
//    			AODV_Activity.logD(cutBuffer, "NewReceiveBeforeCombine"+thisSequenceNo+".dat");
    			// 新しいデータなのに先頭データじゃないなら無視
    			// *重要* 順序の入れ違いに対応させてないため。
    			if(thisSequenceNo != 1){;}
    			else{
    				// 新データの結合準備
    				sequenceNo = thisSequenceNo;
    				dataLength = new byte[thisDataLength.length];
    				System.arraycopy(thisDataLength, 0, dataLength, 0, thisDataLength.length);
    				dataNo = new byte[thisDataId.length];
    				System.arraycopy(thisDataId, 0, dataNo, 0, thisDataId.length);
    				outputStream = new ByteArrayOutputStream(AODV_Activity.byteToInt(dataLength));
    				
    				outputStream.write(cutBuffer, FRAGMENT_HEADER_SIZE, cutBuffer.length-FRAGMENT_HEADER_SIZE);
    			}
    		}
    		// if *重要* シーケンスNoが飛んでいれば無視。入れ違いに未対応なため。
    		// || シーケンスNoが過去のものでも無視
    		else if((sequenceNo+1) != thisSequenceNo){}
    		// 続きのデータ
    		else{
//    			AODV_Activity.logD("ou".getBytes(), "continue.dat");
    			sequenceNo = thisSequenceNo;
    			outputStream.write(cutBuffer, FRAGMENT_HEADER_SIZE, cutBuffer.length-FRAGMENT_HEADER_SIZE);
    			
    			// 最終データかどうかチェック！
    			// if 受け取り断片数*１断片のデータサイズ >= dataLength
    			if(sequenceNo*(FRAGMENT_MTU-FRAGMENT_HEADER_SIZE) >= AODV_Activity.byteToInt(dataLength)){
//            		mHandler.post(new Runnable() {
//            			@Override
//            			public void run() {
//            				// TODO 自動生成されたメソッド・スタブ
//            				EditText text_view_received = AODV_Activity.testtext;
//            				text_view_received.append("Finish!\n");
//            				text_view_received.setSelection(text_view_received.getText().length());
//            			}
//            		});
//            		try {
//						Thread.sleep(500);
//					} catch (InterruptedException e) {
//						// TODO 自動生成された catch ブロック
//						e.printStackTrace();
//					}
    				
//    				AODV_Activity.logD("ou".getBytes(), "finish.dat");
    				// 最終データなので、ハッシュ値を計算してみる。
    				byte[] result = outputStream.toByteArray();
    				byte[] data = new byte[result.length-FRAGMENT_HASH_SIZE];
    				byte[] hash = new byte[FRAGMENT_HASH_SIZE];
    				System.arraycopy(result, 0, data, 0, result.length-FRAGMENT_HASH_SIZE);
    				System.arraycopy(result, result.length-FRAGMENT_HASH_SIZE, hash, 0, FRAGMENT_HASH_SIZE);
//    				AODV_Activity.logD(data, "Last.dat");
    				
	    	    	MessageDigest digest2 = java.security.MessageDigest.getInstance("MD5");
	    	    	digest2.update(data);
	    	    	byte[] hash2 = digest2.digest();	// 再計算したハッシュ値
    				
	    	    	if(Arrays.equals(hash, hash2)){
        				// 結合データを処理部に渡す
        				ReceiveProcess.process(data, preHopAddress, true, my_address, context, mAODV_Service);
	    	    	}
	    	    	else{
	    	    		Log.d("Hash","HashError");
	    	    	}

    				// 次のために初期化
    				outputStream.close();
    				outputStream = null;
    			}
    		}
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                Log.d(getName(), "送信バイト:"+buffer.length);
                
                mAODV_Service.subMoveSensorValue(buffer.length / AODV_Service.SUB_RATE);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    
    /* Connected Device Manager */
    private class ConnectedDeviceManager {
    	private byte[] ipAddress;
    	private String macAddress;
    	private String deviceName;
    	private ConnectedThread connectedThread;
    	private UUID uuid;
    	private int rssi;
    	private long getRssiTime;
    	
    	public ConnectedDeviceManager() {
    		ipAddress = null;
    		macAddress = null;
    		deviceName = null;
    		connectedThread = null;
    		rssi = 0;
    		getRssiTime = 0;
    	}

    	public byte[] getIpAddress() {
    		return ipAddress;
    	}
    	public void setIpAddress(byte[] ipAddress) {
    		this.ipAddress = ipAddress;
    	}
    	public String getMacAddress() {
    		return macAddress;
    	}
    	public void setMacAddress(String macAddress) {
    		this.macAddress = macAddress;
    	}
    	public String getDeviceName() {
    		return deviceName;
    	}
    	public void setDeviceName(String deviceName) {
    		this.deviceName = deviceName;
    	}
    	public ConnectedThread getConnectedThread(){
    		return connectedThread;
    	}
    	public void setConnectedThread(ConnectedThread connected){
    		this.connectedThread = connected;
    	}
		public UUID getUuid() {
			return uuid;
		}
		public void setUuid(UUID uuid) {
			this.uuid = uuid;
		}
		public int getRssi() {
			return rssi;
		}
		public void setRssi(int rssi) {
			this.rssi = rssi;
		}
		public long getGetRssiTime() {
			return getRssiTime;
		}
		public void setGetRssiTime(long getRssiTime) {
			this.getRssiTime = getRssiTime;
		}
    }
    
    /*
     * this thread is being connection every X second
     */
    private class AutoConnectingThread extends Thread {
        
    	private final int X = 60000;
    	private Timer timer;
    	private TimerTask timerTask;
    	private BluetoothAdapter mAdapter;
    	
        public AutoConnectingThread() {
        	// 定期処理の内容を定義
        	timerTask = new TimerTask() {
				
				@Override
				public void run() {
					// 既知デバイスのみと接続処理。スキャンなし。
//		            //BluetoothAdapterから、接続履歴のあるデバイスの情報を取得
//		            Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();
//		            if(pairedDevices.size() > 0){
//		                //接続履歴のあるデバイスが存在する
//		                for(BluetoothDevice device:pairedDevices){
//		                	// 接続履歴がある・接続中でない
//		                	if(device.getBondState() == BluetoothDevice.BOND_BONDED){
//		                        // Attempt to connect to the device
//		                        BluetoothChatService.this.connect(device);
//		                	}
//		                }
//		            }
					// スキャンを行い、発見したものから既知デバイスのみと接続
					if(mConnThreads.size() == 0)
					if(!mAdapter.isDiscovering()){
						mAdapter.startDiscovery();
					}
					
					// 接続済みリストから，IPアドレスが無いものを探しIPアドレスの再要求
//					synchronized(mConnThreadsLock){
//						for(int i=0;i<mConnThreads.size();i++){
//							ConnectedDeviceManager m = mConnThreads.get(i);
//							if(m.getIpAddress() == null){
//	                    		byte[] requestMessage = new byte[1];
//	                    		requestMessage[0] = STATIC_IP_REQUEST;
//								m.getConnectedThread().write(requestMessage);
////								m.getConnectedThread().cancel();
////								mConnThreads.remove(i);
//							}
//						}
//					}
				}
			};
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAutoConnectingThread" + this);
            
            mAdapter = BluetoothAdapter.getDefaultAdapter();
            
            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(mReceiver, filter);
            
            // Test
            filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            context.registerReceiver(mReceiver, filter);
            
            // X秒毎に定期処理
            timer = new Timer(false);
            timer.schedule(timerTask, 5000, X);
        }
        
        // The BroadcastReceiver that listens for discovered devices and
        // changes the title when discovery is finished
        public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

//        		mHandler.post(new Runnable() {
//        			@Override
//        			public void run() {
//        				// TODO 自動生成されたメソッド・スタブ
//        				EditText text_view_received = AODV_Activity.testtext;
//        				text_view_received.append("Receiver()\n");
//        				text_view_received.setSelection(text_view_received.getText().length());
//        			}
//        		});
                
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // If it's already paired, skip it, because it's been listed already
                    if (device.getBondState() == BluetoothDevice.BOND_BONDING){
                    }
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        // 既知デバイス発見
                    	// RSSIはここでのみ記録できる
                    	// 接続中端末もBONDED検知されるっぽい。ひどい。
                    	boolean connectedCheck = false;
                    	for(int i=0;i<mConnThreads.size();i++){
                    		ConnectedDeviceManager m = mConnThreads.get(i);
                    		if (device.getAddress().equals(m.getMacAddress())){
                    			connectedCheck = true;
                    			m.setRssi( intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE));
                    			m.setGetRssiTime(System.currentTimeMillis());
                    			break;
                    		}
                    	}
                    	if(connectedCheck == false){
                    		BluetoothChatService.this.connect(device);
                    	}
                    	
                    }
                // When discovery is finished, change the Activity title
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            		String mac = device.getAddress();
            		for(int i=0;i<mConnThreads.size();i++){
            			ConnectedDeviceManager m = mConnThreads.get(i);
            			if(mac.equals(m.getMacAddress())){
            				// 切断端末が接続リストに残っているなら
            				if(m.getConnectedThread() != null){
            					m.getConnectedThread().cancel();
            				}
            				for(int j=0;j<mUuids.size();j++){
            					UUID u = m.getUuid();
            					if(u.compareTo(mUuids.get(j)) == 0){
            						connectState[j] = false;
            					}
            				}
            				mConnThreads.remove(i);
            				break;
            			}
            		}
            		mAODV_Service.appendMessageOnActivity("\ndisconnected@2_"+ device.getName() +"\n");
                }
                
            }
        };

        public void cancel() {
        	context.unregisterReceiver(mReceiver);
        	
            if (D) Log.d(TAG, "cancel " + this);
            
        }
    }
    
    public String showConnection(){
    	synchronized(mConnThreadsLock){
	    	if(mConnThreads == null)return null;
	    	
	    	if(mConnThreads.size() != 0){
	    		StringBuilder str = new StringBuilder();
	    		Iterator<ConnectedDeviceManager> it = mConnThreads.iterator();
	    		while(it.hasNext()){
	    			ConnectedDeviceManager m = it.next();
	    			if(m.getConnectedThread() != null){
		    			if(m.getConnectedThread().isAlive()){
		    				if(m.getIpAddress() != null)
		    				 str.append(m.getMacAddress()+","+AODV_Activity.getStringByByteAddress(m.getIpAddress())+"\n");
		    				else{
		    					str.append(m.getMacAddress()+",え？\n");
		    				}
		    			}
	    			}
	    		}
	    		
	    		return str.toString();
	    	}
	    	else
	    		return null;
    	}
    }
}
