package nl.mijnpersoonlijkeapp.widgets;
public class TasksWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Takenbord";} String icon(){return "☷";} String target(){return "tasks";} int requestCode(){return 502;}
}