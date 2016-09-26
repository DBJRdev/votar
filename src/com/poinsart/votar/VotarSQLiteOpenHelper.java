package com.poinsart.votar;

import java.util.ArrayList;
import java.util.List;

import com.poinsart.votar.data.JsonString;
import com.poinsart.votar.data.Vote;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class VotarSQLiteOpenHelper extends SQLiteOpenHelper {
	// If you change the database schema, you must increment the database version.
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "votar.db";
	
	private static abstract class Field {
		public static final String T_VOTES="votes";
		public static final String C_ID="id";
		public static final String C_DELETED="deleted";
		public static final String C_CREATE_TIME="create_time";
		public static final String C_CHANGE_TIME="change_time";
		public static final String C_PRCOUNT_A="prcount_a";
		public static final String C_PRCOUNT_B="prcount_b";
		public static final String C_PRCOUNT_C="prcount_c";
		public static final String C_PRCOUNT_D="prcount_d";
		public static final String C_JSONMARKS="jsonmarks";
	}
	
	private static final String SQL_CREATE_ENTRIES = "CREATE TABLE "+ Field.T_VOTES
			+ "("
			+ Field.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
			+ Field.C_DELETED + " INTEGER NOT NULL," 
			+ Field.C_CREATE_TIME + " INTEGER,"
			+ Field.C_CHANGE_TIME + " INTEGER,"
			+ Field.C_PRCOUNT_A + " INTEGER,"
			+ Field.C_PRCOUNT_B + " INTEGER,"
			+ Field.C_PRCOUNT_C + " INTEGER,"
			+ Field.C_PRCOUNT_D + " INTEGER,"
			+ Field.C_JSONMARKS + " TEXT"
			+ ")";
	
	private VotarSQLiteOpenHelper dbHelper;

	public VotarSQLiteOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		dbHelper=this;
	}
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_ENTRIES);
	}
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// first version
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    	// first version
    }
    
    public Vote insertVote(Vote vote) {
    	SQLiteDatabase db = dbHelper.getWritableDatabase();
    	SQLiteStatement stmt = db.compileStatement("INSERT INTO "+Field.T_VOTES
    			+ " ("
    			+ Field.C_DELETED+","		// static field
    			+ Field.C_CREATE_TIME+","	// 1
    			+ Field.C_CHANGE_TIME+","	// 2
    			+ Field.C_PRCOUNT_A+","		// 3
    			+ Field.C_PRCOUNT_B+","		// 4
    			+ Field.C_PRCOUNT_C+","		// 5
    			+ Field.C_PRCOUNT_D+","		// 6
    			+ Field.C_JSONMARKS			// 7
    			+ ") VALUES (0, ?, ?, ?, ?, ?, ?, ?)"); // 8 values
    	stmt.bindLong(1, vote.change_time);
    	stmt.bindLong(2, vote.change_time);
    	stmt.bindLong(3, vote.prcount[0]);
    	stmt.bindLong(4, vote.prcount[1]);
    	stmt.bindLong(5, vote.prcount[2]);
    	stmt.bindLong(6, vote.prcount[3]);
    	stmt.bindString(7, vote.jsonmarks.value);
    	vote.id=stmt.executeInsert();
    	db.close();
    	return vote;
    }
    
    public Vote deleteVote(Vote vote) {
    	SQLiteDatabase db = dbHelper.getWritableDatabase();
    	SQLiteStatement stmt = db.compileStatement("UPDATE "+Field.T_VOTES + " SET "
    			+ Field.C_DELETED+"=1, "
    			+ Field.C_CREATE_TIME+"=-1,"
    			+ Field.C_CHANGE_TIME+"=?,"
    			+ Field.C_PRCOUNT_A+"=-1,"
    			+ Field.C_PRCOUNT_B+"=-1,"
    			+ Field.C_PRCOUNT_C+"=-1,"
    			+ Field.C_PRCOUNT_D+"=-1,"
    			+ Field.C_JSONMARKS+"=NULL"		// 1
    			+ " WHERE "+Field.C_ID+"=?");	// 2
    	stmt.bindLong(1, vote.change_time);
    	stmt.bindLong(2, vote.id);
    	stmt.executeUpdateDelete();
    	db.close();
    	return vote;
    }
    
    public List<Vote> getAllVotes() {
    	List<Vote> votes = new ArrayList<Vote>();
    	
    	SQLiteDatabase db = dbHelper.getReadableDatabase();
    	Cursor c = db.rawQuery("SELECT "
    			+ Field.C_ID+","			// 0
    			+ Field.C_DELETED+","		// 1
    			+ Field.C_CREATE_TIME+","	// 2
    			+ Field.C_CHANGE_TIME+","	// 3
    			+ Field.C_PRCOUNT_A+","		// 4
    			+ Field.C_PRCOUNT_B+","		// 5
    			+ Field.C_PRCOUNT_C+","		// 6
    			+ Field.C_PRCOUNT_D+","		// 7
    			+ Field.C_JSONMARKS			// 8
    			+ " FROM "+Field.T_VOTES
    			, null);
    	
    	if (c.moveToFirst()) {
    		do {
    			Vote newvote=new Vote();
    			newvote.id=c.getLong(0);
    			newvote.deleted=(c.getInt(1)!=0);
    			newvote.create_time=c.getLong(2);
    			newvote.change_time=c.getLong(3);
    			newvote.prcount[0]=c.getInt(4);
    			newvote.prcount[1]=c.getInt(5);
    			newvote.prcount[2]=c.getInt(6);
    			newvote.prcount[3]=c.getInt(7);
    			newvote.jsonmarks=new JsonString(c.getString(8));
    			votes.add(newvote);
    		} while (c.moveToNext());
    	}

    	c.close();
    	db.close();
    	return votes;
    }

}
