package com.example.latihanfirestore03;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LatihanFirestore03";
    private static final String COLLECTION_ITEMS = "items";

    EditText etKode, etNama, etSatuan, etHarga;
    Button btnSave;
    RecyclerView recyclerView;
    List<Item> itemList = new ArrayList<>();
    ItemAdapter adapter;

    // Instance Cloud Firestore
    FirebaseFirestore db;
    ListenerRegistration snapshotListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Inisialisasi Cloud Firestore
        db = FirebaseFirestore.getInstance();

        // Hubungkan ke layout
        etKode = findViewById(R.id.etKode);
        etNama = findViewById(R.id.etNama);
        etSatuan = findViewById(R.id.etSatuan);
        etHarga = findViewById(R.id.etHarga);
        btnSave = findViewById(R.id.btnSave);
        recyclerView = findViewById(R.id.recyclerView);

        adapter = new ItemAdapter (itemList, db);
        recyclerView.setLayoutManager (new LinearLayoutManager(this));
        recyclerView.setAdapter (adapter);

        // --- CREATE / UPDATE: Simpan dokumen dengan ID = kode ---
        btnSave.setOnClickListener(v -> {
            String kode = etKode.getText().toString();
            String nama = etNama.getText().toString();
            String satuan = etSatuan.getText().toString();
            int harga = Integer.parseInt(etHarga.getText().toString());

            Item item = new Item (kode, nama, satuan, harga);
            // Simpan ke Firestore dengan document ID = kode
            db.collection(COLLECTION_ITEMS).document(kode).set(item)
                    .addOnSuccessListener (aVoid ->
                            Toast.makeText(MainActivity.this, "Item berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Gagal menyimpan item", e);
                        Toast.makeText(MainActivity.this, "Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
        });

        // --- READ: Dengarkan perubahan data secara realtime ---
        snapshotListener = db.collection(COLLECTION_ITEMS)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listener gagal", error);
                        return;
                    }
                    if (value == null) return;

                    itemList.clear();
                    for (DocumentSnapshot document : value.getDocuments()) {
                        Item item = document.toObject(Item.class);
                        if (item != null) {
                            itemList.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Lepas listener agar tidak memory leak
        if (snapshotListener != null) {
            snapshotListener.remove();
        }
    }
}
