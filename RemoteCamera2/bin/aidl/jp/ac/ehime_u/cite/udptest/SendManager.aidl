package jp.ac.ehime_u.cite.udptest;

// AODV��ő���f�[�^�̑��M�v���C���^�t�F�[�X
// ���g���̂��̂�Service����Binder�ɋL��
interface SendManager{
	// ����dataMap��Map<String,byte[]>�������Ƃ��Ă��邪�CAIDL�������F�߂Ȃ��d�l�̂��ߌ^�w�肵�Ă��Ȃ�
	void SendMessage(String destination_address,String source_address,
		byte flag, String package_name, String intent_action,
		int intent_flags, String intent_type, String intent_scheme,
		in List<String> intent_categories,in Map dataMap);
	void WriteLog(int state, String sourceAddress, String destinationAddress,
		int dataLength, String packageName);
}