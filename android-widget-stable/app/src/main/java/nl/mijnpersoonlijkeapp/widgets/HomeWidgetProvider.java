package nl.mijnpersoonlijkeapp.widgets;

public class HomeWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Mijn dag";}
    String icon(){return "⌂";}
    String target(){return "home";}
    int requestCode(){return 530;}
}
