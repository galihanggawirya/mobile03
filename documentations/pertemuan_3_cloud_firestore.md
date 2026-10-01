# BAB 4: CLOUD FIRESTORE

## Konfigurasi Awal (Gradle)

Sebelum memulai, pastikan pustaka Firebase Firestore sudah ditambahkan ke file `build.gradle` (Module: app):

```groovy
dependencies {
    // Import Firebase BoM (Bill of Materials)
    implementation platform('com.google.firebase:firebase-bom:33.1.0')
    
    // Pustaka Cloud Firestore
    implementation 'com.google.firebase:firebase-firestore'
}
```

## 4.1 Realtime Database vs. Cloud Firestore

Meskipun keduanya adalah solusi database NoSQL dari Firebase yang mendukung sinkronisasi secara realtime, arsitektur dan kapabilitas dasarnya sangat berbeda.

### Perbandingan Utama

| Fitur | Firebase Realtime Database | Cloud Firestore |
| --- | --- | --- |
| **Struktur Data** | Pohon JSON raksasa (JSON Tree). Data bersarang (nested) secara dalam. | Dokumenter (Collections, Documents, dan Sub-collections). |
| **Kueri & Filter** | Terbatas. Hanya dapat memfilter atau mengurutkan berdasarkan satu properti dalam satu kueri. | Sangat fleksibel. Mendukung compound query (filter/sortir banyak field sekaligus) dan indexing otomatis. |
| **Skalabilitas** | Terbatas hingga ~100.000 koneksi simultan per database (perlu sharding manual jika lebih). | Skalabilitas otomatis. Mendukung jutaan koneksi simultan secara bawaan (out-of-the-box). |
| **Performa Kueri** | Bergantung pada ukuran data yang diunduh (mengambil node akan mengambil seluruh sub-tree). | Bergantung pada ukuran result set (hanya mengambil dokumen yang cocok, bukan sub-koleksi di bawahnya). |
| **Model Biaya** | Berdasarkan Bandwidth (ukuran data yang ditransfer) & kapasitas penyimpanan. | Berdasarkan jumlah operasi (Reads, Writes, Deletes) & penyimpanan. |
| **Dukungan Offline** | Tersedia untuk Android & iOS. | Tersedia untuk Android, iOS, dan Web dengan kemampuan manajemen cache yang lebih komprehensif. |

### Kapan Menggunakan yang Mana di Android?

*   **Pilih Realtime Database:** Jika aplikasi Android Anda memerlukan latency yang sangat rendah untuk pembaruan data berfrekuensi tinggi (misalnya aplikasi kolaborasi kursor, game multiplayer sederhana, atau indikator status online/offline pengguna).
*   **Pilih Cloud Firestore:** Pilihan standar utama untuk aplikasi Android modern yang membutuhkan data terstruktur, pencarian kueri kompleks (misal: filter katalog e-commerce), dataset berukuran besar, dan skalabilitas tinggi.

## 4.2 Model Data: Collections, Documents, dan Sub-collections

Cloud Firestore menyimpan data dalam bentuk berorientasi dokumen. Struktur utamanya terdiri dari:

```text
[Collection: users]
  └── [Document: user_01]
       ├── Field: name = "Budi Santoso"
       ├── Field: email = "budi@gmail.com"
       └── [Sub-collection: orders]
            └── [Document: order_A1]
                 ├── Field: total = 250000
                 └── Field: status = "LUNAS"
```

1.  **Collection (Koleksi):** Wadah yang berisi kumpulan Documents. Collection tidak dapat menampung field atau teks mentah secara langsung.
2.  **Document (Dokumen):** Unit data tunggal berupa pasangan Key-Value (Field). Ukuran maksimum satu dokumen adalah 1 MB.
3.  **Sub-collection (Sub-koleksi):** Koleksi yang berada di dalam suatu dokumen. Mengambil data dari dokumen induk tidak akan secara otomatis mengunduh data di dalam sub-koleksinya (sehingga menghemat bandwidth).

### Tipe Data Field yang Didukung

Firestore pada Android Java mendukung berbagai tipe data:
*   `String`, `Boolean`, `Long` / `Double` (Number)
*   `Timestamp` (`com.google.firebase.Timestamp` / `java.util.Date`)
*   `Map<String, Object>` (Nested Object)
*   `List<Object>` (Array)
*   `GeoPoint` (`com.google.firebase.firestore.GeoPoint`)
*   `DocumentReference` (Referensi ke dokumen lain)
*   `null`

## 4.3 Manipulasi Data & Kueri (CRUD, Filtering, Sorting)

Berikut adalah contoh implementasi antarmuka XML dan kode Java untuk melakukan operasi CRUD serta kueri Firestore.

### 1. Tampilan XML (`activity_main.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <EditText
            android:id="@+id/etProductName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Nama Produk" />

        <EditText
            android:id="@+id/etProductPrice"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Harga Produk"
            android:inputType="numberDecimal" />

        <EditText
            android:id="@+id/etProductCategory"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Kategori (misal: elektronik)" />

        <Button
            android:id="@+id/btnSave"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Tambah Produk (Auto ID)" />

        <Button
            android:id="@+id/btnSetWithId"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Simpan dengan Custom ID (set)" />

        <Button
            android:id="@+id/btnFetch"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Ambil Data Filter &amp; Sort" />

        <TextView
            android:id="@+id/tvResult"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textSize="14sp"
            android:textColor="@android:color/black" />
    </LinearLayout>
</ScrollView>
```

### 2. Model Class POJO (`Product.java`)

Firestore dapat secara otomatis memetakan dokumen menjadi objek Java jika kelas POJO (Plain Old Java Object) memiliki constructor kosong.

```java
package com.example.firestoreapp;

public class Product {
    private String id;
    private String name;
    private double price;
    private String category;

    // Constructor kosong wajib untuk deserialisasi Firestore
    public Product() {}

    public Product(String name, double price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // Getter dan Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
```

### 3. Logika Java (`MainActivity.java`)

```java
package com.example.firestoreapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FirestoreDemo";
    private EditText etProductName, etProductPrice, etProductCategory;
    private Button btnSave, btnSetWithId, btnFetch;
    private TextView tvResult;

    // Inisialisasi Instance Firestore
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Inisialisasi komponen UI & Firestore
        db = FirebaseFirestore.getInstance();
        etProductName = findViewById(R.id.etProductName);
        etProductPrice = findViewById(R.id.etProductPrice);
        etProductCategory = findViewById(R.id.etProductCategory);
        btnSave = findViewById(R.id.btnSave);
        btnSetWithId = findViewById(R.id.btnSetWithId);
        btnFetch = findViewById(R.id.btnFetch);
        tvResult = findViewById(R.id.tvResult);

        // Listener Tombol
        btnSave.setOnClickListener(v -> addDataAutoId());
        btnSetWithId.setOnClickListener(v -> setDataCustomId());
        btnFetch.setOnClickListener(v -> queryAndFilterData());
    }

    // --- A. CREATE (add): Menambah dokumen dengan ID Otomatis
    private void addDataAutoId() {
        String name = etProductName.getText().toString();
        double price = Double.parseDouble(etProductPrice.getText().toString());
        String category = etProductCategory.getText().toString();
        
        Product product = new Product(name, price, category);
        
        db.collection("products")
            .add(product)
            .addOnSuccessListener(documentReference -> {
                Toast.makeText(MainActivity.this, "Berhasil! ID: " + documentReference.getId(), Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Gagal menambah dokumen", e);
            });
    }

    // --- B. CREATE / OVERWRITE (set): Menggunakan Custom ID
    private void setDataCustomId() {
        String name = etProductName.getText().toString();
        double price = Double.parseDouble(etProductPrice.getText().toString());
        String category = etProductCategory.getText().toString();
        
        Map<String, Object> productMap = new HashMap<>();
        productMap.put("name", name);
        productMap.put("price", price);
        productMap.put("category", category);
        
        // Menentukan ID Dokumen sendiri: "PROD-001"
        db.collection("products").document("PROD-001")
            .set(productMap)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(MainActivity.this, "Data PROD-001 disimpan", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> Log.e(TAG, "Gagal menyimpan", e));
    }

    // --- C. UPDATE: Memperbarui field tertentu tanpa menimpa seluruh dokumen
    private void updateSingleField(String docId, double newPrice) {
        db.collection("products").document(docId)
            .update("price", newPrice)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Harga berhasil diperbarui"))
            .addOnFailureListener(e -> Log.e(TAG, "Gagal update", e));
    }

    // --- D. DELETE: Menghapus dokumen atau field tertentu
    private void deleteDocument(String docId) {
        // Menghapus seluruh dokumen
        db.collection("products").document(docId)
            .delete()
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Dokumen terhapus"));
            
        // Contoh Menghapus Field 'category' saja dari dokumen:
        /*
        db.collection("products").document(docId)
            .update("category", FieldValue.delete());
        */
    }

    // --- E. READ, FILTERING & SORTING DATA
    private void queryAndFilterData() {
        // Kueri: Ambil kategori 'elektronik', urutkan harga dari termurah, maksimal 5 data
        db.collection("products")
            .whereEqualTo("category", "elektronik")
            .orderBy("price", Query.Direction.ASCENDING)
            .limit(5)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    StringBuilder builder = new StringBuilder();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Product product = document.toObject(Product.class);
                        product.setId(document.getId());
                        
                        builder.append("ID: ").append(product.getId())
                            .append("\nNama: ").append(product.getName())
                            .append("\nHarga: Rp").append(product.getPrice())
                            .append("\n------------------\n");
                    }
                    tvResult.setText(builder.toString());
                } else {
                    Log.w(TAG, "Error mendapatkan dokumen.", task.getException());
                }
            });
    }
}
```

## 4.4 Realtime Updates & Offline Persistence

### 1. Realtime Updates (EventListener)

Untuk mendengarkan perubahan data secara langsung tanpa perlu melakukan panggilan `get()` ulang, gunakan `addSnapshotListener`.

```java
private com.google.firebase.firestore.ListenerRegistration snapshotListener;

private void startRealtimeListener() {
    // Memantau seluruh perubahan di koleksi "products"
    snapshotListener = db.collection("products")
        .whereGreaterThan("price", 50000)
        .addSnapshotListener(this, (value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listener gagal:", error);
                return;
            }
            if (value != null) {
                StringBuilder builder = new StringBuilder("--- Realtime Data ---\n");
                for (DocumentSnapshot doc : value.getDocuments()) {
                    builder.append(doc.getString("name"))
                        .append(" - Rp")
                        .append(doc.getDouble("price"))
                        .append("\n");
                }
                tvResult.setText(builder.toString());
            }
        });
}

@Override
protected void onDestroy() {
    super.onDestroy();
    // Menghentikan listener saat Activity dihancurkan agar tidak memicu memory leak
    if (snapshotListener != null) {
        snapshotListener.remove();
    }
}
```

*Catatan: Mempassing `this` (Activity context) sebagai argumen pertama pada `addSnapshotListener(this, ...)` akan secara otomatis melepas listener saat Activity memasuki state STOPPED.*

### 2. Offline Persistence (Penyimpanan Luring)

Pada SDK Android Firebase, fitur Offline Persistence sudah diaktifkan secara default.

**Cara Kerja:**
1.  **Cache Lokal:** Setiap data yang dibaca dari Firestore secara otomatis disimpan dalam database SQLite lokal perangkat.
2.  **Operasi Write saat Offline:** Saat perangkat tidak terkoneksi internet, operasi `add()`, `set()`, `update()`, atau `delete()` akan langsung ditulis ke cache lokal dan mengembalikan status success secara langsung di UI.
3.  **Auto-Synchronization:** Ketika koneksi internet kembali aktif, Firestore akan mengirimkan seluruh perubahan lokal yang tertunda (queued writes) ke server Firestore di cloud.

#### Konfigurasi Ukuran Cache (Opsional)

Anda dapat menyesuaikan ukuran cache lokal atau mengubah pengaturannya pada saat inisialisasi aplikasi:

```java
import com.google.firebase.firestore.FirebaseFirestoreSettings;

private void setupFirestoreSettings() {
    FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true) // Default true di Android
        .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Mengatur cache tanpa batas
        .build();
        
    db.setFirestoreSettings(settings);
}
```

#### Memeriksa Sumber Data (Cache vs Server)

Anda dapat mengetahui apakah data yang ditampilkan berasal dari cache lokal atau dari server cloud menggunakan Metadata:

```java
db.collection("products").document("PROD-001")
    .get()
    .addOnSuccessListener(documentSnapshot -> {
        if (documentSnapshot.exists()) {
            // Memeriksa sumber data
            boolean isFromCache = documentSnapshot.getMetadata().isFromCache();
            if (isFromCache) {
                Log.d(TAG, "Data diambil dari CACHE LOKAL (Offline)");
            } else {
                Log.d(TAG, "Data diambil dari SERVER CLOUD (Online)");
            }
        }
    });
```