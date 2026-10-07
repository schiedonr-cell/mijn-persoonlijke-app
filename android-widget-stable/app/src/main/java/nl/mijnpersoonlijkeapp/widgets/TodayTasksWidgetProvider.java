package nl.mijnpersoonlijkeapp.widgets;
public class TodayTasksWidgetProvider extends CategoryWidgetProvider {
    String kind(){return "Taak";} String title(){return "Taken";} String emptyText(){return "Geen taken";}
    String target(){return "today-tasks";} int iconRes(){return R.drawable.ic_grid_tasks;} int requestCode(){return 522;}
}