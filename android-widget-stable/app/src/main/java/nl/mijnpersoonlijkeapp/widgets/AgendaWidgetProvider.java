package nl.mijnpersoonlijkeapp.widgets;

import org.json.JSONObject;

public class AgendaWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Agenda";}
    String title(){return "Agenda";}
    String emptyText(){return "Geen afspraken vandaag";}
    String target(){return "today-agenda";}
    int iconRes(){return R.drawable.ic_grid_agenda;}
    int requestCode(){return 521;}
    boolean showTime(){return true;}

    @Override String formatLine(JSONObject row) {
        String text=row.optString("text","");
        String time=row.optString("time","").trim();
        if(time.isEmpty()) return text;

        // Alleen afspraken van vandaag komen via de widgetsnapshot binnen.
        if(time.startsWith("Vandaag · ")) {
            return time.substring("Vandaag · ".length()) + "  " + text;
        }
        if("Vandaag".equals(time)) {
            return text;
        }

        int sep=time.indexOf(" · ");
        if(sep>0) {
            return time.substring(0,sep) + "  " + text;
        }
        return time + "  " + text;
    }}
