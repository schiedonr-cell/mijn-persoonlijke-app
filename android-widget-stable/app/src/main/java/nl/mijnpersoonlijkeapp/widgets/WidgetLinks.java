package nl.mijnpersoonlijkeapp.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class WidgetLinks {
    private WidgetLinks() {}

    static PendingIntent open(Context context, String target, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("target", target == null ? "" : target);
        intent.setAction("nl.mijnpersoonlijkeapp.OPEN_" + requestCode + "_" + (target == null ? "" : target));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
