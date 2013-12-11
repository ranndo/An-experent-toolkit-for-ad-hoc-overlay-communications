package jp.ac.ehime_u.cite.udptest;

// AODV上で送るデータの送信要請インタフェース
// 中身そのものはService内のBinderに記載
interface SendManager{
	// 引数dataMapはMap<String,byte[]>を引数としているが，AIDLがそれを認めない仕様のため型指定していない
	void SendMessage(String destination_address,String source_address,
		byte flag, String package_name, String intent_action,
		int intent_flags, String intent_type, String intent_scheme,
		in List<String> intent_categories,in Map dataMap);
	void WriteLog(int state, String sourceAddress, String destinationAddress,
		int dataLength, String packageName);
}