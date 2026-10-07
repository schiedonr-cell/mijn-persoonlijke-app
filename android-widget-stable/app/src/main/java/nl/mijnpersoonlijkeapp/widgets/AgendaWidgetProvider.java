package nl.mijnpersoonlijkeapp.widgets;
public class AgendaWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Agenda";} String title(){return "Agenda";} String emptyText(){return "Geen afspraken";}
    String target(){return "today-agenda";} int iconRes(){return R.drawable.ic_grid_agenda;} int requestCode(){return 521;}
    boolean showTime(){return true;}
}