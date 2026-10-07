package nl.mijnpersoonlijkeapp.widgets;
public class DumpWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Dump";} String icon(){return "↓";} String target(){return "dump";} int requestCode(){return 501;}
}