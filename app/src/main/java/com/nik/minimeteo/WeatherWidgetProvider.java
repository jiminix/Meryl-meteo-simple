package com.nik.minimeteo;

import android.app.PendingIntent;
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
        }
        e.apply();
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews loading = baseViews(context, appWidgetId);
        loading.setTextViewText(R.id.weather_temp, "…");
        manager.updateAppWidget(appWidgetId, loading);

        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat_" + appWidgetId) || !p.contains("lon_" + appWidgetId)) {
            RemoteViews rv = baseViews(context, appWidgetId);
            rv.setTextViewText(R.id.weather_temp, "--°");
            rv.setImageViewResource(R.id.weather_icon, R.drawable.ic_partly_cloudy);
            manager.updateAppWidget(appWidgetId, rv);
            return;
        }

        double lat = Double.longBitsToDouble(p.getLong("lat_" + appWidgetId, 0));
        double lon = Double.longBitsToDouble(p.getLong("lon_" + appWidgetId, 0));

        EXECUTOR.execute(() -> {
            try {
                String endpoint = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,weather_code&timezone=auto",
                    lat, lon);
                HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "MiniMeteoWidget/1.0");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    JSONObject current = new JSONObject(sb.toString()).getJSONObject("current");
                    int temp = (int) Math.round(current.getDouble("temperature_2m"));
                    int code = current.getInt("weather_code");

                    RemoteViews rv = baseViews(context, appWidgetId);
                    rv.setTextViewText(R.id.weather_temp, temp + "°");
                    rv.setImageViewResource(R.id.weather_icon, drawableForCode(code));
                    manager.updateAppWidget(appWidgetId, rv);
                } finally {
                    c.disconnect();
                }
            } catch (Exception ignored) {
                RemoteViews rv = baseViews(context, appWidgetId);
                rv.setTextViewText(R.id.weather_temp, "--°");
                manager.updateAppWidget(appWidgetId, rv);
            }
        });
    }

    private static RemoteViews baseViews(Context context, int appWidgetId) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_weather);
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
