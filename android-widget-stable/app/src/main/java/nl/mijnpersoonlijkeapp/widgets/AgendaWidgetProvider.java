package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AgendaWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Agenda";}
    String title(){return "Afspraken";}
    String emptyText(){return "Geen komende afspraken";}
    String target(){return "today-agenda";}
    int iconRes(){return R.drawable.ic_grid_agenda;}
    int requestCode(){return 521;}

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("nl-NL"));
    private static final int[] ROW_IDS = {
            R.id.category_row_1,R.id.category_row_2,R.id.category_row_3,R.id.category_row_4
    };
    private static final int[] DATE_IDS = {
            R.id.agenda_date_1,R.id.agenda_date_2,R.id.agenda_date_3,R.id.agenda_date_4
    };
    private static final int[] LINE_IDS = {
            R.id.agenda_line_1,R.id.agenda_line_2,R.id.agenda_line_3,R.id.agenda_line_4
    };

    @Override void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.widget_agenda);
        v.setTextViewText(R.id.category_title,title());
        v.setImageViewResource(R.id.category_icon,iconRes());
        // Dezelfde kleuren, rand en transparantie als de overige kleine widgets.
        WidgetStyle.applyCategory(v,context,id);
        v.setOnClickPendingIntent(R.id.category_root,WidgetLinks.open(context,target(),requestCode()));
        v.setViewVisibility(R.id.category_tabs,View.GONE);

        // Een vaste datumkolom houdt de afspraaknamen altijd op dezelfde plek.
        // Het tijdstip blijft in de app beschikbaar, niet in deze compacte widget.
        JSONArray events=CalendarData.upcomingEvents(context,4);
        int count=Math.min(ROW_IDS.length,events.length());
        int accent=WidgetStyle.accent(context,id);
        for(int i=0;i<ROW_IDS.length;i++){
            if(i<count){
                JSONObject event=events.optJSONObject(i);
                v.setTextViewText(DATE_IDS[i],event==null?"":dateLabel(event));
                v.setTextViewText(ROW_IDS[i],event==null?"":event.optString("summary","Afspraak"));
                v.setTextColor(DATE_IDS[i],accent);
                v.setViewVisibility(LINE_IDS[i],View.VISIBLE);
            }else if(i==0&&count==0){
                v.setTextViewText(DATE_IDS[i],"");
                v.setTextViewText(ROW_IDS[i],emptyText());
                v.setViewVisibility(LINE_IDS[i],View.VISIBLE);
            }else v.setViewVisibility(LINE_IDS[i],View.GONE);
        }
        v.setViewVisibility(R.id.category_more,View.GONE);
        manager.updateAppWidget(id,v);
    }

    private static String dateLabel(JSONObject event){
        try {
            JSONObject start=event.optJSONObject("start");
            if(start==null)return "";
            String dateOnly=start.optString("date","");
            LocalDate date;
            if(!dateOnly.isEmpty())date=LocalDate.parse(dateOnly);
            else date=OffsetDateTime.parse(start.getString("dateTime"))
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            return date.format(DATE_LABEL);
        }catch(Exception ignored){return "";}
    }
}
