package ca.cumulonimbus.barometernetwork;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;

public class ForecastService extends Service {

	public static String ACTION_UPDATE_FORECAST = "ca.cumulonimbus.barometernetwork.ACTION_UPDATE_FORECAST";
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		// Run a forecast update
		log("app received action_update_forecast, running temperature forecast updates");
		
		downloadTemperatures();
		
		
	
		return super.onStartCommand(intent, flags, startId);
	}
	
	
	private class DelayedTemperatureDownloader implements Runnable {

		@Override
		public void run() {
			TemperatureDownloader temps = new TemperatureDownloader();
			temps.execute("");
		}
		
	}
	
	private void downloadTemperatures() {
		log("forecast service downloading temperature data");
		
		
		Handler handle = new Handler();
		handle.postDelayed(new DelayedTemperatureDownloader(), 1000*30);
	}
	
	@SuppressWarnings("deprecation")
	public class TemperatureDownloader extends AsyncTask<String, String, String> {

		@Override
		protected String doInBackground(String... params) {
			String responseText = "";
			try {
				DefaultHttpClient client = new DefaultHttpClient();

				String serverURL = PressureNETConfiguration.TEMPERATURE_FORECASTS;

				log("app downloading temperature forecasts");
				HttpGet get = new HttpGet(serverURL);
				// Execute the GET call and obtain the response
				HttpResponse getResponse = client.execute(get);
				HttpEntity responseEntity = getResponse.getEntity()	;
				log("temperature response " + responseEntity.getContentLength());
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(responseEntity.getContent(), "UTF-8"));
				responseText = reader.readLine();

			} catch (Exception e) {
				log("app temperature exception " + e.getMessage());
			}
			
			
			try {
				log("forecast service finished downloading, now processing json temperatures");
				JSONObject object = new JSONObject(responseText);
				JSONArray forecastArray = object.getJSONArray("data");
				
				String forecastID;
				double latitude;
				double longitude;
				ArrayList<ForecastLocation> forecastLocations = new ArrayList<ForecastLocation>();
				ArrayList<TemperatureForecast> temperatureForecasts = new ArrayList<TemperatureForecast>();
				
				PnDb db = new PnDb(getApplicationContext());
				db.open();
				
				db.deleteAllTemperatureData();
				
				SQLiteDatabase mDB = db.getDB();
				mDB.beginTransaction();
				
				String insertTemperatureSQL = "INSERT INTO "
						+ PnDb.TEMPERATURES
						+ " ("
						+ PnDb.KEY_FORECAST_LOCATION_ID
						+ ", "
						+ PnDb.KEY_TEMP_FORECAST_START_TIME
						+ ", "
						+ PnDb.KEY_TEMP_FORECAST_HOUR
						+ ", "
						+ PnDb.KEY_TEMP_FORECAST_SCALE
						+ ", "
						+ PnDb.KEY_TEMP_FORECAST_VALUE
						+ ") values (?, ?, ?, ?, ?)";
				
				String insertLocationSQL = "INSERT INTO "
						+ PnDb.FORECAST_LOCATIONS
						+ " ("
						+ PnDb.KEY_FORECAST_LOCATION_ID
						+ ", "
						+ PnDb.KEY_FORECAST_LATITUDE
						+ ", "
						+ PnDb.KEY_FORECAST_LONGITUDE
						+ ") values (?, ?, ?)";
				
				SQLiteStatement insertTemperatures = mDB.compileStatement(insertTemperatureSQL);
				SQLiteStatement insertLocations = mDB.compileStatement(insertLocationSQL);
				
				for(int i = 0; i< forecastArray.length(); i++ ){
					
					// Location
					
					JSONObject row = forecastArray.getJSONObject(i);
					forecastID = row.getString("id");
					JSONObject location = row.getJSONObject("location");
					latitude = location.getDouble("latitude");
					longitude = location.getDouble("longitude");
					
					// forecastLocations.add(new ForecastLocation(forecastID, latitude, longitude));
					insertLocations.bindString(1, forecastID);
					insertLocations.bindDouble(2, latitude);
					insertLocations.bindDouble(3, longitude);
					insertLocations.executeInsert();
					
					
					// Temperature forecasts
					JSONObject forecastObject = row.getJSONObject("temperatureForecast");
					String forecastTime = forecastObject.getString("date");
					JSONArray forecastTemps = forecastObject.getJSONArray("forecast");
					
					for(int j = 0; j < forecastTemps.length(); j++) {
						JSONObject forecastRow = forecastTemps.getJSONObject(j);
						int scale = forecastRow.getInt("scale");
						double degrees = forecastRow.getDouble("degrees");
						
						//temperatureForecasts.add(new TemperatureForecast(forecastID, scale, degrees, forecastTime, j));
						insertTemperatures.bindString(1, forecastID);
						insertTemperatures.bindString(2, forecastTime);
						insertTemperatures.bindLong(3, j);
						insertTemperatures.bindLong(4, scale);
						insertTemperatures.bindDouble(5, degrees);
						insertTemperatures.executeInsert();
					}

				}
				mDB.setTransactionSuccessful();
				mDB.endTransaction();
				// log("forecast service created both arraylists, size " + forecastLocations.size() + ", " + temperatureForecasts.size() ); 
				
				//db.addTemperatureForecastArrayList(temperatureForecasts);
				//log("forecast service added temp forecast array list");
				// db.addForecastLocationArrayList(forecastLocations);
				db.close();
				log("forecast service done; added locations to db");
			} catch(JSONException jsone) {
				log("app failed to parse temperature json: " + jsone.getMessage());
			}
			
			
			return responseText;
		}

		protected void onPostExecute(String result) {
			// notify app that data is ready
			
		}
		
		private void log(String text) {
			if(PressureNETConfiguration.DEBUG_MODE) {
				System.out.println(text);
			}
		}

	}

	
	@Override
	public IBinder onBind(Intent intent) {
		
		return null;
	}

	
	private void log(String text) {
		if(PressureNETConfiguration.DEBUG_MODE) {
			System.out.println(text);
		}
	}
	
}
