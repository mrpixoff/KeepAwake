# Component classes are referenced from the manifest and must survive shrinking.
-keep class com.example.keepawake.** extends android.app.Service { *; }
-keep class com.example.keepawake.** extends android.service.quicksettings.TileService { *; }
-keep class com.example.keepawake.** extends android.content.BroadcastReceiver { *; }
