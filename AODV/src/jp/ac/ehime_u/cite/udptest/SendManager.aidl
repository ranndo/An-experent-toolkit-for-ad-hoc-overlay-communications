package jp.ac.ehime_u.cite.udptest;

// AODV��ő���f�[�^�̑��M�v���C���^�t�F�[�X
// ���g���̂��̂�Service����Binder�ɋL��
interface SendManager{
	void SendMessage(String destination_address,String source_address,
		byte flag, String package_name, String intent_action,
		int intent_flags, String intent_type, String intent_scheme,
		in List<String> intent_categories,in byte[] data);
}