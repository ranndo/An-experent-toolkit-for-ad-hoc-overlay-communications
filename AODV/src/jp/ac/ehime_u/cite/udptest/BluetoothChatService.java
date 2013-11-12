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
import java.util.Set;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothChatService {
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChatMulti";

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private ArrayList<String> mDeviceAddresses;
    private ArrayList<ConnectedDeviceManager> mConnThreads;
    private ArrayList<BluetoothSocket> mSockets;
    
    // add to Sample by Student
    private AutoConnectingThread mAutoConnectingThread;
    
    /**
     * A bluetooth piconet can support up to 7 connections. This array holds 7 unique UUIDs.
     * When attempting to make a connection, the UUID on the client must match one that the server
     * is listening for. When accepting incoming connections server listens for all 7 UUIDs. 
     * When trying to form an outgoing connection, the client tries each UUID one at a time. 
     */
    private ArrayList<UUID> mUuids;
    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    // fragment data id
    int fragmentId;
    public static final int FRAGMENT_MTU = 1008;
    public static final byte FRAGMENT_DATA = 101;
    public static final int FRAGMENT_HEADER_SIZE = 10;
    public static final int FRAGMENT_HASH_SIZE = 16;
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothChatService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mDeviceAddresses = new ArrayList<String>();
        mConnThreads = new ArrayList<ConnectedDeviceManager>();
        mSockets = new ArrayList<BluetoothSocket>();
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
        
        fragmentId = 0;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(AODV_Activity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
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
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

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

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Create a new thread and attempt to connect to each UUID one-by-one.    
        for (int i = 0; i < 7; i++) {
        	try {
                mConnectThread = new ConnectThread(device, mUuids.get(i));
                mConnectThread.start();
                setState(STATE_CONNECTING);
        	} catch (Exception e) {
        	}
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
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
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        // Add each connected thread to an array
        ConnectedDeviceManager connectedDevice = new ConnectedDeviceManager();
        connectedDevice.setConnectedThread(mConnectedThread);
        connectedDevice.setMacAddress(device.getAddress());
        connectedDevice.setDeviceName(device.getName());
        mConnThreads.add(connectedDevice);

//        // Send the name of the connected device back to the UI Activity
//        Message msg = mHandler.obtainMessage(AODV_Activity.MESSAGE_DEVICE_NAME);
//        Bundle bundle = new Bundle();
//        bundle.putString(AODV_Activity.DEVICE_NAME, device.getName());
//        bundle.putString(AODV_Activity.DEVICE_ADDRESS, device.getAddress());
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
        
        // ���񑗐M�EIp�A�h���X��ʒm
    	StaticIpAddress sIp = new StaticIpAddress(AODV_Activity.context);
    	mConnectedThread.write(sIp.getStaticIpByte());
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        if (mAutoConnectingThread != null) {mAutoConnectingThread.cancel(); mAutoConnectingThread = null;}
        
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
    	// When writing, try to write out to all connected threads 
    	for (int i = 0; i < mConnThreads.size(); i++) {
    		try {
                // Create temporary object
                ConnectedThread r;
                // Synchronize a copy of the ConnectedThread
                synchronized (this) {
                    //if (mState != STATE_CONNECTED) return;
                    r = mConnThreads.get(i).getConnectedThread();
                }
                // Perform the write unsynchronized
                if(out.length > FRAGMENT_MTU)
                	fragment_write(r, out);
                else
                	r.write(out);
    		} catch (Exception e) {    			
    		}
    	}
    }
    public void write(byte[] out,byte[] ipAddress) {
    	// When writing, try to write out to all connected threads 
    	for (int i = 0; i < mConnThreads.size(); i++) {
    		ConnectedDeviceManager m = mConnThreads.get(i);
    		if(m.getIpAddress().equals(ipAddress)){
	    		try {
	                // Create temporary object
	                ConnectedThread r;
	                // Synchronize a copy of the ConnectedThread
	                synchronized (this) {
	                    //if (mState != STATE_CONNECTED) return;
	                    r = m.getConnectedThread();
	                }
	                // Perform the write unsynchronized
	                if(out.length > FRAGMENT_MTU)
	                	fragment_write(r, out);
	                else
	                	r.write(out);
	                break;
	    		} catch (Exception e) {    			
	    		}
    		}
    	}
    }
    
    /*
     * �t���O�����g��
     * �T�C�Y����FRAGMENT_MTU���傫����΁A�������A�w�b�_��t�����ĕ������M����B
     * �X�ɁA�f�[�^�̖�����MD5�n�b�V���l��ǉ�����B���̒l�̓f�[�^�̈ꕔ�Ƃ��Ĉ����B�ʂɂ���Ɩʓ|�Ȃ̂ŁB
     * �����͎�M�m�[�h�������ɍs���B�n�b�V���l���Čv�Z���A�f�[�^�̖����̒l�Ɣ�r����B
     * 
     * param:ipAddress == null -> broadcast
     */
    private void fragment_write(ConnectedThread r, byte[] out){
    	int length = out.length + FRAGMENT_HASH_SIZE;
    	fragmentId++;
    	int dataId = fragmentId;
    	byte sequenceNo = 0;
    	MessageDigest digest = null;
		try {
			digest = java.security.MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			// TODO �����������ꂽ catch �u���b�N
			e.printStackTrace();
		}
    	
    	// MD5 calculate
    	digest.update(out);
    	byte[] hash = digest.digest();
    	
    	// byte[]�^�ɕϊ�
    	byte[] byteLength = AODV_Activity.intToByte(length);
    	byte[] byteDataId = AODV_Activity.intToByte(dataId);
    	
    	// �������đ��M
    	int dataSize = FRAGMENT_MTU-FRAGMENT_HEADER_SIZE;
    	for(int i=0;i<length;i+=dataSize){
    		sequenceNo++;
    		
    		int thisSize = ((i+dataSize) > length)? length-i:dataSize; 
    		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(thisSize);
    		
    		// �w�b�_�[��ǉ�
    		outputStream.write(FRAGMENT_DATA);
    		outputStream.write(sequenceNo);
    		outputStream.write(byteDataId, 0, byteDataId.length);
    		outputStream.write(byteLength, 0, byteLength.length);
    		// �f�[�^��ǉ�
    		/*
    		 * �S�̂Ƃ��ċN���肤��Ԃ؂�̏ꍇ�����́A
				1.[�f�[�^�̓r���܂�] [�f�[�^�̎c��+�n�b�V���l]
				2.[�f�[�^���ׂ�] [�n�b�V���l]
				3.[�f�[�^�̏I���܂�+�n�b�V���l�̓r���܂�] [�n�b�V���l�̎c��] 
			 * ��3�ʂ�B
    		 */
    		// ���M�f�[�^���n�b�V����������荞�ޏꍇ
    		if(i+thisSize > length-FRAGMENT_HASH_SIZE){
    			
    			// ����ő���I����ꍇ
    			if(i+thisSize == length){
    				
    				// �P�[�XA.�n�b�V���l�݂̂ł���
					// 2,3�̍ŏI�p�P�b�g��S���B
    				if(thisSize <= FRAGMENT_HASH_SIZE){
    					outputStream.write(hash, FRAGMENT_HASH_SIZE-thisSize, thisSize);
    				}
    				
    				// �P�[�XB.�f�[�^+�n�b�V���l
					// 1�̍ŏI�p�P�b�g�B
    				else{
    					outputStream.write(out, i, thisSize-FRAGMENT_HASH_SIZE);
    					outputStream.write(hash, 0, FRAGMENT_HASH_SIZE);
    				}
    			}
    			
    			// ����ő���I���Ȃ��ꍇ
				// �P�[�XC.�f�[�^+�n�b�V���l�̓r���܂�
				// 3�̍ŏI���O�p�P�b�g�B
    			else{
    				outputStream.write(out, i, length-FRAGMENT_HASH_SIZE-i);
    				outputStream.write(hash, 0, thisSize-(length-FRAGMENT_HASH_SIZE-i));
    			}
    		}
    		
    		// ���M�f�[�^���f�[�^���������ō\�������ꍇ
    		else{
    			outputStream.write(out, i, thisSize);
    		}
    		// ���M
    		r.write(outputStream.toByteArray());
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
            		serverSocket = mAdapter.listenUsingRfcommWithServiceRecord(NAME, mUuids.get(i));
                    socket = serverSocket.accept();
                    if (socket != null) {
                    	String address = socket.getRemoteDevice().getAddress();
	                    mSockets.add(socket);
	                    mDeviceAddresses.add(address);
	                    connected(socket, socket.getRemoteDevice());
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
            connected(mmSocket, mmDevice);
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
        private boolean ipAddressFlag;

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
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[65536];
            Log.i(TAG, "buffer:"+buffer.length);
            int bytes;
            
            byte[] preHopAddress = null;	// �O�z�b�v�m�[�h�̃A�h���X(�����b�Z�[�W�̑��M��)
            
            // Fragment
            ByteArrayOutputStream outputStream = null;
            byte sequenceNo = -1;
            byte[] dataLength = null;
            byte[] dataNo = null;
            
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                	if(mmInStream.available() > 0){
	                    bytes = mmInStream.read(buffer);
	                    Log.d(TAG,"�o�C�g��:"+bytes);
	                    AODV_Activity.logD("BT_PacketLength:"+bytes);
	                    // BT��M����
	                    // 1�o�C�g�Ȃ�IpAddress�𑗂�
	                    if(bytes == 1){
	                        // Ip�A�h���X��ʒm
	                    	StaticIpAddress sIp = new StaticIpAddress(AODV_Activity.context);
	                    	this.write(sIp.getStaticIpByte());
	                    }
	                    else{
		                    // 4�o�C�g���CIpAddress��null�Ȃ�AIpAddress�Ƃ݂�
		                    if(!ipAddressFlag){
		                    	if(bytes == 4){
		                    		// �A�h���X�̕ۊ�
		                    		synchronized (BluetoothChatService.this) {
		                        		for(ConnectedDeviceManager manager:mConnThreads){
		                        			if(manager.getMacAddress().equals(mmSocket.getRemoteDevice().getAddress())){
		                        				preHopAddress = new byte[4];
		                        				System.arraycopy(buffer, 0, preHopAddress, 0, 4);
		                        				manager.setIpAddress(preHopAddress);
		                        				ipAddressFlag = true;
		                        				Log.d("IPaddressReceive","True");
		                        				break;
		                        			}
		                        		}
									}
		                    	}
		                    	else{
		                    		// �܂�IpAddress���͂��ĂȂ��̂ōÑ����鐧�䃁�b�Z�[�W�̑��M
		                    		byte[] request = new byte[1];
		                    		request[0] = -1;
		                    		this.write(request);
		                    	}
		                    }
		                    else{
		                    	// ��M����
		                    	byte[] cutBuffer = new byte[bytes];
		                    	System.arraycopy(buffer, 0, cutBuffer, 0, bytes);
		                    	
		                    	// �擪�o�C�g��101�Ȃ�t���O�����g������Ă���p�P�b�g
		                    	if(cutBuffer[0] == 101){
		                    		// �w�b�_�[��񔲂��o��
		                    		byte thisSequenceNo = cutBuffer[1];
		                    		byte[] thisDataId = new byte[4];
		                    		byte[] thisDataLength = new byte[4];
		                    		System.arraycopy(cutBuffer, 2, thisDataId, 0, 4);
		                    		System.arraycopy(cutBuffer, 6, thisDataLength, 0, 4);
		                    		
		                    		// ����܂ł̑������A���邢�͐V�����t���O�����g�f�[�^������
		                    		// if �󂯎���o�b�t�@���������Ȃ�C�V�����f�[�^
		                    		// || ID�Ȃ�T�C�Y�Ȃ肪�قȂ��Ă���ΐV�����f�[�^
		                    		if(outputStream == null || !Arrays.equals(dataLength,thisDataLength) || !Arrays.equals(dataNo,thisDataId)){
		                    			// �V�����f�[�^�Ȃ̂ɐ擪�f�[�^����Ȃ��Ȃ疳��
		                    			// *�d�v* �����̓���Ⴂ�ɑΉ������ĂȂ����߁B
		                    			if(thisSequenceNo != 1){}
		                    			else{
		                    				// �V�f�[�^�̌�������
		                    				sequenceNo = thisSequenceNo;
		                    				dataLength = thisDataLength;
		                    				dataNo = thisDataId;
		                    				outputStream = new ByteArrayOutputStream(AODV_Activity.byteToInt(dataLength));
		                    				
		                    				outputStream.write(cutBuffer, FRAGMENT_HEADER_SIZE, bytes-FRAGMENT_HEADER_SIZE);
		                    			}
		                    		}
		                    		// if *�d�v* �V�[�P���XNo�����ł���Ζ����B����Ⴂ�ɖ��Ή��Ȃ��߁B
		                    		// || �V�[�P���XNo���ߋ��̂��̂ł�����
		                    		else if((sequenceNo+1) != thisSequenceNo){}
		                    		// �����̃f�[�^
		                    		else{
		                    			sequenceNo = thisSequenceNo;
		                    			outputStream.write(cutBuffer, FRAGMENT_HEADER_SIZE, bytes-FRAGMENT_HEADER_SIZE);
		                    			
		                    			// �ŏI�f�[�^���ǂ����`�F�b�N�I
		                    			// if �󂯎��f�А�*�P�f�Ђ̃f�[�^�T�C�Y >= dataLength
		                    			if(sequenceNo*(FRAGMENT_MTU-FRAGMENT_HEADER_SIZE) >= AODV_Activity.byteToInt(dataLength)){
		                    				// �ŏI�f�[�^�Ȃ̂ŁA�n�b�V���l���v�Z���Ă݂�B
		                    				byte[] result = outputStream.toByteArray();
		                    				byte[] data = new byte[result.length-FRAGMENT_HASH_SIZE];
		                    				byte[] hash = new byte[FRAGMENT_HASH_SIZE];
		                    				System.arraycopy(result, 0, data, 0, result.length-FRAGMENT_HASH_SIZE);
		                    				System.arraycopy(result, result.length-FRAGMENT_HASH_SIZE, hash, 0, FRAGMENT_HASH_SIZE);
		                    				
		                	    	    	MessageDigest digest2 = java.security.MessageDigest.getInstance("MD5");
		                	    	    	digest2.update(data);
		                	    	    	byte[] hash2 = digest2.digest();	// �Čv�Z�����n�b�V���l
		                    				
		                	    	    	if(Arrays.equals(hash, hash2)){
			                    				// �����f�[�^���������ɓn��
			                    				AODV_Activity.receiveProcess.process(data, preHopAddress, true);
		                	    	    	}
		                	    	    	else{
		                	    	    		Log.d("Hash","HashError");
		                	    	    	}

		                    				// ���̂��߂ɏ�����
		                    				outputStream.close();
		                    				outputStream = null;
		                    			}
		                    		}
		                    	}
		                    	else{
		                    		AODV_Activity.receiveProcess.process(cutBuffer, preHopAddress, true);
		                    	}
		                    }
		                    
		                    // Send the obtained bytes to the UI Activity
		//                    mHandler.obtainMessage(AODV_Activity.MESSAGE_READ, bytes, -1, buffer)
		//                            .sendToTarget();
	                    }
                	}
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    e.printStackTrace();
                    // ArrayList���玩���̍폜
            		synchronized (BluetoothChatService.this) {
                		for(int i=0;i<mConnThreads.size();i++){
                			if(mConnThreads.get(i).getConnectedThread().getId() == this.getId()){
                				mConnThreads.remove(i);
                				break;
                			}
                		}
					}
            		connectionLost();
                    break;
                } catch (NoSuchAlgorithmException e) {
					// TODO �����������ꂽ catch �u���b�N
					e.printStackTrace();
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
                Log.d(getName(), "���M�o�C�g:"+buffer.length);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(AODV_Activity.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
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
    	
    	public ConnectedDeviceManager() {
    		ipAddress = null;
    		macAddress = null;
    		deviceName = null;
    		connectedThread = null;
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
    }
    
    /*
     * this thread is being connection every X second
     */
    private class AutoConnectingThread extends Thread {
        
    	private final int X = 5000;
    	private Timer timer;
    	private TimerTask timerTask;
    	private boolean scaning_flag;
    	private BluetoothAdapter mAdapter;
    	
        public AutoConnectingThread() {
        	// ��������̓��e���`
        	timerTask = new TimerTask() {
				
				@Override
				public void run() {
					// ���m�f�o�C�X�݂̂Ɛڑ������B�X�L�����Ȃ��B
//		            //BluetoothAdapter����A�ڑ������̂���f�o�C�X�̏����擾
//		            Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();
//		            if(pairedDevices.size() > 0){
//		                //�ڑ������̂���f�o�C�X�����݂���
//		                for(BluetoothDevice device:pairedDevices){
//		                	// �ڑ�����������E�ڑ����łȂ�
//		                	if(device.getBondState() == BluetoothDevice.BOND_BONDED){
//		                        // Attempt to connect to the device
//		                        BluetoothChatService.this.connect(device);
//		                	}
//		                }
//		            }
					// �X�L�������s���A�����������̂�����m�f�o�C�X�݂̂Ɛڑ�
					
					if(scaning_flag == false){
						scaning_flag = true;
						
						mAdapter.startDiscovery();
						
					}
				}
			};
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAutoConnectingThread" + this);
            
            scaning_flag = false;
            mAdapter = BluetoothAdapter.getDefaultAdapter();
            
            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            AODV_Activity.context.registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            AODV_Activity.context.registerReceiver(mReceiver, filter);
            
            // X�b���ɒ������
            timer = new Timer(false);
            timer.schedule(timerTask, 5000, X);
        }
        
        // The BroadcastReceiver that listens for discovered devices and
        // changes the title when discovery is finished
        public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // If it's already paired, skip it, because it's been listed already
                    if (device.getBondState() == BluetoothDevice.BOND_BONDING){
                    	Log.d("Bluetooth","��[���I�Ȃ�Ƃ��Ȃ�I");
                    }
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        // ���m�f�o�C�X����
                    	// RSSI�͂����ł̂݋L�^�ł���
                    	// �ڑ����[����BONDED���m�������ۂ��B�Ђǂ��B
                    	boolean connectedCheck = false;
                    	for(ConnectedDeviceManager m : mConnThreads){
                    		if (device.getAddress().equals(m.getMacAddress())){
                    			connectedCheck = true;
                    			break;
                    		}
                    	}
                    	if(connectedCheck == false){
                    		BluetoothChatService.this.connect(device);
                    	}
                    	
                    }
                // When discovery is finished, change the Activity title
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    scaning_flag = false;
                }
            }
        };

        public void cancel() {
        	AODV_Activity.context.unregisterReceiver(mReceiver);
        	
            if (D) Log.d(TAG, "cancel " + this);
            
        }
    }
    
    public String showConnection(){
    	if(mConnThreads == null)return null;
    	if(mConnThreads.size() != 0){
    		StringBuilder str = new StringBuilder();
    		for(ConnectedDeviceManager m:mConnThreads){
    			str.append(m.macAddress+","+AODV_Activity.getStringByByteAddress(m.ipAddress)+"\n");
    		}
    		return str.toString();
    	}
    	else
    		return null;
    }
}
