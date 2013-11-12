/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\eclipse\\workspace\\AODV\\src\\jp\\ac\\ehime_u\\cite\\udptest\\SendManager.aidl
 */
package jp.ac.ehime_u.cite.udptest;
// AODV上で送るデータの送信要請インタフェース
// 中身そのものはService内のBinderに記載

public interface SendManager extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements jp.ac.ehime_u.cite.udptest.SendManager
{
private static final java.lang.String DESCRIPTOR = "jp.ac.ehime_u.cite.udptest.SendManager";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an jp.ac.ehime_u.cite.udptest.SendManager interface,
 * generating a proxy if needed.
 */
public static jp.ac.ehime_u.cite.udptest.SendManager asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof jp.ac.ehime_u.cite.udptest.SendManager))) {
return ((jp.ac.ehime_u.cite.udptest.SendManager)iin);
}
return new jp.ac.ehime_u.cite.udptest.SendManager.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_SendMessage:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
byte _arg2;
_arg2 = data.readByte();
java.lang.String _arg3;
_arg3 = data.readString();
java.lang.String _arg4;
_arg4 = data.readString();
int _arg5;
_arg5 = data.readInt();
java.lang.String _arg6;
_arg6 = data.readString();
java.lang.String _arg7;
_arg7 = data.readString();
java.util.List<java.lang.String> _arg8;
_arg8 = data.createStringArrayList();
byte[] _arg9;
_arg9 = data.createByteArray();
this.SendMessage(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6, _arg7, _arg8, _arg9);
reply.writeNoException();
return true;
}
case TRANSACTION_Test:
{
data.enforceInterface(DESCRIPTOR);
this.Test();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements jp.ac.ehime_u.cite.udptest.SendManager
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void SendMessage(java.lang.String destination_address, java.lang.String source_address, byte flag, java.lang.String package_name, java.lang.String intent_action, int intent_flags, java.lang.String intent_type, java.lang.String intent_scheme, java.util.List<java.lang.String> intent_categories, byte[] data) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(destination_address);
_data.writeString(source_address);
_data.writeByte(flag);
_data.writeString(package_name);
_data.writeString(intent_action);
_data.writeInt(intent_flags);
_data.writeString(intent_type);
_data.writeString(intent_scheme);
_data.writeStringList(intent_categories);
_data.writeByteArray(data);
mRemote.transact(Stub.TRANSACTION_SendMessage, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void Test() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_Test, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_SendMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_Test = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void SendMessage(java.lang.String destination_address, java.lang.String source_address, byte flag, java.lang.String package_name, java.lang.String intent_action, int intent_flags, java.lang.String intent_type, java.lang.String intent_scheme, java.util.List<java.lang.String> intent_categories, byte[] data) throws android.os.RemoteException;
public void Test() throws android.os.RemoteException;
}
