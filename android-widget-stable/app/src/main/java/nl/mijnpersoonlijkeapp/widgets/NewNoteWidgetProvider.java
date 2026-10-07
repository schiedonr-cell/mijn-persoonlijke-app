package nl.mijnpersoonlijkeapp.widgets;
public class NewNoteWidgetProvider extends SimpleShortcutWidgetProvider {
    String title(){return "Nieuwe notitie";} String icon(){return "+";} String target(){return "note-new";} int requestCode(){return 504;}
}