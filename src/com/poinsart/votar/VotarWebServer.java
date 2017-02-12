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

/*
 * this file handle a small webserver for the remote result display (laptop + video-projector)
 */

package com.poinsart.votar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.poinsart.votar.data.Vote;


import android.util.Log;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.IStatus;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

public class VotarWebServer extends NanoHTTPD {
	private VotarMain mainact;

	public static final int SOCKET_READ_TIMEOUT = 125000;

	public VotarWebServer(int port, VotarMain mainact) {
		super(port);
		this.mainact = mainact;
		mainact.updateWifiStatus();
	}

	private Response createResponse(IStatus status, String mimeType, String message) {
		Response res = Response.newFixedLengthResponse(status, mimeType, message);
		res.addHeader("Access-Control-Allow-Origin", "*");
		if (status != Status.OK)
			Log.w("Votar WebServer", message);
		return res;
	}

	public Response createResponse(IStatus status, String mimeType, File file) {
		FileInputStream fis;
		try {
			fis = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			return createResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The server failed while attempting to read the file to deliver.");
		}
		
		Response res = Response.newFixedLengthResponse(status, mimeType, fis, file.length());
		res.addHeader("Access-Control-Allow-Origin", "*");
		return res;
	}

	private Response http_error(Status status, String msg) {
		if (msg == null) {
			msg = "I'm sorry. My responses are limited. You must ask the right questions.";
		}
		return createResponse(status, NanoHTTPD.MIME_PLAINTEXT,
				"Error " + status.getRequestStatus() + " (" + status.getDescription() + ")" + msg);
	}

	private Response http_error(Status status) {
		return http_error(status, null);
	}

	// based on
	// http://stackoverflow.com/questions/13592236/parse-a-uri-string-into-name-value-collection
	public static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		String query = url.getQuery();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
					URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}

	@SuppressWarnings("unchecked")
	private Response http_votes(long startid, long endid, long startcreate, long endcreate, long startchange, long endchange) {
		JSONArray builder = new JSONArray();
		if (startid==-1)
			startid=0;
		if (endid==-1)
			endid=mainact.allvotes.size();
		for (long i = startid; i < endid; i++) {
			Vote v = mainact.allvotes.get((int) i);
			if 		  ((startcreate>0 && v.create_time<startcreate) || (endcreate>0 && v.create_time>endcreate)
					|| (startchange>0 && v.change_time<startchange) || (endchange>0 && v.change_time>endchange))
				continue;
			builder.add(v);
		}
		return createResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, builder.toString());
	}
	
	private Response http_photo(long id) {
		if (mainact.photoLock==null) {
			return createResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). The file is not ready because no photo has been used yet.");
		}
		try {
			if (!mainact.photoLock.await(60, TimeUnit.SECONDS)) {
				return createResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was locked for too long and can't deliver the file.");
			}
		} catch (InterruptedException e) {
			return createResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was interrupted before the server could deliver the file.");
		}
		if (mainact.lastPhotoFilePath==null) {
			return createResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). The file is not ready because no photo has been used yet.");
		}
		File file = new File(mainact.lastPhotoFilePath);

		return createResponse(Status.OK, "image/jpeg", file);
	}
	
	private Response http_delete_vote(long id) {
		if (id>mainact.allvotes.size()) {
			return http_error(Status.BAD_REQUEST, "The vote you try to delete does not exists");
		}
		Vote vote=mainact.allvotes.get((int) id);
		if (vote.deleted) {
			return http_error(Status.BAD_REQUEST, "The vote you try to delete is already deleted");
		}
		vote.deleted=true;
		vote.jsonmarks=null;
		vote.create_time=-1;
		vote.change_time=System.currentTimeMillis() / 1000;
		vote.prcount[0]=vote.prcount[1]=vote.prcount[2]=vote.prcount[3]=-1;
		mainact.dbHelper.deleteVote(vote);
		return createResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Photo deleted");
	}
	
	// return -1 if null, long value otherwise
	private long parseLongOrNull(String string) throws NumberFormatException {
		if (string==null)
			return -1;
		return Long.parseLong(string);
	}
	
	private boolean notLocal(Map <String,String> headers) {
		return true;
// return (headers.get("http-client-ip").equals("127.0.0.1"));
	}

	public Response serve(IHTTPSession session) {
		Method method = session.getMethod();
		Map <String, String> headers=session.getHeaders();
		String uri = session.getUri();
		if (uri.length() < 2)
			return http_error(Status.BAD_REQUEST);
		if (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		String path[] = uri.substring(1).split("/");
		
		Log.i("Votar WebServer", "Request: " + uri + ", params: " + session.getParms());
		
		Map<String, String> querymap = session.getParms();

		switch (method) {
		case GET:
			if (path.length != 1) {
				return http_error(Status.BAD_REQUEST);
			}
			if (path[0].equals("votes")) {
				long startid=-1, endid=-1;
				long startcreate=-1, endcreate=-1, startchange=-1, endchange=-1;
				try {
					startid=parseLongOrNull(querymap.get("start_id"));
				} catch (NumberFormatException e) {
					return http_error(Status.BAD_REQUEST, "start_id argument can't be parsed as number");
				}
				try {
					endid=parseLongOrNull(querymap.get("end_id"));
				} catch (NumberFormatException e) {
					return http_error(Status.BAD_REQUEST, "end_id argument can't be parsed as number");
				}
				try {
					startcreate=parseLongOrNull(querymap.get("start_create"));
				} catch (Exception e) {
					return http_error(Status.BAD_REQUEST, "start_create argument can't be parsed as an unix timestamp");
				}
				try {
					endcreate=parseLongOrNull(querymap.get("start_create"));
				} catch (Exception e) {
					return http_error(Status.BAD_REQUEST, "end_create argument can't be parsed as an unix timestamp");
				}
				try {
					startchange=parseLongOrNull(querymap.get("start_change"));
				} catch (Exception e) {
					return http_error(Status.BAD_REQUEST, "start_change argument can't be parsed as an unix timestamp");
				}
				try {
					startchange=parseLongOrNull(querymap.get("start_create"));
				} catch (Exception e) {
					return http_error(Status.BAD_REQUEST, "end_change argument can't be parsed as an unix timestamp");
				}
				return http_votes(startid, endid, startcreate, endcreate, startchange, endchange);
			}
			if (path[0].equals("events")) {
				// ### TODO
			}
			if (path[0].equals("photo")) {
				if (path.length != 1) {
					return http_error(Status.BAD_REQUEST);
				}
				long id;
				try {
					id=parseLongOrNull(querymap.get("id"));
				} catch (NumberFormatException e) {
					return http_error(Status.BAD_REQUEST, "id argument can't be parsed as number");
				}
				return http_photo(id);
			}
		case POST:
			if (path[0].equals("photo")) {
				mainact.startCamera();
				return http_error(Status.OK, "Started photo capture on the device");
			}
		case DELETE:
			long id=-1;
			if (path[0].equals("vote")) {
				if (notLocal(headers)) {
					return http_error(Status.FORBIDDEN, "DELETE opperation need special privileges (in general, can't be run remotely)");
				}
				try {
					id=parseLongOrNull(querymap.get("id"));
				} catch (NumberFormatException e) {
					return http_error(Status.BAD_REQUEST, "id argument can't be parsed as number");
				}
				if (id<0) {
					return http_error(Status.BAD_REQUEST, "you need to provide the id of the vote to delete");
				}
				
				return http_delete_vote(id);
			}
			
		default:
		}
		return http_error(Status.BAD_REQUEST);
		/*
		//Log.i("Votar WebServer", "Request: "+uri);
		
		if (uri.equals("/photo")) {
			if (mainact.photoLock==null) {
				return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). The file is not ready because no photo has been used yet.");
			}
			try {
				if (!mainact.photoLock.await(60, TimeUnit.SECONDS)) {
					return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was locked for too long and can't deliver the file.");
				}
			} catch (InterruptedException e) {
				return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was interrupted before the server could deliver the file.");
			}
			if (mainact.lastPhotoFilePath==null) {
				return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). The file is not ready because no photo has been used yet.");
			}
			FileInputStream fis;
			try {
				fis = new FileInputStream(new File(mainact.lastPhotoFilePath));
			} catch (FileNotFoundException e) {
				return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The server failed while attempting to read the file to deliver.");
			}
			return createResponse(Response.Status.OK, "image/jpeg", fis);
		}
		if (uri.equals("/points")) {
			if (mainact.photoLock==null) {
				return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). The file is not ready because no photo has been used yet.");
			}
			try {
				if (!mainact.pointsLock.await(60, TimeUnit.SECONDS)) {
					return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was locked for too long and can't deliver the file.");
				}
			} catch (InterruptedException e) {
				return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The process was interrupted before the server could deliver the file.");
			}
			if (mainact.lastPointsJsonString==null) {
				return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). There was an error in the application and the points data failed to be generated.");
			}
			return createResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, mainact.lastPointsJsonString);
		}
		if (uri.equals("/datatimestamp")) {
			return createResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, ""+mainact.datatimestamp);
		}
		if (uri.equals("/") || uri.equals("/footer_deco.png") || uri.equals("/votar_logo.png")) {
			String mime, filename;
			if (uri.equals("/")) {
				mime=NanoHTTPD.MIME_HTML;
				filename="index.html";
			} else {
				mime="image/png";
				filename=uri.substring(1);
			}
			InputStream is;
			try {
				is = mainact.assetMgr.open(filename);
			} catch (IOException e) {
				return createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error 500 (INTERNAL SERVER ERROR). The server failed while attempting to read the static file to deliver.");
			}
			return createResponse(Response.Status.OK, mime, is);
			
		}
		return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404 (NOT FOUND). I'm sorry. My responses are limited. You must ask the right questions.");
		*/
	}
}
