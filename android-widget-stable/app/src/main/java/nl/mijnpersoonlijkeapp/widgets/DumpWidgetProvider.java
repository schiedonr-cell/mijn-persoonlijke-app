package nl.mijnpersoonlijkeapp.widgets;
public class DumpWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Inbox";} String icon(){return "";} int iconRes(){return R.drawable.ic_inbox;} String target(){return "dump";} int requestCode(){return 501;}
}