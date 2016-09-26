/*
    VotAR : Vote with Augmented reality
    Copyright (C) 2013 Stephane Poinsart <s@poinsart.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.poinsart.votar.data;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;


public class Vote implements JSONAware {
	// position of the mark
	public long id;
	public boolean deleted;
	public long create_time;
	public long change_time;
	public int prcount[];
	
	
	// normaly we would go for :
	//
	// 		public Mark mark[];
	//
	// but marks are handled client-side so it's more efficient to store pre-serialized data  
	public JsonString jsonmarks;
 
	
	public Session session;
	
	public Vote() {
		prcount=new int[4];
		prcount[0]=prcount[1]=prcount[2]=prcount[3]=-1;
		id=-1;
		deleted=false;
		create_time=change_time=-1;
	}


	@SuppressWarnings("unchecked")
	@Override
	public String toJSONString() {
		JSONObject obj = new JSONObject();
		obj.put("id", id);
		obj.put("deleted", deleted);
		obj.put("create_time", create_time);
		obj.put("change_time", change_time);
			
		JSONArray jsonprcount=new JSONArray();
		jsonprcount.add(prcount[0]);
		jsonprcount.add(prcount[1]);
		jsonprcount.add(prcount[2]);
		jsonprcount.add(prcount[3]);
		obj.put("prcount", jsonprcount);
		obj.put("marks", jsonmarks);
		return obj.toJSONString();
	}
	
}
