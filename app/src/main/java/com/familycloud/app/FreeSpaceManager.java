package com.familycloud.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FreeSpaceManager {
    public static final int FREE_SPACE_DELETE_REQUEST = 707;

    public static void start(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE);
        Set<String> uploadedUris = new HashSet<>(prefs.getStringSet("uploaded_media_uris", new HashSet<>()));

        if (uploadedUris.isEmpty()) {
            Toast.makeText(activity, "No cloud-backed local files found yet. Run Auto Sync first.", Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();

        for (String value : uploadedUris) {
            try {
                uris.add(Uri.parse(value));
            } catch (Exception ignored) {}
        }

        if (uris.isEmpty()) {
            Toast.makeText(activity, "Nothing safe to delete.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Free up device space?")
                .setMessage(
                        "This deletes only local phone copies already uploaded by this APK.\n\n" +
                        "Cloud files will NOT be deleted.\n\n" +
                        "Files found: " + uris.size()
                )
                .setPositiveButton("Delete Local Copies", (dialog, which) -> deleteLocalCopies(activity, uris))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void deleteLocalCopies(Activity activity, ArrayList<Uri> uris) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent pendingIntent = MediaStore.createDeleteRequest(activity.getContentResolver(), uris);

                activity.startIntentSenderForResult(
                        pendingIntent.getIntentSender(),
                        FREE_SPACE_DELETE_REQUEST,
                        null,
                        0,
                        0,
                        0
                );
            } else {
                ContentResolver resolver = activity.getContentResolver();
                int deleted = 0;

                for (Uri uri : uris) {
                    try {
                        deleted += resolver.delete(uri, null, null);
                    } catch (Exception ignored) {}
                }

                activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
                        .edit()
                        .remove("uploaded_media_uris")
                        .apply();

                Toast.makeText(activity, "Deleted local copies: " + deleted, Toast.LENGTH_LONG).show();
            }
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(activity, "Delete request failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Free space failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void onDeleteResult(Activity activity, int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
                    .edit()
                    .remove("uploaded_media_uris")
                    .apply();

            Toast.makeText(activity, "Local cloud-backed copies deleted.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, "Free space cancelled.", Toast.LENGTH_LONG).show();
        }
    }
}
