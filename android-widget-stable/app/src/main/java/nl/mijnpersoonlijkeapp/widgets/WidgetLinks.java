package nl.mijnpersoonlijkeapp.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class WidgetLinks {
    private static final String BASE = "https://schiedonr-cell.github.io/mijn-persoonlijke-app/";
    private WidgetLinks() {}

    static PendingIntent open(Context context, String target, int requestCode) {
        String url = target == null || target.isEmpty() ? BASE : BASE + "?open=" + target;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
