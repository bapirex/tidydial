# libphonenumber loads its region metadata reflectively by resource name.
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn com.google.i18n.phonenumbers.**
