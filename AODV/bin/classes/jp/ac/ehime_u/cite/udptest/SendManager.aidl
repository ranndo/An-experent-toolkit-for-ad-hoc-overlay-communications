package jp.ac.ehime_u.cite.udptest;

// AODV上で送るデータの送信要請インタフェース
// 中身そのものはService内のBinderに記載
interface SendManager{
	void SendMessage(String destination_address,String source_address,
		byte flag, String package_name, String intent_action,
		int intent_flags, String intent_type, String intent_scheme,
		in List<String> intent_categories,in byte[] data);
}