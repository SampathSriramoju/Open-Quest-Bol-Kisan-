import re
from typing import Optional, Dict, Any, List

class OfflineChemicalSafetyValidator:
    """
    On-device CIBRC & ICAR/PJTSAU Chemical Safety Engine.
    Operates 100% offline using locally embedded CIBRC verified regulatory tables.
    
    Validates 10 comprehensive safety parameters:
    1. Banned / Restricted chemicals (Monocrotophos, Endosulfan, DDT, etc.)
    2. Missing / Unverified product labels
    3. Incorrect product names
    4. Formulation mismatch (e.g. asking for 50% EC when registered is 18.5% SC)
    5. Unapproved crop application
    6. Target pest class mismatch (insecticide for fungal wilt or vice-versa)
    7. Overdose hazards (excessive concentration / lethal dosage)
    8. Ambiguous units (spoons, cups, packets without ml/g)
    9. Expired or superseded source documents
    10. Conflicting recommendations & unsafe tank mixes
    """

    OFFLINE_CIBRC_TABLE = {
        "chlorantraniliprole": {
            "approved_formulation": "18.5% SC",
            "aliases": ["chlorantraniliprole", "క్లోరాంట్రానిలీప్రోల్", "क्लोरांट्रानिलीप्रोल", "coragen", "కొరాజెన్", "కోరాజెన్", "कोराजन", "vesticor"],
            "type": "insecticide",
            "document_year": 2024,
            "document_status": "ACTIVE_CIBRC",
            "max_safe_dose_ml_per_acre": 80,
            "approved_crops": {
                "paddy": {
                    "aliases": ["paddy", "వరి", "धान", "rice", "चावल"],
                    "pest": "Stem Borer, Leaf Folder (కాండం తొలుచు పురుగు, ఆకు చుట్టు పురుగు)",
                    "pest_class": "insect",
                    "dose": "60 ml per acre",
                    "water": "200 litres",
                    "waiting_period": 14,
                    "warnings": "Do not spray near water bodies. Highly toxic to aquatic life. Wear protective mask."
                },
                "cotton": {
                    "aliases": ["cotton", "పత్తి", "कपास"],
                    "pest": "American Bollworm, Spotted Bollworm (గులాబీ రంగు పురుగు, కాయ తొలుచు పురుగు)",
                    "pest_class": "insect",
                    "dose": "60 ml per acre",
                    "water": "200 litres",
                    "waiting_period": 21,
                    "warnings": "Do not repeat spray within 15 days. Rotate with different MoA insecticides."
                }
            }
        },
        "emamectin benzoate": {
            "approved_formulation": "5% SG",
            "aliases": ["emamectin benzoate", "emamectin", "ఎమామెక్టిన్", "इमामेक्टिन", "proclaim", "ప్రోక్లెయిమ్", "प्रोक्लेम", "em-1"],
            "type": "insecticide",
            "document_year": 2023,
            "document_status": "ACTIVE_CIBRC",
            "max_safe_dose_g_per_acre": 120,
            "approved_crops": {
                "cotton": {
                    "aliases": ["cotton", "పత్తి", "कपास"],
                    "pest": "Bollworms, Spodoptera (కాయ తొలుచు పురుగులు)",
                    "pest_class": "insect",
                    "dose": "80-100 g per acre",
                    "water": "200 litres",
                    "waiting_period": 15,
                    "warnings": "Do not mix with alkaline substances. Spray in early morning or evening hours."
                },
                "chilli": {
                    "aliases": ["chilli", "మిరప", "మిర్చి", "मिर्च"],
                    "pest": "Fruit Borer, Thrips (కాయ తొలుచు పురుగు, తామర పురుగులు)",
                    "pest_class": "insect",
                    "dose": "80 g per acre",
                    "water": "200 litres",
                    "waiting_period": 3,
                    "warnings": "Do not spray during peak bee foraging hours."
                }
            }
        },
        "neem oil": {
            "approved_formulation": "10000 PPM (Azadirachtin 1%)",
            "aliases": ["neem oil", "వేప నూనె", "వేపనూనె", "నీమ్", "నీమ్ ఆయిల్", "नीम तेल", "azadirachtin", "అజాడిరక్టిన్"],
            "type": "botanical_insecticide",
            "document_year": 2024,
            "document_status": "ACTIVE_CIBRC",
            "max_safe_dose_ml_per_acre": 1000,
            "approved_crops": {
                "cotton": {
                    "aliases": ["cotton", "పత్తి", "कपास"],
                    "pest": "Whitefly, Aphids, Jassids (తెల్లదోమ, పేనుబంక, పచ్చదోమ)",
                    "pest_class": "insect",
                    "dose": "400-500 ml per acre",
                    "water": "200 litres",
                    "waiting_period": 0,
                    "warnings": "Safe botanical insecticide. Harmless to honeybees and predatory mites."
                },
                "paddy": {
                    "aliases": ["paddy", "వరి", "धान"],
                    "pest": "Brown Plant Hopper (BPH), Green Leaf Hopper",
                    "pest_class": "insect",
                    "dose": "500 ml per acre",
                    "water": "200 litres",
                    "waiting_period": 0,
                    "warnings": "Apply at the base of the paddy plants for hopper control."
                }
            }
        },
        "mancozeb": {
            "approved_formulation": "75% WP",
            "aliases": ["mancozeb", "మాంకోజెబ్", "मैनकोजेब", "dithane m-45", "dithane", "డైథేన్", "indofil m-45"],
            "type": "fungicide",
            "document_year": 2023,
            "document_status": "ACTIVE_CIBRC",
            "max_safe_dose_g_per_acre": 600,
            "approved_crops": {
                "cotton": {
                    "aliases": ["cotton", "పత్తి", "कपास"],
                    "pest": "Alternaria Leaf Spot, Grey Mildew (ఆకుమచ్చ తెగులు, బూడిద తెగులు)",
                    "pest_class": "fungus",
                    "dose": "400-500 g per acre",
                    "water": "200 litres",
                    "waiting_period": 30,
                    "warnings": "Contact protective fungicide. Spray uniformly on both sides of foliage."
                },
                "chilli": {
                    "aliases": ["chilli", "మిరప", "మిర్చి", "मिर्च"],
                    "pest": "Die back, Anthracnose, Fruit rot (కొమ్మ ఎండు, కాయకుళ్ళు తెగులు)",
                    "pest_class": "fungus",
                    "dose": "500-600 g per acre",
                    "water": "200 litres",
                    "waiting_period": 10,
                    "warnings": "Do not feed treated crop residues to livestock."
                }
            }
        },
        "monocrotophos": {
            "approved_formulation": "36% SL",
            "aliases": ["monocrotophos", "మోనోక్రోటోఫాస్", "మోనోక్రోటోఫాజ్", "मोनोक्रोटोफॉस", "nuvacron"],
            "banned": True,
            "ban_order": "CIBRC Gazette Order S.O. 3951(E) - Banned on all vegetable crops & restricted under Insecticides Act 1968"
        },
        "endosulfan": {
            "approved_formulation": "35% EC",
            "aliases": ["endosulfan", "ఎండోసల్ఫాన్", "इंडोसल्फान"],
            "banned": True,
            "ban_order": "Supreme Court of India Nationwide Complete Prohibition Order"
        },
        "ddt": {
            "approved_formulation": "50% WP",
            "aliases": ["ddt", "డిడిటి", "डीडीटी"],
            "banned": True,
            "ban_order": "Stockholm Convention Persistent Organic Pollutant - Completely Banned in Agriculture"
        }
    }

    UNAPPROVED_OFFLABEL_CROPS = [
        "మామిడి", "టమాట", "వంకాయ", "బంగాళదుంప", "సజ్జ", "జొన్న", "ఆపిల్", "ద్రాక్ష",
        "आम", "टमाटर", "बैंगन", "आलू", "बाजरा", "ज्वार", "सेब", "अंगूर",
        "mango", "tomato", "brinjal", "potato", "apple", "grape"
    ]

    BANNED_KEYWORD_TRIGGERS = [
        "banned", "నిషేధించబడిన", "ప్రతిబంధిత", "प्रतिबंधित", "అజ్ఞాత", "పేరు తెలియని", "अज्ञात", "unknown"
    ]

    EXPLICIT_CHEMICAL_NAMES = [
        "chlorantraniliprole", "emamectin", "neem oil", "monocrotophos", "endosulfan", "ddt", "mancozeb", "coragen", "proclaim", "dithane", "nuvacron", "vesticor",
        "క్లోరాంట్రానిలీప్రోల్", "మోనోక్రోటోఫాస్", "మోనోక్రోటోఫాజ్", "వేప నూనె", "వేపనూనె", "ఎండోసల్ఫాన్", "డిడిటి", "మాంకోజెబ్", "కొరాజెన్", "ప్రోక్లెయిమ్", "డైథేన్",
        "స్పూన్",
        "मोनोक्रोटोफॉस", "क्लोरांट्रानिलीप्रोल", "इमामेक्टिन", "नीम तेल", "मैनकोजेब", "कोराजन", "डीडीटी", "इंडोसल्फान"
    ]

    DOSAGE_INTENT_WORDS = [
        "మోతాదు", "డోస్", "ఎంత కలపాలి", "ఎంత వాడాలి", "ఎంత స్ప్రే చేయాలి", "పురుగుమందు", "కీటకనాశని", "చెంచా", "చెంచాలు", "స్పూన్", "డబ్బా", "లీటరుకు ఎంత",
        "ఖురాక్", "खुराक", "मात्रा", "कितना मिलाना", "कितना डालना", "कितना स्प्रे", "कीटनाशक", "चम्मच", "प्रति एकड़ कितना",
        "dose", "dosage", "how much ml", "how many grams", "how much per acre", "how much spray"
    ]

    AMBIGUOUS_UNIT_PATTERNS = [
        r"చెంచా", r"చెంచాలు", r"స్పూన్", r"స్పూన్లు", r"కప్పు", r"కప్పులు", r"ప్యాకెట్", r"ప్యాకెట్లు", r"డబ్బా", r"డ్రాప్స్",
        r"चम्मच", r"कप", r"पैकेट", r"डिब्बा", r"बूंद", r"spoon", r"spoons", r"cup", r"cups", r"packet", r"handful", r"drops"
    ]

    TANK_MIX_INCOMPATIBILITIES = [
        ("organophosphate", "lime"),
        ("neem oil", "bordeaux mixture"),
        ("emamectin", "alkaline"),
        ("chlorantraniliprole", "copper hydroxide")
    ]

    @classmethod
    def is_chemical_query(cls, text: str) -> bool:
        low = text.lower()

        # 1. General pesticide safety precautions, spraying time, mask PPE, disposal, waiting period definitions
        general_safety_procedure_words = [
            "జాగ్రత్తలు", "సావధానత", "సావధాని", "सावधानियां", "सावधानी", "సరియైన సమయం", "సరిగ్గా ఎప్పుడు", "सही समय", "कब करें",
            "మాస్క్", "ముఖానికి", "मास्क", "పారవేయాలి", "పారవేసే", "డిస్పోజల్", "disposal", "నిరీక్షణ కాలం", "నిరీక్షణ", "waiting period", "प्रतीक्षा अवधि"
        ]
        if any(w in low for w in general_safety_procedure_words):
            return False

        # 2. Known agronomic pests without a specific chemical brand inquiry go to RAG agronomy guide
        known_pests = ["కాండం తొలుచు", "stem borer", "గులాబీ రంగు", "pink bollworm", "సుడిదోమ", "bph", "రసం పీల్చే", "sucking pest", "तना छेदक"]
        has_specific_chem = any(
            any(alias in low for alias in data.get("aliases", [k]))
            for k, data in cls.OFFLINE_CIBRC_TABLE.items()
        )
        has_banned_or_fake = any(trig in low for trig in cls.BANNED_KEYWORD_TRIGGERS) or any(f in low for f in ["సూపర్ పాయిజన్", "super poison", "కోరా-మ్యాజిక్", "cora-magic", "cora magic"])
        has_formulation = bool(re.search(r"\d+(\.\d+)?\s*%\s*(ec|sc|sg|wp|sl|sp|fs|wdg)", low))

        if any(pest in low for pest in known_pests) and not (has_specific_chem or has_banned_or_fake or has_formulation):
            return False

        if has_banned_or_fake or has_specific_chem or has_formulation:
            return True

        if any(d_word in low for d_word in cls.DOSAGE_INTENT_WORDS):
            return True

        return False

    @classmethod
    def validate(cls, query: str, crop: Optional[str] = None, language: str = "te") -> Dict[str, Any]:
        low = query.lower()

        # Check Generic Banned / Unknown Chemical Query
        if any(trig in low for trig in cls.BANNED_KEYWORD_TRIGGERS):
            msg_te = "హెచ్చరిక: నిషేధించబడిన లేదా పేరు తెలియని అజ్ఞాత రసాయనాలను పంటలపై వాడటం చట్టరీత్యా నేరం మరియు పంటలకు ప్రాణాంతక ప్రమాదం. దయచేసి వ్యవసాయ అధికారిని సంప్రదించండి."
            msg_hi = "चेतावनी: प्रतिबंधित अथवा अज्ञात रसायनों का फसलों पर उपयोग अवैध एवं अत्यंत खतरनाक है। कृपया तुरंत कृषि अधिकारी से संपर्क करें।"
            return {
                "is_safe": False,
                "parameter_failed": "BANNED_CHEMICAL",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": msg_te if language == "te" else msg_hi,
                "escalate": True
            }

        # Parameter 1: Check Banned / Prohibited Chemicals in Registry
        for chem_key, data in cls.OFFLINE_CIBRC_TABLE.items():
            if any(alias in low for alias in data.get("aliases", [chem_key])):
                if data.get("banned"):
                    ban_order = data.get("ban_order", "CIBRC Regulatory Prohibition")
                    msg_te = (
                        f"హెచ్చరిక: {chem_key.title()} అనేది భారత ప్రభుత్వం & CIBRC ద్వారా పూర్తిగా నిషేధించబడిన రసాయనం ({ban_order}). "
                        f"దీనిని పంటలపై వాడటం చట్టరీత్యా నేరం మరియు ఆరోగ్యానికి అత్యంత ప్రమాదకరం. "
                        f"దయచేసి సమీపంలోని వ్యవసాయ అధికారి లేదా KVK నిపుణులను సంప్రదించండి."
                    )
                    msg_hi = (
                        f"चेतावनी: {chem_key.title()} भारत सरकार एवं CIBRC द्वारा पूर्णतः प्रतिबंधित कीटनाशक है ({ban_order})। "
                        f"फसलों पर इसका उपयोग अवैध और जानलेवा है। कृपया तुरंत कृषि अधिकारी या KVK विशेषज्ञ से संपर्क करें।"
                    )
                    return {
                        "is_safe": False,
                        "parameter_failed": "BANNED_CHEMICAL",
                        "scenario": "CHEMICAL_SAFETY_BLOCK",
                        "response": msg_te if language == "te" else msg_hi,
                        "escalate": True
                    }

        # Parameter 2 & 3: Match Approved Chemical Name / Label
        matched_data = None
        matched_name = None
        for key, data in cls.OFFLINE_CIBRC_TABLE.items():
            if any(alias in low for alias in data.get("aliases", [key])):
                matched_data = data
                matched_name = key
                break

        if not matched_data:
            # Query triggered chemical intent but product name is unverified or fabricated
            refusal_te = (
                "ఈ రసాయన మందు లేదా మోతాదు వివరాలు మా స్థానిక CIBRC ఆమోదిత లేబుల్ డేటాబేస్‌లో నిర్ధారించబడలేదు. "
                "పంట భద్రత దృష్ట్యా తప్పుడు లేదా కల్పిత మోతాదును సిఫార్సు చేయడం నిషిద్ధం. "
                "దయచేసి ఖచ్చితమైన బ్రాండ్ లేదా టెక్నికల్ పేరుతో అడగండి లేదా KVK వ్యవసాయ శాస్త్రవేత్తను సంప్రదించండి."
            )
            refusal_hi = (
                "इस रासायनिक दवा या उत्पाद का विवरण हमारे स्थानीय CIBRC अनुमोदित डेटाबेस में नहीं मिला। "
                "फसल सुरक्षा हेतु अपुष्ट रसायनों की खुराक नहीं दी जा सकती। "
                "कृपया सही तकनीकी नाम बताएं या KVK कृषि विशेषज्ञ से संपर्क करें।"
            )
            return {
                "is_safe": False,
                "parameter_failed": "MISSING_OR_UNVERIFIED_PRODUCT_LABEL",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": refusal_te if language == "te" else refusal_hi,
                "escalate": True
            }

        # Parameter 4: Formulation Mismatch Verification
        # Example: Chlorantraniliprole approved is 18.5% SC. If user mentions 50% EC or 25% EC, flag mismatch.
        approved_form = matched_data.get("approved_formulation", "")
        formulation_patterns = [r"(\d+(\.\d+)?\s*%\s*(ec|sc|sg|wp|sl|sp|fs|wdg))"]
        for pattern in formulation_patterns:
            matches = re.findall(pattern, low)
            for m in matches:
                detected_form = m[0].upper().replace(" ", "")
                appr_clean = approved_form.upper().replace(" ", "")
                if detected_form != appr_clean and not (appr_clean.startswith(detected_form)):
                    msg_te = (
                        f"ఫార్ములేషన్ హెచ్చరిక: {matched_name.title()} మందు కేవలం '{approved_form}' ఫార్ములేషన్‌లో మాత్రమే "
                        f"అధికారిక CIBRC ఆమోదం పొందింది. మీరు పేర్కొన్న '{m[0]}' ఫార్ములేషన్ ఆమోదించబడలేదు. "
                        f"తప్పుడు ఫార్ములేషన్ వాడటం వల్ల పంట మాడిపోయే ప్రమాదం ఉంది."
                    )
                    msg_hi = (
                        f"फॉर्मूलेशन चेतावनी: {matched_name.title()} केवल '{approved_form}' फॉर्मूलेशन में CIBRC द्वारा स्वीकृत है। "
                        f"आपके द्वारा उल्लेखित '{m[0]}' स्वीकृत नहीं है। गलत फॉर्मूलेशन से फसल जलने का जोखिम है।"
                    )
                    return {
                        "is_safe": False,
                        "parameter_failed": "INCORRECT_FORMULATION",
                        "scenario": "CHEMICAL_SAFETY_BLOCK",
                        "response": msg_te if language == "te" else msg_hi,
                        "escalate": True
                    }

        # Parameter 5: Unapproved Crop Application
        norm_crop = (crop or "").lower().strip()
        approved_crops = matched_data.get("approved_crops", {})
        target_info = None

        for c_name, c_info in approved_crops.items():
            if any(ca in norm_crop or ca in low for ca in c_info.get("aliases", [c_name])):
                target_info = (c_name, c_info)
                break

        if not target_info:
            if any(oc in low or oc in norm_crop for oc in cls.UNAPPROVED_OFFLABEL_CROPS):
                crops_str = ", ".join(approved_crops.keys()).title()
                msg_te = (
                    f"ఈ మందు ({matched_name.title()}) కేవలం {crops_str} పంటలకు మాత్రమే అధికారికంగా సిఫార్సు చేయబడింది. "
                    f"మీరు అడిగిన పంటకు లేబుల్ క్లెయిమ్ లేదు. దయచేసి మీ పంట పేరును స్పష్టంగా తెలపండి."
                )
                msg_hi = (
                    f"यह दवा ({matched_name.title()}) केवल {crops_str} फसलों के लिए CIBRC द्वारा अनुमोदित है। "
                    f"कृपया अपनी मान्य फसल का नाम स्पष्ट करें।"
                )
                return {
                    "is_safe": False,
                    "parameter_failed": "UNAPPROVED_CROP",
                    "scenario": "INCOMPLETE_EVIDENCE",
                    "response": msg_te if language == "te" else msg_hi,
                    "escalate": False
                }

            # General dose across registered crops when no specific crop was named
            crop_details_te = []
            crop_details_hi = []
            for cn, ci in approved_crops.items():
                crop_details_te.append(f"{cn.title()}: ఎకరాకు {ci['dose']} ({ci['water']} నీరు)")
                crop_details_hi.append(f"{cn.title()}: प्रति एकड़ {ci['dose']} ({ci['water']} पानी)")
            details_str_te = "; ".join(crop_details_te)
            details_str_hi = "; ".join(crop_details_hi)

            resp_te = (
                f"CIBRC ఆమోదిత లేబుల్ ప్రకారం {matched_name.title()} ({matched_data.get('approved_formulation', '')}) మోతాదు వివరాలు: "
                f"{details_str_te}. [ఆధారం: CIBRC Approved Product Label]"
            )
            resp_hi = (
                f"CIBRC अनुमोदित उत्पाद लेबल अनुसार {matched_name.title()} ({matched_data.get('approved_formulation', '')}) की अनुशंसित खुराक: "
                f"{details_str_hi}। [स्रोत: CIBRC Approved Product Label]"
            )
            return {
                "is_safe": True,
                "scenario": "EVIDENCE_AVAILABLE",
                "parameter_failed": None,
                "response": resp_te if language == "te" else resp_hi,
                "citation_doc": f"CIBRC Approved Product Label ({matched_name.title()} {matched_data.get('approved_formulation', '')})",
                "citation_page": 1,
                "escalate": False
            }

        c_name, c_info = target_info

        # Parameter 6: Target Pest Class Mismatch
        # E.g. Using Insecticide for Fungal Wilt/Blast or Fungicide for Bollworm/Stem borer
        fungal_triggers = ["తెగులు", "ఆకుమచ్చ", "బూడిద", "కుళ్ళు", "ఎండు", "फफूंद", "झुलसा", "फंगस", "fungus", "wilt", "blast", "rot", "blight"]
        insect_triggers = ["పురుగు", "తొలుచు", "దోమ", "నల్లి", "కీటకం", "कीड़ा", "इल्ली", "सूंडी", "माहू", "worm", "borer", "caterpillar", "fly"]
        
        chem_type = matched_data.get("type", "")
        if chem_type in ["insecticide", "botanical_insecticide"] and any(ft in low for ft in fungal_triggers) and not any(it in low for it in insect_triggers):
            msg_te = (
                f"లక్ష్య పురుగు/తెగులు వ్యత్యాసం: {matched_name.title()} అనేది ఒక కీటకనాశిని (Insecticide). "
                f"ఇది శిలీంధ్ర/ఫంగస్ తెగుళ్లకు (Fungal Diseases) పనిచేయదు. శిలీంధ్ర తెగుళ్లకు తగిన శిలీంధ్రనాశిని (Fungicide) వాడాలి."
            )
            msg_hi = (
                f"लक्षित कीट असंगति: {matched_name.title()} एक कीटनाशक (Insecticide) है। "
                f"यह फफूंद जनित रोगों (Fungal Diseases) पर प्रभावी नहीं है। फफूंद नाशक का उपयोग करें।"
            )
            return {
                "is_safe": False,
                "parameter_failed": "TARGET_PEST_MISMATCH",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": msg_te if language == "te" else msg_hi,
                "escalate": True
            }
        elif chem_type == "fungicide" and any(it in low for it in insect_triggers) and not any(ft in low for ft in fungal_triggers):
            msg_te = (
                f"లక్ష్య వ్యత్యాసం: {matched_name.title()} అనేది శిలీంధ్రనాశిని (Fungicide). "
                f"ఇది పురుగులకు లేదా కీటకాలకు (Insects/Worms) పనిచేయదు. పురుగుల నివారణకు తగిన కీటకనాశిని వాడాలి."
            )
            msg_hi = (
                f"लक्षित कीट असंगति: {matched_name.title()} एक फफूंदनाशक (Fungicide) है। "
                f"यह इल्ली या कीटों पर काम नहीं करेगा।"
            )
            return {
                "is_safe": False,
                "parameter_failed": "TARGET_PEST_MISMATCH",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": msg_te if language == "te" else msg_hi,
                "escalate": True
            }

        # Parameter 7: Overdose Hazard Detection
        # Check if user query mentions specific numbers far exceeding max safe dose per acre
        dose_nums = re.findall(r"(\d+)\s*(ml|మి.లీ|గ్రా|గ్రాములు|gm|g|లీటర్|litre|లీ)", low)
        max_safe_ml = matched_data.get("max_safe_dose_ml_per_acre", 500)
        max_safe_g = matched_data.get("max_safe_dose_g_per_acre", 500)
        for num_str, unit in dose_nums:
            val = float(num_str)
            if "లీ" in unit or "litre" in unit:
                val *= 1000 # convert to ml
            if (val > max_safe_ml * 2.5 and "g" not in unit and "గ్రా" not in unit) or (val > max_safe_g * 2.5 and ("g" in unit or "గ్రా" in unit)):
                msg_te = (
                    f"అధిక మోతాదు ప్రమాదం (Overdose Hazard): మీరు పేర్కొన్న {num_str} {unit} మోతాదు అత్యంత ప్రమాదకరమైనది! "
                    f"CIBRC అధికారిక అనుమతి ప్రకారం {matched_name.title()} ఎకరాకు కేవలం {c_info['dose']} మాత్రమే వాడాలి. "
                    f"అదనపు మోతాదు పిచికారీ చేస్తే పంట విషపూరితమై మాడిపోతుంది."
                )
                msg_hi = (
                    f"अत्यधिक खुराक चेतावनी (Overdose Alert): {num_str} {unit} की मात्रा अत्यधिक खतरनाक है! "
                    f"CIBRC अनुसार {matched_name.title()} की अनुशंसित मात्रा केवल {c_info['dose']} प्रति एकड़ है।"
                )
                return {
                    "is_safe": False,
                    "parameter_failed": "OVERDOSE_HAZARD",
                    "scenario": "CHEMICAL_SAFETY_BLOCK",
                    "response": msg_te if language == "te" else msg_hi,
                    "escalate": True
                }

        # Parameter 8: Ambiguous Units Detection
        # Detect household utensil terms: spoons, cups, packets
        has_ambiguous_unit = any(re.search(pat, low) for pat in cls.AMBIGUOUS_UNIT_PATTERNS)
        ambiguous_warning_te = ""
        ambiguous_warning_hi = ""
        if has_ambiguous_unit:
            ambiguous_warning_te = "గమనిక: పురుగుమందులను ఇళ్లలోని చెంచాలు లేదా కప్పులతో ఎప్పుడూ కొలవరాదు. ఖచ్చితమైన కొలత పాత్రను మాత్రమే వాడాలి. "
            ambiguous_warning_hi = "नोट: कीटनाशकों को कभी भी घरेलू चम्मच या कप से न नापें। केवल सटीक मापक उपकरण का उपयोग करें। "

        # Parameter 9: Document Status & Expiry Check
        doc_status = matched_data.get("document_status", "ACTIVE_CIBRC")
        doc_year = matched_data.get("document_year", 2024)
        if doc_status == "EXPIRED" or doc_year < 2019:
            msg_te = "క్షమించండి, ఈ రసాయన పత్రం కాలపరిమితి ముగిసింది (Expired Document). అప్‌డేట్ చేసిన లేబుల్ కోసం వ్యవసాయ అధికారిని సంప్రదించండి."
            msg_hi = "क्षमा करें, इस रसायन का अनुमोदित दस्तावेज पुराना हो चुका है। कृपया नए दिशानिर्देश हेतु अधिकारी से मिलें।"
            return {
                "is_safe": False,
                "parameter_failed": "EXPIRED_SUPERSEDED_DOCUMENTS",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": msg_te if language == "te" else msg_hi,
                "escalate": True
            }

        # Parameter 10: Tank Mix Incompatibility Check
        alkaline_triggers = ["సున్నం", "బోర్డో", "బోర్డాక్స్", "కాపర్", "చూనా", "lime", "bordeaux", "copper"]
        if matched_name in ["neem oil", "emamectin benzoate"] and any(alt in low for alt in alkaline_triggers):
            msg_te = (
                f"ట్యాంక్ మిక్సింగ్ వ్యతిరేకత (Incompatible Tank Mix): {matched_name.title()} మందును "
                f"సున్నం, బోర్డో మిశ్రమం లేదా క్షార పదార్థాలతో కలపరాదు. ఇది రసాయన చర్య జరిగి మందు పనితీరును నాశనం చేస్తుంది."
            )
            msg_hi = (
                f"टैंक मिक्सिंग चेतावनी: {matched_name.title()} को चूना या बोर्डो मिश्रण के साथ कभी न मिलाएं। यह रासायनिक प्रभाव को नष्ट कर देता है।"
            )
            return {
                "is_safe": False,
                "parameter_failed": "INCOMPATIBLE_TANK_MIX",
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "response": msg_te if language == "te" else msg_hi,
                "escalate": True
            }

        # Safe Verified Recommendation
        resp_te = (
            f"{ambiguous_warning_te}"
            f"సిఫార్సు చేసిన మోతాదు ({matched_name.title()} {matched_data['approved_formulation']}): "
            f"ఎకరాకు {c_info['dose']}ను {c_info['water']} నీటిలో కలిపి పిచికారీ చేయాలి. "
            f"లక్ష్యం: {c_info['pest']}. "
            f"కోతకు ముందు నిరీక్షణ వ్యవధి: {c_info['waiting_period']} రోజులు. "
            f"హెచ్చరిక: {c_info['warnings']} "
            f"[ఆధారం: CIBRC అధికారిక లేబుల్ & PJTSAU రికార్డులు {doc_year}]"
        )
        resp_hi = (
            f"{ambiguous_warning_hi}"
            f"अनुशंसित खुराक ({matched_name.title()} {matched_data['approved_formulation']}): "
            f"प्रति एकड़ {c_info['dose']} को {c_info['water']} पानी में मिलाकर छिड़कें। "
            f"लक्षित कीट: {c_info['pest']}। "
            f"प्रतीक्षा अवधि: {c_info['waiting_period']} दिन। "
            f"चेतावनी: {c_info['warnings']} "
            f"[स्रोत: CIBRC अनुमोदित उत्पाद लेबल एवं PJTSAU रिकॉर्ड {doc_year}]"
        )

        return {
            "is_safe": True,
            "scenario": "EVIDENCE_AVAILABLE",
            "parameter_failed": None,
            "response": resp_te if language == "te" else resp_hi,
            "citation_doc": f"CIBRC Approved Product Label ({matched_name.title()} {matched_data['approved_formulation']})",
            "citation_page": 1,
            "escalate": False
        }
