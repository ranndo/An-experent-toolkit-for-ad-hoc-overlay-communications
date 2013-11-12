package jp.ac.ehime_u.cite.udptest;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LogDataBaseOpenHelper extends SQLiteOpenHelper {

	public static final String LOG_TABLE_AODV = "Log_Table_AODV";
	public static final String LOG_TABLE_ROUTE= "Log_Table_Route";
	public static final String LOG_TABLE_DATA = "Log_Table_DATA";

	public LogDataBaseOpenHelper(Context context) {
		super(context, "LogDataBase", null, 1);
		// TODO 自動生成されたコンストラクター・スタブ
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			// テーブルの生成
			StringBuilder createSql = new StringBuilder();
			createSql.append("create table " + LOG_TABLE_AODV +" (");

			createSql.append("Type integer not null,");
			createSql.append("MyIP text not null,");
			createSql.append("SourceIP text not null,");
			createSql.append("DestinationIP text not null,");

			createSql.append("HopCount integer,");
			createSql.append("SequenceNo integer,");

			createSql.append("Time text not null,");
			createSql.append("NetworkInterface text not null");
			createSql.append(")");

			db.execSQL(createSql.toString());

			// テーブルの生成
			StringBuilder createSql2 = new StringBuilder();
			createSql2.append("create table "+LOG_TABLE_DATA +" (");

			createSql2.append("Type integer not null,");
			createSql2.append("MyIP text not null,");
			createSql2.append("SourceIP text not null,");
			createSql2.append("DestinationIP text not null,");


			createSql2.append("DataLength integer,");
			createSql2.append("ApplicationName text,");

			createSql2.append("Time text not null,");
			createSql2.append("NetworkInterface text not null");
			createSql2.append(")");

			db.execSQL(createSql2.toString());

			// テーブルの生成
			StringBuilder createSql3 = new StringBuilder();
			createSql3.append("create table "+LOG_TABLE_ROUTE +" (");

			createSql3.append("Type integer not null,");
			createSql3.append("MyIP text not null,");
		//	createSql3.append("SourceIP integer not null,");
			createSql3.append("DestinationIP text not null,");

			createSql3.append("HopCount integer,");
			createSql3.append("SequenceNo integer,");
			createSql3.append("NextIP text,");
			createSql3.append("Enable integer,");
			createSql3.append("LifeTime integer,");

			createSql3.append("Time text not null,");
			createSql3.append("NetworkInterface text not null");
			createSql3.append(")");

			db.execSQL(createSql3.toString());

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public static void insertLogTableAODV(SQLiteDatabase db,int type,String myip, String source_ip, String destination_ip, int hopcount, int sequence_no, String time, String network){

		ContentValues values = new ContentValues();
		values.put("Type", type);
		values.put("MyIP", myip);
		values.put("SourceIP", source_ip);
		values.put("DestinationIP", destination_ip);
		values.put("HopCount", hopcount);
		values.put("SequenceNo", sequence_no);
		values.put("Time", time);
		values.put("NetworkInterface", network);

		try{
			db.insert(LOG_TABLE_AODV, null, values);
		}catch(Exception e){
			e.printStackTrace();
		}

	}

	public static void insertLogTableDATA(SQLiteDatabase db,int type,String myip, String source_ip, String destination_ip, int datalength, String application_name, String time, String network){

		ContentValues values = new ContentValues();
		values.put("Type", type);
		values.put("MyIP", myip);
		values.put("SourceIP", source_ip);
		values.put("DestinationIP", destination_ip);
		values.put("DataLength", datalength);
		values.put("ApplicationName", application_name);
		values.put("Time", time);
		values.put("NetworkInterface", network);

		db.insert(LOG_TABLE_DATA, null, values);

	}

	public static void insertLogTableROUTE(SQLiteDatabase db,int type,String myip, String destination_ip, int hopcount, int sequence_no, String next_ip, int enable, int life, String time, String network){

		ContentValues values = new ContentValues();
		values.put("Type", type);
		values.put("MyIP", myip);
		values.put("DestinationIP", destination_ip);
		values.put("HopCount", hopcount);
		values.put("SequenceNo", sequence_no);
		values.put("NextIP", next_ip);
		values.put("Enable", enable);
		values.put("LifeTime", life);
		values.put("Time", time);
		values.put("NetworkInterface", network);

		db.insert(LOG_TABLE_ROUTE, null, values);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO 自動生成されたメソッド・スタブ

	}

}
