package nl.mijnpersoonlijkeapp.widgets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://schiedonr-cell.github.io/mijn-persoonlijke-app/")));
        finish();
    }
}
