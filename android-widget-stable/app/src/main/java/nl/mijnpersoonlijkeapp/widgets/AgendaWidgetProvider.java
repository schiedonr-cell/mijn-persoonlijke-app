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

        // Vandaag: tijd + afspraak. Toekomst: alleen dag/datum + afspraak,
        // zodat de naam van de afspraak zoveel mogelijk ruimte krijgt.
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
