package nl.mijnpersoonlijkeapp.widgets;
public class ProjectsWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Projecten";} String icon(){return "◆";} String target(){return "projects";} int requestCode(){return 503;}
}