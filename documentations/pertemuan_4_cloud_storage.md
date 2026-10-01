# BAB 5: CLOUD STORAGE (PENYIMPANAN BERKAS)

Firebase Cloud Storage adalah layanan penyimpanan objek berbasis cloud yang dirancang untuk menyimpan data biner berukuran besar, seperti gambar, video, berkas audio, dan dokumen (PDF/Word). Firebase Storage dibangun di atas Google Cloud Storage yang menawarkan skalabilitas tinggi, keamanan berbasis aturan (Security Rules), dan kemampuan menoleransi koneksi internet yang terputus-putus (resumable upload).

## Konfigurasi Awal (Gradle & Manifest)

### 1. File `build.gradle` (Module: app)

Tambahkan dependensi Firebase Storage dan pustaka pemroses gambar Glide:

```
dependencies {
    // Import Firebase BoM
    implementation platform('com.google.firebase:firebase-bom:33.1.0')
    
    // Firebase Cloud Storage & Authentication (Opsional untuk user ID)
    implementation 'com.google.firebase:firebase-storage'
    implementation 'com.google.firebase:firebase-auth'
    
    // Pustaka Glide untuk memuat dan menampilkan gambar
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'
}

```

### 2. File `AndroidManifest.xml`

Tambahkan izin akses internet di dalam berkas manifest:

```
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Izin Akses Internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <application>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>

```

## 5.1 Manajemen Berkas Cloud

Firebase Storage mengorganisasi berkas menggunakan struktur folder hierarkis yang mirip dengan sistem berkas lokal pada komputer.

### Inisialisasi dan Pembuatan Referensi (Storage Reference)

Seluruh interaksi dengan Firebase Storage dimulai dengan membuat `StorageReference`.

```
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

// 1. Inisialisasi Instance
FirebaseStorage storage = FirebaseStorage.getInstance();

// 2. Referensi Root Storage (gs://nama-app.appspot.com)
StorageReference storageRef = storage.getReference();

// 3. Alokasi Folder berdasarkan Jenis Berkas
StorageReference imageFolderRef = storageRef.child("images");
StorageReference documentFolderRef = storageRef.child("documents/pdf");
StorageReference audioFolderRef = storageRef.child("media/audio");

// 4. Referensi Spesifik ke Sebuah File
StorageReference userAvatarRef = storageRef.child("images/avatars/user_101.jpg");

```

**Praktik Terbaik Struktur Folder:**

* **Pengelompokan Berorientasi Pengguna:** Simpan berkas spesifik pengguna berdasarkan UID autentikasi: `storageRef.child("users/" + userId + "/profile.jpg")`

* **Pemberian Nama Unik:** Gunakan `UUID.randomUUID().toString()` atau timestamp `System.currentTimeMillis()` untuk mencegah penimpaan berkas (file overwriting).

## 5.2 Upload & Progress Bar

Proses unggah dilakukan dengan mengirimkan Uri berkas lokal menggunakan metode `putFile()`. Untuk memantau progres, kita mendaftarkan `OnProgressListener` pada objek `UploadTask`.

**Alur Kerja Upload Berkas:**

1. Pengguna memilih berkas dari Galeri/Penyimpanan Lokal via `ActivityResultLauncher`.

2. Aplikasi mendapatkan `Uri` dari berkas tersebut.

3. Memanggil `storageRef.putFile(fileUri)` untuk memulai proses unggah.

4. Mendengarkan persentase unggah dan memperbarui `ProgressBar`.

**Rumus Menghitung Persentase Upload:**

$$
\text{Progress (\%)} = \left(\frac{\text{bytesTransferred}}{\text{totalByteCount}} \right) \times 100
$$

## 5.3 Download URL & Image Loader

Setelah berkas berhasil diunggah, berkas tersebut memiliki Download URL berformat HTTPS ber-token khusus yang dapat diakses secara publik.

### Mengambil Download URL:

```
uploadTask.continueWithTask(task -> {
    if (!task.isSuccessful()) {
        throw task.getException();
    }
    // Mengembalikan Task berisi Uri download
    return fileRef.getDownloadUrl();
}).addOnCompleteListener(task -> {
    if (task.isSuccessful()) {
        Uri downloadUri = task.getResult();
        String downloadUrl = downloadUri.toString();
        Log.d("Storage", "URL Download: " + downloadUrl);
    }
});

```

### Menampilkan Gambar dengan Glide

Gunakan pustaka Glide untuk mengunduh, meng-cache, dan merender gambar secara otomatis ke dalam ImageView:

```
import com.bumptech.glide.Glide;

Glide.with(context)
    .load(downloadUrl)
    .placeholder(R.drawable.ic_placeholder) // Gambar sementara saat loading
    .error(R.drawable.ic_error)             // Gambar jika gagal dimuat
    .into(imageViewPreview);

```

## 5.4 Delete & Metadata Management

### 1. Menghapus Berkas (delete)

Untuk menghapus berkas, panggil metode `delete()` dari referensi berkas yang dituju:

```
StorageReference fileRef = storageRef.child("images/sample.jpg");

fileRef.delete()
    .addOnSuccessListener(aVoid -> {
        Toast.makeText(context, "Berkas berhasil dihapus!", Toast.LENGTH_SHORT).show();
    })
    .addOnFailureListener(exception -> {
        Log.e("Storage", "Gagal menghapus berkas: ", exception);
    });

```

### 2. Mengelola Metadata (Storage Metadata)

Metadata berisi informasi seputar berkas, seperti tipe MIME (`contentType`), ukuran berkas (`sizeBytes`), tanggal dibuat, serta custom metadata (key-value pair).

**Membaca & Memperbarui Metadata:**

```
import com.google.firebase.storage.StorageMetadata;

// A. Membaca Metadata
fileRef.getMetadata().addOnSuccessListener(metadata -> {
    String contentType = metadata.getContentType();
    long sizeInBytes = metadata.getSizeBytes();
    String uploader = metadata.getCustomMetadata("uploaded_by");
    Log.d("Metadata", "Type: " + contentType + " | Size: " + sizeInBytes + "B");
});

// B. Memperbarui Custom Metadata
StorageMetadata newMetadata = new StorageMetadata.Builder()
    .setContentType("image/jpeg")
    .setCustomMetadata("uploaded_by", "Budi Santoso")
    .setCustomMetadata("description", "Foto Profil Utama")
    .build();

fileRef.updateMetadata(newMetadata)
    .addOnSuccessListener(updatedMetadata -> {
        Log.d("Metadata", "Metadata berhasil diperbarui!");
    });

```

## Implementasi Lengkap (XML + Java)

Berikut adalah contoh aplikasi terpadu yang mencakup pilihan galeri, upload dengan progress bar, menampilkan gambar via Glide, membaca metadata, dan menghapus berkas.

### 1. Tampilan XML (`activity_main.xml`)

```
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Demo Firebase Cloud Storage"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <!-- Preview Gambar -->
        <ImageView
            android:id="@+id/ivPreview"
            android:layout_width="250dp"
            android:layout_height="250dp"
            android:scaleType="centerCrop"
            android:background="#E0E0E0"
            android:contentDescription="Preview Gambar"
            android:layout_marginBottom="16dp" />

        <!-- Indicator Progress Upload -->
        <ProgressBar
            android:id="@+id/progressBar"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:visibility="gone"
            android:max="100" />

        <TextView
            android:id="@+id/tvProgress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Progress: 0%"
            android:visibility="gone"
            android:layout_marginBottom="16dp" />

        <!-- Tombol Aksi -->
        <Button
            android:id="@+id/btnSelectFile"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Pilih Gambar dari Galeri" />

        <Button
            android:id="@+id/btnUpload"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Unggah ke Storage"
            android:enabled="false" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="8dp">

            <Button
                android:id="@+id/btnGetMetadata"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Cek Metadata"
                android:enabled="false" />

            <Button
                android:id="@+id/btnDelete"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Hapus File"
                android:backgroundTint="@android:color/holo_red_light"
                android:enabled="false" />
        </LinearLayout>

        <!-- Keterangan Metadata / Output -->
        <TextView
            android:id="@+id/tvInfo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="#F5F5F5"
            android:padding="12dp"
            android:text="Informasi berkas akan muncul di sini."
            android:textSize="13sp" />
    </LinearLayout>
</ScrollView>

```

### 2. Logika Java (`MainActivity.java`)

```
package com.example.storageapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CloudStorageDemo";
    
    // Component UI
    private ImageView ivPreview;
    private ProgressBar progressBar;
    private TextView tvProgress, tvInfo;
    private Button btnSelectFile, btnUpload, btnGetMetadata, btnDelete;
    
    // Firebase Storage Reference
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private StorageReference uploadedFileRef; // Referensi file yang berhasil diunggah
    
    private Uri selectedImageUri;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 1. Inisialisasi Firebase Storage
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        
        // 2. Inisialisasi UI
        ivPreview = findViewById(R.id.ivPreview);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);
        tvInfo = findViewById(R.id.tvInfo);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnUpload = findViewById(R.id.btnUpload);
        btnGetMetadata = findViewById(R.id.btnGetMetadata);
        btnDelete = findViewById(R.id.btnDelete);
        
        // 3. Register ActivityResultLauncher untuk Pilih Gambar dari Galeri
        setupGalleryLauncher();
        
        // Event Listener Tombol
        btnSelectFile.setOnClickListener(v -> openGallery());
        btnUpload.setOnClickListener(v -> uploadFileToStorage());
        btnGetMetadata.setOnClickListener(v -> fetchFileMetadata());
        btnDelete.setOnClickListener(v -> deleteFileFromStorage());
    }

    private void setupGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        // Tampilkan preview lokal
                        ivPreview.setImageURI(selectedImageUri);
                        btnUpload.setEnabled(true);
                        tvInfo.setText("Berkas dipilih: " + selectedImageUri.getLastPathSegment());
                    }
                }
            }
        );
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    // --- 5.2 UPLOAD & PROGRESS BAR
    private void uploadFileToStorage() {
        if (selectedImageUri == null) return;
        
        // Tampilkan Progress Bar
        progressBar.setVisibility(View.VISIBLE);
        tvProgress.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);
        
        // Buat nama berkas unik dalam folder "images/"
        String fileName = "images/" + UUID.randomUUID().toString() + ".jpg";
        uploadedFileRef = storageRef.child(fileName);
        
        // Buat Metadata Tambahan
        StorageMetadata metadata = new StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploaded_by", "Android User")
            .build();
            
        // Mulai Upload File
        UploadTask uploadTask = uploadedFileRef.putFile(selectedImageUri, metadata);
        
        // Pantau Progres Upload
        uploadTask.addOnProgressListener(snapshot -> {
            double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
            int currentProgress = (int) progress;
            progressBar.setProgress(currentProgress);
            tvProgress.setText("Progress: " + currentProgress + "%");
        }).addOnSuccessListener(taskSnapshot -> {
            // Upload Sukses
            progressBar.setVisibility(View.GONE);
            tvProgress.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, "Unggah Berhasil!", Toast.LENGTH_SHORT).show();
            
            // Skenario 5.3: Ambil Download URL
            fetchDownloadUrl();
            
            // Aktifkan tombol metadata dan delete
            btnGetMetadata.setEnabled(true);
            btnDelete.setEnabled(true);
        }).addOnFailureListener(e -> {
            // Upload Gagal
            progressBar.setVisibility(View.GONE);
            tvProgress.setVisibility(View.GONE);
            btnUpload.setEnabled(true);
            Log.e(TAG, "Gagal mengunggah berkas", e);
            Toast.makeText(MainActivity.this, "Gagal Unggah: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // --- 5.3 DOWNLOAD URL & GLIDE IMAGE LOADER
    private void fetchDownloadUrl() {
        if (uploadedFileRef == null) return;
        
        uploadedFileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            String downloadUrl = uri.toString();
            tvInfo.setText("Download URL:\n" + downloadUrl);
            
            // Tampilkan gambar hasil upload dari Cloud Storage menggunakan Glide
            Glide.with(MainActivity.this)
                .load(downloadUrl)
                .centerCrop()
                .into(ivPreview);
        }).addOnFailureListener(e -> Log.e(TAG, "Gagal mendapatkan URL download", e));
    }

    // --- 5.4 METADATA MANAGEMENT ---
    private void fetchFileMetadata() {
        if (uploadedFileRef == null) return;
        
        uploadedFileRef.getMetadata().addOnSuccessListener(metadata -> {
            String info = "=== METADATA BERKAS ===" +
                "\nNama File: " + metadata.getName() +
                "\nUkuran: " + (metadata.getSizeBytes() / 1024) + " KB" +
                "\nType: " + metadata.getContentType() +
                "\nDibuat: " + metadata.getCreationTimeMillis() +
                "\nUploader: " + metadata.getCustomMetadata("uploaded_by");
            tvInfo.setText(info);
        }).addOnFailureListener(e -> Toast.makeText(this, "Gagal membaca metadata", Toast.LENGTH_SHORT).show());
    }

    // --- 5.4 DELETE FILE ---
    private void deleteFileFromStorage() {
        if (uploadedFileRef == null) return;
        
        uploadedFileRef.delete().addOnSuccessListener(aVoid -> {
            Toast.makeText(MainActivity.this, "Berkas di Cloud Storage terhapus!", Toast.LENGTH_SHORT).show();
            // Reset UI State
            ivPreview.setImageResource(0);
            tvInfo.setText("Berkas telah dihapus dari cloud.");
            btnGetMetadata.setEnabled(false);
            btnDelete.setEnabled(false);
            uploadedFileRef = null;
            selectedImageUri = null;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Gagal menghapus berkas", e);
            Toast.makeText(MainActivity.this, "Gagal menghapus berkas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}

```

## Ringkasan Fungsi Utama

| Metode / Pustaka | Fungsi | 
 | ----- | ----- | 
| `storage.getReference()` | Mengambil referensi root dari Firebase Storage. | 
| `child("path/file.ext")` | Mengalokasikan direktori folder atau menentukan jalur lokasi berkas. | 
| `putFile(uri)` | Mengunggah berkas dari penyimpanan lokal berbasis Uri ke cloud. | 
| `addOnProgressListener()` | Mendengarkan dan menghitung persentase progres unggah secara realtime. | 
| `getDownloadUrl()` | Mengambil URL HTTPS publik ber-token untuk mengakses/mengunduh berkas. | 
| `Glide.with(...).load()` | Mengunduh dan menampilkan gambar dari Download URL ke komponen ImageView. | 
| `getMetadata()` / `updateMetadata()` | Membaca dan memperbarui rincian metadata berkas (tipe berkas, ukuran, uploader). | 
| `delete()` | Menghapus berkas yang tersimpan di Firebase Storage. | 
