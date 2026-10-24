package com.nik.minimeteo;

import android.app.PendingIntent;
import android.util.Log;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherWidgetProvider extends AppWidgetProvider {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final String PREFS = "mini_weather";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : appWidgetIds) {
            e.remove("lat_" + id);
            e.remove("lon_" + id);
            e.remove("locality_" + id);
            e.remove("last_temp_" + id);
            e.remove("last_code_" + id);
        }
        e.apply();
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews loading = baseViews(context, appWidgetId);
        loading.setTextViewText(R.id.weather_temp, cachedTemperature(context, appWidgetId));
        manager.updateAppWidget(appWidgetId, loading);

        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat_" + appWidgetId) || !p.contains("lon_" + appWidgetId)) {
            RemoteViews rv = baseViews(context, appWidgetId);
            rv.setTextViewText(R.id.weather_temp, cachedTemperature(context, appWidgetId));
            rv.setImageViewResource(R.id.weather_icon, R.drawable.ic_partly_cloudy);
            rv.setTextViewText(R.id.weather_locality, "VILLE À CONFIGURER");
            manager.updateAppWidget(appWidgetId, rv);
            return;
        }

        double lat = Double.longBitsToDouble(p.getLong("lat_" + appWidgetId, 0));
        double lon = Double.longBitsToDouble(p.getLong("lon_" + appWidgetId, 0));

        EXECUTOR.execute(() -> {
            Exception failure = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpURLConnection c = null;
                try {
                    String endpoint = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,weather_code&timezone=auto",
                        lat, lon);
                    c = (HttpURLConnection) new URL(endpoint).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "MiniMeteoWidget/1.2");
                    int httpStatus = c.getResponseCode();
                    if (httpStatus != 200) throw new java.io.IOException("HTTP " + httpStatus);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONObject current = new JSONObject(sb.toString()).getJSONObject("current");
                    int temp = (int) Math.round(current.getDouble("temperature_2m"));
                    int code = current.getInt("weather_code");
                    p.edit().putInt("last_temp_" + appWidgetId, temp).putInt("last_code_" + appWidgetId, code).apply();
                    RemoteViews rv = baseViews(context, appWidgetId);
                    rv.setTextViewText(R.id.weather_temp, temp + "°");
                    rv.setImageViewResource(R.id.weather_icon, drawableForCode(code));
                    manager.updateAppWidget(appWidgetId, rv);
                    return;
                } catch (Exception error) {
                    failure = error;
                    Log.w("MerylMeteo", "Weather fetch attempt " + (attempt + 1) + " failed", error);
                    if (error.getMessage() != null && error.getMessage().startsWith("HTTP 4")) break;
                    if (attempt < 2) {
                        try { Thread.sleep(1000L * (attempt + 1)); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                    }
                } finally {
                    if (c != null) c.disconnect();
                }
            }
            RemoteViews rv = baseViews(context, appWidgetId);
            rv.setTextViewText(R.id.weather_temp, cachedTemperature(context, appWidgetId));
            rv.setImageViewResource(R.id.weather_icon, drawableForCode(p.getInt("last_code_" + appWidgetId, 2)));
            String kind = failure == null ? "INCONNUE" :
                failure instanceof java.net.UnknownHostException ? "DNS" :
                failure instanceof java.net.SocketTimeoutException ? "DÉLAI" :
                failure instanceof javax.net.ssl.SSLException ? "SSL" :
                failure instanceof org.json.JSONException ? "DONNÉES" :
                failure.getMessage() != null && failure.getMessage().startsWith("HTTP ") ? failure.getMessage() :
                failure.getClass().getSimpleName();
            rv.setTextViewText(R.id.weather_locality, kind);
            manager.updateAppWidget(appWidgetId, rv);
        });
    }

    private static String cachedTemperature(Context context, int widgetId) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.contains("last_temp_" + widgetId) ? p.getInt("last_temp_" + widgetId, 0) + "°" : "--°";
    }

    private static RemoteViews baseViews(Context context, int appWidgetId) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_weather);

        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String locality = p.getString("locality_" + appWidgetId, "");
        String date = new SimpleDateFormat("EEE d MMM", Locale.FRENCH)
            .format(new Date())
            .toUpperCase(Locale.FRENCH);

        rv.setTextViewText(R.id.weather_date, date);
        rv.setTextViewText(R.id.weather_locality, locality == null ? "" : locality.toUpperCase(Locale.FRENCH));

        Intent click = new Intent(context, WeatherProxyActivity.class);
        click.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pi = PendingIntent.getActivity(
            context,
            appWidgetId,
            click,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        return rv;
    }

    private static int drawableForCode(int code) {
        if (code == 0) return R.drawable.ic_sun;
        if (code == 1 || code == 2) return R.drawable.ic_partly_cloudy;
        if (code == 3) return R.drawable.ic_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_rain;
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) return R.drawable.ic_snow;
        if (code >= 95) return R.drawable.ic_thunder;
        return R.drawable.ic_partly_cloudy;
    }
}
