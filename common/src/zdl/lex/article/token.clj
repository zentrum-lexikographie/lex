(ns zdl.lex.article.token
  (:require [clojure.string :as str]
            [zdl.xml.util :as xml]))

(def abbreviation-whitelist
  #{"etw.", "jmd.", "jmds.", "jmdn.", "jmdm."})

(def camel-case-whitelist
  #{"SonntagsBlick", "McCarthy-Ära", "(WoW)", "AfD.", "CyberProfit", "LfV",
    "FrischeKiste", "GraWe", "McEnroe", "HochschulabsolventInnen", "StVRG",
    "(TransMIT)", "BayBG", "KfZ-Zulieferers", "HelloFresh", "LoanDepot",
    "»BiF«", "NeuLand", "eRikm", "YouGov-Umfrage", "GoSMS", "VfB",
    "(MfS)", "McCormack)", "(SchwbG)", "HipHop-Künstler", "VfL-Spielerinnen",
    "DeLillos", "(IGfM)", "(vCJD),", "KaDeWe", "HändlerInnen", "KünstlerInnen",
    "YouTube-Szene", "YouTube-Trends", "YouTubes", "CareerCalling",
    "YouTube.", "DuVernay", "LiMo", "GründerInnen",
    "KapitalgeberInnen", "DradioWissen", "StGB.",
    "ExVira", "(ExVira", "eBook-Konvertierung",
    "StrÄndG)", "(BGBl.", "PiCTeX", "iOS)", "YouTuber",
    "McQueen.", "(StGB.", "(IStGH)", "(BetrVG)", "MdF",
    "McDonald’s,", "EyePhone,", "McClelland", "AT&T",
    "AutorInnen", "(BauGB)", "BauGB)", "mPFC", "DeClercq",
    "BamS-Reporterin", "iPhones,", "WhatsApp,", "AnwenderInnen",
    "DoubleClick", "BtMG", "(DroLeg)", "C&A", "»PPmP",
    "CarPlay", "McKinsey.", "RecRec)", "McCartney,",
    "McAleese,", "S.A.-Männer.", "CvD", "FoodCenter,",
    "BdBSt", "WStB),", "BdBSt).", "MyBibRSS", "cSt",
    "YouTube,", "AfD", "AutoCAD-Test", "eBook", "(DStV)",
    "(BfArM)", "GutsMuths", "GlücksritterInnen", "(DiS)",
    "McKinsey-Studien", "openSUSE", "(VoIP)", "McClaren.",
    "cSt,", "hiLDEBRANDT:", "HbbTV", "HfÖ", "OpenType",
    "GitHub-Anwendungsfall", "AussiedlerInnen", "BürgerInnen",
    "StS.", "(StS.)", "AirPlay", "(iOS)", "KPdSU,",
    "AngehörigenInnen", "»eBook«", "neu(st)e", "L’Empéri",
    "»Lunch In Time«-Menü,", "InterContintal-Hotels",
    "LuftBO)", "grM", "BetrVG", "(BfR)", "(kW)", "mmHg",
    "TüV-Gebühren,", "FotoStation", "»AutoAusfüllen«,", "(BdV),",
    "»TextMaker", "OpenOffice.org", "Word-AutoFormen", "(BaT),",
    "»iPhone", "UdK", "iPad,", "McGill", "TeamBank-Chef",
    "BgVV.", "MgT", "HfG", "StVO,", "StVO", "McKean",
    "EuroStoxx", "(BfV),", "Drive-in-McDonalds", "MigrantInnen,",
    "»PdR«", "(StGB)", "WikiLeaks", "(SoFFin)", "iCraveTV",
    "EM.TV", "HipHop", "McAfee", "KaZaA", "eMule", "MacBook",
    "O-TonArt,", "SammlerInnen", "JavaScript-Programm", "BaFin",
    "McAllister", "IfZ-Team.", "(SdK),", "KirchMedia,",
    "SinnLeffers-Modehäuser", "McCain,", "McGee", "McKibben",
    "(An-)Sprache.", "KeePass,", "iPhone", "CompuServe",
    "»CompuServe", "(NewsGroups)", "ImmOnline", "iPad", "iOS",
    "McDonald’s", "McGuire", "»BerlinOnline.de«", "HypoVereinsbank",
    "(GmbH)", "MfNV", "DeTeMedien", "BfG", "MfS,", "MfS-Zwecke",
    "StGB)", "StGB),", "McNair,", "MfS-Spionin",
    "sFr", "sFr).", "WestLB", "iPod", "HaMü", "ComicLiteratur",
    "S.S.", "SystemConsult", "VfGH,", "O’Neill", "KaDeWe.",
    "McBeal«", "StGB,", "BayernLB,", "McDonald-Hamburger-Kette",
    "O’Connor", "LeMay", "eBay", "mbH", "HipHop,", "MoMA",
    "McDonald’s-Filiale;", "WikiWeb", "AdW", "»WorldCom«",
    "λ-Phagen", "¬p", "KPdSU.", "GmbH.", "eG", "DrKW", "Lf",
    "(BVfS),", "(HeLP)", "InnoTrans", "TransFair-Geschäftsführer",
    "IgE-Antikörper", "VfL", "McCartney", "»Peanuts«-Geschichten",
    "kW", "GeV", "PiS", "kJ.", "MfS-Offizieren", "InsO",
    "MfS", "(KPdSU).", "KPdS", "Frankfurt/Oder",
    "UdSSR", "»DHfK", "(BBiG)", "(VaR)", "BayernLB",
    "ActiveX", "JavaScript", "CeBIT", "CeBit", "YouTube",
    "StepStone", "UdSSR,", "KfW-Gesetzes", "ProSieben",
    "SkyDrive-Cloud", "McCarthy", "HiFi-Videorekorder",
    "»Deutschland-Investitions-GmbH«", "(StPO).", "(KfW),",
    "ThyssenKrupp", "ActiveX-Control,", "eGK", "UdSSR;",
    "F.A.Z.-Aktienindex", "AntiLobby-Gesetzgebung",
    "DeutschlandPremiere.", "»Oscar«-Kandidaten",
    "(eCommerce)", "eLearning", "CyberSurfr", "OpenOffice",
    "(BfA)", "BfA«", "EnBW,", "AktG).", "GmbH", "GmbH«",
    "BetriebVG.", "TuS", "MdB,", "OpenSource-affine-Firmen-WG",
    "(AsF)", "Robert-Bosch-GmbH", "eGmbH«.", "BlueHybrid", "GmbH,",
    "S.S.-Männer", "BenQ", "FlexScan", "UdSSR:", "PopArt",
    "MfS-Gliederungen", "MfS-Zentrale", "BfA", "BvR",
    "KüSchG", "F.A.Z.", "vH", "vH.", "(BWpV)", "iPad?",
    "GdP-Bundesvorsitzende", "eSwiss", "(GfN)",
    "KitKat«", "KitKat-Break", "KitKat-Pause,", "iTunes",
    "BamS", "(DuMont)", "AVerErgG", "TubeAgency.",
    "@-Zeichen", "@Benutzername«", "VfB-Chefs",
    "FußballBundes", "»großmäulige(n)", "BdL,",
    "McKinsey-Studie", "(GdP)", "(Ost-)Berliner",
    "AufenthG;", "DeutschlandRadio", "SiCon",
    "VfB,", "MfS«:", "MfS-Offiziere", "AStA", "(AStA)",
    "AStA,", "ContiTech", "(BoA)", "kWh", "kWh/m²",
    "MicroLink", "dLAN", "iMac", "»SprecherInnenrat«", "DaZ",
    "SmartCard", "TechniSat", "TomTom", "McGregor.",
    "McCreesh,", "KarstadtQuelle", "HipHop-", "sFr.", "sFr.;",
    "DeLillo", "McCarthy-Romane", "Apple-iPhone.", "O’Malley,",
    "O’Nan", "literaturWERKstatt", "CompuServe.", "»FreePen«.",
    "(KfW)", "(F.A.Z.", "DaimlerChrysler", "VfL.", "kBit/s",
    "RuleML", "iPhone-Modelle", "DigiTex", "GmbH;", "EnBW",
    "Verlags-GmbH", "»SpVgg", "SonntagsBlick-Autor",
    "EuroStoxx50", "(BVerwG)", "(BVwG)", "(BVerfG),",
    "BVerfGG).", "BfV", "KirchMedia", "ProSiebenSat.1",
    "BesucherInnen", "MdIs", "(Bkm", "MultiThématiques",
    "(http://www.Siicom.Com/odrazb/).", "iPod-Interface",
    "(KaDeWe)", "(EuGH)", "EinwanderInnen", "EuGH",
    "WhatsApp", "StGB", "(TeV)", "KundInnen", "HipHoper.",
    "BvS", "MitarbeiterInnen", "eBusiness", "MathInBraille",
    "GründerIdeenMesse.", "TecDax-Unternehmen", "DaShaun",
    "AStAs", "FreeYellow-Server", "McKeown,", "(VfGH)",
    "»Macworld«.", "MeinFernbus", "StarWriter", "StarCalc.",
    "(KiKA)", "WordPress", "SumTotal", "aXos", "(BvS).",
    "ThyssenKrupp,", "LinkedIn", "GdED", "ThyssenKrupp-Konzern",
    "TravelScout24", "(WoGeHe)", "McCann", "VfR", "YouTubern,",
    "McDonnell", "GfK-Experte.", "(KonTraG)",
    "»UnAufgefordert«", "AvW", "9ObA121/06v)", "derStandard.at",
    "S&P", "(BaFin)", "I.S.A.R.", "MoKo-Leiterin",
    "RecyclerEdition", "(IgE).", "EuroStoxx50-Index.",
    "WebShop", "NutzerInnen", "BellSouth", "McKinsey", "KfW",
    "SoHo", "»SOuth", "HOuston", "M:OOP",
    "»McDonaldisierung«",
    "\u200b:-)", "\u200b:)", "\u200b:-("})

(defn tokenize
  [s]
  (str/split s #"\s+"))


(defn ends-with-punctuation
  [s]
  (if-not (re-seq #"(?:[….?!])|(?:[.?!]«)$" s)
    [(subs s (max 0 (- (count s) 2)))]))

(defn unknown-abbreviations
  [s]
  (some->>
   (tokenize s)
   (filter #(str/ends-with? % "."))
   (remove abbreviation-whitelist)
   (distinct) (seq) (vec)))

(defn missing-whitespace
  [s]
  (some->>
   (tokenize s)
   (remove camel-case-whitelist)
   (filter (some-fn
            ;; e.g. aB ,A )A -A
            (partial re-seq #"[\p{Ll}\p{Pe}\p{Po}&&[^/\"']]\p{Lu}")
            ;; e.g. a( A( .( -(
            (partial re-seq #"[\p{Lu}\p{Ll}\p{Po}\p{Pd}]\p{Ps}")
            (partial re-seq #"«[^\p{Pe}\p{Po}]")
            (partial re-seq #"[^\p{Ps}]»")))
   (distinct) (seq) (vec)))

(defn redundant-whitespace
  [s]
  (some->>
   (concat
    (re-seq #"[»(/]\s" s)
    (re-seq #"\s[\p{Po}&&[^%&*†/…\"']]" s))
   (distinct) (seq) (vec)))

(def checks
  [[(xml/selector ".//d:Definition")
    unknown-abbreviations ::unknown-abbreviations]
   [(xml/selector ".//d:Beleg/d:Belegtext")
    ends-with-punctuation ::final-punctuation]
   [(xml/selector "(.//d:Beleg/d:Belegtext)|(.//d:Definition)")
    missing-whitespace ::missing-whitespace]
   [(xml/selector "(.//d:Beleg/d:Belegtext)|(.//d:Definition)")
    redundant-whitespace ::redundant-whitespace]])
