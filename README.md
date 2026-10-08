# קום | Koom 2.0

**שעון מעורר שלא נותן לך לחזור לישון בלי לפתור חידה.**

גרסה חדשה מהיסוד ל-Android: Kotlin מקורי, Jetpack Compose, Material 3 וצלצול המופעל על ידי מערכת ההפעלה.

## יכולות

- יצירה, עריכה, מחיקה, ביטול והפעלה של שעונים חד פעמיים וחוזרים.
- שעון מערכת אמיתי באמצעות AlarmManager.setAlarmClock, בלי polling ובלי שירות קבוע.
- שירות צלצול עצמאי רק בזמן השכמה, צלצול בלולאה, רטט ומסך נעילה.
- שלושה אתגרי חשבון, שלוש סדרות, או פאזל תמונה 3×3 עם החלפת אריחים.
- ממשק עברי מלא RTL עם עיצוב Material 3 כהה.
- שמירת שעונים סינכרונית, טיפול באתחול, שינוי שעה/אזור זמן, עדכון אפליקציה.
- מיגרציה של שעונים מהאחסון של Flutter, תוך שמירת הנתונים המקוריים.
- ללא רשת, ללא שרת, ללא חשבון וללא ספריות צד שלישי למשימות רקע.

## למה Android Native?

אמינות ההשכמה נשענת על ממשקי Android ולא על תזמון Dart, מנוע Flutter או תוסף רקע. Kotlin מאפשר מגע ישיר עם AlarmManager, BroadcastReceiver, MediaPlayer ושירות foreground. 

## איך לבנות

- נדרש JDK 17, Android SDK Platform 37, Build Tools 36.0.0.
- גרסאות: AGP 9.4.0, Gradle 9.6.0, Kotlin Compose 2.4.10.
- Android 8.0 ומעלה, חבילת Android קיימת: top.zekal.koom.
- פתחו את התיקייה הראשית כפרויקט Android Studio תואם, או התקינו Gradle והפעילו:

```shell
gradle testDebugUnitTest
gradle assembleDebug
gradle lintDebug
```

CI ב-GitHub Actions מריץ Unit Tests, build ו-lint בענף השכתוב. קובץ APK נוצר ב-app/build/outputs/apk/debug/app-debug.apk.

**חשוב:** APK יכול לעדכן התקנה קיימת רק אם החתימה זהה לחתימה המקורית. אין להסיר את ההתקנה הקודמת לפני גיבוי. אימות מיגרציה ושירות הצלצול דורש מכשיר Android אמיתי.

## תכנון המערכת

| קובץ | תפקיד |
|---|---|
| Alarm.kt, AlarmRules.kt | מודל, חישוב הימים והשעה הבאה |
| AlarmStore.kt | אחסון מקומי, מיגרציה וקבלת התראה אטומית |
| AlarmScheduler.kt | setAlarmClock ותזמון מחדש |
| AlarmReceiver.kt, SystemReceiver.kt | קליטת השכמה ואירועי מערכת |
| RingService.kt, RingActivity.kt | צלצול עצמאי ומסך הנעילה |
| PuzzleEngine.kt | לוגיקת חידות טהורה |
| MainActivity.kt, ui/ | מסכי Compose, הרשאות ו-RTL |

## מגבלות ופערי תאימות

- הגרסה החדשה מיועדת ל-Android בלבד; לא נבנו גרסאות iOS/desktop/web.
- נשמר פאזל התמונה המובנית; אין עדיין יבוא תמונה אישית.
- ב-Android 14 ומעלה ייתכן צורך לאפשר full-screen intents בהגדרות.
- הרשאות exact alarms ו-post notifications אינן ניתנות לכפייה; האפליקציה מציגה פעולת תיקון.
- אין מניעה מערכתית של Force Stop או הסרת האפליקציה; גם שעון מקורי אינו יכול להבטיח התעוררות בכל יצרן ומצב סוללה.
- מספר שעונים שמצלצלים במקביל מנוהלים בתור עד שכולם נפתרים.
- בשעת קפיצה של שעון קיץ, זמן מקומי שאינו קיים מועבר לזמן החוקי הבא על פי Java Time.

## בדיקות חובה לפני הפצה

1. התקנת APK מעודכן עם חתימה זהה על גבי גרסת Flutter ובדיקת מעבר שעונים.
2. צלצול אמיתי בעוד 2 דקות כאשר המסך נעול והאפליקציה אינה פתוחה.
3. צלצול אחרי reboot, אחרי שינוי timezone, ובהיעדר חיבור רשת.
4. הרשאות נחסמות: הודעות, exact alarm ו-full-screen; בדיקת הטיפול בחסימה.
5. שעונים חופפים, שעה חד פעמית, חזרה, חידות, מחיקה וכיבוי.
6. מספר מכשירים וגרסאות Android, לרבות מצב חיסכון בסוללה.

## מקורות לתכנון

- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler

License: LICENSE
