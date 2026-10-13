package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AgendaWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Agenda";}
    String title(){return "Volgende afspraken";}
    String emptyText(){return "Geen komende afspraken";}
    String target(){return "today-agenda";}
    int iconRes(){return R.drawable.ic_grid_agenda;}
    int requestCode(){return 521;}
    boolean showTime(){return true;}

    private static final int[] ROW_IDS = {
        R.id.category_row_1, R.id.category_row_2, R.id.category_row_3,
        R.id.category_row_4, R.id.category_row_5, R.id.category_row_6,
        R.id.category_row_7, R.id.category_row_8, R.id.category_row_9,
        R.id.category_row_10, R.id.category_row_11, R.id.category_row_12
    };

    @Override void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.widget_category);
        v.setTextViewText(R.id.category_title,title());
        v.setImageViewResource(R.id.category_icon,iconRes());
        WidgetStyle.applyCategory(v,context,id);
        v.setOnClickPendingIntent(R.id.category_root,WidgetLinks.open(context,target(),requestCode()));
        // Agenda is altijd de volgende vier: geen misleidende Vandaag/Morgen-tabs.
        v.setViewVisibility(R.id.category_tabs,View.GONE);
        JSONArray events=CalendarData.upcomingEvents(context,4);
        int count=Math.min(4,events.length());
        for(int i=0;i<ROW_IDS.length;i++){
            if(i<count){
                JSONObject event=events.optJSONObject(i);
                v.setTextViewText(ROW_IDS[i],event==null?"":formatEvent(event));
                v.setViewVisibility(ROW_IDS[i],View.VISIBLE);
            }else if(i==0&&count==0){
                v.setTextViewText(ROW_IDS[i],emptyText());
                v.setViewVisibility(ROW_IDS[i],View.VISIBLE);
            }else v.setViewVisibility(ROW_IDS[i],View.GONE);
        }
        v.setViewVisibility(R.id.category_more,View.GONE);
        manager.updateAppWidget(id,v);
    }

    private String formatEvent(JSONObject event){
        try{
            JSONObject start=event.optJSONObject("start");
            if(start==null)return event.optString("summary","Afspraak");
            ZoneId zone=ZoneId.systemDefault();
            LocalDate date;
            String time="";
            String dateOnly=start.optString("date","");
            if(!dateOnly.isEmpty()) date=LocalDate.parse(dateOnly);
            else{
                OffsetDateTime dt=OffsetDateTime.parse(start.getString("dateTime"));
                date=dt.atZoneSameInstant(zone).toLocalDate();
                time=" "+dt.atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            String day=date.format(DateTimeFormatter.ofPattern("d MMM",Locale.forLanguageTag("nl-NL")));
            return day+time+"  "+event.optString("summary","Afspraak");
        }catch(Exception ignored){
            return event.optString("summary","Afspraak");
        }
    }
}
