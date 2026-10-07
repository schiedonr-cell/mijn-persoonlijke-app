package nl.mijnpersoonlijkeapp.widgets;
public class RoutinesWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Routine";} String title(){return "Routines";} String emptyText(){return "Alles gedaan";}
    String target(){return "today";} int iconRes(){return R.drawable.ic_grid_routines;} int requestCode(){return 523;}
}