package com.hifz.quran.data

// ─────────────────────────────────────────────
//  Données statiques du Coran
//  114 sourates  +  récitateurs disponibles
// ─────────────────────────────────────────────

data class SurahInfo(
    val number: Int,          // 1–114
    val nameArabic: String,   // nom arabe
    val nameLatin: String,    // translittération
    val nameFr: String,       // traduction française
    val verseCount: Int,      // nombre de versets
    val juz: Int              // numéro de juz (pour info)
)

data class ReciterInfo(
    val id: String,           // clé utilisée dans l'URL everyayah
    val displayName: String,  // nom affiché à l'utilisateur
    val nameArabic: String
)

object QuranData {

    // ── RÉCITATEURS ────────────────────────────────────────────────────────────
    val RECITERS = listOf(
        ReciterInfo("Alafasy_128kbps",                "Mishary Alafasy",         "مشاري العفاسي"),
        ReciterInfo("Abdul_Basit_Murattal_128kbps",   "Abdul Basit (Murattal)",  "عبد الباسط"),
        ReciterInfo("Husary_128kbps",                 "Mahmoud Khalil Husary",   "الحصري"),
        ReciterInfo("Minshawy_Murattal_128kbps",      "Mohamed Minshawi",        "المنشاوي"),
        ReciterInfo("Sudais_192kbps",                 "Abdul Rahman Al-Sudais",  "السديس"),
        ReciterInfo("Maher_AlMuaiqly_128kbps",        "Maher Al-Muaiqly",        "ماهر المعيقلي"),
        ReciterInfo("Yasser_Ad-Dussary_128kbps",      "Yasser Al-Dosari",        "ياسر الدوسري"),
        ReciterInfo("Nasser_Alqatami_128kbps",        "Nasser Al-Qatami",        "ناصر القطامي")
    )

    val DEFAULT_RECITER = RECITERS[0] // Alafasy

    // URL base d'everyayah.com
    fun getVerseUrl(reciterId: String, surah: Int, verse: Int): String {
        val s = surah.toString().padStart(3, '0')
        val v = verse.toString().padStart(3, '0')
        return "https://everyayah.com/data/$reciterId/$s$v.mp3"
    }

    // ── 114 SOURATES ───────────────────────────────────────────────────────────
    val SURAHS = listOf(
        SurahInfo(1,   "الفاتحة",    "Al-Fatiha",       "L'Ouverture",          7,   1),
        SurahInfo(2,   "البقرة",     "Al-Baqara",       "La Vache",             286, 1),
        SurahInfo(3,   "آل عمران",  "Ali 'Imran",      "La Famille d'Imran",   200, 3),
        SurahInfo(4,   "النساء",     "An-Nisa",         "Les Femmes",           176, 4),
        SurahInfo(5,   "المائدة",    "Al-Ma'ida",       "La Table servie",      120, 6),
        SurahInfo(6,   "الأنعام",    "Al-An'am",        "Les Bestiaux",         165, 7),
        SurahInfo(7,   "الأعراف",    "Al-A'raf",        "Les Hauteurs",         206, 8),
        SurahInfo(8,   "الأنفال",    "Al-Anfal",        "Le Butin",             75,  9),
        SurahInfo(9,   "التوبة",     "At-Tawba",        "Le Repentir",          129, 10),
        SurahInfo(10,  "يونس",       "Yunus",            "Jonas",                109, 11),
        SurahInfo(11,  "هود",        "Hud",              "Hud",                  123, 11),
        SurahInfo(12,  "يوسف",       "Yusuf",            "Joseph",               111, 12),
        SurahInfo(13,  "الرعد",      "Ar-Ra'd",         "Le Tonnerre",          43,  13),
        SurahInfo(14,  "إبراهيم",    "Ibrahim",          "Abraham",              52,  13),
        SurahInfo(15,  "الحجر",      "Al-Hijr",         "Al-Hijr",              99,  14),
        SurahInfo(16,  "النحل",      "An-Nahl",         "Les Abeilles",         128, 14),
        SurahInfo(17,  "الإسراء",    "Al-Isra",         "Le Voyage nocturne",   111, 15),
        SurahInfo(18,  "الكهف",      "Al-Kahf",         "La Caverne",           110, 15),
        SurahInfo(19,  "مريم",       "Maryam",           "Marie",                98,  16),
        SurahInfo(20,  "طه",         "Ta-Ha",            "Ta-Ha",                135, 16),
        SurahInfo(21,  "الأنبياء",   "Al-Anbiya",       "Les Prophètes",        112, 17),
        SurahInfo(22,  "الحج",       "Al-Hajj",         "Le Pèlerinage",        78,  17),
        SurahInfo(23,  "المؤمنون",   "Al-Mu'minun",     "Les Croyants",         118, 18),
        SurahInfo(24,  "النور",      "An-Nur",          "La Lumière",           64,  18),
        SurahInfo(25,  "الفرقان",    "Al-Furqan",       "Le Discernement",      77,  18),
        SurahInfo(26,  "الشعراء",    "Ash-Shu'ara",     "Les Poètes",           227, 19),
        SurahInfo(27,  "النمل",      "An-Naml",         "Les Fourmis",          93,  19),
        SurahInfo(28,  "القصص",      "Al-Qasas",        "Le Récit",             88,  20),
        SurahInfo(29,  "العنكبوت",   "Al-'Ankabut",     "L'Araignée",           69,  20),
        SurahInfo(30,  "الروم",      "Ar-Rum",          "Les Byzantins",        60,  21),
        SurahInfo(31,  "لقمان",      "Luqman",           "Luqman",               34,  21),
        SurahInfo(32,  "السجدة",     "As-Sajda",        "La Prosternation",     30,  21),
        SurahInfo(33,  "الأحزاب",    "Al-Ahzab",        "Les Coalisés",         73,  21),
        SurahInfo(34,  "سبأ",        "Saba",             "Saba",                 54,  22),
        SurahInfo(35,  "فاطر",       "Fatir",            "Le Créateur",          45,  22),
        SurahInfo(36,  "يس",         "Ya-Sin",           "Ya-Sin",               83,  22),
        SurahInfo(37,  "الصافات",    "As-Saffat",       "Rangées en rangs",     182, 23),
        SurahInfo(38,  "ص",          "Sad",              "Sad",                  88,  23),
        SurahInfo(39,  "الزمر",      "Az-Zumar",        "Les Groupes",          75,  23),
        SurahInfo(40,  "غافر",       "Ghafir",           "Le Pardonneur",        85,  24),
        SurahInfo(41,  "فصلت",       "Fussilat",         "Exposées en détail",   54,  24),
        SurahInfo(42,  "الشورى",     "Ash-Shura",       "La Consultation",      53,  25),
        SurahInfo(43,  "الزخرف",     "Az-Zukhruf",      "Les Ornements d'or",   89,  25),
        SurahInfo(44,  "الدخان",     "Ad-Dukhan",       "La Fumée",             59,  25),
        SurahInfo(45,  "الجاثية",    "Al-Jathiya",      "L'Agenouillée",        37,  25),
        SurahInfo(46,  "الأحقاف",    "Al-Ahqaf",        "Les Dunes de sable",   35,  26),
        SurahInfo(47,  "محمد",       "Muhammad",         "Muhammad",             38,  26),
        SurahInfo(48,  "الفتح",      "Al-Fath",         "La Victoire",          29,  26),
        SurahInfo(49,  "الحجرات",    "Al-Hujurat",      "Les Appartements",     18,  26),
        SurahInfo(50,  "ق",          "Qaf",              "Qaf",                  45,  26),
        SurahInfo(51,  "الذاريات",   "Adh-Dhariyat",    "Les Vents éparpilleurs",60, 26),
        SurahInfo(52,  "الطور",      "At-Tur",          "Le Mont Sinaï",        49,  27),
        SurahInfo(53,  "النجم",      "An-Najm",         "L'Étoile",             62,  27),
        SurahInfo(54,  "القمر",      "Al-Qamar",        "La Lune",              55,  27),
        SurahInfo(55,  "الرحمن",     "Ar-Rahman",       "Le Tout Miséricordieux",78, 27),
        SurahInfo(56,  "الواقعة",    "Al-Waqi'a",       "L'Événement",          96,  27),
        SurahInfo(57,  "الحديد",     "Al-Hadid",        "Le Fer",               29,  27),
        SurahInfo(58,  "المجادلة",   "Al-Mujadila",     "La Discussion",        22,  28),
        SurahInfo(59,  "الحشر",      "Al-Hashr",        "L'Exode",              24,  28),
        SurahInfo(60,  "الممتحنة",   "Al-Mumtahina",    "L'Éprouvée",           13,  28),
        SurahInfo(61,  "الصف",       "As-Saf",          "Le Rang",              14,  28),
        SurahInfo(62,  "الجمعة",     "Al-Jumu'a",       "Le Vendredi",          11,  28),
        SurahInfo(63,  "المنافقون",  "Al-Munafiqun",    "Les Hypocrites",       11,  28),
        SurahInfo(64,  "التغابن",    "At-Taghabun",     "La Dépossession",      18,  28),
        SurahInfo(65,  "الطلاق",     "At-Talaq",        "Le Divorce",           12,  28),
        SurahInfo(66,  "التحريم",    "At-Tahrim",       "L'Interdiction",       12,  28),
        SurahInfo(67,  "الملك",      "Al-Mulk",         "La Royauté",           30,  29),
        SurahInfo(68,  "القلم",      "Al-Qalam",        "La Plume",             52,  29),
        SurahInfo(69,  "الحاقة",     "Al-Haqqa",        "L'Inévitable",         52,  29),
        SurahInfo(70,  "المعارج",    "Al-Ma'arij",      "Les Degrés",           44,  29),
        SurahInfo(71,  "نوح",        "Nuh",              "Noé",                  28,  29),
        SurahInfo(72,  "الجن",       "Al-Jinn",         "Les Djinns",           28,  29),
        SurahInfo(73,  "المزمل",     "Al-Muzzammil",    "L'Enveloppé",          20,  29),
        SurahInfo(74,  "المدثر",     "Al-Muddaththir",  "Le Revêtu d'un manteau",56, 29),
        SurahInfo(75,  "القيامة",    "Al-Qiyama",       "La Résurrection",      40,  29),
        SurahInfo(76,  "الإنسان",    "Al-Insan",        "L'Homme",              31,  29),
        SurahInfo(77,  "المرسلات",   "Al-Mursalat",     "Les Envoyés",          50,  29),
        SurahInfo(78,  "النبأ",      "An-Naba",         "La Nouvelle",          40,  30),
        SurahInfo(79,  "النازعات",   "An-Nazi'at",      "Ceux qui arrachent",   46,  30),
        SurahInfo(80,  "عبس",        "'Abasa",           "Il s'est renfrogné",   42,  30),
        SurahInfo(81,  "التكوير",    "At-Takwir",       "L'Enroulement",        29,  30),
        SurahInfo(82,  "الانفطار",   "Al-Infitar",      "La Déchirure",         19,  30),
        SurahInfo(83,  "المطففين",   "Al-Mutaffifin",   "Les Fraudeurs",        36,  30),
        SurahInfo(84,  "الانشقاق",   "Al-Inshiqaq",     "La Fissure",           25,  30),
        SurahInfo(85,  "البروج",     "Al-Buruj",        "Les Constellations",   22,  30),
        SurahInfo(86,  "الطارق",     "At-Tariq",        "L'Astre nocturne",     17,  30),
        SurahInfo(87,  "الأعلى",     "Al-A'la",         "Le Très-Haut",         19,  30),
        SurahInfo(88,  "الغاشية",    "Al-Ghashiya",     "L'Enveloppante",       26,  30),
        SurahInfo(89,  "الفجر",      "Al-Fajr",         "L'Aube",               30,  30),
        SurahInfo(90,  "البلد",      "Al-Balad",        "La Cité",              20,  30),
        SurahInfo(91,  "الشمس",      "Ash-Shams",       "Le Soleil",            15,  30),
        SurahInfo(92,  "الليل",      "Al-Layl",         "La Nuit",              21,  30),
        SurahInfo(93,  "الضحى",      "Ad-Duhaa",        "La Matinée",           11,  30),
        SurahInfo(94,  "الشرح",      "Ash-Sharh",       "L'Expansion",          8,   30),
        SurahInfo(95,  "التين",      "At-Tin",          "Le Figuier",           8,   30),
        SurahInfo(96,  "العلق",      "Al-'Alaq",        "L'Adhérence",          19,  30),
        SurahInfo(97,  "القدر",      "Al-Qadr",         "La Nuit du Destin",    5,   30),
        SurahInfo(98,  "البينة",     "Al-Bayyina",      "La Preuve",            8,   30),
        SurahInfo(99,  "الزلزلة",    "Az-Zalzala",      "Le Séisme",            8,   30),
        SurahInfo(100, "العاديات",   "Al-'Adiyat",      "Les Coureurs",         11,  30),
        SurahInfo(101, "القارعة",    "Al-Qari'a",       "La Fracassante",       11,  30),
        SurahInfo(102, "التكاثر",    "At-Takathur",     "L'Accumulation",       8,   30),
        SurahInfo(103, "العصر",      "Al-'Asr",         "Le Temps",             3,   30),
        SurahInfo(104, "الهمزة",     "Al-Humaza",       "Le Calomniateur",      9,   30),
        SurahInfo(105, "الفيل",      "Al-Fil",          "L'Éléphant",           5,   30),
        SurahInfo(106, "قريش",       "Quraysh",          "Quraysh",              4,   30),
        SurahInfo(107, "الماعون",    "Al-Ma'un",        "L'Ustensile",          7,   30),
        SurahInfo(108, "الكوثر",     "Al-Kawthar",      "L'Abondance",          3,   30),
        SurahInfo(109, "الكافرون",   "Al-Kafirun",      "Les Mécréants",        6,   30),
        SurahInfo(110, "النصر",      "An-Nasr",         "Le Secours",           3,   30),
        SurahInfo(111, "المسد",      "Al-Masad",        "La Corde de fibre",    5,   30),
        SurahInfo(112, "الإخلاص",    "Al-Ikhlas",       "Le Monothéisme pur",   4,   30),
        SurahInfo(113, "الفلق",      "Al-Falaq",        "L'Aube naissante",     5,   30),
        SurahInfo(114, "الناس",      "An-Nas",          "Les Hommes",           6,   30)
    )

    fun getSurahByNumber(number: Int): SurahInfo? = SURAHS.firstOrNull { it.number == number }
}
