package nl.mijnpersoonlijkeapp.widgets;

import org.json.JSONObject;

public class AgendaWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Agenda";}
    String title(){return "Agenda";}
    String emptyText(){return "Geen afspraken";}
    String target(){return "today-agenda";}
    int iconRes(){return R.drawable.ic_grid_agenda;}
    int requestCode(){return 521;}
    boolean showTime(){return true;}

    @Override String formatLine(JSONObject row) {
        String text=row.optString("text","");
        String time=row.optString("time","").trim();
        if(time.isEmpty()) return text;

        if(time.startsWith("Vandaag · ")) {
            return time.substring("Vandaag · ".length()) + "  " + text;
        }
        if("Vandaag".equals(time)) {
            return text;
        }
        if(time.startsWith("Morgen · ")) {
            return "Morgen · " + time.substring("Morgen · ".length()) + "  " + text;
        }
        if(time.startsWith("Overmorgen · ")) {
            return "Overmorgen · " + time.substring("Overmorgen · ".length()) + "  " + text;
        }
        return time + "  " + text;
    }
}
