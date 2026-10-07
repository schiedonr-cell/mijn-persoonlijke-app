package nl.mijnpersoonlijkeapp.widgets;
public class HouseholdWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Huis";} String title(){return "Huishouden";} String emptyText(){return "Alles gedaan";}
    String target(){return "today";} int iconRes(){return R.drawable.ic_grid_house;} int requestCode(){return 524;}
}