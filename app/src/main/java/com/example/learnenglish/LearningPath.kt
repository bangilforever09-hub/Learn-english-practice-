package com.example.learnenglish

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

const val PATH_DAYS = 28
private const val STEP_COUNT = 4

data class PathDay(
    val day: Int,
    val title: String,
    val tip: String,
    val challenge: String,
    val topic: ChatTopic,
    val words: List<Pair<String, String>>,
    val sentences: List<String>
)

private fun pathDay(
    n: Int,
    title: String,
    topic: ChatTopic,
    tip: String,
    challenge: String,
    words: String,
    sentences: String
): PathDay {
    val wordList = words.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { item ->
            val parts = item.split("=")
            Pair(parts[0].trim(), if (parts.size > 1) parts[1].trim() else "")
        }
    val sentenceList = sentences.split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return PathDay(n, title, tip, challenge, topic, wordList, sentenceList)
}

private val PATH: List<PathDay> = listOf(
    pathDay(
        1, "Memperkenalkan diri", ChatTopic.Daily,
        "Gunakan am, is, are untuk memperkenalkan diri: I am a student. She is my friend. They are from Bali.",
        "Perkenalkan dirimu ke AI dalam 4 kalimat: nama, asal, pekerjaan, dan hobi.",
        "introduce=memperkenalkan;hometown=kota asal;hobby=hobi;job=pekerjaan;student=pelajar atau mahasiswa",
        "Hello, my name is Alex and I live in Jakarta.|I work as a designer and I love my job.|Nice to meet you, and thank you for your time."
    ),
    pathDay(
        2, "Rutinitas pagi", ChatTopic.Daily,
        "Present simple untuk kebiasaan. Tambahkan -s atau -es setelah he, she, it: She wakes up early. He goes to work by bus.",
        "Ceritakan rutinitas pagimu dari bangun sampai berangkat, minimal 5 kalimat.",
        "wake up=bangun tidur;breakfast=sarapan;shower=mandi;leave=berangkat;early=pagi-pagi atau awal",
        "I wake up at six every morning.|I usually have breakfast at home.|I leave for work at half past seven."
    ),
    pathDay(
        3, "Keluarga dan teman", ChatTopic.Daily,
        "Have atau has untuk kepemilikan: I have a car. She has a brother. Untuk jamak, tambahkan s: two brothers.",
        "Ceritakan tentang satu anggota keluarga atau teman dekatmu.",
        "family=keluarga;sibling=saudara kandung;friend=teman;parents=orang tua;married=sudah menikah",
        "I have two brothers and one sister.|My parents live in a small village.|My best friend is very funny."
    ),
    pathDay(
        4, "Hobi dan waktu luang", ChatTopic.Daily,
        "Setelah enjoy, like, dan love biasanya pakai kata kerja + ing: I enjoy reading. She likes swimming.",
        "Jelaskan hobimu dan kenapa kamu menyukainya.",
        "free time=waktu luang;enjoy=menikmati;practice=berlatih;weekend=akhir pekan;relax=bersantai",
        "In my free time, I enjoy watching movies.|I like playing games with my friends.|On weekends I usually relax at home."
    ),
    pathDay(
        5, "Waktu dan jadwal", ChatTopic.Daily,
        "Preposisi waktu: at untuk jam (at nine), on untuk hari (on Monday), in untuk bulan, tahun, dan bagian hari (in May, in the morning).",
        "Jelaskan jadwalmu minggu ini dan ajak AI bertemu di hari tertentu.",
        "busy=sibuk;free=kosong atau bebas;late=terlambat;plan=rencana;meet=bertemu",
        "I am busy on Monday, but free on Tuesday.|The meeting starts at nine o'clock.|Could we meet at four in the afternoon?"
    ),
    pathDay(
        6, "Tempat tinggal dan cuaca", ChatTopic.Daily,
        "There is untuk satu benda, there are untuk banyak benda: There is a park near my house. There are many shops here.",
        "Gambarkan tempat tinggalmu dan cuaca di sana.",
        "apartment=apartemen;neighborhood=lingkungan sekitar;quiet=tenang;hot=panas;rainy=hujan",
        "I live in a quiet neighborhood.|It is very hot and humid today.|My house is close to a big market."
    ),
    pathDay(
        7, "Ulasan minggu 1", ChatTopic.Daily,
        "Hari ulasan: ulangi kata-kata di tab Latihan dan ngobrol bebas dengan AI 5 menit. Kalau kesalahan yang sama berulang, simpan koreksinya ke Catatan.",
        "Ngobrol bebas 5 menit: ceritakan apa yang paling sulit dan paling mudah minggu ini.",
        "review=mengulang;remember=mengingat;mistake=kesalahan;progress=kemajuan;goal=tujuan",
        "This week I learned many new words.|I made some mistakes, but I am improving.|My goal is to speak English fluently."
    ),
    pathDay(
        8, "Memesan makanan", ChatTopic.Restaurant,
        "Untuk sopan, pakai Could I ... atau I would like ... Itu lebih halus daripada I want.",
        "Pesan makan malam lengkap di restoran dan minta tagihan.",
        "menu=daftar makanan;order=memesan;bill=tagihan;spicy=pedas;dessert=hidangan penutup",
        "Could I see the menu, please?|I would like the chicken soup and an iced tea.|Could we have the bill, please?"
    ),
    pathDay(
        9, "Belanja", ChatTopic.Daily,
        "How much untuk harga atau benda tak terhitung (How much is it?). How many untuk benda yang bisa dihitung (How many apples?).",
        "Tawar harga sebuah barang dan tanyakan ukuran atau warna lain.",
        "price=harga;discount=diskon;cheap=murah;expensive=mahal;size=ukuran",
        "How much does this cost?|Do you have this in a larger size?|Is there a discount today?"
    ),
    pathDay(
        10, "Menanyakan dan memberi arah", ChatTopic.Daily,
        "Memberi arah pakai kalimat perintah tanpa subjek: Go straight. Turn left. Cross the street.",
        "Tanyakan jalan ke stasiun, lalu jelaskan jalan dari rumahmu ke warung terdekat.",
        "straight=lurus;corner=sudut jalan;turn=belok;opposite=seberang;nearby=dekat sini",
        "Go straight and turn left at the corner.|The bank is opposite the post office.|Is there a pharmacy nearby?"
    ),
    pathDay(
        11, "Naik transportasi umum", ChatTopic.Daily,
        "Pertanyaan jam: What time does ... leave, start, atau open? Setelah does, kata kerja kembali ke bentuk dasar.",
        "Beli tiket kereta dan tanyakan jam keberangkatan berikutnya.",
        "ticket=tiket;platform=peron;departure=keberangkatan;transfer=pindah kendaraan;passenger=penumpang",
        "One ticket to the city center, please.|What time does the next train leave?|I need to transfer at the main station."
    ),
    pathDay(
        12, "Di bandara", ChatTopic.Airport,
        "Need to + kata kerja dasar untuk keharusan: I need to check in. Do I need to pay for extra luggage?",
        "Lakukan check-in, lalu tanyakan bagasi dan nomor gerbang.",
        "passport=paspor;luggage=bagasi;boarding pass=kartu naik pesawat;gate=gerbang keberangkatan;flight=penerbangan",
        "I would like to check in for my flight.|Do I need to pay for extra luggage?|Which gate does the flight to Bali leave from?"
    ),
    pathDay(
        13, "Menginap di hotel", ChatTopic.Daily,
        "Too + kata sifat berarti terlalu: too noisy, too expensive. Kata sifat + enough berarti cukup: big enough.",
        "Check-in ke hotel dan minta ganti kamar yang lebih tenang.",
        "reservation=pemesanan;available=tersedia atau ada;night=malam menginap;facility=fasilitas;noisy=berisik",
        "I have a reservation under the name Rina.|Is breakfast included in the price?|The room is too noisy. Could I change it?"
    ),
    pathDay(
        14, "Ulasan minggu 2", ChatTopic.Daily,
        "Past simple untuk cerita yang sudah lewat: I tried, I visited, I went. Banyak kata kerja tak beraturan: go-went, eat-ate, see-saw.",
        "Ceritakan perjalanan atau makan di luar terakhirmu dalam bentuk lampau.",
        "delicious=lezat;suggest=menyarankan;journey=perjalanan;adventure=petualangan;worth it=sepadan",
        "Last week I tried a new restaurant.|The trip was tiring, but it was worth it.|I would recommend this place to my friends."
    ),
    pathDay(
        15, "Pekerjaanku", ChatTopic.Work,
        "Work in, at, for: work in a department, work at a company, work for a boss.",
        "Jelaskan pekerjaanmu (atau pekerjaan impianmu) dan tugas utamanya.",
        "position=jabatan;department=departemen;task=tugas;boss=atasan;workplace=tempat kerja",
        "I work in the marketing department.|My main task is to answer customer emails.|My boss is supportive and fair."
    ),
    pathDay(
        16, "Email dan telepon", ChatTopic.Work,
        "Frasa email: I am writing to ..., Please find attached ..., Best regards. Pakai could untuk permintaan sopan.",
        "Ucapkan isi email singkat yang meminta jadwal rapat.",
        "attach=melampirkan;reply=membalas;regards=salam hormat;call back=menelepon balik;message=pesan",
        "Please find the file attached.|I am calling about the invoice.|Could you call me back this afternoon?"
    ),
    pathDay(
        17, "Rapat", ChatTopic.Work,
        "Memberi pendapat: I think ..., In my opinion ..., I agree with you, I disagree because ... Untuk saran: Why don't we ...?",
        "Pimpin rapat singkat dan ajukan satu saran.",
        "agenda=agenda;agree=setuju;disagree=tidak setuju;decision=keputusan;item=butir atau topik",
        "Let's start with the first item on the agenda.|I agree with you, but I have another idea.|We need to make a decision today."
    ),
    pathDay(
        18, "Presentasi", ChatTopic.Work,
        "Penanda alur: First, Next, Then, Finally, To sum up. Membantu pendengar mengikuti presentasimu.",
        "Presentasikan satu topik selama 1 menit dengan pembuka, isi, dan penutup.",
        "audience=hadirin;chart=grafik;summary=ringkasan;conclusion=kesimpulan;increase=meningkat",
        "Today I will talk about our sales results.|As you can see on this chart, sales increased.|To sum up, we reached our target."
    ),
    pathDay(
        19, "Wawancara kerja", ChatTopic.Interview,
        "Jawaban wawancara: Situasi, Tindakan, Hasil. Pakai past simple untuk pengalaman: I managed, I solved, I improved.",
        "Jawab pertanyaan Tell me about yourself dan What is your weakness?",
        "strength=kekuatan;weakness=kelemahan;qualification=kualifikasi;candidate=kandidat;immediately=segera",
        "My greatest strength is working in a team.|I am looking for a chance to grow my career.|I can start immediately if needed."
    ),
    pathDay(
        20, "Meminta dengan sopan", ChatTopic.Work,
        "Permintaan sangat sopan: Would it be possible to ...? Would you mind + ing? (Would you mind helping me?)",
        "Minta perpanjangan tenggat waktu dengan sopan dan beri alasannya.",
        "request=permintaan;offer=penawaran;condition=syarat;possible=mungkin dilakukan;favor=bantuan kecil",
        "Would it be possible to extend the deadline?|I would like to ask for a small favor.|That sounds fair to me."
    ),
    pathDay(
        21, "Ulasan minggu 3", ChatTopic.Work,
        "Present perfect untuk pengalaman sampai sekarang: I have worked here for two years. Have you ever ...?",
        "Ceritakan pencapaian kerja terbesarmu.",
        "improvement=peningkatan;challenge=tantangan;success=keberhasilan;career=karier;project=proyek",
        "This month I finished an important project.|The biggest challenge was the tight deadline.|I learned a lot from my team."
    ),
    pathDay(
        22, "Menyatakan pendapat", ChatTopic.Ielts,
        "Struktur jawaban speaking: Pendapat, Alasan, Contoh. Penghubung: because, for example, however, therefore.",
        "Jawab: Do you think English is important? Beri 2 alasan dan 1 contoh.",
        "believe=percaya;reason=alasan;example=contoh;however=namun;therefore=oleh karena itu",
        "In my opinion, learning English is very important.|There are two main reasons for this.|For example, many jobs require good English."
    ),
    pathDay(
        23, "Membandingkan", ChatTopic.Ielts,
        "Kata sifat pendek + er than (cheaper than). Kata sifat panjang pakai more ... than (more expensive than). As ... as untuk yang setara.",
        "Bandingkan tinggal di kota dan tinggal di desa.",
        "compare=membandingkan;similar=mirip;different=berbeda;popular=populer;both=keduanya",
        "Living in a city is more expensive than living in a village.|Online classes are as useful as classroom lessons.|Both options are similar, but one is cheaper."
    ),
    pathDay(
        24, "Rencana masa depan", ChatTopic.Ielts,
        "Going to untuk rencana yang sudah dipikirkan, will untuk keputusan spontan atau prediksi: I am going to study. I think it will rain.",
        "Ceritakan rencana kariermu 5 tahun ke depan.",
        "intend=bermaksud;future=masa depan;dream=impian;prepare=mempersiapkan;someday=suatu hari nanti",
        "I am going to take an English test next year.|I will probably move to a bigger city.|My dream is to work in another country."
    ),
    pathDay(
        25, "Menceritakan pengalaman", ChatTopic.Ielts,
        "Alur cerita: First, Then, After that, Suddenly, Finally. Past simple untuk kejadian, past continuous untuk latar: It was raining when we arrived.",
        "Ceritakan pengalaman liburan tak terlupakan dengan urutan waktu.",
        "unforgettable=tak terlupakan;recently=belakangan ini;suddenly=tiba-tiba;luckily=untungnya;finally=akhirnya",
        "Last summer I traveled to Bali with my family.|Suddenly it started to rain, but we kept walking.|Finally, we found a beautiful beach."
    ),
    pathDay(
        26, "Teknologi dan media sosial", ChatTopic.Ielts,
        "Present perfect untuk perubahan sampai sekarang: Technology has changed our lives. Pakai some people atau many people untuk opini umum.",
        "Diskusikan sisi baik dan sisi buruk media sosial.",
        "device=perangkat;social media=media sosial;online=daring;addicted=kecanduan;useful=berguna",
        "Smartphones have made life easier.|Some people spend too much time on social media.|Technology can be useful if we use it wisely."
    ),
    pathDay(
        27, "Lingkungan dan kesehatan", ChatTopic.Ielts,
        "Should untuk saran dan opini: We should reduce plastic. People should exercise more. Bentuk negatif: should not.",
        "Beri 3 saran untuk mengurangi polusi di kotamu.",
        "pollution=polusi;healthy=sehat;exercise=olahraga;protect=melindungi;recycle=mendaur ulang",
        "We should recycle plastic to protect the environment.|Regular exercise keeps people healthy.|Pollution is a serious problem in big cities."
    ),
    pathDay(
        28, "Ujian akhir dan ulasan", ChatTopic.Ielts,
        "Selamat, kamu sampai di hari terakhir. Ulangi hari-hari yang terasa sulit, lalu jalani jalur ini lagi dengan target bicara yang lebih lancar.",
        "Ngobrol 10 menit tanpa melihat catatan dan ceritakan kemajuanmu selama 28 hari.",
        "fluent=lancar;confident=percaya diri;accent=aksen;pronunciation=pengucapan;vocabulary=kosakata",
        "I feel more confident when I speak English now.|My pronunciation is getting better every week.|I will keep practicing every day."
    )
)

fun pathDayTitle(day: Int): String = PATH.firstOrNull { it.day == day }?.title ?: ""

fun pathDayComplete(done: Set<String>, day: Int): Boolean =
    (0 until STEP_COUNT).all { done.contains("$day-$it") }

fun pathCompletedCount(done: Set<String>): Int =
    (1..PATH_DAYS).count { pathDayComplete(done, it) }

fun pathCurrentDay(done: Set<String>): Int =
    (1..PATH_DAYS).firstOrNull { !pathDayComplete(done, it) } ?: PATH_DAYS

private fun weekTitle(day: Int): String = when ((day - 1) / 7) {
    0 -> "Minggu 1 · Perkenalan dan rutinitas"
    1 -> "Minggu 2 · Makanan, belanja, dan perjalanan"
    2 -> "Minggu 3 · Kerja dan karier"
    else -> "Minggu 4 · Opini dan IELTS"
}

class PathViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("path_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.get(application)
    private val noteDao = db.noteDao()
    private val dailyDao = db.dailyProgressDao()

    private val _done = MutableStateFlow<Set<String>>(
        (prefs.getStringSet("done_steps", emptySet()) ?: emptySet()).toSet()
    )
    val done: StateFlow<Set<String>> = _done

    fun toggle(day: Int, step: Int) {
        val key = "$day-$step"
        val next = _done.value.toMutableSet()
        if (!next.add(key)) {
            next.remove(key)
        }
        prefs.edit().putStringSet("done_steps", next).apply()
        _done.value = next
    }

    fun saveWords(day: PathDay) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            for ((word, meaning) in day.words) {
                noteDao.insert(
                    Note(
                        type = "Kata",
                        text = word,
                        extra = meaning,
                        createdDay = today,
                        reviewsDone = 0,
                        nextReview = today + 1
                    )
                )
            }
            val key = LocalDate.now().toString()
            val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
            if (!daily.noteDone) {
                dailyDao.upsert(daily.copy(noteDone = true))
            }
        }
    }
}

@Composable
private fun StepCard(
    emoji: String,
    title: String,
    checked: Boolean,
    container: Color,
    onCheck: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    emoji + " " + title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(checked = checked, onCheckedChange = { onCheck() })
            }
            content()
        }
    }
}

@Composable
fun PathPage(
    vm: PathViewModel,
    onSpeak: (String) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenSpeaking: () -> Unit,
    onStartChat: (ChatTopic) -> Unit,
    onBack: () -> Unit
) {
    val done by vm.done.collectAsStateWithLifecycle()
    val current = pathCurrentDay(done)
    var selected by rememberSaveable { mutableStateOf(pathCurrentDay(done)) }
    var savedWords by remember(selected) { mutableStateOf(false) }

    val dayNo = selected.coerceIn(1, PATH_DAYS)
    val day = PATH.first { it.day == dayNo }
    val completed = pathCompletedCount(done)
    val dayDone = pathDayComplete(done, dayNo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Kembali") }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🗺️ Jalur belajar $PATH_DAYS hari",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "$completed dari $PATH_DAYS hari selesai · langkah berikutnya: hari ke-$current",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                LinearProgressIndicator(
                    progress = { completed / PATH_DAYS.toFloat() },
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0 until 7) {
                        val n = row * 7 + col + 1
                        val complete = pathDayComplete(done, n)
                        val isSelected = n == dayNo
                        val bg = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            complete -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val fg = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            complete -> MaterialTheme.colorScheme.onTertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val frame = if (n == current && !isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(frame)
                                .background(bg, RoundedCornerShape(10.dp))
                                .clickable { selected = n }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(n.toString(), style = MaterialTheme.typography.labelLarge, color = fg)
                        }
                    }
                }
            }
        }

        if (dayNo != current) {
            TextButton(onClick = { selected = current }) {
                Text("↩ Ke langkah berikutnya (hari ke-$current)")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(weekTitle(dayNo), style = MaterialTheme.typography.labelLarge)
                Text(
                    "Hari ke-$dayNo: ${day.title}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Kerjakan 4 langkah di bawah, lalu centang tiap langkah yang sudah selesai.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        StepCard(
            emoji = "📖",
            title = "5 kata baru",
            checked = done.contains("$dayNo-0"),
            container = MaterialTheme.colorScheme.primaryContainer,
            onCheck = { vm.toggle(dayNo, 0) }
        ) {
            day.words.forEach { (word, meaning) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            word,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(meaning, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { onSpeak(word) }) { Text("🔊") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.saveWords(day)
                        savedWords = true
                    },
                    enabled = !savedWords
                ) {
                    Text(if (savedWords) "Tersimpan ✅" else "📝 Simpan ke Catatan")
                }
                OutlinedButton(onClick = onOpenPractice) { Text("🃏 Latihan") }
            }
        }

        StepCard(
            emoji = "🎤",
            title = "3 kalimat untuk diucapkan",
            checked = done.contains("$dayNo-1"),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onCheck = { vm.toggle(dayNo, 1) }
        ) {
            day.sentences.forEach { sentence ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sentence,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSpeak(sentence) }) { Text("🔊") }
                }
            }
            Text(
                "Dengar contoh, lalu ucapkan keras-keras tiga kali. Setelah itu uji ucapanmu di Latihan ucapan.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onOpenSpeaking) { Text("🎤 Buka Latihan ucapan") }
        }

        StepCard(
            emoji = "💬",
            title = "Tantangan ngobrol",
            checked = done.contains("$dayNo-2"),
            container = MaterialTheme.colorScheme.secondaryContainer,
            onCheck = { vm.toggle(dayNo, 2) }
        ) {
            Text(day.challenge, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { onStartChat(day.topic) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("💬 Mulai chat: ${day.topic.label}")
            }
        }

        StepCard(
            emoji = "💡",
            title = "Tips tata bahasa",
            checked = done.contains("$dayNo-3"),
            container = MaterialTheme.colorScheme.primaryContainer,
            onCheck = { vm.toggle(dayNo, 3) }
        ) {
            Text(day.tip, style = MaterialTheme.typography.bodyLarge)
        }

        if (dayDone) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎉 Hari ke-$dayNo selesai!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                    if (dayNo < PATH_DAYS) {
                        Button(
                            onClick = { selected = dayNo + 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiary,
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Lanjut ke hari berikutnya →")
                        }
                    }
                }
            }
        }
    }
}
