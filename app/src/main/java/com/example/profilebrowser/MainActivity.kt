package com.example.profilebrowser

import android.Manifest
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var profiles by remember { mutableStateOf(ProfileStore.loadAll(this)) }
            var showAdd by remember { mutableStateOf(false) }
            var newName by remember { mutableStateOf("") }
            var newType by remember { mutableStateOf("facebook") }
            var newUrl by remember { mutableStateOf("") }
            var deleteTarget by remember { mutableStateOf<Profile?>(null) }

            fun refresh() {
                profiles = ProfileStore.loadAll(this)
            }

            fun openProfile(p: Profile) {
                val intent = Intent(this, BrowserActivity::class.java).apply {
                    putExtra("profile_id", p.id)
                }
                startActivity(intent)
            }

            /** ফোনের Google (Gmail) অ্যাকাউন্টগুলো পড়ে প্রতিটার জন্য Gmail প্রোফাইল বানায়।
                ফেরত: নতুন কতগুলো প্রোফাইল বানানো হলো; অ্যাকাউন্ট না পেলে -১। */
            fun buildGmailProfiles(): Int {
                val before = profiles.size
                val emails = try {
                    AccountManager.get(this).getAccountsByType("com.google").map { it.name }
                } catch (_: Exception) {
                    emptyList()
                }
                if (emails.isEmpty()) return -1
                profiles = ProfileStore.addGmailProfiles(this, emails)
                return profiles.size - before
            }

            val gmailPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    val added = buildGmailProfiles()
                    val msg = when {
                        added < 0 -> "ফোনে কোনো Gmail অ্যাকাউন্ট পাওয়া যায়নি"
                        added == 0 -> "সব Gmail-এর প্রোফাইল আগেই আছে"
                        else -> "${added}টি Gmail প্রোফাইল বানানো হয়েছে"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "অনুমতি না দিলে Gmail অ্যাকাউন্ট পড়া যাবে না",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "লোগো",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "প্রোফাইল ব্রাউজার",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "প্রতিটি প্রোফাইলের লগইন আলাদা থাকে — ল্যাপটপের Chrome প্রোফাইলের মতো।",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        gmailPermissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📧 Gmail অ্যাকাউন্ট থেকে সব প্রোফাইল বানান")
                }
                Text(
                    "ফোনে যতগুলো Gmail আছে, ততগুলো প্রোফাইল একবারে বানাবে। প্রথমবার একবার অনুমতি চাইবে।",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        newName = ""
                        newType = "facebook"
                        newUrl = ""
                        showAdd = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("＋ নতুন প্রোফাইল যোগ করুন")
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (profiles.isEmpty()) {
                    Text("এখনো কোনো প্রোফাইল নেই। উপরের Gmail বোতাম চাপলে ফোনের সব Gmail-এর প্রোফাইল একবারে বানাবে, আর চাইলে হাতে করেও বানাতে পারেন।")
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles, key = { it.id }) { p ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${p.typeLabel()} • ${p.url}", fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { openProfile(p) }) {
                                        Text("খুলুন")
                                    }
                                    OutlinedButton(onClick = {
                                        val ok = pinProfileShortcut(this@MainActivity, p)
                                        Toast.makeText(
                                            this@MainActivity,
                                            if (ok) "হোম স্ক্রিনে শর্টকাট যোগ হচ্ছে"
                                            else "এই ফোনে শর্টকাট পিন করা যাচ্ছে না",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }) {
                                        Text("হোম স্ক্রিনে যোগ করুন")
                                    }
                                    OutlinedButton(onClick = { deleteTarget = p }) {
                                        Text("মুছুন")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAdd) {
                AlertDialog(
                    onDismissRequest = { showAdd = false },
                    title = { Text("নতুন প্রোফাইল") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("প্রোফাইলের নাম (যেমন: Facebook ১)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { newType = "facebook" },
                                        enabled = newType != "facebook"
                                    ) { Text("Facebook") }
                                    OutlinedButton(
                                        onClick = { newType = "messenger" },
                                        enabled = newType != "messenger"
                                    ) { Text("Messenger") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { newType = "gmail" },
                                        enabled = newType != "gmail"
                                    ) { Text("Gmail") }
                                    OutlinedButton(
                                        onClick = { newType = "custom" },
                                        enabled = newType != "custom"
                                    ) { Text("অন্য সাইট") }
                                }
                            }
                            if (newType == "custom") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newUrl,
                                    onValueChange = { newUrl = it },
                                    label = { Text("ওয়েবসাইটের ঠিকানা") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (newName.isBlank()) {
                                Toast.makeText(this, "নাম দিন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newType == "custom" && newUrl.isBlank()) {
                                Toast.makeText(this, "ঠিকানা দিন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            profiles = ProfileStore.add(
                                this,
                                Profile.create(newName, newType, newUrl)
                            )
                            showAdd = false
                        }) { Text("যোগ করুন") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdd = false }) { Text("বাতিল") }
                    }
                )
            }

            deleteTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text("প্রোফাইল মুছবেন?") },
                    text = { Text("“${target.name}” প্রোফাইলটি মুছে যাবে — এর লগইনও মুছে যাবে।") },
                    confirmButton = {
                        Button(onClick = {
                            profiles = ProfileStore.delete(this, target.id)
                            deleteTarget = null
                        }) { Text("মুছুন") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteTarget = null }) { Text("বাতিল") }
                    }
                )
            }
        }
    }
}
