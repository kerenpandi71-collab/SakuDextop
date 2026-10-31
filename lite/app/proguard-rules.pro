-keep class moe.shizuku.server.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class android.hardware.input.IInputManager$Stub { *; }
-keepclassmembers class android.view.InputEvent {
    public void setDisplayId(int);
}
