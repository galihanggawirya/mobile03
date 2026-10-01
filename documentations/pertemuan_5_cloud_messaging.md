# BAB 6: FIREBASE CLOUD MESSAGING (FCM)

Firebase Cloud Messaging (FCM) adalah solusi pengiriman pesan lintas platform (cross-platform messaging) dari Google yang memungkinkan pengembang mengirimkan notifikasi (push notification) dan data pesan secara andal, cepat, dan efisien tanpa membebani daya baterai perangkat.

Melalui FCM, server aplikasi dapat memberitahukan aplikasi klien mengenai pembaruan data, promosi, pesan obrolan (chat), atau status transaksi secara real-time.

## 6.1 Konsep Push Notification: Arsitektur FCM, Token Perangkat, dan Payload

### 1. Arsitektur Komunikasi FCM

Sistem push notification berbasis FCM melibatkan tiga komponen utama dalam alur komunikasinya:

```text
+-----------------------+     +----------------------+     +------------------------+
| App Server / Provider |     |     FCM Backend      |     |   Android Client App   |
| (Node.js/Laravel/etc) |---->| (Google Cloud Infra) |---->| (Google Play Services) |
+-----------------------+     +----------------------+     +------------------------+
            |                                                           |
            +----------------- Device Token (HTTPS) <-------------------+
```

1. **App Server / Firebase Console:** Komponen pemrakarsa pesan. Server backend milik aplikasi mengeksekusi permintaan pengiriman pesan ke FCM REST API menggunakan Kunci Server/OAuth2, atau pengembang mengirimkannya manual via Firebase Console.
2. **FCM Backend:** Infrastruktur cloud Google yang menerima permintaan pesan, memvalidasi target, mengantrekan pesan, dan merutekannya ke perangkat target.
3. **Android Client App:** Aplikasi Android yang dipasang di perangkat pengguna. Komponen Google Play Services yang berjalan di latar belakang perangkat menerima paket data dari FCM Backend dan meneruskannya ke aplikasi terkait.

### 2. Device Registration Token (FCM Token)

Device Registration Token adalah string unik berkarakter acak yang dihasilkan oleh FCM SDK untuk setiap instalasi aplikasi pada perangkat tertentu.

* **Fungsi:** Bertindak sebagai alamat tujuan (address destination) spesifik saat pengiriman pesan targeted single device.
* **Siklus Hidup (Lifecycle):** Token tidak bersifat permanen. FCM dapat memperbarui (refresh) token dalam kondisi:
  1. Aplikasi dipasang ulang (re-install) oleh pengguna.
  2. Pengguna melakukan Clear Data / Clear Storage aplikasi.
  3. Aplikasi dipulihkan (restored) pada perangkat baru.
  4. Token kedaluwarsa atau direset secara internal oleh sistem Firebase.

### 3. Struktur dan Tipe Message Payload

FCM mendukung dua jenis key utama pada struktur JSON pesan, yang menentukan bagaimana pesan diproses oleh Android OS:

#### A. Notification Payload
Menggunakan kunci pradefinisi seperti `title`, `body`, dan `image`. Ditangani secara otomatis oleh System Tray jika aplikasi berada di latar belakang (background).

```json
{
  "message": {
    "token": "eXz8...fK9",
    "notification": {
      "title": "Promo Spesial",
      "body": "Dapatkan diskon 50% hari ini saja!"
    }
  }
}
```

#### B. Data Payload
Menggunakan kunci custom key-value bebas yang didefinisikan oleh pengembang (di dalam objek `"data"`). Selalu diproses secara terprogram (programmatically) oleh aplikasi di dalam kode Java, tanpa memandang status aplikasi (foreground maupun background).

```json
{
  "message": {
    "token": "eXz8...fK9",
    "data": {
      "transaction_id": "TRX-9082",
      "status": "PAID",
      "screen_target": "DetailTransactionActivity"
    }
  }
}
```

#### C. Combined Payload (Notification + Data)
Menggabungkan elemen notification dan data sekaligus dalam satu pengiriman JSON.

```json
{
  "message": {
    "token": "eXz8...fK9",
    "notification": {
      "title": "Pesanan Dikirim",
      "body": "Kurir sedang menuju ke lokasi Anda."
    },
    "data": {
      "order_id": "ORD-5541",
      "tracking_url": "https://express.com/track/5541"
    }
  }
}
```

### Contoh Konfigurasi Manifest dan Dependency (XML & Gradle)

**File: `build.gradle` (Module: app)**

```groovy
dependencies {
    // Import Firebase BOM
    implementation platform('com.google.firebase:firebase-bom:33.1.0')
    
    // Firebase Cloud Messaging Library
    implementation 'com.google.firebase:firebase-messaging'
}
```

**File: `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Izin Akses Internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- Izin Menampilkan Notifikasi untuk Android 13+ (API Level 33+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="FCM Lecture App"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## 6.2 Service Handler: Mengimplementasikan Firebase Messaging Service

Untuk menerima, memproses, dan menampilkan notifikasi kustom, aplikasi Android memerlukan komponen Service yang diturunkan dari kelas `FirebaseMessagingService`.

### 1. Peran dan Callback Utama Service
`FirebaseMessagingService` berjalan di latar belakang sistem dan menyediakan dua metode callback utama:
* `onNewToken(String token)`: Dipanggil oleh FCM SDK setiap kali token pendaftaran perangkat baru dihasilkan atau dirotasi. Metode ini digunakan untuk meneruskan token terbaru ke App Server milik kita.
* `onMessageReceived(RemoteMessage message)`: Dipanggil ketika pesan masuk dan aplikasi berada dalam status Foreground, atau ketika pesan mengandung Data Payload.

### 2. Persyaratan Notification Channel (Android 8.0 / API 26+)
Mulai Android 8.0 (Oreo), semua notifikasi yang ditayangkan ke System Tray wajib diasosiasikan dengan sebuah Notification Channel. Kanal ini memungkinkan pengguna mengatur preferensi notifikasi (suara, getaran, atau mematikan grup notifikasi tertentu) melalui Setelan Sistem OS.

### Contoh Implementasi Kode

**File: `AndroidManifest.xml` (Registrasi Service)**
Service wajib didaftarkan di dalam `<application>` dengan intent-filter `com.google.firebase.MESSAGING_EVENT`.

```xml
<service
    android:name=".MyFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

**File: `MyFirebaseMessagingService.java`**

```java
package com.praktikum.fcmlecture;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";
    private static final String CHANNEL_ID = "channel_fcm_lecture";
    private static final String CHANNEL_NAME = "Notifikasi Perkuliahan";

    /**
     * Callback saat Token FCM baru dihasilkan atau di-refresh.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Registration Token: " + token);
        // Kirimkan token baru ke server backend Anda di sini
        sendTokenToBackendServer(token);
    }

    /**
     * Callback saat Pesan FCM Diterima.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());
        
        String title = "Notifikasi Baru";
        String body = "Anda menerima pesan baru.";
        
        // 1. Memeriksa Notification Payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }
        
        // 2. Memeriksa Data Payload
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Data Payload Content: " + data.toString());
            
            if (data.containsKey("custom_title")) {
                title = data.get("custom_title");
            }
            if (data.containsKey("custom_body")) {
                body = data.get("custom_body");
            }
        }
        
        // 3. Tampilkan Notifikasi di System Tray
        sendNotification(title, body, remoteMessage.getData());
    }

    /**
     * Membuat dan Menampilkan Notifikasi Menggunakan NotificationCompat.Builder
     */
    private void sendNotification(String title, String body, Map<String, String> dataMap) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Memasukkan data payload ke Intent
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        
        // PendingIntent wajib diberi flag IMMUTABLE/MUTABLE pada API 31+
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
        // Membuat Notification Channel untuk Android Oreo (API 26) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Kanal untuk Notifikasi FCM");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
        
        NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);
                
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    private void sendTokenToBackendServer(String token) {
        // Implementasi integrasi HTTP POST ke server backend
        Log.i(TAG, "Syncing token to App Server: " + token);
    }
}
```

## 6.3 Handling Message Payload: Foreground, Background, dan Killed

Perilaku (behavior) penerimaan pesan oleh sistem Android dipengaruhi oleh dua variabel: **Status/State Aplikasi** dan **Tipe Payload Pesan**.

### Matrix Perilaku Eksekusi Pesan FCM

| Status Aplikasi | Notification Payload | Data Payload | Combined Payload |
| --- | --- | --- | --- |
| **Foreground** (Aplikasi sedang aktif di layar) | Diproses oleh `onMessageReceived()`. Notifikasi TIDAK otomatis muncul di System Tray kecuali dibuat kustom. | Diproses oleh `onMessageReceived()`. | Diproses oleh `onMessageReceived()`. |
| **Background** (Aplikasi diminimalkan/di-home) | Ditampilkan otomatis oleh OS ke System Tray. `onMessageReceived()` TIDAK dipanggil. | Diproses oleh `onMessageReceived()`. | Notification otomatis ke System Tray. Data masuk ke Intent Extras ketika notifikasi diklik. |
| **Killed / Terminated** (Aplikasi ditutup dari Recent Apps) | Ditampilkan otomatis oleh OS ke System Tray. | Diproses jika High-Priority Data message. | Notification otomatis ke System Tray. Data masuk ke Intent Extras ketika notifikasi diklik. |

### Penanganan Ekstraksi Data dari Notification Intent

Ketika aplikasi berada di status Background atau Killed, pesan bertipe Combined Payload yang ditayangkan otomatis oleh OS akan mengirimkan elemen data ke dalam *Extras* milik Intent peluncur ketika notifikasi diklik oleh pengguna.

**File: `res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="FCM Payload Status Monitor"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />
        
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Pesan Data / Payload Diterima:"
        android:textStyle="bold" />
        
    <TextView
        android:id="@+id/tvPayloadContent"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#EFEFEF"
        android:padding="12dp"
        android:text="Belum ada data payload."
        android:textSize="13sp"
        android:layout_marginTop="8dp"/>
</LinearLayout>
```

**File: `MainActivity.java`**

```java
package com.praktikum.fcmlecture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN_ACTIVITY";
    private TextView tvPayloadContent;
    
    // ActivityResultLauncher untuk Izin Runtime Notification (Android 13+)
    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Izin Notifikasi Diberikan", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Izin Notifikasi Ditolak!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        tvPayloadContent = findViewById(R.id.tvPayloadContent);
        
        // 1. Memeriksa & Meminta Izin Notification (Android 13+)
        checkNotificationPermission();
        
        // 2. Memeriksa Intent Extras (Penanganan Payload dari Background/Killed State)
        handleNotificationIntentData();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void handleNotificationIntentData() {
        if (getIntent() != null && getIntent().getExtras() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DATA PAYLOAD DITERIMA ===\n\n");
            
            // Iterasi seluruh key-value di dalam Intent Extras
            for (String key : getIntent().getExtras().keySet()) {
                Object value = getIntent().getExtras().get(key);
                Log.d(TAG, "Intent Extra Key: " + key + " | Value: " + value);
                
                // Mengabaikan kunci internal milik Firebase
                if (!key.startsWith("google.") && !key.startsWith("gcm.")) {
                    sb.append(key).append(": ").append(value).append("\n");
                }
            }
            tvPayloadContent.setText(sb.toString());
        }
    }
}
```

## 6.4 Targeting & Topic Subscription

FCM mendukung tiga strategi pengiriman pesan (message targeting):

1. **Single Device Targeting:** Pengiriman pesan menggunakan Device Registration Token spesifik. Cocok untuk notifikasi personal seperti info transfer bank, status pesanan, atau verifikasi OTP.
2. **Topic Messaging (Publish-Subscribe Pattern):** Pengiriman pesan ke grup pengguna banyak (multicast) yang mendaftar pada topik (topic) tertentu tanpa memerlukan pengelolaan daftar token di server backend.
3. **Segment Targeting:** Pengiriman berdasarkan kombinasi kriteria seperti versi OS, bahasa, wilayah geografis, atau demografi analitis.

### Pola Kerja Topic Messaging (Pub/Sub)

```text
       +-----------------+
       | FCM Backend Hub |
       +-----------------+
             |      \
 Subscribe   |       \ Subscribe "pengumuman"
"pengumuman" |        \
             v         v
      +-------------+ +-------------+ +-------------+
      | Perangkat A | | Perangkat B | | Perangkat C |
      +-------------+ +-------------+ +-------------+
```

**Keunggulan Topic Messaging:**
* **Efisiensi Server:** Server backend tidak perlu menyimpan jutaan token atau melakukan looping HTTP Request. Server hanya mengirim satu request pesan ke nama topik (misal: `/topics/pengumuman_kampus`).
* **Skalabilitas:** Menangani jutaan perangkat secara paralel dan instan.

### Contoh Implementasi Interface dan Kode Java

**File: `res/layout/activity_topic.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="FCM Token &amp; Topic Management"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />
        
    <!-- Tampilan Token Perangkat -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Device Registration Token:"
        android:textStyle="bold" />
        
    <EditText
        android:id="@+id/etDeviceToken"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:focusable="false"
        android:hint="Memuat Token FCM..."
        android:textSize="12sp"
        android:padding="8dp"
        android:background="#F0F0F0"
        android:layout_marginVertical="8dp" />
        
    <Button
        android:id="@+id/btnGetToken"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Ambil FCM Token" />
        
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#CCCCCC"
        android:layout_marginVertical="16dp" />
        
    <!-- Pengelolaan Topik -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Pendaftaran Topik (Topic Subscription):"
        android:textStyle="bold" />
        
    <EditText
        android:id="@+id/etTopicName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Nama Topik (misal: pengumuman_kampus)"
        android:text="pengumuman_kampus"
        android:layout_marginVertical="8dp" />
        
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">
        
        <Button
            android:id="@+id/btnSubscribe"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="4dp"
            android:text="Subscribe" />
            
        <Button
            android:id="@+id/btnUnsubscribe"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="4dp"
            android:text="Unsubscribe"
            android:backgroundTint="@android:color/holo_red_dark" />
    </LinearLayout>
</LinearLayout>
```

**File: `TopicActivity.java`**

```java
package com.praktikum.fcmlecture;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.messaging.FirebaseMessaging;

public class TopicActivity extends AppCompatActivity {

    private static final String TAG = "TOPIC_ACTIVITY";
    private EditText etDeviceToken, etTopicName;
    private Button btnGetToken, btnSubscribe, btnUnsubscribe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic);
        
        etDeviceToken = findViewById(R.id.etDeviceToken);
        etTopicName = findViewById(R.id.etTopicName);
        btnGetToken = findViewById(R.id.btnGetToken);
        btnSubscribe = findViewById(R.id.btnSubscribe);
        btnUnsubscribe = findViewById(R.id.btnUnsubscribe);
        
        btnGetToken.setOnClickListener(v -> fetchDeviceToken());
        btnSubscribe.setOnClickListener(v -> subscribeToTopic());
        btnUnsubscribe.setOnClickListener(v -> unsubscribeFromTopic());
    }

    /**
     * Mengambil Registration Token FCM dari Perangkat
     */
    private void fetchDeviceToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Gagal mengambil token FCM", task.getException());
                    Toast.makeText(this, "Gagal mengambil token", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Mendapatkan Token FCM Baru
                String token = task.getResult();
                Log.d(TAG, "Current FCM Token: " + token);
                etDeviceToken.setText(token);
                Toast.makeText(this, "Token Berhasil Diambil!", Toast.LENGTH_SHORT).show();
            });
    }

    /**
     * Mendaftarkan Perangkat ke Topik Tertentu
     */
    private void subscribeToTopic() {
        String topic = etTopicName.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(this, "Nama topik tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String msg = "Berhasil Subscribe ke topik: " + topic;
                    Log.d(TAG, msg);
                    Toast.makeText(TopicActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    String msg = "Gagal Subscribe ke topik!";
                    Log.e(TAG, msg, task.getException());
                    Toast.makeText(TopicActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * Membatalkan Pendaftaran Perangkat dari Topik Tertentu
     */
    private void unsubscribeFromTopic() {
        String topic = etTopicName.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(this, "Nama topik tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String msg = "Berhasil Unsubscribe dari topik: " + topic;
                    Log.d(TAG, msg);
                    Toast.makeText(TopicActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    String msg = "Gagal Unsubscribe dari topik!";
                    Log.e(TAG, msg, task.getException());
                    Toast.makeText(TopicActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
    }
}
```

---

## RANGKUMAN MATERI
1. **FCM (Firebase Cloud Messaging)** bertindak sebagai perantara aman antara App Server dan perangkat Android untuk mengirim notifikasi dan data secara real-time.
2. **Device Registration Token** adalah identitas unik instalasi aplikasi yang dapat berubah (dynamic) seiring siklus hidup aplikasi di perangkat.
3. **FirebaseMessagingService** adalah kelas inti untuk memproses pesan masuk (`onMessageReceived`) dan memperbarui token (`onNewToken`).
4. **NotificationChannel** wajib dibuat mulai Android 8.0 (API 26) agar notifikasi dapat ditampilkan di System Tray.
5. Penanganan pesan bervariasi berdasarkan status aplikasi (Foreground, Background, atau Killed) serta jenis payload (Notification, Data, atau Combined).
6. **Topic Messaging** mempermudah pengiriman notifikasi broadcast/grup tanpa perlu mengelola atau mengiterasi daftar token di server backend.

## SOAL EVALUASI & DISKUSI KELAS
1. Jelaskan perbedaan perilaku penanganan pesan Notification Payload dan Data Payload saat aplikasi berada dalam kondisi Background!
2. Mengapa metode `onMessageReceived()` pada `FirebaseMessagingService` tidak dipanggil saat pesan bertipe Notification Payload masuk ketika aplikasi dalam keadaan Background?
3. Sebutkan 3 kondisi yang menyebabkan FCM Device Registration Token mengalami pembaruan (refresh)!
4. Skenario: Sebuah aplikasi e-commerce ingin mengirim notifikasi promosi kilat (flash sale) kepada 100.000 pengguna aktifnya secara bersamaan. Antara metode Targeted Token dan Topic Subscription, metode manakah yang paling efektif digunakan? Jelaskan alasannya!